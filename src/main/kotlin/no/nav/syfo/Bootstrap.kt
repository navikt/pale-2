package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.network.sockets.SocketTimeoutException
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.Pale2ReglerClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.services.SamhandlerService
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.TrackableException
import no.nav.syfo.vedlegg.google.BucketUploadService
import no.nav.syfo.ws.createPort
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import java.io.FileInputStream
import java.net.ProxySelector
import javax.jms.Session

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.pale-2")

@DelicateCoroutinesApi
fun main() {
    val env = Environment()

    val vaultSecrets = VaultSecrets()

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
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
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

    val httpClient = HttpClient(Apache, config)
    val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    val accessTokenClientV2 = AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClientWithProxy)

    val sarClient = SarClient(env.kuhrSarApiUrl, httpClient)
    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)

    val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
        proxy { features.add(WSAddressingFeature()) }
        port { withBasicAuth(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword) }
    }

    val samhandlerService = SamhandlerService(sarClient, subscriptionEmottak)

    val aivenKakfaProducerConfig = KafkaUtils.getAivenKafkaConfig().toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val aivenKafkaProducer = KafkaProducer<String, LegeerklaeringKafkaMessage>(aivenKakfaProducerConfig)

    val pale2ReglerClient = Pale2ReglerClient(env.pale2ReglerEndpointURL, httpClient)

    val paleVedleggStorageCredentials: Credentials = GoogleCredentials.fromStream(FileInputStream("/var/run/secrets/nais.io/vault/pale2-google-creds.json"))
    val paleVedleggStorage: Storage = StorageOptions.newBuilder().setCredentials(paleVedleggStorageCredentials).build().service
    val paleVedleggBucketUploadService = BucketUploadService(env.legeerklaeringBucketName, env.paleVedleggBucketName, paleVedleggStorage)

    launchListeners(
        applicationState, env, samhandlerService, pdlPersonService, vaultSecrets,
        aivenKafkaProducer, pale2ReglerClient, paleVedleggBucketUploadService
    )
}

@DelicateCoroutinesApi
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

@DelicateCoroutinesApi
fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    samhandlerService: SamhandlerService,
    pdlPersonService: PdlPersonService,
    secrets: VaultSecrets,
    aivenKafkaProducer: KafkaProducer<String, LegeerklaeringKafkaMessage>,
    pale2ReglerClient: Pale2ReglerClient,
    bucketUploadService: BucketUploadService
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(secrets.serviceuserUsername, secrets.serviceuserPassword).use { connection ->
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
                    jedis, session, env, receiptProducer, backoutProducer, samhandlerService, pdlPersonService,
                    arenaProducer, aivenKafkaProducer, pale2ReglerClient, bucketUploadService
                )
            }
        }
    }
}
