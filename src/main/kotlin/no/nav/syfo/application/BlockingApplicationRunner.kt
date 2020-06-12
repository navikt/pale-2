package no.nav.syfo.application

import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import java.time.ZoneOffset
import java.util.UUID
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.Pale2ReglerClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.handlestatus.handleDoctorNotFoundInAktorRegister
import no.nav.syfo.handlestatus.handleDuplicateEdiloggid
import no.nav.syfo.handlestatus.handleDuplicateSM2013Content
import no.nav.syfo.handlestatus.handlePatientNotFoundInAktorRegister
import no.nav.syfo.handlestatus.handleStatusINVALID
import no.nav.syfo.handlestatus.handleStatusOK
import no.nav.syfo.handlestatus.handleTestFnrInProd
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MELDING_FEILET
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.LegeerklaeringSak
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.model.Status
import no.nav.syfo.model.toLegeerklaring
import no.nav.syfo.services.FindNAVKontorService
import no.nav.syfo.services.samhandlerPraksisErLegevakt
import no.nav.syfo.services.sha256hashstring
import no.nav.syfo.services.startSubscription
import no.nav.syfo.services.updateRedis
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.erTestFnr
import no.nav.syfo.util.extractLegeerklaering
import no.nav.syfo.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.util.extractOrganisationNumberFromSender
import no.nav.syfo.util.extractOrganisationRashNumberFromSender
import no.nav.syfo.util.extractPersonIdent
import no.nav.syfo.util.extractSenderOrganisationName
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.wrapExceptions
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException

class BlockingApplicationRunner {

