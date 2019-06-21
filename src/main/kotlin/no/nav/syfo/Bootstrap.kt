package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments
import no.kith.xmlstds.apprec._2004_11_21.XMLAppRec
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.apprec._2004_11_21.XMLCV as AppRecCV
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.metrics.APPREC_COUNTER
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.rules.Rule
import no.nav.syfo.rules.RuleData
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.executeFlow
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jms.Connection
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import javax.xml.bind.Marshaller

fun doReadynessCheck(): Boolean {
    return true
}

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

data class ApplicationState(
    var running: Boolean = true,
    var initialized: Boolean = false
)

val coroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

val log = LoggerFactory.getLogger("nav.syfopale-application")!!

@KtorExperimentalAPI
fun main() = runBlocking(coroutineContext) {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())

    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
        connection.start()

    val listeners = (0.until(env.applicationThreads)).map {
        launch {
            try {
                createListener(applicationState, env, connection, credentials)
            } finally {
                applicationState.running = false
            }
        }
    }.toList()

    applicationState.initialized = true

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })

    listeners.forEach { it.join() }
    }
}

@KtorExperimentalAPI
suspend fun createListener(
    applicationState: ApplicationState,
    env: Environment,
    connection: Connection,
    credentials: VaultCredentials
) {
    Jedis(env.redishost, 6379).use { jedis ->
        val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        val inputconsumer = session.consumerForQueue(env.inputQueueName)
        val receiptProducer = session.producerForQueue(env.apprecQueueName)
        val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)

        val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
        val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient)
        val sarClient = SarClient(env.kuhrSarApiUrl, credentials)

        blockingApplicationLogic(
            applicationState,
            inputconsumer,
            jedis,
            session,
            env,
            receiptProducer,
            backoutProducer,
            sarClient,
            aktoerIdClient,
            credentials
            )
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    inputconsumer: MessageConsumer,
    jedis: Jedis,
    session: Session,
    env: Environment,
    receiptProducer: MessageProducer,
    backoutProducer: MessageProducer,
    kuhrSarClient: SarClient,
    aktoerIdClient: AktoerIdClient,
    credentials: VaultCredentials
) = coroutineScope {
    loop@ while (applicationState.running) {
        val message = inputconsumer.receiveNoWait()
        if (message == null) {
            delay(100)
            continue
        }

        var logValues = arrayOf(
            StructuredArguments.keyValue("mottakId", "missing"),
            StructuredArguments.keyValue("organizationNumber", "missing"),
            StructuredArguments.keyValue("msgId", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
        }
        try {
            val inputMessageText = when (message) {
                is TextMessage -> message.text
                else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
            }
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
            val msgHead = fellesformat.get<XMLMsgHead>()
            val ediLoggId = receiverBlock.ediLoggId
            val msgId = msgHead.msgInfo.msgId
            val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
            val legeerklaring = extractLegeerklaering(fellesformat)
            val sha256String = sha256hashstring(legeerklaring)
            val personNumberPatient = extractPersonIdent(legeerklaring)!!
            val legekontorOrgName = extractSenderOrganisationName(fellesformat)
            val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur

            INCOMING_MESSAGE_COUNTER.inc()
            val requestLatency = REQUEST_TIME.startTimer()

            logValues = arrayOf(
                StructuredArguments.keyValue("mottakId", ediLoggId),
                StructuredArguments.keyValue("organizationNumber", legekontorOrgNr),
                StructuredArguments.keyValue("msgId", msgId)
            )

            log.info("Received message, $logKeys", *logValues)

            val aktoerIdsDeferred = async {
                aktoerIdClient.getAktoerIds(
                    listOf(personNumberDoctor,
                        personNumberPatient),
                    msgId, credentials.serviceuserUsername)
            }

            val samhandlerInfo = kuhrSarClient.getSamhandler(personNumberDoctor)
            val samhandlerPraksis = findBestSamhandlerPraksis(
                samhandlerInfo,
                legekontorOrgName)?.samhandlerPraksis

            try {
                val redisSha256String = jedis.get(sha256String)
                val redisEdiloggid = jedis.get(ediLoggId)

                if (redisSha256String != null) {
                    log.warn(
                        "Message with {} marked as duplicate $logKeys",
                        StructuredArguments.keyValue("originalEdiLoggId", redisSha256String), *logValues
                    )
                    sendReceipt(
                        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                            createApprecError(
                                "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                                        "Skal ikke sendes på nytt."
                            )
                        )
                    )
                    log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueueName, *logValues)
                    continue
                } else if (redisEdiloggid != null) {
                    log.warn(
                        "Message with {} marked as duplicate $logKeys",
                        StructuredArguments.keyValue("originalEdiLoggId", redisEdiloggid), *logValues
                    )
                    sendReceipt(
                        session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                            createApprecError(
                                "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                                        "Skal ikke sendes på nytt."
                            )
                        )
                    )
                    log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueueName, *logValues)
                    continue
                } else {
                    jedis.setex(ediLoggId, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
                    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
                }
            } catch (connectionException: JedisConnectionException) {
                log.warn("Unable to contact redis, will allow possible duplicates.", connectionException)
            }

            val aktoerIds = aktoerIdsDeferred.await()
            val patientIdents = aktoerIds[personNumberPatient]
            val doctorIdents = aktoerIds[personNumberDoctor]

            if (patientIdents == null || patientIdents.feilmelding != null) {
                log.info("Patient not found i aktorRegister $logKeys, {}", *logValues,
                    StructuredArguments.keyValue("errorMessage", patientIdents?.feilmelding ?: "No response for FNR")
                )
                sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                    no.nav.syfo.apprec.createApprecError("Pasienten er ikkje registrert i folkeregisteret")
                ))
                log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueueName, *logValues)
                INVALID_MESSAGE_NO_NOTICE.inc()
                continue@loop
            }
            if (doctorIdents == null || doctorIdents.feilmelding != null) {
                log.info("Doctor not found i aktorRegister $logKeys, {}", *logValues,
                    StructuredArguments.keyValue("errorMessage", doctorIdents?.feilmelding ?: "No response for FNR")
                )
                sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                    no.nav.syfo.apprec.createApprecError("Behandler er ikkje registrert i folkeregisteret")
                ))
                log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueueName, *logValues)
                INVALID_MESSAGE_NO_NOTICE.inc()
                continue@loop
            }

            val validationRuleResults: List<Rule<Any>> = listOf<List<Rule<RuleData<RuleMetadata>>>>(
                ValidationRuleChain.values().toList()
            ).flatten().executeFlow(legeerklaring, RuleMetadata(
                receivedDate = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime().toLocalDateTime(),
                signatureDate = msgHead.msgInfo.genDate,
                patientPersonNumber = personNumberPatient,
                legekontorOrgnr = legekontorOrgNr,
                tssid = samhandlerPraksis?.tss_ident)
            )

            val results = listOf(validationRuleResults).flatten()

            log.info("Rules hit {}, $logKeys", results.map { it.name }, *logValues)

            when (results.firstOrNull()) {
                null -> log.info("Message has rules hit to $logKeys", *logValues)
                else -> log.info("Message has NO rules hit to $logKeys", *logValues)
            }
            } catch (e: Exception) {
                log.error("Exception caught while handling message, sending to backout $logKeys", *logValues, e)
                backoutProducer.send(message)
            }
        }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(readynessCheck = ::doReadynessCheck, livenessCheck = { applicationState.running })
    }
}

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
        it.typeId.v == "ENH"
    }

fun extractLegeerklaering(fellesformat: XMLEIFellesformat): Legeerklaring =
    fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as Legeerklaring

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun createApprecError(textToTreater: String): AppRecCV = AppRecCV().apply {
    dn = textToTreater
    v = "2.16.578.1.12.4.1.1.8221"
    s = "X99"
}

fun sha256hashstring(legeerklaring: Legeerklaring): String =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(legeerklaring))
        .fold("") { str, it -> str + "%02x".format(it) }

fun sendReceipt(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    apprecStatus: ApprecStatus,
    apprecErrors: List<no.kith.xmlstds.apprec._2004_11_21.XMLCV> = listOf()
) {
    APPREC_COUNTER.inc()
    receiptProducer.send(session.createTextMessage().apply {
        val apprec = createApprec(fellesformat, apprecStatus)
        apprec.get<XMLAppRec>().error.addAll(apprecErrors)
        text = apprecMarshaller.toString(apprec)
    })
}

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

fun extractPersonIdent(legeerklaering: Legeerklaring): String? =
    legeerklaering.pasientopplysninger.pasient.fodselsnummer

fun extractSenderOrganisationName(fellesformat: XMLEIFellesformat): String =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.organisationName ?: ""