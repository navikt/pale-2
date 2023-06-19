package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.vedlegg.model.Content
import no.nav.syfo.vedlegg.model.Vedlegg
import org.w3c.dom.Element

fun removeVedleggFromFellesformat(fellesformat: XMLEIFellesformat) {
    fellesformat.get<XMLMsgHead>().document.removeIf { it.documentConnection?.v == "V" }
}

fun getVedlegg(fellesformat: XMLEIFellesformat): List<Vedlegg> {
    return fellesformat
        .get<XMLMsgHead>()
        .document
        .filter { it.documentConnection?.v == "V" }
        .map {
            val description = it.refDoc.description
            val type = it.refDoc.mimeType

            if (it.refDoc.content.any.size > 1) {
                throw RuntimeException("Unsupported content")
            }
            val contentElement = it.refDoc.content.any.first() as Element
            val contentType = contentElement.localName
            val content = contentElement.textContent
            Vedlegg(Content(contentType, content), type, description)
        }
}
