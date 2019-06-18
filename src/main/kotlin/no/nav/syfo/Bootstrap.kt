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
import no.nav.syfo.metrics.APPREC_COUNTER
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisSentinelPool
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

val redisMasterName = "mymaster"
val redisHost = "rfs-redis-syfosmmottak" // TODO: Do this properly with naiserator

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
                createListener(applicationState, env, connection)
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
    connection: Connection
) {
    JedisSentinelPool(redisMasterName, setOf("$redisHost:26379")).resource.use { jedis ->
        val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        val inputconsumer = session.consumerForQueue(env.inputQueueName)
        val receiptProducer = session.producerForQueue(env.apprecQueueName)
        blockingApplicationLogic(applicationState, inputconsumer, jedis, session, env, receiptProducer)
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    inputconsumer: MessageConsumer,
    jedis: Jedis,
    session: Session,
    env: Environment,
    receiptProducer: MessageProducer
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

            INCOMING_MESSAGE_COUNTER.inc()

            logValues = arrayOf(
                StructuredArguments.keyValue("mottakId", ediLoggId),
                StructuredArguments.keyValue("organizationNumber", legekontorOrgNr),
                StructuredArguments.keyValue("msgId", msgId)
            )

            log.info("Received message, $logKeys", *logValues)

            try {
                val redisSha256String = jedis.get(sha256String)
                val redisEdiloggid = jedis.get(ediLoggId)

                if (redisSha256String != null) {
                    log.warn("Message with {} marked as duplicate $logKeys",
                        StructuredArguments.keyValue("originalEdiLoggId", redisSha256String), *logValues)
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                        createApprecError("Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                                "Skal ikke sendes på nytt.")))
                    log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueueName, *logValues)
                    continue
                } else if (redisEdiloggid != null) {
                    log.warn("Message with {} marked as duplicate $logKeys",
                        StructuredArguments.keyValue("originalEdiLoggId", redisEdiloggid), *logValues)
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                        createApprecError("Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                                "Skal ikke sendes på nytt.")))
                    log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueueName, *logValues)
                    continue
                } else {
                    jedis.setex(ediLoggId, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
                    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
                }
            } catch (connectionException: JedisConnectionException) {
                log.warn("Unable to contact redis, will allow possible duplicates.", connectionException)
            }
        } catch (e: Exception) {
        log.error("Exception caught while handling message $logKeys", *logValues, e)
        }

        delay(100)
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