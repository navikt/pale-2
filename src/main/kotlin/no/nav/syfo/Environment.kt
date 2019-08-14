package no.nav.syfo

import no.nav.syfo.mq.MqConfig

data class Environment(
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "1").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "pale-2"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val inputQueueName: String = getEnvVar("MQ_INPUT_QUEUE_NAME"),
    val apprecQueueName: String = getEnvVar("MQ_APPREC_QUEUE_NAME"),
    val redishost: String = getEnvVar("REDIS_HOST", "pale-2-redis.default.svc.nais.local"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val aktoerregisterV1Url: String = getEnvVar("AKTOR_REGISTER_V1_URL"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val helsepersonellv1EndpointURL: String = getEnvVar("HELSEPERSONELL_V1_ENDPOINT_URL"),
    val legeSuspensjonEndpointURL: String = getEnvVar("LEGE_SUSPENSJON_ENDPOINT_URL", "http://btsys"),
    val pdfgen: String = getEnvVar("PDF_GEN_URL", "http://syfopdfgen/api/v1/genpdf/pale-2/pale-2"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL", "http://sak/api/v1/saker"),
    val dokmotMottaInngaaendeUrl: String = getEnvVar("DOKMOT_MOTTA_INNGAAENDE_URL"),
    val diskresjonskodeEndpointUrl: String = getEnvVar("DISKRESJONSKODE_ENDPOINT_URL"),
    val arenaQueueName: String = getEnvVar("ARENA_OUTBOUND_QUEUENAME")
) : MqConfig

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
