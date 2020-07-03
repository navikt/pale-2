package no.nav.syfo.kafka.vedlegg.producer

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.Environment
import no.nav.syfo.kafka.vedlegg.model.BehandlerInfo
import no.nav.syfo.kafka.vedlegg.model.Vedlegg
import no.nav.syfo.kafka.vedlegg.model.VedleggKafkaMessage
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaVedleggProducer(private val environment: Environment, private val kafkaProducer: KafkaProducer<String, VedleggKafkaMessage>) {
    fun sendVedlegg(vedlegg: List<Vedlegg>, legeerklaering: ReceivedLegeerklaering, loggingMeta: LoggingMeta) {
        vedlegg.map { toKafkaVedleggMessage(it, legeerklaering) }.forEach {
            try {
                log.info("Sender vedlegg til kafka, {} {}", it.vedlegg.type, loggingMeta)
                kafkaProducer.send(ProducerRecord(environment.sm2013VedleggTopic, legeerklaering.legeerklaering.id, it)).get()
            } catch (ex: Exception) {
                log.error("Error producing vedlegg to kafka {}", fields(loggingMeta), ex)
                throw ex
            }
        }
    }

    private fun toKafkaVedleggMessage(vedlegg: Vedlegg, legeerklaering: ReceivedLegeerklaering): VedleggKafkaMessage {
        return VedleggKafkaMessage(
                vedlegg = vedlegg,
                msgId = legeerklaering.msgId,
                pasientFnr = legeerklaering.personNrPasient,
                behandler = BehandlerInfo(
                    fnr = legeerklaering.personNrLege,
                    fornavn = null,
                    etternavn = null
                ),
                pasientAktorId = legeerklaering.pasientAktoerId
        )
    }
}
