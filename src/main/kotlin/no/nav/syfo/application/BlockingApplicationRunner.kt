package no.nav.syfo.application

import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import java.time.format.DateTimeFormatter
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.LegeSuspensjonClient
import no.nav.syfo.client.Norg2Client
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.createJournalpostPayload
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.createApprecError
import no.nav.syfo.extractPersonIdent
import no.nav.syfo.get
import no.nav.syfo.handlestatus.handleStatusINVALID
import no.nav.syfo.handlestatus.handleStatusMANUALPROCESSING
import no.nav.syfo.handlestatus.handleStatusOK
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.rules.HPRRuleChain
import no.nav.syfo.rules.LegesuspensjonRuleChain
import no.nav.syfo.rules.PostDiskresjonskodeRuleChain
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.rules.executeFlow
import no.nav.syfo.sendReceipt
import no.nav.syfo.services.FindNAVKontorService
import no.nav.syfo.services.fetchDiskresjonsKode
import no.nav.syfo.services.sha256hashstring
import no.nav.syfo.services.updateRedis
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractLegeerklaering
import no.nav.syfo.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.util.extractOrganisationNumberFromSender
import no.nav.syfo.util.extractSenderOrganisationName
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.wrapExceptions
import no.nav.syfo.validationResult
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
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
        legeSuspensjonClient: LegeSuspensjonClient,
        pdfgenClient: PdfgenClient,
        sakClient: SakClient,
        dokArkivClient: DokArkivClient,
        arenaProducer: MessageProducer,
        personV3: PersonV3,
        norg2Client: Norg2Client,
        norskHelsenettClient: NorskHelsenettClient
    ) = coroutineScope {
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
                    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
                    val msgHead = fellesformat.get<XMLMsgHead>()
                    val ediLoggId = receiverBlock.ediLoggId
                    val msgId = msgHead.msgInfo.msgId
                    val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                    val legeerklaring = extractLegeerklaering(fellesformat)
                    val sha256String = sha256hashstring(legeerklaring)
                    val personNumberPatient = extractPersonIdent(legeerklaring)!!
                    val legekontorOrgName = extractSenderOrganisationName(fellesformat)
                    val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
                    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id

                    INCOMING_MESSAGE_COUNTER.inc()
                    val requestLatency = REQUEST_TIME.startTimer()

                    val loggingMeta = LoggingMeta(
                        mottakId = receiverBlock.ediLoggId,
                        orgNr = extractOrganisationNumberFromSender(fellesformat)?.id,
                        msgId = msgHead.msgInfo.msgId
                    )

                    log.info("Received message, {}", StructuredArguments.fields(loggingMeta))

                    val aktoerIds = aktoerIdClient.getAktoerIds(
                        listOf(personNumberDoctor, personNumberPatient),
                        secrets.serviceuserUsername, loggingMeta
                    )

                    val samhandlerInfo = kuhrSarClient.getSamhandler(personNumberDoctor)
                    val samhandlerPraksis = findBestSamhandlerPraksis(
                        samhandlerInfo,
                        legekontorOrgName,
                        legekontorHerId,
                        loggingMeta
                    )?.samhandlerPraksis

                    val redisSha256String = jedis.get(sha256String)
                    val redisEdiloggid = jedis.get(ediLoggId)

                    if (redisSha256String != null) {
                        log.warn(
                            "Message with {} marked as duplicate {}",
                            StructuredArguments.keyValue("originalEdiLoggId", redisSha256String),
                            StructuredArguments.fields(loggingMeta)
                        )
                        sendReceipt(
                            session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                                createApprecError(
                                    "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                                            "Skal ikke sendes på nytt."
                                )
                            )
                        )
                        log.info(
                            "Apprec Receipt sent to {}, {}", env.apprecQueueName,
                            StructuredArguments.fields(loggingMeta)
                        )
                        continue@loop
                    } else if (redisEdiloggid != null) {
                        log.warn(
                            "Message with {} marked as duplicate, {}",
                            StructuredArguments.keyValue("originalEdiLoggId", redisEdiloggid),
                            StructuredArguments.fields(loggingMeta)
                        )
                        sendReceipt(
                            session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                                createApprecError(
                                    "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                                            "Skal ikke sendes på nytt."
                                )
                            )
                        )
                        log.info(
                            "Apprec Receipt sent to {}, {}", env.apprecQueueName,
                            StructuredArguments.fields(loggingMeta)
                        )
                        continue@loop
                    } else {
                        updateRedis(jedis, ediLoggId, sha256String)
                    }

                    val patientIdents = aktoerIds[personNumberPatient]
                    val doctorIdents = aktoerIds[personNumberDoctor]

                    log.info("Hentet ut aktorider, {}", StructuredArguments.fields(loggingMeta))

                    if (patientIdents == null || patientIdents.feilmelding != null) {
                        log.info(
                            "Patient not found i aktorRegister {}, {}", StructuredArguments.fields(loggingMeta),
                            StructuredArguments.keyValue(
                                "errorMessage",
                                patientIdents?.feilmelding ?: "No response for FNR"
                            )
                        )
                        sendReceipt(
                            session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                                createApprecError("Pasienten er ikkje registrert i folkeregisteret")
                            )
                        )
                        log.info(
                            "Apprec Receipt sent to {}, {}", env.apprecQueueName,
                            StructuredArguments.fields(loggingMeta)
                        )
                        INVALID_MESSAGE_NO_NOTICE.inc()
                        continue@loop
                    }
                    if (doctorIdents == null || doctorIdents.feilmelding != null) {
                        log.info(
                            "Doctor not found i aktorRegister {}, {}", StructuredArguments.fields(loggingMeta),
                            StructuredArguments.keyValue(
                                "errorMessage",
                                doctorIdents?.feilmelding ?: "No response for FNR"
                            )
                        )
                        sendReceipt(
                            session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                                createApprecError("Behandler er ikkje registrert i folkeregisteret")
                            )
                        )
                        log.info(
                            "Apprec Receipt sent to {}, {}", env.apprecQueueName,
                            StructuredArguments.fields(loggingMeta)
                        )
                        INVALID_MESSAGE_NO_NOTICE.inc()
                        continue@loop
                    }

                    val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, personNumberPatient)

                    log.info("Hentet ut patientDiskresjonsKode, {}", StructuredArguments.fields(loggingMeta))

                    val doctorSuspendDeferred = async {
                        val signaturDatoString = DateTimeFormatter.ISO_DATE.format(msgHead.msgInfo.genDate)
                        legeSuspensjonClient.checkTherapist(personNumberDoctor, msgId, signaturDatoString).suspendert
                    }

                    log.info("Hentet ut legeSuspensjonClient, {}", StructuredArguments.fields(loggingMeta))

                    val behandler = norskHelsenettClient.finnBehandler(
                        behandlerFnr = personNumberDoctor,
                        msgId = msgId,
                        loggingMeta = loggingMeta
                    )

                    if (behandler == null) {
                        log.info(
                            "Doctor not found i aktorRegister {}, {}", StructuredArguments.fields(loggingMeta),
                            StructuredArguments.keyValue(
                                "errorMessage",
                                doctorIdents?.feilmelding ?: "No response for FNR"
                            )
                        )
                        sendReceipt(
                            session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(
                                createApprecError("Avsender fodselsnummer er registert i Helsepersonellregisteret (HPR)")
                            )
                        )
                        log.info(
                            "Apprec Receipt sent to {}, {}", env.apprecQueueName,
                            StructuredArguments.fields(loggingMeta)
                        )
                        INVALID_MESSAGE_NO_NOTICE.inc()
                        continue@loop
                    }

                    log.info(
                        "Avsender behandler har hprnummer: ${behandler.hprNummer}, {}",
                        StructuredArguments.fields(loggingMeta)
                    )

                    val results = listOf(
                        ValidationRuleChain.values().executeFlow(
                            legeerklaring, RuleMetadata(
                                receivedDate = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime().toLocalDateTime(),
                                signatureDate = msgHead.msgInfo.genDate,
                                patientPersonNumber = personNumberPatient,
                                legekontorOrgnr = legekontorOrgNr,
                                tssid = samhandlerPraksis?.tss_ident
                            )
                        ),
                        PostDiskresjonskodeRuleChain.values().executeFlow(legeerklaring, patientDiskresjonsKode),
                        HPRRuleChain.values().executeFlow(legeerklaring, behandler),
                        LegesuspensjonRuleChain.values().executeFlow(legeerklaring, doctorSuspendDeferred.await())
                    ).flatten()

                    log.info("Rules hit {}, {}", results.map { it.name }, StructuredArguments.fields(loggingMeta))

                    val findNAVKontorService = FindNAVKontorService(
                        personNumberPatient,
                        personV3,
                        norg2Client,
                        patientDiskresjonsKode,
                        loggingMeta
                    )

                    val lokaltNavkontor = findNAVKontorService.finnLokaltNavkontor()

                    val legeerklaering = extractLegeerklaering(fellesformat)
                    val plan = legeerklaering.planUtredBehandle
                    val forslagTiltak = legeerklaering.forslagTiltak
                    val typeLegeerklaering = legeerklaering.legeerklaringGjelder[0].typeLegeerklaring.toInt()
                    val funksjonsevne = legeerklaering.vurderingFunksjonsevne
                    val prognose = legeerklaering.prognose
                    val healthcareProfessional =
                        fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.healthcareProfessional

                    val validationResult = validationResult(results)
                    RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()

                    val pdfPayload = pdfgenClient.createPdfPayload(
                        legeerklaering,
                        plan,
                        forslagTiltak,
                        typeLegeerklaering,
                        funksjonsevne,
                        prognose,
                        healthcareProfessional,
                        fellesformat,
                        validationResult
                    )

                    val sakid = sakClient.findOrCreateSak(
                        patientIdents.identer!!.first().ident, msgId,
                        loggingMeta
                    ).id.toString()

                    val pdf = pdfgenClient.createPDF(pdfPayload)
                    log.info("PDF generated {}", StructuredArguments.fields(loggingMeta))

                    val journalpostPayload = createJournalpostPayload(
                        legeerklaering,
                        sakid,
                        pdf,
                        msgHead,
                        receiverBlock,
                        validationResult
                    )

                    val journalpost = dokArkivClient.createJournalpost(journalpostPayload, loggingMeta)

                    log.info(
                        "Message successfully persisted in Joark {}, {}",
                        StructuredArguments.keyValue("journalpostId", journalpost.journalpostId),
                        StructuredArguments.fields(loggingMeta)
                    )

                    when (validationResult.status) {

                        Status.OK -> handleStatusOK(
                            session,
                            receiptProducer,
                            fellesformat,
                            arenaProducer,
                            lokaltNavkontor,
                            samhandlerPraksis?.tss_ident,
                            ediLoggId,
                            personNumberDoctor,
                            healthcareProfessional
                        )

                        Status.MANUAL_PROCESSING -> handleStatusMANUALPROCESSING(
                            session,
                            receiptProducer,
                            fellesformat
                        )

                        Status.INVALID -> handleStatusINVALID(
                            validationResult,
                            session,
                            receiptProducer,
                            fellesformat
                        )
                    }

                    val currentRequestLatency = requestLatency.observeDuration()

                    log.info(
                        "Message got outcome {}, {}, processing took {}s",
                        StructuredArguments.keyValue("status", validationResult.status),
                        StructuredArguments.keyValue(
                            "ruleHits",
                            validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                        StructuredArguments.keyValue("latency", currentRequestLatency),
                        StructuredArguments.fields(loggingMeta)
                    )
                } catch (jedisException: JedisConnectionException) {
                    log.error(
                        "Exception caught, redis issue while handling message, sending to backout",
                        jedisException
                    )
                    backoutProducer.send(message)
                    log.error("Setting applicationState.alive to false")
                    applicationState.alive = false
                } catch (e: Exception) {
                    log.error("Exception caught while handling message, sending to backout, {}", e)
                    backoutProducer.send(message)
                }
            }
        }
    }
}
