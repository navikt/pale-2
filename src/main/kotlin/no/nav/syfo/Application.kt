package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.network.sockets.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.prometheus.client.hotspot.DefaultExports
import jakarta.jms.Session
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.accesstoken.AccessTokenClientV2
import no.nav.syfo.client.clamav.ClamAvClient
import no.nav.syfo.client.emottaksubscription.EmottakSubscriptionClient
import no.nav.syfo.client.pale2regler.Pale2ReglerClient
import no.nav.syfo.client.smtss.SmtssClient
import no.nav.syfo.db.Database
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.mq.MqTlsUtils
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.PdlFactory
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.plugins.configureLifecycleHooks
import no.nav.syfo.plugins.configureRouting
import no.nav.syfo.services.duplicationcheck.DuplicationCheckService
import no.nav.syfo.services.samhandlerservice.SamhandlerService
import no.nav.syfo.services.virusscanservice.VirusScanService
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.TrackableException
import no.nav.syfo.vedlegg.google.BucketUploadService
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper =
    ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.pale-2")

val secureLog: Logger = LoggerFactory.getLogger("securelog")

fun main() {

    val embeddedServer =
        embeddedServer(
            Netty,
            port = EnvironmentVariables().applicationPort,
            module = Application::module,
        )
    Runtime.getRuntime()
        .addShutdownHook(
            Thread {
                embeddedServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
            },
        )
    embeddedServer.start(true)
}

fun Application.module() {
    val environmentVariables = EnvironmentVariables()
    val database = Database(environmentVariables)
    val mqUser = MqUser()

    val applicationState = ApplicationState()

    MqTlsUtils.getMqTlsConfig().forEach { key, value ->
        System.setProperty(key as String, value as String)
    }

    configureLifecycleHooks(applicationState = applicationState)
    configureRouting(applicationState = applicationState)

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
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }

        install(HttpRequestRetry) {
            constantDelay(50, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                no.nav.syfo.log.warn(
                    "Caught exception ${throwable.message}, for url ${request.url}"
                )
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    no.nav.syfo.log.warn(
                        "Retrying for statuscode ${response.status.value}, for url ${request.url}"
                    )
                    true
                } else {
                    false
                }
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
        }
        expectSuccess = false
    }

    val httpClient = HttpClient(Apache, config)

    val accessTokenClientV2 =
        AccessTokenClientV2(
            environmentVariables.aadAccessTokenV2Url,
            environmentVariables.clientIdV2,
            environmentVariables.clientSecretV2,
            httpClient
        )

    val pdlPersonService =
        PdlFactory.getPdlService(
            environmentVariables,
            httpClient,
            accessTokenClientV2,
            environmentVariables.pdlScope
        )

    val emottakSubscriptionClient =
        EmottakSubscriptionClient(
            environmentVariables.smgcpProxyUrl,
            accessTokenClientV2,
            environmentVariables.smgcpProxyScope,
            httpClient
        )

    val smtssClient =
        SmtssClient(
            environmentVariables.smtssApiUrl,
            accessTokenClientV2,
            environmentVariables.smtssApiScope,
            httpClient
        )
    val samhandlerService = SamhandlerService(smtssClient, emottakSubscriptionClient)

    val aivenKakfaProducerConfig =
        KafkaUtils.getAivenKafkaConfig()
            .toProducerConfig(
                environmentVariables.applicationName,
                valueSerializer = JacksonKafkaSerializer::class
            )
    val aivenKafkaProducer =
        KafkaProducer<String, LegeerklaeringKafkaMessage>(aivenKakfaProducerConfig)

    val pale2ReglerClient =
        Pale2ReglerClient(
            environmentVariables.pale2ReglerEndpointURL,
            httpClient,
            accessTokenClientV2,
            environmentVariables.pale2ReglerApiScope
        )

    val paleVedleggStorage: Storage = StorageOptions.newBuilder().build().service

    val paleVedleggBucketUploadService =
        BucketUploadService(
            environmentVariables.legeerklaeringBucketName,
            environmentVariables.paleVedleggBucketName,
            paleVedleggStorage
        )

    val clamAvClient = ClamAvClient(httpClient, environmentVariables.clamAvEndpointUrl)

    val virusScanService = VirusScanService(clamAvClient)

    val duplicationCheckService = DuplicationCheckService(database)
    val legeerklaringConsumerService = LegeerklaringConsumerService(
        applicationState,
        environmentVariables,
        samhandlerService,
        pdlPersonService,
        aivenKafkaProducer,
        pale2ReglerClient,
        paleVedleggBucketUploadService,
        virusScanService,
        duplicationCheckService,
    )

    monitor.subscribe(ApplicationStarted) {
        launch { legeerklaringConsumerService.start() }

    }
    this.monitor.raise(ApplicationStopping, this)
    monitor.subscribe(ApplicationStopping) {
        applicationState.ready = false
        applicationState.alive = false
        runBlocking { legeerklaringConsumerService.stop() }
    }
}

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = true,
)

class ServiceUnavailableException(message: String?) : Exception(message)
