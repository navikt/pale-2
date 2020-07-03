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
import javax.jms.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.KafkaClients
import no.nav.syfo.client.Norg2Client
import no.nav.syfo.client.Pale2ReglerClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.sts.StsOidcClient
import no.nav.syfo.kafka.vedlegg.producer.KafkaVedleggProducer
import no.nav.syfo.model.LegeerklaeringSak
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.services.FindNAVKontorService
import no.nav.syfo.services.SamhandlerService
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.cxf.ws.addressing.WSAddressingFeature
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
        clientsecret = getFileAsString("/secrets/azuread/pale-2/client_secret"),
        redisSecret = getFileAsString("/secrets/default/redisSecret")
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

    val httpClient = HttpClient(Apache, config)

    val oidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient, httpClient)

    val sarClient = SarClient(env.kuhrSarApiUrl, httpClient)
    val norg2Client = Norg2Client(env.norg2V1EndpointURL, httpClient)

    val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
        port {
            withSTS(
                vaultSecrets.serviceuserUsername,
                vaultSecrets.serviceuserPassword,
                env.securityTokenServiceURL
            )
        }
    }

    val findNAVKontorService = FindNAVKontorService(personV3, norg2Client)
    val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
        proxy { features.add(WSAddressingFeature()) }
        port { withBasicAuth(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword) }
    }

    val samhandlerService = SamhandlerService(sarClient, subscriptionEmottak)

    val kafkaClients = KafkaClients(env, vaultSecrets)

    val pale2ReglerClient = Pale2ReglerClient(env.pale2ReglerEndpointURL, httpClient)

    launchListeners(
        applicationState, env, samhandlerService,
        aktoerIdClient, vaultSecrets,
        findNAVKontorService, kafkaClients.kafkaProducerLegeerklaeringSak,
        kafkaClients.kafkaProducerLegeerklaeringFellesformat, pale2ReglerClient,
        kafkaClients.kafkaVedleggProducer
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
    samhandlerService: SamhandlerService,
    aktoerIdClient: AktoerIdClient,
    secrets: VaultSecrets,
    findNAVKontorService: FindNAVKontorService,
    kafkaProducerLegeerklaeringSak: KafkaProducer<String, LegeerklaeringSak>,
    kafkaProducerLegeerklaeringFellesformat: KafkaProducer<String, XMLEIFellesformat>,
    pale2ReglerClient: Pale2ReglerClient,
    kafkaVedleggProducer: KafkaVedleggProducer
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(secrets.mqUsername, secrets.mqPassword).use { connection ->
            Jedis(env.redishost, 6379).use { jedis ->
                connection.start()
                val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

                val inputconsumer = session.consumerForQueue(env.inputQueueName)
                val receiptProducer = session.producerForQueue(env.apprecQueueName)
                val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)
                val arenaProducer = session.producerForQueue(env.arenaQueueName)

                applicationState.ready = true

                jedis.auth(secrets.redisSecret)

                BlockingApplicationRunner().run(
                    applicationState, inputconsumer,
                    jedis, session, env, receiptProducer, backoutProducer,
                    samhandlerService, aktoerIdClient, secrets,
                    arenaProducer, findNAVKontorService, kafkaProducerLegeerklaeringSak,
                    kafkaProducerLegeerklaeringFellesformat, pale2ReglerClient, kafkaVedleggProducer
                )
            }
        }
    }
}
