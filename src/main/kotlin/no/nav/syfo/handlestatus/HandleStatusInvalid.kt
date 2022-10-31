package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.apprecV1.XMLCV
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.Environment
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprecCV
import no.nav.syfo.log
import no.nav.syfo.metrics.FOR_MANGE_TEGN
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.TEST_FNR_IN_PROD
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.services.sendReceipt
import no.nav.syfo.services.updateRedis
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import redis.clients.jedis.Jedis
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
    legeerklaeringId: String
) {
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        validationResult.ruleHits.map { it.toApprecCV() }
    )
    log.info("Apprec Receipt sent to {}, {}", apprecQueueName, fields(loggingMeta))

    sendTilTopic(aivenKafkaProducer, topic, legeerklaringKafkaMessage, legeerklaeringId, loggingMeta)
}

fun handleDuplicateSM2013Content(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    env: Environment,
    redisSha256String: String
) {

    log.warn(
        "Melding med {} har samme innhold som tidligere mottatt legeerklæring og er avvist som duplikat {}, {}",
        keyValue("originalEdiLoggId", redisSha256String),
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Duplikat! - Denne legeerklæringen er mottatt tidligere. " +
                    "Skal ikke sendes på nytt."
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleDuplicateEdiloggid(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    loggingMeta: LoggingMeta,
    env: Environment,
    redisEdiloggid: String
) {

    log.warn(
        "Melding med {} har samme ediLoggId som tidligere mottatt legeerklæring og er avvist som duplikat {}, {}",
        keyValue("originalEdiLoggId", redisEdiloggid),
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Duplikat! Denne legeerklæringen har samme identifikator som en legeerklæring som er mottatt tidligere" +
                    " og skal ikke sendes på nytt. Dersom dette ikke stemmer, kontakt din EPJ-leverandør"
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handlePatientNotFoundInPDL(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
    env: Environment,
    loggingMeta: LoggingMeta
) {
    log.warn(
        "Legeerklæringen er avvist fordi pasienten ikke finnes i folkeregisteret {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError("Pasienten er ikkje registrert i folkeregisteret")
        )
    )

    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleDoctorNotFoundInPDL(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
    env: Environment,
    loggingMeta: LoggingMeta
) {
    log.warn(
        "Legeerklæringen er avvist fordi legen ikke finnes i folkeregisteret {}, {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Behandler er ikke registrert i folkeregisteret"
            )
        )
    )

    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleFritekstfeltHarForMangeTegn(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
    env: Environment,
    loggingMeta: LoggingMeta,
    fritekstfelt: String,
    aivenKafkaProducer: KafkaProducer<String, LegeerklaeringKafkaMessage>,
    legeerklaringKafkaMessage: LegeerklaeringKafkaMessage,
    legeerklaeringId: String
) {
    log.warn(
        "Legeerklæringen er avvist fordi $fritekstfelt inneholder mer enn 15 000 tegn {}, {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Legeerklæringen er avvist fordi den inneholder for mange tegn: " +
                    " $fritekstfelt inneholder mer enn 15 000 tegn. Benytt heller vedlegg for epikriser og lignende. "
            )
        )
    )

    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    sendTilTopic(aivenKafkaProducer, env.legeerklaringTopic, legeerklaringKafkaMessage, legeerklaeringId, loggingMeta)
    log.info("Sendt avvist legeerklæring til topic {}", fields(loggingMeta))

    FOR_MANGE_TEGN.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleVedleggContainsVirus(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
    env: Environment,
    loggingMeta: LoggingMeta
) {
    log.warn(
        "Legeerklæringen er avvist fordi eit eller flere vedlegg kan potensielt inneholde virus {}, {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )
    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Legeerklæringen er avvist fordi eit eller flere vedlegg kan potensielt inneholde virus" +
                    "sjekk om vedleggene inneholder virus"
            )
        )
    )

    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()

    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleTestFnrInProd(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    jedis: Jedis,
    sha256String: String,
    env: Environment,
    loggingMeta: LoggingMeta
) {
    log.warn(
        "Legeerklæring avvist: Testfødselsnummer er kommet inn i produksjon! {}, {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    log.warn(
        "Avsender fodselsnummer er registert i Helsepersonellregisteret (HPR), {}",
        fields(loggingMeta)
    )

    sendReceipt(
        session, receiptProducer, fellesformat, ApprecStatus.avvist,
        listOf(
            createApprecError(
                "Dette fødselsnummeret tilhører en testbruker og skal ikke brukes i produksjon"
            )
        )
    )
    log.info("Apprec Receipt sent to {}, {}", env.apprecQueueName, fields(loggingMeta))

    INVALID_MESSAGE_NO_NOTICE.inc()
    TEST_FNR_IN_PROD.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun createApprecError(textToTreater: String): XMLCV = XMLCV().apply {
    dn = textToTreater
    v = "2.16.578.1.12.4.1.1.8221"
    s = "X99"
}
