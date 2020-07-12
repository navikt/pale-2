package no.nav.syfo.client

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.vedlegg.producer.KafkaVedleggProducer
import no.nav.syfo.model.LegeerklaeringSak
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer

class KafkaClients(env: Environment, credentials: VaultSecrets) {

    private val kafkaBaseConfig = loadBaseConfig(env, credentials)
    private val producerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    private val vedleggProducerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    private val producerPropertiesString = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = StringSerializer::class)
    init {
        vedleggProducerProperties[ProducerConfig.RETRIES_CONFIG] = 100_000
        vedleggProducerProperties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        vedleggProducerProperties[ProducerConfig.MAX_REQUEST_SIZE_CONFIG] = "8388608"
    }
    val kafkaProducerLegeerklaeringSak = KafkaProducer<String, LegeerklaeringSak>(producerProperties)
    val kafkaProducerLegeerklaeringFellesformat = KafkaProducer<String, String>(producerPropertiesString)
    val kafkaVedleggProducer = KafkaVedleggProducer(env, KafkaProducer(vedleggProducerProperties))
}
