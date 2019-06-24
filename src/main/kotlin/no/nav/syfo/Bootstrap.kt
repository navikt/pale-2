package no.nav.syfo

import com.ctc.wstx.exc.WstxException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments
import no.kith.xmlstds.apprec._2004_11_21.XMLAppRec
import no.kith.xmlstds.msghead._2006_05_24.XMLHealthcareProfessional
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.helse.legeerklaering.AktueltTiltak
import no.nav.helse.legeerklaering.Arbeidssituasjon
import no.nav.helse.legeerklaering.DiagnoseArbeidsuforhet
import no.nav.helse.legeerklaering.Enkeltdiagnose
import no.nav.helse.legeerklaering.ForslagTiltak
import no.kith.xmlstds.apprec._2004_11_21.XMLCV as AppRecCV
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.helse.legeerklaering.PlanUtredBehandle
import no.nav.helse.legeerklaering.VurderingFunksjonsevne
import no.nav.syfo.api.LegeSuspensjonClient
import no.nav.syfo.api.PdfgenClient
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.helpers.retry
import no.nav.syfo.metrics.APPREC_COUNTER
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.Fagmelding
import no.nav.syfo.model.ForslagTilTiltak
import no.nav.syfo.model.FunksjonsOgArbeidsevne
import no.nav.syfo.model.Henvisning
import no.nav.syfo.model.Kontakt
import no.nav.syfo.model.Pasient
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.Plan
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Signatur
import no.nav.syfo.model.Status
import no.nav.syfo.model.SykdomsOpplysninger
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.rules.HPRRuleChain
import no.nav.syfo.rules.LegesuspensjonRuleChain
import no.nav.syfo.rules.PostTPSRuleChain
import no.nav.syfo.rules.Rule
import no.nav.syfo.rules.RuleData
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.executeFlow
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException
import java.io.IOException
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
import org.apache.cxf.binding.soap.SoapMessage
import org.apache.cxf.message.Message
import org.apache.cxf.phase.Phase
import org.apache.cxf.ws.addressing.WSAddressingFeature
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import no.nhn.schemas.reg.hprv2.IHPR2Service
import no.nhn.schemas.reg.hprv2.Person
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person as TPSPerson

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

val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()

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
        val pdfgenClient = PdfgenClient(env.pdfgen)

        val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
            port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceURL) }
        }

        val helsepersonellV1 = createPort<IHPR2Service>(env.helsepersonellv1EndpointURL) {
            proxy {
                // TODO: Contact someone about this hacky workaround
                // talk to HDIR about HPR about they claim to send a ISO-8859-1 but its really UTF-8 payload
                val interceptor = object : AbstractSoapInterceptor(Phase.RECEIVE) {
                    override fun handleMessage(message: SoapMessage?) {
                        if (message != null)
                            message[Message.ENCODING] = "utf-8"
                    }
                }

                inInterceptors.add(interceptor)
                inFaultInterceptors.add(interceptor)
                features.add(WSAddressingFeature())
            }

            port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceURL) }
        }

        val legeSuspensjonClient = LegeSuspensjonClient(env.legeSuspensjonEndpointURL, credentials, oidcClient)

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
            credentials,
            personV3,
            helsepersonellV1,
            legeSuspensjonClient,
            pdfgenClient
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
    credentials: VaultCredentials,
    personV3: PersonV3,
    helsepersonellv1: IHPR2Service,
    legeSuspensjonClient: LegeSuspensjonClient,
    pdfgenClient: PdfgenClient
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

            val patient = fetchPerson(personV3, personNumberPatient)
            val tpsRuleResults = PostTPSRuleChain.values().executeFlow(legeerklaring, patient.await())

            val signaturDatoString = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(msgHead.msgInfo.genDate)
            val doctorSuspend = legeSuspensjonClient.checkTherapist(personNumberDoctor, msgId, signaturDatoString).suspendert
            val doctorRuleResults = LegesuspensjonRuleChain.values().executeFlow(legeerklaring, doctorSuspend)

            val doctor = fetchDoctor(helsepersonellv1, personNumberDoctor).await()
            val hprRuleResults = HPRRuleChain.values().executeFlow(legeerklaring, doctor)

            val results = listOf(
                validationRuleResults,
                tpsRuleResults,
                hprRuleResults,
                doctorRuleResults
            ).flatten()

            log.info("Rules hit {}, $logKeys", results.map { it.name }, *logValues)

            val legeerklaering = extractLegeerklaering(fellesformat)
            val plan = legeerklaering.planUtredBehandle
            val forslagTiltak = legeerklaering.forslagTiltak
            val typeLegeerklaering = legeerklaering.legeerklaringGjelder[0].typeLegeerklaring.toInt()
            val funksjonsevne = legeerklaering.vurderingFunksjonsevne
            val prognose = legeerklaering.prognose
            val healthcareProfessional = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.healthcareProfessional

            val validationResult = validationResult(results)
            RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()

            val pdfPayload = createPdfPayload(
                legeerklaering,
                plan,
                forslagTiltak,
                typeLegeerklaering,
                funksjonsevne,
                prognose,
                healthcareProfessional,
                fellesformat,
                validationResult)

            when (results.firstOrNull()) {
                null -> log.info("Message has NO rules hit $logKeys", *logValues)
                else -> log.info("Message has rules hit $logKeys", *logValues)
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

fun CoroutineScope.fetchPerson(personV3: PersonV3, ident: String): Deferred<TPSPerson> = async {
    retry(
        callName = "tps_hent_person",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L, 60000L),
        legalExceptions = *arrayOf(IOException::class, WstxException::class)
    ) {
        personV3.hentPerson(HentPersonRequest()
            .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(ident)))
        ).person
    }
}

