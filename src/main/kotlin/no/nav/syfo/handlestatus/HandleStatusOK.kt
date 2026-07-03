package no.nav.syfo.handlestatus

import jakarta.jms.MessageProducer
import jakarta.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.log
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.services.apprec.sendReceipt
import no.nav.syfo.services.duplicationcheck.DuplicationCheckService
import no.nav.syfo.services.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.services.journalpoststatus.JournalpostStatusService
import no.nav.syfo.services.journalpoststatus.model.ArenaPayload
import no.nav.syfo.services.journalpoststatus.model.ProcessingStatusType
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

fun handleStatusOK(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    tssId: String?,
    ediLoggId: String,
    fnrLege: String,
    legeerklaring: Legeerklaering,
    loggingMeta: LoggingMeta,
    aivenKafkaProducer: KafkaProducer<String, LegeerklaeringKafkaMessage>,
    topic: String,
    legeerklaringKafkaMessage: LegeerklaeringKafkaMessage,
    apprecQueueName: String,
    duplicationCheckService: DuplicationCheckService,
    duplicateCheck: DuplicateCheck,
    behandlerName: String,
    journalpostStatusService: JournalpostStatusService,
    processingStatus: ProcessingStatusType,
) {
    journalpostStatusService.updateArenaPayload(
        ediLoggId,
        ArenaPayload(
            tssId = tssId,
            ediLoggId = ediLoggId,
            fnrLege = fnrLege,
            behandlerName = behandlerName,
            legeerklaering = legeerklaring,
        ),
    )

    if (processingStatus == ProcessingStatusType.MOTTATT) {
        sendReceipt(
            session,
            receiptProducer,
            fellesformat,
            ApprecStatus.OK,
            emptyList(),
            duplicationCheckService,
            duplicateCheck,
            loggingMeta,
            apprecQueueName,
        )
        journalpostStatusService.updateProcessingStatus(
            ediLoggId,
            ProcessingStatusType.APPREC_SENDT,
        )
    }

    sendTilTopic(
        aivenKafkaProducer,
        topic,
        legeerklaringKafkaMessage,
        legeerklaring.id,
        loggingMeta
    )
    journalpostStatusService.updateProcessingStatus(
        ediLoggId,
        ProcessingStatusType.SENDT_TIL_TOPIC,
    )
    log.info(
        "Legeerklæring avventer journalføring før sending til arena, {}",
        fields(loggingMeta),
    )
}

fun sendTilTopic(
    aivenKafkaProducer: KafkaProducer<String, LegeerklaeringKafkaMessage>,
    topic: String,
    legeerklaeringKafkaMessage: LegeerklaeringKafkaMessage,
    legeerklaeringId: String,
    loggingMeta: LoggingMeta,
) {
    try {
        aivenKafkaProducer
            .send(ProducerRecord(topic, legeerklaeringId, legeerklaeringKafkaMessage))
            .get()
        log.info("Melding med id $legeerklaeringId sendt til kafka topic {}", topic)
    } catch (e: Exception) {
        log.error("Noe gikk galt ved sending til ok-topic, {}, {}", e.message, fields(loggingMeta))
        throw e
    }
}
