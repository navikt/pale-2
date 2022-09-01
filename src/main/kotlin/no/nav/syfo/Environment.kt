package no.nav.syfo

import no.nav.syfo.mq.MqConfig

data class Environment(
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "pale-2"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val inputQueueName: String = getEnvVar("MQ_INPUT_QUEUE_NAME"),
    val apprecQueueName: String = getEnvVar("MQ_APPREC_QUEUE_NAME"),
    val redishost: String = getEnvVar("REDIS_HOST", "pale-2-redis.teamsykmelding.svc.cluster.local"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val arenaQueueName: String = getEnvVar("ARENA_OUTBOUND_QUEUENAME"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val pale2ReglerEndpointURL: String = getEnvVar("PALE_2_REGLER_ENDPOINT_URL"),
    val pale2ReglerApiScope: String = getEnvVar("PALE_2_REGLER_API_SCOPE"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val paleVedleggBucketName: String = getEnvVar("PALE_VEDLEGG_BUCKET_NAME"),
    val legeerklaeringBucketName: String = getEnvVar("PALE_BUCKET_NAME"),
    val legeerklaringTopic: String = "teamsykmelding.legeerklaering",
    val smgcpProxyUrl: String = getEnvVar("SMGCP_PROXY_URL"),
    val smgcpProxyScope: String = getEnvVar("SMGCP_PROXY_SCOPE"),
    val redisSecret: String = getEnvVar("REDIS_PASSWORD")
) : MqConfig

data class VaultServiceUser(
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
