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
import org.amshove.kluent.shouldEqual
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
        legeerklaering.pasient.arbeidsgiver.adresse?.shouldBeEqualTo("Oppdiktet gate 32")
        legeerklaering.pasient.arbeidsgiver.postnummer?.shouldEqualTo(1234)
        legeerklaering.pasient.arbeidsgiver.poststed?.shouldBeEqualTo("Ukjentby")
        legeerklaering.sykdomsopplysninger.hoveddiagnose?.kode?.shouldBeEqualTo("K74")
        legeerklaering.sykdomsopplysninger.hoveddiagnose?.tekst?.shouldBeEqualTo("82-01-Le")
        legeerklaering.sykdomsopplysninger.bidiagnose.firstOrNull()?.kode?.shouldBeEqualTo("U99")
        legeerklaering.sykdomsopplysninger.bidiagnose.firstOrNull()?.tekst?.shouldBeEqualTo("Nyresvikt kronisk")
        legeerklaering.sykdomsopplysninger.arbeidsuforFra shouldEqual LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        legeerklaering.sykdomsopplysninger.sykdomshistorie.shouldBeEqualTo("vethellerikkehvasomskalhit")
        legeerklaering.sykdomsopplysninger.statusPresens.shouldBeEqualTo("vetikkehvasomskalhit")
        legeerklaering.sykdomsopplysninger.borNavKontoretVurdereOmDetErEnYrkesskade shouldEqualTo false
        legeerklaering.sykdomsopplysninger.yrkesSkadeDato shouldEqual LocalDateTime.of(2017, 10, 14, 0, 0, 0)
        legeerklaering.plan?.utredning?.tekst?.shouldBeEqualTo("Dra p� fisketur med en guide")
        legeerklaering.plan?.utredning?.dato shouldEqual LocalDateTime.of(2017, 11, 5, 0, 0, 0)
        legeerklaering.plan?.utredning?.antattVentetIUker?.shouldEqualTo(3)
        legeerklaering.plan?.behandling?.tekst?.shouldBeEqualTo("Dra p� fisketur med en guide")
        legeerklaering.plan?.behandling?.dato shouldEqual null
        legeerklaering.plan?.behandling?.antattVentetIUker?.shouldEqualTo(3)
        legeerklaering.plan?.utredningsplan?.shouldBeEqualTo("Burde dra ut p� fisketur for � slappe av")
        legeerklaering.plan?.behandlingsplan?.shouldBeEqualTo("Trenger � slappe av med litt fisking")
        legeerklaering.plan?.vurderingAvTidligerePlan?.shouldBeEqualTo("Trenger ikke ny vurdering")
        legeerklaering.plan?.narSporreOmNyeLegeopplysninger?.shouldBeEqualTo("Den gamle planen fungerte ikke")
        legeerklaering.plan?.videreBehandlingIkkeAktueltGrunn?.shouldBeEqualTo("Trenger � slappe av med litt fisking")
        legeerklaering.forslagTilTiltak.behov shouldEqualTo false
        legeerklaering.forslagTilTiltak.kjopAvHelsetjenester shouldEqualTo true
        legeerklaering.forslagTilTiltak.reisetilskudd shouldEqualTo false
        legeerklaering.forslagTilTiltak.aktivSykmelding shouldEqualTo false
        legeerklaering.forslagTilTiltak.hjelpemidlerArbeidsplassen shouldEqualTo false
        legeerklaering.forslagTilTiltak.arbeidsavklaringspenger shouldEqualTo false
        legeerklaering.forslagTilTiltak.friskmeldingTilArbeidsformidling shouldEqualTo false
        legeerklaering.forslagTilTiltak.andreTiltak?.shouldBeEqualTo("Den gamle planen fungerte ikke")
        legeerklaering.forslagTilTiltak.naermereOpplysninger.shouldBeEqualTo("")
        legeerklaering.forslagTilTiltak.tekst.shouldBeEqualTo("Trenger lettere arbeid")
        legeerklaering.funksjonsOgArbeidsevne?.vurderingFunksjonsevne?.shouldBeEqualTo("Kan ikke lengre danse")
        legeerklaering.funksjonsOgArbeidsevne.inntektsgivendeArbeid shouldEqualTo false
        legeerklaering.funksjonsOgArbeidsevne.hjemmearbeidende shouldEqualTo false
        legeerklaering.funksjonsOgArbeidsevne.student shouldEqualTo false
        legeerklaering.funksjonsOgArbeidsevne.annetArbeid?.shouldBeEqualTo("")
    }
}
