package no.nav.syfo.handlestatus

import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprecCV
import no.nav.syfo.log
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.TEST_FNR_IN_PROD
import no.nav.syfo.model.IdentInfoResult
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sendReceipt
import no.nav.syfo.services.updateRedis
import no.nav.syfo.util.LoggingMeta
import redis.clients.jedis.Jedis

fun handleStatusINVALID(
    validationResult: ValidationResult,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat
) {
    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist,
        validationResult.ruleHits.map { it.toApprecCV() })
}

fun handleDuplicateSM2013Content(
    validationResult: ValidationResult,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat
) {

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleDuplicateEdiloggid(
    validationResult: ValidationResult,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat
) {

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handlePatientNotFoundInAktorRegister(
    validationResult: ValidationResult,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String
) {

    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleDoctorNotFoundInAktorRegister(
    doctorIdents: IdentInfoResult?,
    validationResult: ValidationResult,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
    loggingMeta: LoggingMeta
) {
    log.warn("Doctor not found i aktorRegister error: {}, {}",
            keyValue("errorMessage", doctorIdents?.feilmelding ?: "No response for FNR"),
            fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleTestFnrInProd(
    validationResult: ValidationResult,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
    loggingMeta: LoggingMeta
) {
    log.warn("Test fødselsnummer er kommet inn i produksjon {}", fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    TEST_FNR_IN_PROD.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}
