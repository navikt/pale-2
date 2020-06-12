package no.nav.syfo.handlestatus

import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.client.createArenaInfo
import no.nav.syfo.log
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.LegeerklaeringSak
import no.nav.syfo.services.FindNAVKontorService
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.arenaMarshaller
import no.nav.syfo.util.toString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleStatusOK(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    arenaProducer: MessageProducer,
    findNAVKontorService: FindNAVKontorService,
    fnrPasient: String,
    tssId: String?,
    ediLoggId: String,
    fnrLege: String,
    legeerklaring: Legeerklaering,
    loggingMeta: LoggingMeta,
    kafkaProducerLegeerklaeringSak: KafkaProducer<String, LegeerklaeringSak>,
    pale2OkTopic: String,
    legeerklaeringSak: LegeerklaeringSak,
    apprecQueueName: String
) {
    val lokaltNavkontor = findNAVKontorService.finnLokaltNavkontor(fnrPasient, loggingMeta)

    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
    log.info("Apprec Receipt sent to {}, {}", apprecQueueName, fields(loggingMeta))

    sendArenaInfo(arenaProducer, session,
        lokaltNavkontor, tssId, ediLoggId,
        fnrLege, legeerklaring)
    log.info("Legeerklæring sendt til arena, til lokal kontornr: $lokaltNavkontor, {}", fields(loggingMeta))

    sendTilOKTopic(kafkaProducerLegeerklaeringSak, pale2OkTopic, legeerklaeringSak, loggingMeta)
}

fun sendArenaInfo(
    producer: MessageProducer,
    session: Session,
    lokaltNavkontor: String,
    tssId: String?,
    mottakid: String,
    fnrbehandler: String,
    legeerklaring: Legeerklaering
) = producer.send(session.createTextMessage().apply {
    val info = createArenaInfo(tssId, lokaltNavkontor, mottakid, fnrbehandler, legeerklaring)
    text = arenaMarshaller.toString(info)
})

fun sendTilOKTopic(
    kafkaProducerLegeerklaeringSak: KafkaProducer<String, LegeerklaeringSak>,
    pale2OkTopic: String,
    legeerklaeringSak: LegeerklaeringSak,
    loggingMeta: LoggingMeta
) {
    try {
        kafkaProducerLegeerklaeringSak.send(ProducerRecord(pale2OkTopic, legeerklaeringSak)).get()
        log.info("Melding sendt til kafka topic {}", pale2OkTopic)
    } catch (e: Exception) {
        log.error("Noe gikk galt ved sending til ok-topic, {}, {}", e.message, fields(loggingMeta))
        throw e
    }
}
