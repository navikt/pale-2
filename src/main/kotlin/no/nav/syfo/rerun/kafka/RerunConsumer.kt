package no.nav.syfo.rerun.kafka

import java.time.Duration
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import org.apache.kafka.clients.consumer.KafkaConsumer

class RerunConsumer(
    private val kafkaRerunConsumer: KafkaConsumer<String, XMLEIFellesformat>
) {
    fun poll(): List<XMLEIFellesformat> {
        return kafkaRerunConsumer.poll(Duration.ofMillis(0)).map { it.value() }
    }
}
