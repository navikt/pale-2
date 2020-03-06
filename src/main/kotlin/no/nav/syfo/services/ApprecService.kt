package no.nav.syfo.services

import javax.jms.MessageProducer
import javax.jms.Session
import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.metrics.APPREC_COUNTER
import no.nav.syfo.util.apprecMarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.toString

fun sendReceipt(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    apprecStatus: ApprecStatus,
    apprecErrors: List<XMLCV> = listOf()
) {
    receiptProducer.send(session.createTextMessage().apply {
        val apprec = createApprec(fellesformat, apprecStatus)
        apprec.get<XMLAppRec>().error.addAll(apprecErrors)
        text = apprecMarshaller.toString(apprec)
    })
    APPREC_COUNTER.inc()
}
