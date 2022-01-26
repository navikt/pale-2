package no.nav.syfo

import no.nav.syfo.mq.MqConfig
import no.nav.syfo.util.getFileAsString

data class Environment(
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "pale-2"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val inputQueueName: String = getEnvVar("MQ_INPUT_QUEUE_NAME"),
    val apprecQueueName: String = getEnvVar("MQ_APPREC_QUEUE_NAME"),
    val redishost: String = getEnvVar("REDIS_HOST", "pale-2-redis.teamsykmelding.svc.nais.local"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api.teamkuhr.svc.nais.local"),
    val arenaQueueName: String = getEnvVar("ARENA_OUTBOUND_QUEUENAME"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val subscriptionEndpointURL: String = getEnvVar("SUBSCRIPTION_ENDPOINT_URL"),
    val pale2ReglerEndpointURL: String = getEnvVar("PALE_2_REGLER_ENDPOINT_URL"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val paleVedleggBucketName: String = getEnvVar("PALE_VEDLEGG_BUCKET_NAME"),
    val legeerklaeringBucketName: String = getEnvVar("PALE_BUCKET_NAME"),
    val legeerklaringTopic: String = "teamsykmelding.legeerklaering"
) : MqConfig

data class VaultSecrets(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password"),
    val redisSecret: String = getEnvVar("REDIS_PASSWORD")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
