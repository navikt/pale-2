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
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.Pale2ReglerClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.mq.MqTlsUtils
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.services.SamhandlerService
import no.nav.syfo.services.VirusScanService
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.TrackableException
import no.nav.syfo.vedlegg.google.BucketUploadService
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import java.io.FileInputStream
import javax.jms.Session

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.pale-2")

val secureLog = LoggerFactory.getLogger("secureLog")

@DelicateCoroutinesApi
fun main() {
    val env = Environment()

    val serviceUser = VaultServiceUser()

    MqTlsUtils.getMqTlsConfig().forEach { key, value -> System.setProperty(key as String, value as String) }

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    DefaultExports.initialize()

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        expectSuccess = false
    }
    val retryConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config().apply {
            install(HttpRequestRetry) {
                maxRetries = 3
                delayMillis { retry ->
                    retry * 500L
                }
            }

            install(Logging) {
                level = LogLevel.BODY
                logger = object : io.ktor.client.plugins.logging.Logger {
                    override fun log(message: String) {
                        log.info(message)
                    }
                }
            }
        }
    }

    val httpClient = HttpClient(Apache, config)
    val httpClientWithRetry = HttpClient(Apache, retryConfig)

    val accessTokenClientV2 =
        AccessTokenClientV2(env.aadAccessTokenV2Url, env.clientIdV2, env.clientSecretV2, httpClientWithRetry)

    val sarClient = SarClient(env.smgcpProxyUrl, accessTokenClientV2, env.smgcpProxyScope, httpClientWithRetry)
    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)

    val emottakSubscriptionClient =
        EmottakSubscriptionClient(env.smgcpProxyUrl, accessTokenClientV2, env.smgcpProxyScope, httpClientWithRetry)

    val samhandlerService = SamhandlerService(sarClient, emottakSubscriptionClient)

    val aivenKakfaProducerConfig = KafkaUtils.getAivenKafkaConfig()
        .toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val aivenKafkaProducer = KafkaProducer<String, LegeerklaeringKafkaMessage>(aivenKakfaProducerConfig)

    val pale2ReglerClient =
        Pale2ReglerClient(env.pale2ReglerEndpointURL, httpClientWithRetry, accessTokenClientV2, env.pale2ReglerApiScope)

    val paleVedleggStorageCredentials: Credentials =
        GoogleCredentials.fromStream(FileInputStream("/var/run/secrets/pale2-google-creds.json"))
    val paleVedleggStorage: Storage =
        StorageOptions.newBuilder().setCredentials(paleVedleggStorageCredentials).build().service
    val paleVedleggBucketUploadService =
        BucketUploadService(env.legeerklaeringBucketName, env.paleVedleggBucketName, paleVedleggStorage)

    val clamAvClient = ClamAvClient(httpClientWithRetry, env.clamAvEndpointUrl)

    val virusScanService = VirusScanService(clamAvClient)

    launchListeners(
        applicationState, env, samhandlerService, pdlPersonService, serviceUser,
        aivenKafkaProducer, pale2ReglerClient, paleVedleggBucketUploadService, virusScanService
    )

    applicationServer.start()
}

@DelicateCoroutinesApi
fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter", e.cause)
        } finally {
            applicationState.ready = false
            applicationState.alive = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    samhandlerService: SamhandlerService,
    pdlPersonService: PdlPersonService,
    serviceUser: VaultServiceUser,
    aivenKafkaProducer: KafkaProducer<String, LegeerklaeringKafkaMessage>,
    pale2ReglerClient: Pale2ReglerClient,
    bucketUploadService: BucketUploadService,
    virusScanService: VirusScanService
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(serviceUser.serviceuserUsername, serviceUser.serviceuserPassword)
            .use { connection ->
                Jedis(env.redishost, 6379).use { jedis ->
                    connection.start()
                    val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

                    val inputconsumer = session.consumerForQueue(env.inputQueueName)
                    val receiptProducer = session.producerForQueue(env.apprecQueueName)
                    val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)
                    val arenaProducer = session.producerForQueue(env.arenaQueueName)

                    jedis.auth(env.redisSecret)

                    BlockingApplicationRunner(
                        applicationState = applicationState,
                        jedis = jedis,
                        env = env,
                        samhandlerService = samhandlerService,
                        pdlPersonService = pdlPersonService,
                        aivenKafkaProducer = aivenKafkaProducer,
                        pale2ReglerClient = pale2ReglerClient,
                        bucketUploadService = bucketUploadService,
                        virusScanService = virusScanService
                    ).run(
                        inputconsumer = inputconsumer,
                        session = session,
                        receiptProducer = receiptProducer,
                        backoutProducer = backoutProducer,
                        arenaProducer = arenaProducer
                    )
                }
            }
    }
}
