package no.nav.syfo.rerun.kafka

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

fun getKafkaRerunConsumer(env: Environment, vaultSecrets: VaultSecrets): KafkaConsumer<String, XMLEIFellesformat> {
    val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
    val properties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = JacksonKafkaDeserializer::class)
    properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

    val kafkaRerunConsumer = KafkaConsumer<String, XMLEIFellesformat>(properties, StringDeserializer(), JacksonKafkaDeserializer(XMLEIFellesformat::class))
    kafkaRerunConsumer.subscribe(listOf(env.pale2DumpTopic))
    return kafkaRerunConsumer
}
