package no.nav.syfo.vedlegg.model

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.util.get

data class Vedlegg(
    val content: Content,
    val type: String,
    val description: String
)

data class VedleggMessage(
    val vedlegg: Vedlegg,
    val behandler: BehandlerInfo,
    val pasientAktorId: String,
    val msgId: String,
    val pasientFnr: String,
    val source: String = "pale-2"
)

data class Content(val contentType: String, val content: String)

data class BehandlerInfo(val fornavn: String?, val etternavn: String?, val fnr: String?)

fun XMLEIFellesformat.getBehandlerInfo(fnr: String): BehandlerInfo {
    val fornavn = get<XMLMsgHead>().msgInfo?.sender?.organisation?.healthcareProfessional?.givenName
    val etternavn = get<XMLMsgHead>().msgInfo?.sender?.organisation?.healthcareProfessional?.familyName
    return BehandlerInfo(
        fornavn = fornavn,
        etternavn = etternavn,
        fnr = fnr
    )
}
