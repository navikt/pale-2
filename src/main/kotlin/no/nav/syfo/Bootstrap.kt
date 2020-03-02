package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.io.StringWriter
import java.net.ProxySelector
import javax.jms.MessageProducer
import javax.jms.Session
import javax.xml.bind.Marshaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.apprecV1.XMLCV as AppRecCV
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.KafkaClients
import no.nav.syfo.client.LegeSuspensjonClient
import no.nav.syfo.client.Norg2Client
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.metrics.APPREC_COUNTER
import no.nav.syfo.model.LegeerklaeringSak
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.rules.Rule
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.apprecMarshaller
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.pale-2")

const val NAV_OPPFOLGING_UTLAND_KONTOR_NR = "0393"

@KtorExperimentalAPI
fun main() {
    val env = Environment()

    val vaultSecrets = VaultSecrets(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username"),
        mqUsername = getFileAsString("/secrets/default/mqUsername"),
        mqPassword = getFileAsString("/secrets/default/mqPassword"),
        clientId = getFileAsString("/secrets/azuread/pale-2/client_id"),
        clientsecret = getFileAsString("/secrets/azuread/pale-2/client_secret")
    )

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    DefaultExports.initialize()

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    val httpClientWithProxy = HttpClient(Apache, proxyConfig)
    val httpClient = HttpClient(Apache, config)

    val oidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient, httpClient)

    val sarClient = SarClient(env.kuhrSarApiUrl, httpClient)
    val norg2Client = Norg2Client(env.norg2V1EndpointURL, httpClient)
    val legeSuspensjonClient = LegeSuspensjonClient(
        env.legeSuspensjonEndpointURL,
        vaultSecrets,
        oidcClient,
        httpClient
    )

    val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
        port {
            withSTS(
                vaultSecrets.serviceuserUsername,
                vaultSecrets.serviceuserPassword,
                env.securityTokenServiceURL
            )
        }
    }

    val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, vaultSecrets.clientId, vaultSecrets.clientsecret, httpClientWithProxy)

    val norskHelsenettClient = NorskHelsenettClient(env.norskHelsenettEndpointURL, accessTokenClient, env.helsenettproxyId, httpClient)

    val kafkaClients = KafkaClients(env, vaultSecrets)

    launchListeners(
        applicationState, env, sarClient,
        aktoerIdClient, vaultSecrets,
        legeSuspensjonClient,
        personV3, norg2Client, norskHelsenettClient, kafkaClients.kafkaProducerLegeerklaeringSak,
        kafkaClients.kafkaProducerLegeerklaeringFellesformat
    )
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", e.cause)
        } finally {
            applicationState.alive = false
        }
    }

@KtorExperimentalAPI
fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    kuhrSarClient: SarClient,
    aktoerIdClient: AktoerIdClient,
    secrets: VaultSecrets,
    legeSuspensjonClient: LegeSuspensjonClient,
    personV3: PersonV3,
    norg2Client: Norg2Client,
    norskHelsenettClient: NorskHelsenettClient,
    kafkaProducerLegeerklaeringSak: KafkaProducer<String, LegeerklaeringSak>,
    kafkaProducerLegeerklaeringFellesformat: KafkaProducer<String, XMLEIFellesformat>
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(secrets.mqUsername, secrets.mqPassword).use { connection ->
            Jedis(env.redishost, 6379).use { jedis ->
                connection.start()
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

                val inputconsumer = session.consumerForQueue(env.inputQueueName)
                val receiptProducer = session.producerForQueue(env.apprecQueueName)
                val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)
                val arenaProducer = session.producerForQueue(env.arenaQueueName)

                applicationState.ready = true

                BlockingApplicationRunner().run(applicationState, inputconsumer,
                    jedis, session, env, receiptProducer, backoutProducer,
                    kuhrSarClient, aktoerIdClient, secrets, legeSuspensjonClient,
                    arenaProducer, personV3, norg2Client, norskHelsenettClient, kafkaProducerLegeerklaeringSak,
                    kafkaProducerLegeerklaeringFellesformat
                )
            }
        }
    }
}

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun sendReceipt(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    apprecStatus: ApprecStatus,
    apprecErrors: List<AppRecCV> = listOf()
) {
    receiptProducer.send(session.createTextMessage().apply {
        val apprec = createApprec(fellesformat, apprecStatus)
        apprec.get<XMLAppRec>().error.addAll(apprecErrors)
        text = apprecMarshaller.toString(apprec)
    })
    APPREC_COUNTER.inc()
}

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

fun extractPersonIdent(legeerklaering: Legeerklaring): String? =
    legeerklaering.pasientopplysninger.pasient.fodselsnummer

fun XMLHealthcareProfessional.formatName(): String = if (middleName == null) {
    "${familyName.toUpperCase()} ${givenName.toUpperCase()}"
} else {
    "${familyName.toUpperCase()} ${givenName.toUpperCase()} ${middleName.toUpperCase()}"
}

fun validationResult(results: List<Rule<Any>>): ValidationResult = ValidationResult(
    status = results
        .map { status -> status.status }.let {
            it.firstOrNull { status -> status == Status.INVALID }
                ?: it.firstOrNull { status -> status == Status.MANUAL_PROCESSING }
                ?: Status.OK
        },
    ruleHits = results.map { rule -> RuleInfo(rule.name, rule.messageForSender!!, rule.messageForUser!!, rule.status) }
)
