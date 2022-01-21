package no.nav.syfo.vedlegg.google

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
    private val bucketName: String,
    private val storage: Storage
) {
    fun lastOppVedlegg(
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
        }.map { create(legeerklaering.legeerklaering.id, it, loggingMeta) }
    }

    private fun create(legeerklaeringId: String, vedleggMessage: VedleggMessage, loggingMeta: LoggingMeta): String {
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
}