fun CoroutineScope.fetchDoctor(hprService: IHPR2Service, doctorIdent: String): Deferred<Person> = async {
    retry(
        callName = "hpr_hent_person_med_personnummer",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions = *arrayOf(IOException::class, WstxException::class)
    ) {
        hprService.hentPersonMedPersonnummer(doctorIdent, datatypeFactory.newXMLGregorianCalendar(GregorianCalendar()))
    }
}

fun createPdfPayload(
    legeerklaring: Legeerklaring,
    plan: PlanUtredBehandle,
    forslagTiltak: ForslagTiltak,
    typeLegeerklaering: Int,
    funksjonsevne: VurderingFunksjonsevne,
    prognose: no.nav.helse.legeerklaering.Prognose,
    healthcareProfessional: XMLHealthcareProfessional?,
    fellesformat: XMLEIFellesformat,
    validationResult: ValidationResult
): PdfPayload = PdfPayload(
     fagmelding = Fagmelding(
             arbeidsvurderingVedSykefravaer = typeLegeerklaering == LegeerklaeringType.Arbeidsevnevurdering.type,
             arbeidsavklaringsPenger = typeLegeerklaering == LegeerklaeringType.Arbeidsavklaringspenger.type,
             yrkesrettetAttfoering = typeLegeerklaering == LegeerklaeringType.YrkesrettetAttfoering.type,
             ufoerepensjon = typeLegeerklaering == LegeerklaeringType.Ufoerepensjon.type,
             pasient = legeerklaeringToPasient(legeerklaring),
             sykdomsOpplysninger = mapLegeerklaeringToSykdomDiagnose(legeerklaring.diagnoseArbeidsuforhet),
             plan = Plan(
                 utredning = plan?.henvistUtredning?.let {
                     Henvisning(
                         tekst = it.spesifikasjon,
                         dato = it.henvistDato.toGregorianCalendar().toZonedDateTime(),
                         antattVentetIUker = it.antattVentetid.toInt()
                     )
                 },
                 behandling = plan?.henvistBehandling?.let {
                     Henvisning(
                         tekst = it.spesifikasjon,
                         dato = it.henvistDato.toGregorianCalendar().toZonedDateTime(),
                         antattVentetIUker = it.antattVentetid.toInt()
                     )
                 },
                 utredningsplan = plan?.utredningsPlan,
                 behandlingsplan = plan?.behandlingsPlan,
                 vurderingAvTidligerePlan = plan?.nyVurdering,
                 naarSpoerreOmNyeLegeopplysninger = plan?.nyeLegeopplysninger,
                 videreBehandlingIkkeAktuellGrunn = plan?.ikkeVidereBehandling
             ),
             forslagTilTiltak = ForslagTilTiltak(
                 behov = forslagTiltak.aktueltTiltak.isEmpty(),
                 kjoepAvHelsetjenester = TypeTiltak.KjoepHelsetjenester in forslagTiltak.aktueltTiltak,
                 reisetilskudd = TypeTiltak.Reisetilskudd in forslagTiltak.aktueltTiltak,
                 aktivSykMelding = TypeTiltak.AktivSykemelding in forslagTiltak.aktueltTiltak,
                 hjelpemidlerArbeidsplassen = TypeTiltak.HjelpemidlerArbeidsplass in forslagTiltak.aktueltTiltak,
                 arbeidsavklaringsPenger = TypeTiltak.Arbeidsavklaringspenger in forslagTiltak.aktueltTiltak,
                 friskemeldingTilArbeidsformidling = TypeTiltak.FriskemeldingTilArbeidsformidling in forslagTiltak.aktueltTiltak,
                 andreTiltak = forslagTiltak.aktueltTiltak.find { it.typeTiltak == TypeTiltak.AndreTiltak }?.hvilkeAndreTiltak,
                 naermereOpplysninger = forslagTiltak.opplysninger,
                 tekst = forslagTiltak.begrensningerTiltak ?: forslagTiltak.begrunnelseIkkeTiltak
             ),
             funksjonsOgArbeidsevne = FunksjonsOgArbeidsevne(
                 vurderingFunksjonsevne = funksjonsevne.funksjonsevne,
                 iIntektsgivendeArbeid = ArbeidssituasjonType.InntektsgivendeArbeid in funksjonsevne.arbeidssituasjon,
                 hjemmearbeidende = ArbeidssituasjonType.Hjemmearbeidende in funksjonsevne.arbeidssituasjon,
                 student = ArbeidssituasjonType.Student in funksjonsevne.arbeidssituasjon,
                 annetArbeid = funksjonsevne.arbeidssituasjon?.find { it.arbeidssituasjon?.let {
                     it.toInt() == ArbeidssituasjonType.Annet?.type
                 } ?: false }?.annenArbeidssituasjon ?: "",
                 kravTilArbeid = funksjonsevne?.kravArbeid,
                 kanGjenopptaTidligereArbeid = funksjonsevne.vurderingArbeidsevne?.gjenopptaArbeid?.toInt() == 1,
                 kanGjenopptaTidligereArbeidNaa = funksjonsevne.vurderingArbeidsevne?.narGjenopptaArbeid?.toInt() == 1,
                 kanGjenopptaTidligereArbeidEtterBehandling = funksjonsevne.vurderingArbeidsevne?.narGjenopptaArbeid?.toInt() == 2,
                 kanTaAnnetArbeid = funksjonsevne.vurderingArbeidsevne?.taAnnetArbeid?.toInt() == 1,
                 kanTaAnnetArbeidNaa = funksjonsevne.vurderingArbeidsevne?.narTaAnnetArbeid?.toInt() == 1,
                 kanTaAnnetArbeidEtterBehandling = funksjonsevne.vurderingArbeidsevne?.narTaAnnetArbeid?.toInt() == 2,
                 kanIkkeINaaverendeArbeid = funksjonsevne.vurderingArbeidsevne?.ikkeGjore,
                 kanIkkeIAnnetArbeid = funksjonsevne.vurderingArbeidsevne?.hensynAnnetYrke
             ),
             prognose = Prognose(
                 vilForbedreArbeidsevne = prognose.bedreArbeidsevne?.toInt() == 1,
                 anslaatVarighetSykdom = prognose.antattVarighet,
                 anslaatVarighetFunksjonsNedsetting = prognose.varighetFunksjonsnedsettelse,
                 anslaatVarighetNedsattArbeidsevne = prognose.varighetNedsattArbeidsevne
             ),
             aarsaksSammenheng = legeerklaring.arsakssammenhengLegeerklaring,
             andreOpplysninger = legeerklaring.andreOpplysninger?.opplysning,
             kontakt = Kontakt(
                 skalKontakteBehandlendeLege = KontaktType.BehandlendeLege in legeerklaring.kontakt,
                 skalKontakteArbeidsgiver = KontaktType.Arbeidsgiver in legeerklaring.kontakt,
                 skalKontakteBasisgruppe = KontaktType.Basisgruppe in legeerklaring.kontakt,
                 kontakteAnnenInstans = legeerklaring.kontakt.find { it.kontakt?.toInt() == KontaktType.AnnenInstans.type }?.annenInstans,
                 oenskesKopiAvVedtak = legeerklaring.andreOpplysninger?.onskesKopi?.let { it.toInt() == 1 } ?: false
             ),
             pasientenBurdeIkkeVite = legeerklaring.forbeholdLegeerklaring.borTilbakeholdes,
             signatur = Signatur(
                 dato = ZonedDateTime.now(),
                 navn = healthcareProfessional?.formatName() ?: "",
                 adresse = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.address?.streetAdr,
                 postnummer = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.address?.postalCode?.toInt(),
                 poststed = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.address?.city,
                 signatur = "",
                 tlfNummer = healthcareProfessional?.teleCom?.firstOrNull()?.teleAddress?.v ?: ""
             )
         ),
    validationResult = validationResult)

