package no.nav.syfo

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.model.toLegeerklaring
import no.nav.syfo.util.extractLegeerklaering
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.getFileAsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.time.LocalDateTime
import java.util.UUID

internal class LegeerklaringMapperTest {

    @Test
    internal fun `Tester mapping fra fellesformat til Legeerklaring format`() {
        val felleformatLe = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/fellesformat_le.xml")),
        ) as XMLEIFellesformat
        val legeerklaringxml = extractLegeerklaering(felleformatLe)

        val legeerklaering = legeerklaringxml.toLegeerklaring(
            legeerklaringId = UUID.randomUUID().toString(),
            fellesformat = felleformatLe,
            signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0),
            behandlerNavn = "Navn Navnesen",
        )

        assertEquals(false, legeerklaering.arbeidsvurderingVedSykefravaer)
        assertEquals(true, legeerklaering.arbeidsavklaringspenger)
        assertEquals(false, legeerklaering.yrkesrettetAttforing)
        assertEquals(false, legeerklaering.uforepensjon)
        assertEquals("Kari", legeerklaering.pasient.fornavn)
        assertEquals("Setesdal", legeerklaering.pasient.mellomnavn)
        assertEquals("Nordmann", legeerklaering.pasient.etternavn)
        assertEquals("12349812345", legeerklaering.pasient.fnr)
        assertEquals("NAV Sagene", legeerklaering.pasient.navKontor)
        assertEquals("Hjemmeaddresse 2", legeerklaering.pasient.adresse)
        assertEquals(1234, legeerklaering.pasient.postnummer)
        assertEquals("Dokumentforfalsker", legeerklaering.pasient.yrke)
        assertEquals("NAV IKT", legeerklaering.pasient.arbeidsgiver.navn)
        assertEquals("Oppdiktet gate 32", legeerklaering.pasient.arbeidsgiver.adresse)
        assertEquals(1234, legeerklaering.pasient.arbeidsgiver.postnummer)
        assertEquals("Ukjentby", legeerklaering.pasient.arbeidsgiver.poststed)
        assertEquals("K74", legeerklaering.sykdomsopplysninger.hoveddiagnose?.kode)
        assertEquals("82-01-Le", legeerklaering.sykdomsopplysninger.hoveddiagnose?.tekst)
        assertEquals("U99", legeerklaering.sykdomsopplysninger.bidiagnose.firstOrNull()?.kode)
        assertEquals("Nyresvikt kronisk", legeerklaering.sykdomsopplysninger.bidiagnose.firstOrNull()?.tekst)
        assertEquals(LocalDateTime.of(2017, 11, 5, 0, 0, 0), legeerklaering.sykdomsopplysninger.arbeidsuforFra)
        assertEquals("vethellerikkehvasomskalhit", legeerklaering.sykdomsopplysninger.sykdomshistorie)
        assertEquals("vetikkehvasomskalhit", legeerklaering.sykdomsopplysninger.statusPresens)
        assertEquals(false, legeerklaering.sykdomsopplysninger.borNavKontoretVurdereOmDetErEnYrkesskade)
        assertEquals(LocalDateTime.of(2017, 10, 14, 0, 0, 0), legeerklaering.sykdomsopplysninger.yrkesSkadeDato)
        assertEquals("Dra p� fisketur med en guide", legeerklaering.plan?.utredning?.tekst)
        assertEquals(LocalDateTime.of(2017, 11, 5, 0, 0, 0), legeerklaering.plan?.utredning?.dato)
        assertEquals(3, legeerklaering.plan?.utredning?.antattVentetIUker)
        assertEquals("Dra p� fisketur med en guide", legeerklaering.plan?.behandling?.tekst)
        assertEquals(LocalDateTime.of(2017, 11, 5, 0, 0, 0), legeerklaering.plan?.behandling?.dato)
        assertEquals(3, legeerklaering.plan?.behandling?.antattVentetIUker)
        assertEquals("Burde dra ut p� fisketur for � slappe av", legeerklaering.plan?.utredningsplan)
        assertEquals("Trenger � slappe av med litt fisking", legeerklaering.plan?.behandlingsplan)
        assertEquals("Trenger ikke ny vurdering", legeerklaering.plan?.vurderingAvTidligerePlan)
        assertEquals("Den gamle planen fungerte ikke", legeerklaering.plan?.narSporreOmNyeLegeopplysninger)
        assertEquals("Trenger � slappe av med litt fisking", legeerklaering.plan?.videreBehandlingIkkeAktueltGrunn)
        assertEquals(true, legeerklaering.forslagTilTiltak.behov)
        assertEquals(false, legeerklaering.forslagTilTiltak.kjopAvHelsetjenester)
        assertEquals(false, legeerklaering.forslagTilTiltak.reisetilskudd)
        assertEquals(false, legeerklaering.forslagTilTiltak.aktivSykmelding)
        assertEquals(false, legeerklaering.forslagTilTiltak.hjelpemidlerArbeidsplassen)
        assertEquals(false, legeerklaering.forslagTilTiltak.arbeidsavklaringspenger)
        assertEquals(false, legeerklaering.forslagTilTiltak.friskmeldingTilArbeidsformidling)
        assertEquals("Den gamle planen fungerte ikke", legeerklaering.forslagTilTiltak.andreTiltak)
        assertEquals("", legeerklaering.forslagTilTiltak.naermereOpplysninger)
        assertEquals("Trenger lettere arbeid", legeerklaering.forslagTilTiltak.tekst)
        assertEquals("Kan ikke lengre danse", legeerklaering.funksjonsOgArbeidsevne.vurderingFunksjonsevne)
        assertEquals(false, legeerklaering.funksjonsOgArbeidsevne.inntektsgivendeArbeid)
        assertEquals(false, legeerklaering.funksjonsOgArbeidsevne.hjemmearbeidende)
        assertEquals(false, legeerklaering.funksjonsOgArbeidsevne.student)
        assertEquals("", legeerklaering.funksjonsOgArbeidsevne.annetArbeid)
        assertEquals("Ingen dans", legeerklaering.funksjonsOgArbeidsevne.kravTilArbeid)
        assertEquals(false, legeerklaering.funksjonsOgArbeidsevne.kanGjenopptaTidligereArbeid)
        assertEquals(true, legeerklaering.funksjonsOgArbeidsevne.kanGjenopptaTidligereArbeidNa)
        assertEquals(false, legeerklaering.funksjonsOgArbeidsevne.kanGjenopptaTidligereArbeidEtterBehandling)
        assertEquals("Ikke tunge l�ft", legeerklaering.funksjonsOgArbeidsevne.kanIkkeGjenopptaNaverendeArbeid)
        assertEquals(true, legeerklaering.funksjonsOgArbeidsevne.kanTaAnnetArbeid)
        assertEquals(true, legeerklaering.funksjonsOgArbeidsevne.kanTaAnnetArbeidNa)
        assertEquals(false, legeerklaering.funksjonsOgArbeidsevne.kanTaAnnetArbeidEtterBehandling)
        assertEquals("Ingen dans", legeerklaering.funksjonsOgArbeidsevne.kanIkkeTaAnnetArbeid)
        assertEquals(false, legeerklaering.prognose.vilForbedreArbeidsevne)
        assertEquals("Ikke varig.", legeerklaering.prognose.anslattVarighetSykdom)
        assertEquals("Ikke varig.", legeerklaering.prognose.anslattVarighetFunksjonsnedsetting)
        assertEquals("Ikke varig.", legeerklaering.prognose.anslattVarighetNedsattArbeidsevne)
        assertEquals("Sikker.", legeerklaering.arsakssammenheng)
        assertEquals("Andre opplysninger", legeerklaering.andreOpplysninger)
        assertEquals(false, legeerklaering.kontakt.skalKontakteBehandlendeLege)
        assertEquals(false, legeerklaering.kontakt.skalKontakteArbeidsgiver)
        assertEquals(false, legeerklaering.kontakt.skalKontakteBasisgruppe)
        assertEquals("DPS", legeerklaering.kontakt.kontakteAnnenInstans)
        assertEquals(true, legeerklaering.kontakt.onskesKopiAvVedtak)
        assertEquals("", legeerklaering.pasientenBurdeIkkeVite)
        assertEquals(true, legeerklaering.tilbakeholdInnhold)
        assertEquals(LocalDateTime.of(2017, 11, 5, 0, 0, 0), legeerklaering.signatur.dato)
        assertEquals("LEGESEN TEST LEGE", legeerklaering.signatur.navn)
        assertEquals("Oppdiktet gate 203", legeerklaering.signatur.adresse)
        assertEquals("1234", legeerklaering.signatur.postnummer)
        assertEquals("Oslo", legeerklaering.signatur.poststed)
        assertEquals("", legeerklaering.signatur.signatur)
        assertEquals("98765432", legeerklaering.signatur.tlfNummer)
        assertEquals(LocalDateTime.of(2017, 11, 5, 0, 0, 0), legeerklaering.signaturDato)
    }

    @Test
    internal fun `Tester mapping fra fellesformat til Legeerklaring format hvis navn mangler`() {
        val felleformatLe = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/fellesformat_le_utennavn.xml")),
        ) as XMLEIFellesformat
        val legeerklaringxml = extractLegeerklaering(felleformatLe)

        val legeerklaering = legeerklaringxml.toLegeerklaring(
            legeerklaringId = UUID.randomUUID().toString(),
            fellesformat = felleformatLe,
            signaturDato = LocalDateTime.of(2017, 11, 5, 0, 0, 0),
            behandlerNavn = "Navn Navnesen",
        )
        assertEquals("Navn Navnesen", legeerklaering.signatur.navn)
    }
}
