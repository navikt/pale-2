package no.nav.syfo

import java.io.StringReader
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.model.toLegeerklaring
import no.nav.syfo.util.extractLegeerklaering
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldEqualTo
import org.junit.Test

internal class LegeerklaringMapperTest {

    @Test
    internal fun `Tester mapping fra fellesformat til Legeerklaring format`() {
        val felleformatLe = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/fellesformat_le.xml"))
        ) as XMLEIFellesformat
        val legeerklaringxml = extractLegeerklaering(felleformatLe)

        val legeerklaering = legeerklaringxml.toLegeerklaring(
            legeerklaringId = UUID.randomUUID().toString(),
            fellesformat = felleformatLe,
            signaturDato = LocalDateTime.now()
        )

        legeerklaering.arbeidsvurderingVedSykefravaer shouldEqualTo false
        legeerklaering.arbeidsavklaringspenger shouldEqualTo true
        legeerklaering.yrkesrettetAttforing shouldEqualTo false
        legeerklaering.uforepensjon shouldEqualTo false
        legeerklaering.pasient.fornavn shouldBeEqualTo "Daniel"
        legeerklaering.pasient.mellomnavn?.shouldBeEqualTo("Yndesdal")
        legeerklaering.pasient.etternavn shouldBeEqualTo "Bergheim"
        legeerklaering.pasient.fnr shouldBeEqualTo "12349812345"
        legeerklaering.pasient.navKontor?.shouldBeEqualTo("NAV Sagene")
        legeerklaering.pasient.adresse?.shouldBeEqualTo("Hjemmeaddresse 2")
        legeerklaering.pasient.postnummer?.shouldEqualTo(1234)
        legeerklaering.pasient.yrke?.shouldBeEqualTo("Dokumentforfalsker")
        legeerklaering.pasient.arbeidsgiver.navn?.shouldBeEqualTo("NAV IKT")
    }
}
