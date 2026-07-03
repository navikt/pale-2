package no.nav.syfo.kafka.aiven

data class KafkaEnvironment(
    val KAFKA_BROKERS: String = getEnvVar("KAFKA_BROKERS"),
    val KAFKA_CLIENT_ID: String = getEnvVar("KAFKA_CLIENT_ID", getEnvVar("NAIS_APP_NAME")),
    val KAFKA_TRUSTSTORE_PATH: String = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
    val KAFKA_KEYSTORE_PATH: String = getEnvVar("KAFKA_KEYSTORE_PATH"),
    val KAFKA_CREDSTORE_PASSWORD: String = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
    val KAFKA_SCHEMA_REGISTRY: String = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
    val KAFKA_SCHEMA_REGISTRY_USER: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
    val KAFKA_SCHEMA_REGISTRY_PASSWORD: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
) {
    companion object {
        fun getEnvVar(varName: String, defaultValue: String? = null) =
            System.getenv(varName)
                ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
    }
}