fun mapEnkeltDiagnoseToDiagnose(enkeltdiagnose: Enkeltdiagnose?): Diagnose =
    Diagnose(tekst = enkeltdiagnose?.diagnose, kode = enkeltdiagnose?.kodeverdi)

fun mapLegeerklaeringToSykdomDiagnose(diagnose: DiagnoseArbeidsuforhet): SykdomsOpplysninger = SykdomsOpplysninger(
    hoveddiagnose = mapEnkeltDiagnoseToDiagnose(diagnose.diagnoseKodesystem.enkeltdiagnose.first()),
    bidiagnose = diagnose.diagnoseKodesystem.enkeltdiagnose.drop(1).map { mapEnkeltDiagnoseToDiagnose(it) },
    arbeidsufoerFra = diagnose.arbeidsuforFra?.toGregorianCalendar()?.toZonedDateTime(),
    sykdomsHistorie = diagnose.symptomerBehandling,
    statusPresens = diagnose.statusPresens,
    boerNavKontoretVurdereOmDetErEnYrkesskade = diagnose.vurderingYrkesskade?.borVurderes?.toInt() == 1
)

fun legeerklaeringToPasient(legeerklaering: Legeerklaring): Pasient {
    val patient = legeerklaering.pasientopplysninger.pasient
    val postalAddress = patient.arbeidsforhold?.virksomhet?.virksomhetsAdr?.postalAddress?.firstOrNull()
    return Pasient(
        fornavn = patient.navn.fornavn,
        mellomnavn = patient.navn.mellomnavn,
        etternavn = patient.navn.etternavn,
        foedselsnummer = patient.fodselsnummer,
        navKontor = patient.trygdekontor,
        adresse = patient.personAdr[0].postalAddress[0].streetAddress,
        postnummer = patient.personAdr[0].postalAddress[0].postalCode.let {
            if (it == null || it.isEmpty()) null else it.toInt()
        },
        poststed = patient.personAdr[0].postalAddress[0].city,
        yrke = patient.arbeidsforhold?.yrkesbetegnelse,
        arbeidsgiver = Arbeidsgiver(
            navn = patient.arbeidsforhold?.virksomhet?.virksomhetsBetegnelse,
            adresse = postalAddress?.streetAddress,
            postnummer = postalAddress?.postalCode.let {
                if (it == null || it.isEmpty()) null else it.toInt()
            },
            poststed = postalAddress?.city
        )
    )
}

