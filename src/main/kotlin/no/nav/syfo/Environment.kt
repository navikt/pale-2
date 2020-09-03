package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
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
    val redishost: String = getEnvVar("REDIS_HOST", "pale-2-redis.default.svc.nais.local"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val aktoerregisterV1Url: String = getEnvVar("AKTOR_REGISTER_V1_URL"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api.teamkuhr.svc.nais.local"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL", "http://sak/api/v1/saker"),
    val arenaQueueName: String = getEnvVar("ARENA_OUTBOUND_QUEUENAME"),
    val personV3EndpointURL: String = getEnvVar("PERSON_V3_ENDPOINT_URL"),
    val norg2V1EndpointURL: String = getEnvVar("NORG2_V1_ENDPOINT_URL"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val pale2SakTopic: String = getEnvVar("KAFKA_PALE_2_SAK_TOPIC", "privat-syfo-pale2-sak-v1"),
    val pale2DumpTopic: String = getEnvVar("KAFKA_PALE_2_DUMP_TOPIC", "privat-syfo-pale2-dump-v2"),
    val subscriptionEndpointURL: String = getEnvVar("SUBSCRIPTION_ENDPOINT_URL"),
    val pale2OkTopic: String = getEnvVar("KAFKA_PALE_2_OK_TOPIC", "privat-syfo-pale2-ok-v1"),
    val pale2AvvistTopic: String = getEnvVar("KAFKA_PALE_2_AVVIST_TOPIC", "privat-syfo-pale2-avvist-v1"),
    val pale2ReglerEndpointURL: String = getEnvVar("PALE_2_REGLER_ENDPOINT_URL", "http://pale-2-regler"),
    val sm2013VedleggTopic: String = "privat-syfo-vedlegg"
) : MqConfig, KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String,
    val clientId: String,
    val clientsecret: String,
    val redisSecret: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
