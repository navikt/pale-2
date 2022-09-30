package no.nav.syfo.vedlegg.google

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.vedlegg.model.BehandlerInfo
import no.nav.syfo.vedlegg.model.Vedlegg
import no.nav.syfo.vedlegg.model.VedleggMessage
import no.nav.syfo.vedlegg.model.getBehandlerInfo
import java.util.UUID

class BucketUploadService(
    private val legeerklaringBucketName: String,
    private val bucketName: String,
    private val storage: Storage
) {
    fun uploadVedlegg(
        vedlegg: List<Vedlegg>,
        legeerklaering: ReceivedLegeerklaering,
        xmleiFellesformat: XMLEIFellesformat,
        loggingMeta: LoggingMeta
    ): List<String> {
        log.info("Laster opp ${vedlegg.size} vedlegg", StructuredArguments.fields(loggingMeta))
        return vedlegg.map {
            toVedleggMessage(
                vedlegg = it,
                msgId = legeerklaering.msgId,
                personNrPasient = legeerklaering.personNrPasient,
                behandlerInfo = xmleiFellesformat.getBehandlerInfo(legeerklaering.personNrLege),
                pasientAktoerId = legeerklaering.pasientAktoerId
            )
        }.map { createVedlegg(legeerklaering.legeerklaering.id, it, loggingMeta) }
    }

    private fun createVedlegg(legeerklaeringId: String, vedleggMessage: VedleggMessage, loggingMeta: LoggingMeta): String {
        val vedleggId = "$legeerklaeringId/${UUID.randomUUID()}"
        storage.create(BlobInfo.newBuilder(bucketName, vedleggId).build(), objectMapper.writeValueAsBytes(vedleggMessage))
        log.info("Lastet opp vedlegg med id $vedleggId {}", StructuredArguments.fields(loggingMeta))
        return vedleggId
    }

    private fun toVedleggMessage(
        vedlegg: Vedlegg,
        msgId: String,
        personNrPasient: String,
        behandlerInfo: BehandlerInfo,
        pasientAktoerId: String
    ): VedleggMessage {
        return VedleggMessage(
            vedlegg = vedlegg,
            msgId = msgId,
            pasientFnr = personNrPasient,
            behandler = behandlerInfo,
            pasientAktorId = pasientAktoerId
        )
    }

    fun uploadLegeerklaering(
        legeerklaering: ReceivedLegeerklaering,
        loggingMeta: LoggingMeta
    ): String {
        log.info("Laster opp legerklæring {}", StructuredArguments.fields(loggingMeta))
        val msgId = legeerklaering.msgId
        storage.create(BlobInfo.newBuilder(legeerklaringBucketName, msgId).build(), objectMapper.writeValueAsBytes(removeIllegalCharacters(legeerklaering)))
        log.info("Lastet opp legeerklæring med id $msgId {}", StructuredArguments.fields(loggingMeta))
        return msgId
    }

    fun removeIllegalCharacters(legeerklaering: ReceivedLegeerklaering): ReceivedLegeerklaering {
        val legeerklaeringAsString = objectMapper.writeValueAsString(legeerklaering)
        if (legeerklaeringAsString.contains("\uFEFF")) {
            val vasketLegeerklaering = legeerklaeringAsString.replace("\uFEFF", "")
            log.info("Fjerner spesialtegn fra legeerklæring med id ${legeerklaering.legeerklaering.id}")
            return objectMapper.readValue<ReceivedLegeerklaering>(vasketLegeerklaering)
        }
        return legeerklaering
    }
}