enum class TypeTiltak(val typeTiltak: Int) {
    KjoepHelsetjenester(1),
    Reisetilskudd(2),
    AktivSykemelding(3),
    HjelpemidlerArbeidsplass(4),
    Arbeidsavklaringspenger(5),
    FriskemeldingTilArbeidsformidling(6),
    AndreTiltak(7)
}

operator fun Iterable<AktueltTiltak>.contains(typeTiltak: TypeTiltak) =
    any { it.typeTiltak.toInt() == typeTiltak.typeTiltak }

enum class LegeerklaeringType(val type: Int) {
    Arbeidsevnevurdering(1),
    Arbeidsavklaringspenger(2),
    YrkesrettetAttfoering(3),
    Ufoerepensjon(4)
}

enum class ArbeidssituasjonType(val type: Int) {
    InntektsgivendeArbeid(1),
    Hjemmearbeidende(2),
    Student(3),
    Annet(4)
}

operator fun Iterable<Arbeidssituasjon>.contains(arbeidssituasjonType: ArbeidssituasjonType): Boolean =
    any { it.arbeidssituasjon?.let {
        it.toInt() == arbeidssituasjonType.type
    } ?: false
    }

enum class KontaktType(val type: Int) {
    BehandlendeLege(1),
    Arbeidsgiver(2),
    Basisgruppe(4),
    AnnenInstans(5)
}

operator fun Iterable<no.nav.helse.legeerklaering.Kontakt>.contains(kontaktType: KontaktType): Boolean =
    any { it.kontakt.toInt() == kontaktType.type }

fun XMLHealthcareProfessional.formatName(): String = if (middleName == null) {
    "${familyName.toUpperCase()} ${givenName.toUpperCase()}"
} else {
    "${familyName.toUpperCase()} ${givenName.toUpperCase()} ${middleName.toUpperCase()}"
}

fun validationResult(results: List<Rule<Any>>): ValidationResult =
    ValidationResult(
        status = results
            .map { status -> status.status }.let {
                it.firstOrNull { status -> status == Status.MANUAL_PROCESSING }
                    ?: Status.OK
            },
        ruleHits = results.map { rule -> RuleInfo(rule.name, rule.messageForUser!!, rule.messageForSender!!) }
    )