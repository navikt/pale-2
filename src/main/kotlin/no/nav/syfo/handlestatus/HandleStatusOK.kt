package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.client.createArenaInfo
import no.nav.syfo.log
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.arenaMarshaller
import no.nav.syfo.util.toString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import javax.jms.MessageProducer
import javax.jms.Session

fun handleStatusOK(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    arenaProducer: MessageProducer,
    tssId: String?,
    ediLoggId: String,
    fnrLege: String,
    legeerklaring: Legeerklaering,
    loggingMeta: LoggingMeta,
    aivenKafkaProducer: KafkaProducer<String, LegeerklaeringKafkaMessage>,
    topic: String,
    legeerklaringKafkaMessage: LegeerklaeringKafkaMessage,
    apprecQueueName: String,
    legeerklaeringId: String
) {
    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
    log.info("Apprec Receipt sent to {}, {}", apprecQueueName, fields(loggingMeta))

    sendArenaInfo(
        arenaProducer, session,
        tssId, ediLoggId, fnrLege, legeerklaring
    )
    log.info("Legeerklæring sendt til arena, {}", fields(loggingMeta))

    sendTilTopic(aivenKafkaProducer, topic, legeerklaringKafkaMessage, legeerklaeringId, loggingMeta)
}

fun sendArenaInfo(
    producer: MessageProducer,
    session: Session,
    tssId: String?,
    mottakid: String,
    fnrbehandler: String,
    legeerklaring: Legeerklaering
) = producer.send(
    session.createTextMessage().apply {
        val info = createArenaInfo(tssId, mottakid, fnrbehandler, legeerklaring)
        text = arenaMarshaller.toString(info)
    }
)

fun sendTilTopic(
    aivenKafkaProducer: KafkaProducer<String, LegeerklaeringKafkaMessage>,
    topic: String,
    legeerklaeringKafkaMessage: LegeerklaeringKafkaMessage,
    legeerklaeringId: String,
    loggingMeta: LoggingMeta
) {
    try {
        aivenKafkaProducer.send(ProducerRecord(topic, legeerklaeringId, legeerklaeringKafkaMessage)).get()
        log.info("Melding med msgId ${legeerklaeringKafkaMessage.legeerklaeringObjectId} sendt til kafka topic {}", topic)
    } catch (e: Exception) {
        log.error("Noe gikk galt ved sending til ok-topic, {}, {}", e.message, fields(loggingMeta))
        throw e
    }
}
