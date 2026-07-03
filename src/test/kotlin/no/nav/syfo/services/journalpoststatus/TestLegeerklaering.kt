package no.nav.syfo.services.journalpoststatus

import java.io.StringReader
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.toLegeerklaring
import no.nav.syfo.util.extractLegeerklaering
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.getFileAsString

fun testLegeerklaering(id: String = UUID.randomUUID().toString()): Legeerklaering {
    val fellesformat =
        fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/fellesformat_le.xml")),
        ) as XMLEIFellesformat
    return extractLegeerklaering(fellesformat)
        .toLegeerklaring(
            legeerklaringId = id,
            fellesformat = fellesformat,
            signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0),
            behandlerNavn = "Navn Navnesen",
        )
}
