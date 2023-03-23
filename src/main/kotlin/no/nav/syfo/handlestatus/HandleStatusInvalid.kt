package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.Environment
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprecCV
import no.nav.syfo.log
import no.nav.syfo.metrics.DUPLICATE_LEGEERKLERING
import no.nav.syfo.metrics.FOR_MANGE_TEGN
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.TEST_FNR_IN_PROD
import no.nav.syfo.metrics.VEDLEGG_VIRUS_COUNTER
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.services.duplicationcheck.DuplicationCheckService
import no.nav.syfo.services.duplicationcheck.model.Duplicate
import no.nav.syfo.services.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import javax.jms.MessageProducer
import javax.jms.Session

fun handleStatusINVALID(
    validationResult: ValidationResult,
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    aivenKafkaProducer: KafkaProducer<String, LegeerklaeringKafkaMessage>,
    topic: String,
    legeerklaringKafkaMessage: LegeerklaeringKafkaMessage,
    apprecQueueName: String,
    legeerklaeringId: String,
    duplicationCheckService: DuplicationCheckService,
    duplicateCheck: DuplicateCheck,
) {
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.AVVIST,
        validationResult.ruleHits.map { it.toApprecCV() },

        duplicationCheckService, duplicateCheck, loggingMeta, apprecQueueName,
    )
    sendTilTopic(aivenKafkaProducer, topic, legeerklaringKafkaMessage, legeerklaeringId, loggingMeta)
}

fun handleDuplicateLegeerklaringContent(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    env: Environment,
    duplicationCheckService: DuplicationCheckService,
    duplicateCheck: DuplicateCheck,
    duplicate: Duplicate,
) {
    log.warn(
        "Melding med {} har samme innhold som tidligere mottatt legeerklæring og er avvist som duplikat {}, {}",
        keyValue("originalEdiLoggId", duplicateCheck.mottakId),
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.AVVIST,
        listOf(
            createApprecError(
                "Duplikat! - Denne legeerklæringen er mottatt tidligere. " +
                    "Skal ikke sendes på nytt.",
            ),
        ),
        duplicationCheckService, duplicateCheck, loggingMeta, env.apprecQueueName,
    )
    INVALID_MESSAGE_NO_NOTICE.inc()
    DUPLICATE_LEGEERKLERING.inc()

    duplicationCheckService.persistDuplication(duplicate)
}

fun handlePatientNotFoundInPDL(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta,
    duplicationCheckService: DuplicationCheckService,
    duplicateCheck: DuplicateCheck,
) {
    log.warn(
        "Legeerklæringen er avvist fordi pasienten ikke finnes i folkeregisteret {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.AVVIST,
        listOf(
            createApprecError("Pasienten er ikkje registrert i folkeregisteret"),
        ),
        duplicationCheckService, duplicateCheck, loggingMeta, env.apprecQueueName,
    )
    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleDoctorNotFoundInPDL(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta,
    duplicationCheckService: DuplicationCheckService,
    duplicateCheck: DuplicateCheck,
) {
    log.warn(
        "Legeerklæringen er avvist fordi legen ikke finnes i folkeregisteret {}, {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.AVVIST,
        listOf(
            createApprecError(
                "Behandler er ikke registrert i folkeregisteret",
            ),
        ),
        duplicationCheckService, duplicateCheck, loggingMeta, env.apprecQueueName,
    )

    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleFritekstfeltHarForMangeTegn(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta,
    fritekstfelt: String,
    aivenKafkaProducer: KafkaProducer<String, LegeerklaeringKafkaMessage>,
    legeerklaringKafkaMessage: LegeerklaeringKafkaMessage,
    legeerklaeringId: String,
    duplicationCheckService: DuplicationCheckService,
    duplicateCheck: DuplicateCheck,
) {
    log.warn(
        "Legeerklæringen er avvist fordi $fritekstfelt inneholder mer enn 15 000 tegn {}, {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.AVVIST,
        listOf(
            createApprecError(
                "Legeerklæringen er avvist fordi den inneholder for mange tegn: " +
                    " $fritekstfelt inneholder mer enn 15 000 tegn. Benytt heller vedlegg for epikriser og lignende. ",
            ),
        ),
        duplicationCheckService, duplicateCheck, loggingMeta, env.apprecQueueName,
    )

    sendTilTopic(aivenKafkaProducer, env.legeerklaringTopic, legeerklaringKafkaMessage, legeerklaeringId, loggingMeta)
    log.info("Sendt avvist legeerklæring til topic {}", fields(loggingMeta))

    FOR_MANGE_TEGN.inc()
}

fun handleVedleggContainsVirus(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta,
    duplicationCheckService: DuplicationCheckService,
    duplicateCheck: DuplicateCheck,
) {
    log.warn(
        "Legeerklæringen er avvist fordi eit eller flere vedlegg kan potensielt inneholde virus {}, {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.AVVIST,
        listOf(
            createApprecError(
                "Legeerklæringen er avvist fordi eit eller flere vedlegg kan potensielt inneholde virus" +
                    "sjekk om vedleggene inneholder virus",
            ),
        ),
        duplicationCheckService, duplicateCheck, loggingMeta, env.apprecQueueName,
    )

    INVALID_MESSAGE_NO_NOTICE.inc()
    VEDLEGG_VIRUS_COUNTER.inc()
}

fun handleTestFnrInProd(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    env: Environment,
    loggingMeta: LoggingMeta,
    duplicationCheckService: DuplicationCheckService,
    duplicateCheck: DuplicateCheck,
) {
    log.warn(
        "Legeerklæring avvist: Testfødselsnummer er kommet inn i produksjon! {}, {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    log.warn(
        "Avsender fodselsnummer er registert i Helsepersonellregisteret (HPR), {}",
        fields(loggingMeta),
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.AVVIST,
        listOf(
            createApprecError(
                "Dette fødselsnummeret tilhører en testbruker og skal ikke brukes i produksjon",
            ),
        ),
        duplicationCheckService, duplicateCheck, loggingMeta, env.apprecQueueName,
    )

    INVALID_MESSAGE_NO_NOTICE.inc()
    TEST_FNR_IN_PROD.inc()
}

fun createApprecError(textToTreater: String): XMLCV = XMLCV().apply {
    dn = textToTreater
    v = "2.16.578.1.12.4.1.1.8221"
    s = "X99"
}
