package no.nav.syfo.kafka.aiven

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import java.util.Properties
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaUtils {
    companion object {
        private fun getAivenKafkaBaseConfig(kafkaEnv: KafkaEnvironment): Properties {
            return Properties().also {
                it[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = kafkaEnv.KAFKA_BROKERS
                it[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
                it[CommonClientConfigs.CLIENT_ID_CONFIG] = kafkaEnv.KAFKA_CLIENT_ID
                it[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "jks"
                it[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
                it[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = kafkaEnv.KAFKA_TRUSTSTORE_PATH
                it[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = kafkaEnv.KAFKA_CREDSTORE_PASSWORD
                it[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = kafkaEnv.KAFKA_KEYSTORE_PATH
                it[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = kafkaEnv.KAFKA_CREDSTORE_PASSWORD
                it[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = kafkaEnv.KAFKA_CREDSTORE_PASSWORD
                it[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
            }
        }

        fun getAivenKafkaProducerConfig(): Properties {
            return getAivenKafkaBaseConfig(KafkaEnvironment()).also {
                it[ProducerConfig.ACKS_CONFIG] = "all"
                it[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
            }
        }

        fun getAivenKafkaAvroConsumerConfig(groupId: String): Properties {
            val kafkaEnv = KafkaEnvironment()
            return getAivenKafkaBaseConfig(kafkaEnv).also {
                it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 100
                it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
                it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
                    KafkaAvroDeserializer::class.java
                it[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] =
                    kafkaEnv.KAFKA_SCHEMA_REGISTRY
                it[AbstractKafkaSchemaSerDeConfig.BASIC_AUTH_CREDENTIALS_SOURCE] = "USER_INFO"
                it[AbstractKafkaSchemaSerDeConfig.USER_INFO_CONFIG] =
                    "${kafkaEnv.KAFKA_SCHEMA_REGISTRY_USER}:${kafkaEnv.KAFKA_SCHEMA_REGISTRY_PASSWORD}"
                it[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
            }
        }
    }
}
