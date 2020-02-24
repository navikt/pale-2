package no.nav.syfo.handlestatus

import javax.jms.MessageProducer
import javax.jms.Session
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.client.createArenaInfo
import no.nav.syfo.sendReceipt
import no.nav.syfo.toString
import no.nav.syfo.util.arenaMarshaller

fun handleStatusOK(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    arenaProducer: MessageProducer,
    lokaltNavkontor: String,
    tssId: String?,
    ediLoggId: String,
    personNumberDoctor: String,
    healthcareProfessional: XMLHealthcareProfessional?
) {
    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)

    sendArenaInfo(arenaProducer, session, fellesformat,
        lokaltNavkontor, tssId,
        ediLoggId, healthcareProfessional, personNumberDoctor)
}

fun sendArenaInfo(
    producer: MessageProducer,
    session: Session,
    fellesformat: XMLEIFellesformat,
    lokaltNavkontor: String,
    tssId: String?,
    mottakid: String,
    healthcareProfessional: XMLHealthcareProfessional?,
    fnrbehandler: String
) = producer.send(session.createTextMessage().apply {
    val info = createArenaInfo(fellesformat, tssId, lokaltNavkontor, mottakid, healthcareProfessional, fnrbehandler)
    text = arenaMarshaller.toString(info)
})
