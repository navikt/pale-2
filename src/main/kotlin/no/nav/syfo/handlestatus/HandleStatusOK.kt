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
    tssId: String?,
    ediLoggId: String,
    personNumberDoctor: String,
    legeerklaring: Legeerklaering,
    loggingMeta: LoggingMeta,
    kafkaProducerLegeerklaeringSak: KafkaProducer<String, LegeerklaeringSak>,
    pale2OkTopic: String,
    legeerklaeringSak: LegeerklaeringSak,
    apprecQueueName: String
) {
    val lokaltNavkontor = findNAVKontorService.finnLokaltNavkontor()

    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
    log.info("Apprec Receipt sent to {}, {}", apprecQueueName, fields(loggingMeta))

    sendArenaInfo(arenaProducer, session,
        lokaltNavkontor, tssId, ediLoggId,
        personNumberDoctor, legeerklaring)

    log.info("Legeerklæring sendt til arena, til lokal kontornr: $lokaltNavkontor, {}", fields(loggingMeta))

    kafkaProducerLegeerklaeringSak.send(
        ProducerRecord(pale2OkTopic, legeerklaeringSak)
    )
    log.info("Melding sendt til kafka topic {}", pale2OkTopic)
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
