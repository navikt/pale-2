package no.nav.syfo.rerun

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import java.time.ZoneOffset
import java.util.UUID
import javax.jms.MessageProducer
import javax.jms.Session
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.Pale2ReglerClient
import no.nav.syfo.client.createArenaInfo
import no.nav.syfo.handlestatus.sendTilAvvistTopic
import no.nav.syfo.handlestatus.sendTilOKTopic
import no.nav.syfo.kafka.vedlegg.producer.KafkaVedleggProducer
import no.nav.syfo.log
import no.nav.syfo.metrics.VEDLEGG_COUNTER
import no.nav.syfo.model.LegeerklaeringSak
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.model.Status
import no.nav.syfo.model.toLegeerklaring
import no.nav.syfo.rerun.kafka.RerunConsumer
import no.nav.syfo.services.FindNAVKontorService
import no.nav.syfo.services.SamhandlerService
import no.nav.syfo.services.sha256hashstring
import no.nav.syfo.services.updateRedis
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.XMLDateAdapter
import no.nav.syfo.util.XMLDateTimeAdapter
import no.nav.syfo.util.arenaEiaInfoJaxBContext
import no.nav.syfo.util.erTestFnr
import no.nav.syfo.util.extractLegeerklaering
import no.nav.syfo.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.util.extractOrganisationNumberFromSender
import no.nav.syfo.util.extractOrganisationRashNumberFromSender
import no.nav.syfo.util.extractPersonIdent
import no.nav.syfo.util.extractSenderOrganisationName
import no.nav.syfo.util.fellesformatJaxBContext
import no.nav.syfo.util.get
import no.nav.syfo.util.getVedlegg
import no.nav.syfo.util.removeVedleggFromFellesformat
import no.nav.syfo.util.toString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException

@KtorExperimentalAPI
class RerunService(
    private val applicationState: ApplicationState,
    private val rerunConsumer: RerunConsumer,
    private val jedis: Jedis,
    private val env: Environment,
    private val session: Session,
    private val samhandlerService: SamhandlerService,
    private val aktoerIdClient: AktoerIdClient,
    private val secrets: VaultSecrets,
    private val arenaProducer: MessageProducer,
    private val findNAVKontorService: FindNAVKontorService,
    private val kafkaProducerLegeerklaeringSak: KafkaProducer<String, LegeerklaeringSak>,
    private val pale2ReglerClient: Pale2ReglerClient,
    private val kafkaVedleggProducer: KafkaVedleggProducer
) {
    val skalBehandles = listOf("27239233023", "15386833405")

    suspend fun start() {
        while (applicationState.ready) {
            val rerunMeldingerSomString = rerunConsumer.poll()
            rerunMeldingerSomString.forEach {
                behandleLegeerklaering(it)
            }
            delay(1)
        }
    }

    suspend fun behandleLegeerklaering(meldingSomString: String) {
        val rerunFellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller().apply {
            setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
            setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
        }
        val fellesformat = rerunFellesformatUnmarshaller.unmarshal(StringReader(meldingSomString)) as XMLEIFellesformat
        val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
        val msgHead = fellesformat.get<XMLMsgHead>()
        val ediLoggId = receiverBlock.ediLoggId
        val msgId = msgHead.msgInfo.msgId

        if (skalBehandleMelding(msgId)) {
            try {
                val vedlegg = getVedlegg(fellesformat)
                if (vedlegg.isNotEmpty()) {
                    VEDLEGG_COUNTER.inc()
                    removeVedleggFromFellesformat(fellesformat)
                }
                val fellesformatText = when (vedlegg.isNotEmpty()) {
                    true -> {
                        val rerunFellesformatMarshaller: Marshaller = fellesformatJaxBContext.createMarshaller().apply {
                            setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
                            setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
                            setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
                            setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
                        }
                        rerunFellesformatMarshaller.toString(fellesformat)
                    }
                    false -> meldingSomString
                }
                val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                val legeerklaringxml = extractLegeerklaering(fellesformat)
                val sha256String = sha256hashstring(legeerklaringxml)
                val fnrPasient = extractPersonIdent(legeerklaringxml)!!
                val legekontorOrgName = extractSenderOrganisationName(fellesformat)
                val fnrLege = receiverBlock.avsenderFnrFraDigSignatur
                val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
                val loggingMeta = LoggingMeta(
                    mottakId = receiverBlock.ediLoggId,
                    orgNr = extractOrganisationNumberFromSender(fellesformat)?.id,
                    msgId = msgHead.msgInfo.msgId
                )
                val aktoerIds = aktoerIdClient.getAktoerIds(
                    listOf(fnrLege, fnrPasient),
                    secrets.serviceuserUsername, loggingMeta
                )

                val tssIdent = samhandlerService.finnTssIdentOgStartSubscription(
                    fnrLege = fnrLege,
                    legekontorOrgName = legekontorOrgName,
                    legekontorHerId = legekontorHerId,
                    receiverBlock = receiverBlock,
                    msgHead = msgHead,
                    loggingMeta = loggingMeta
                )

                val redisSha256String = jedis.get(sha256String)
                val redisEdiloggid = jedis.get(ediLoggId)

                if (redisSha256String != null) {
                    log.error("Duplikat innhold, {}", StructuredArguments.fields(loggingMeta))
                    throw RuntimeException("Duplikat innhold!")
                } else if (redisEdiloggid != null) {
                    log.error("Duplikat ediloggId, {}", StructuredArguments.fields(loggingMeta))
                    throw RuntimeException("Duplikat ediLoggId!")
                } else {
                    val patientIdents = aktoerIds[fnrPasient]
                    val doctorIdents = aktoerIds[fnrLege]

                    if (patientIdents == null || patientIdents.feilmelding != null) {
                        log.error("Fant ikke pasient, {}", StructuredArguments.fields(loggingMeta))
                        throw RuntimeException("Fant ikke pasient")
                    }
                    if (doctorIdents == null || doctorIdents.feilmelding != null) {
                        log.error("Fant ikke behandler, {}", StructuredArguments.fields(loggingMeta))
                        throw RuntimeException("Fant ikke behandler")
                    }
                    if (erTestFnr(fnrPasient) && env.cluster == "prod-fss") {
                        log.error("Test-fnr, ignorerer, {}", StructuredArguments.fields(loggingMeta))
                        return
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
                        fellesformat = fellesformatText,
                        tssid = tssIdent
                    )

                    val validationResult = pale2ReglerClient.executeRuleValidation(receivedLegeerklaering)
                    val legeerklaeringSak = LegeerklaeringSak(receivedLegeerklaering, validationResult)

                    skrivTilSakTopic(
                        kafkaProducerLegeerklaeringSak = kafkaProducerLegeerklaeringSak,
                        pale2SakTopic = env.pale2SakTopic,
                        legeerklaringId = legeerklaring.id,
                        legeerklaeringSak = legeerklaeringSak,
                        loggingMeta = loggingMeta
                    )

                    when (validationResult.status) {
                        Status.OK -> {
                            val lokaltNavkontor = findNAVKontorService.finnLokaltNavkontor(fnrPasient, loggingMeta)
                            val rerunArenaMarshaller: Marshaller = arenaEiaInfoJaxBContext.createMarshaller()
                            arenaProducer.send(session.createTextMessage().apply {
                                val info = createArenaInfo(tssIdent, lokaltNavkontor, ediLoggId, fnrLege, legeerklaring)
                                text = rerunArenaMarshaller.toString(info)
                            })
                            log.info("Legeerklæring sendt til arena, til lokal kontornr: $lokaltNavkontor, {}", StructuredArguments.fields(loggingMeta))
                            sendTilOKTopic(kafkaProducerLegeerklaeringSak, env.pale2OkTopic, legeerklaeringSak, loggingMeta)
                        }

                        Status.INVALID -> sendTilAvvistTopic(kafkaProducerLegeerklaeringSak, env.pale2AvvistTopic, legeerklaeringSak, loggingMeta)
                    }

                    if (vedlegg.isNotEmpty()) {
                        kafkaVedleggProducer.sendVedlegg(vedlegg, receivedLegeerklaering, fellesformat, loggingMeta)
                    }

                    log.info(
                        "Finished message got outcome {}, {}, processing took {}s",
                        StructuredArguments.keyValue("status", validationResult.status),
                        StructuredArguments.keyValue(
                            "ruleHits",
                            validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                        StructuredArguments.fields(loggingMeta)
                    )
                    updateRedis(jedis, ediLoggId, sha256String)
                }
            } catch (jedisException: JedisConnectionException) {
                log.error("Exception caught, redis issue while handling message {}", jedisException.message)
                throw RuntimeException("Redis-feil")
            } catch (e: Exception) {
                log.error("Exception caught while handling message, {}", e)
                throw e
            }
        } else {
            log.info("Ignorerer melding..")
        }
    }

    fun skalBehandleMelding(msgId: String): Boolean {
        return msgId in skalBehandles
    }

    private fun skrivTilSakTopic(
        kafkaProducerLegeerklaeringSak: KafkaProducer<String, LegeerklaeringSak>,
        pale2SakTopic: String,
        legeerklaringId: String,
        legeerklaeringSak: LegeerklaeringSak,
        loggingMeta: LoggingMeta
    ) {
        try {
            kafkaProducerLegeerklaeringSak.send(ProducerRecord(pale2SakTopic, legeerklaringId, legeerklaeringSak)).get()
            log.info(
                "Melding sendt til kafka topic {}, {}", pale2SakTopic,
                StructuredArguments.fields(loggingMeta)
            )
        } catch (e: Exception) {
            log.error("Kunne ikke skrive til sak-topic: {}, {}", e.message, StructuredArguments.fields(loggingMeta))
            throw e
        }
    }
}
