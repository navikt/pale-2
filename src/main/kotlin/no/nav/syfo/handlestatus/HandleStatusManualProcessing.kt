package no.nav.syfo.handlestatus

import javax.jms.MessageProducer
import javax.jms.Session
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.sendReceipt

fun handleStatusMANUALPROCESSING(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat

) {
    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
}
