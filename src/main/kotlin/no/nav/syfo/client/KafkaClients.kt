package no.nav.syfo.client

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.LegeerklaeringSak
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaClients(env: Environment, credentials: VaultSecrets) {

    private val kafkaBaseConfig = loadBaseConfig(env, credentials)
    private val producerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    val kafkaProducerLegeerklaeringSak = KafkaProducer<String, LegeerklaeringSak>(producerProperties)
    val kafkaProducerLegeerklaeringFellesformat = KafkaProducer<String, XMLEIFellesformat>(producerProperties)
}