    @KtorExperimentalAPI
    suspend fun run(
        applicationState: ApplicationState,
        inputconsumer: MessageConsumer,
        jedis: Jedis,
        session: Session,
        env: Environment,
        receiptProducer: MessageProducer,
        backoutProducer: MessageProducer,
        kuhrSarClient: SarClient,
        aktoerIdClient: AktoerIdClient,
        secrets: VaultSecrets,
        arenaProducer: MessageProducer,
        findNAVKontorService: FindNAVKontorService,
        kafkaProducerLegeerklaeringSak: KafkaProducer<String, LegeerklaeringSak>,
        kafkaProducerLegeerklaeringFellesformat: KafkaProducer<String, XMLEIFellesformat>,
        subscriptionEmottak: SubscriptionPort,
        pale2ReglerClient: Pale2ReglerClient
    ) {
        wrapExceptions {
            loop@ while (applicationState.ready) {
                val message = inputconsumer.receiveNoWait()
                if (message == null) {
                    delay(100)
                    continue
                }

                try {
                    val inputMessageText = when (message) {
                        is TextMessage -> message.text
                        else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
                    }
                    val fellesformat =
                        fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat

                    try {
                        kafkaProducerLegeerklaeringFellesformat.send(ProducerRecord(env.pale2DumpTopic, fellesformat)).get()
                        log.info("Melding sendt til kafka dump topic {}", env.pale2DumpTopic)
                    } catch (e: Exception) {
                        log.error("Noe gikk galt ved skriving til topic ${env.pale2DumpTopic}: ${e.message}")
                        throw e
                    }

                    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
                    val msgHead = fellesformat.get<XMLMsgHead>()
                    val ediLoggId = receiverBlock.ediLoggId
                    val msgId = msgHead.msgInfo.msgId
                    val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                    val legeerklaringxml = extractLegeerklaering(fellesformat)
                    val sha256String = sha256hashstring(legeerklaringxml)
                    val fnrPasient = extractPersonIdent(legeerklaringxml)!!
                    val legekontorOrgName = extractSenderOrganisationName(fellesformat)
                    val fnrLege = receiverBlock.avsenderFnrFraDigSignatur
                    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                    val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id

                    val requestLatency = REQUEST_TIME.startTimer()

                    val loggingMeta = LoggingMeta(
                        mottakId = receiverBlock.ediLoggId,
                        orgNr = extractOrganisationNumberFromSender(fellesformat)?.id,
                        msgId = msgHead.msgInfo.msgId
                    )

                    log.info("Received message, {}", fields(loggingMeta))

                    INCOMING_MESSAGE_COUNTER.inc()

                    val aktoerIds = aktoerIdClient.getAktoerIds(
                        listOf(fnrLege, fnrPasient),
                        secrets.serviceuserUsername, loggingMeta
                    )

                    val samhandlerInfo = kuhrSarClient.getSamhandler(fnrLege)
                    val samhandlerPraksisMatch = findBestSamhandlerPraksis(
                        samhandlerInfo,
                        legekontorOrgName,
                        legekontorHerId,
                        loggingMeta
                    )

                    val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

                    if (samhandlerPraksisMatch?.percentageMatch != null && samhandlerPraksisMatch.percentageMatch == 999.0) {
                        log.info(
                            "SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created, {}",
                            fields(loggingMeta)
                        )
                    } else {
                        when (samhandlerPraksis) {
                            null -> log.info(
                                "SamhandlerPraksis is Not found, {}",
                                fields(loggingMeta)
                            )
                            else -> if (!samhandlerPraksisErLegevakt(samhandlerPraksis) &&
                                !receiverBlock.partnerReferanse.isNullOrEmpty() &&
                                receiverBlock.partnerReferanse.isNotBlank()
                            ) {
                                startSubscription(
                                    subscriptionEmottak,
                                    samhandlerPraksis,
                                    msgHead,
                                    receiverBlock,
                                    loggingMeta
                                )
                            } else {
                                log.info(
                                    "SamhandlerPraksis is Legevakt or partnerReferanse is empty or blank, subscription_emottak is not created, {}",
                                    fields(loggingMeta)
                                )
                            }
                        }
                    }

                    val redisSha256String = jedis.get(sha256String)
                    val redisEdiloggid = jedis.get(ediLoggId)

                    if (redisSha256String != null) {
                        handleDuplicateSM2013Content(
                            session, receiptProducer,
                            fellesformat, loggingMeta, env, redisSha256String
                        )
                        continue@loop
                    } else if (redisEdiloggid != null) {
                        handleDuplicateEdiloggid(
                            session, receiptProducer,
                            fellesformat, loggingMeta, env, redisEdiloggid
                        )
                        continue@loop
                    } else {
                        updateRedis(jedis, ediLoggId, sha256String)
                    }

                    val patientIdents = aktoerIds[fnrPasient]
                    val doctorIdents = aktoerIds[fnrLege]

                    if (patientIdents == null || patientIdents.feilmelding != null) {
                        handlePatientNotFoundInAktorRegister(
                            patientIdents, session,
                            receiptProducer, fellesformat, ediLoggId, jedis, sha256String, env, loggingMeta
                        )
                        continue@loop
                    }
                    if (doctorIdents == null || doctorIdents.feilmelding != null) {
                        handleDoctorNotFoundInAktorRegister(
                            doctorIdents, session,
                            receiptProducer, fellesformat, ediLoggId, jedis, sha256String, env, loggingMeta
                        )
                        continue@loop
                    }
                    if (erTestFnr(fnrPasient) && env.cluster == "prod-fss") {
                        handleTestFnrInProd(
                            session, receiptProducer, fellesformat,
                            ediLoggId, jedis, sha256String, env, loggingMeta
                        )
                        continue@loop
                    }

                    val legeerklaring = legeerklaringxml.toLegeerklaring(
                        legeerklaringId = UUID.randomUUID().toString(),
                        fellesformat = fellesformat,
                        signaturDato = msgHead.msgInfo.genDate
                    )

                    val receivedLegeerklaering = ReceivedLegeerklaering(
                        legeerklaering = legeerklaring,
                        personNrPasient = fnrPasient,
                        pasientAktoerId = patientIdents.identer!!.first().ident,
                        personNrLege = fnrLege,
                        legeAktoerId = doctorIdents.identer!!.first().ident,
                        navLogId = ediLoggId,
                        msgId = msgId,
                        legekontorOrgNr = legekontorOrgNr,
                        legekontorOrgName = legekontorOrgName,
                        legekontorHerId = legekontorHerId,
                        legekontorReshId = legekontorReshId,
                        mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime()
                            .withZoneSameInstant(
                                ZoneOffset.UTC
                            ).toLocalDateTime(),
                        fellesformat = inputMessageText,
                        tssid = samhandlerPraksis?.tss_ident ?: ""
                    )

                    val validationResult = pale2ReglerClient.executeRuleValidation(receivedLegeerklaering)

                    val legeerklaeringSak = LegeerklaeringSak(receivedLegeerklaering, validationResult)

                    try {
                        kafkaProducerLegeerklaeringSak.send(ProducerRecord(env.pale2SakTopic, legeerklaring.id, legeerklaeringSak)).get()
                        log.info(
                            "Melding sendt til kafka topic {}, {}", env.pale2SakTopic,
                            fields(loggingMeta)
                        )
                    } catch (e: Exception) {
                        log.error("Kunne ikke skrive til sak-topic: {}, {}", e.message, fields(loggingMeta))
                        throw e
                    }

                    when (validationResult.status) {
                        Status.OK -> handleStatusOK(
                            session,
                            receiptProducer,
                            fellesformat,
                            arenaProducer,
                            findNAVKontorService,
                            fnrPasient,
                            samhandlerPraksis?.tss_ident,
                            ediLoggId,
                            fnrLege,
                            legeerklaring,
                            loggingMeta,
                            kafkaProducerLegeerklaeringSak,
                            env.pale2OkTopic,
                            legeerklaeringSak,
                            env.apprecQueueName
                        )

                        Status.INVALID -> handleStatusINVALID(
                            validationResult,
                            session,
                            receiptProducer,
                            fellesformat,
                            loggingMeta,
                            kafkaProducerLegeerklaeringSak,
                            env.pale2AvvistTopic,
                            legeerklaeringSak,
                            env.apprecQueueName
                        )
                    }

                    val currentRequestLatency = requestLatency.observeDuration()

                    log.info(
                        "Finished message got outcome {}, {}, processing took {}s",
                        StructuredArguments.keyValue("status", validationResult.status),
                        StructuredArguments.keyValue(
                            "ruleHits",
                            validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                        StructuredArguments.keyValue("latency", currentRequestLatency),
                        fields(loggingMeta)
                    )
                } catch (jedisException: JedisConnectionException) {
                    log.error(
                        "Exception caught, redis issue while handling message, sending to backout",
                        jedisException
                    )
                    backoutProducer.send(message)
                    MELDING_FEILET.inc()
                    log.error("Setting applicationState.alive to false")
                    applicationState.alive = false
                } catch (e: Exception) {
                    log.error("Exception caught while handling message, sending to backout, {}", e)
                    backoutProducer.send(message)
                    MELDING_FEILET.inc()
                } finally {
                    message.acknowledge()
                }
            }
        }
    }
}
