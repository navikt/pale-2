package no.nav.syfo.services.apprec

import jakarta.jms.MessageProducer
import jakarta.jms.Session
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.log
import no.nav.syfo.metrics.APPREC_COUNTER
import no.nav.syfo.services.duplicationcheck.DuplicationCheckService
import no.nav.syfo.services.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.apprecMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.toString

fun sendReceipt(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    apprecStatus: ApprecStatus,
    apprecErrors: List<XMLCV> = listOf(),
    duplicationCheckService: DuplicationCheckService,
    duplicateCheck: DuplicateCheck,
    loggingMeta: LoggingMeta,
    apprecQueueName: String,
) {
    receiptProducer.send(
        session.createTextMessage().apply {
            val apprec = createApprec(fellesformat, apprecStatus)
            if (apprecErrors.isNotEmpty()) {
                apprec.get<XMLAppRec>().error.addAll(apprecErrors)
            }
            text = apprecMarshaller.toString(apprec)
        },
    )
    APPREC_COUNTER.inc()
    log.info(
        "Apprec Receipt sent to {}, {}",
        apprecQueueName,
        StructuredArguments.fields(loggingMeta)
    )

    duplicationCheckService.persistDuplicationCheck(duplicateCheck)
}
