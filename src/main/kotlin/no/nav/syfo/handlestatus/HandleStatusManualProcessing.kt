package no.nav.syfo.handlestatus

import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.log
import no.nav.syfo.sendReceipt
import no.nav.syfo.util.LoggingMeta

fun handleStatusMANUALPROCESSING(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta

) {
    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
    log.info("Informasjon om Legeerklæring blir ikkje sendt til arena {}", fields(loggingMeta))
}
