package no.nav.syfo.rerun.kafka

import java.time.Duration
import org.apache.kafka.clients.consumer.KafkaConsumer

class RerunConsumer(
    private val kafkaRerunConsumer: KafkaConsumer<String, String>
) {
    fun poll(): List<String> {
        return kafkaRerunConsumer.poll(Duration.ofMillis(0)).map { it.value() }
    }

    fun close() {
        kafkaRerunConsumer.close()
    }
}
