package no.nav.syfo.model

import java.time.LocalDateTime
import java.time.ZonedDateTime
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.legeerklaering.AktueltTiltak
import no.nav.helse.legeerklaering.Arbeidssituasjon
import no.nav.helse.legeerklaering.DiagnoseArbeidsuforhet
import no.nav.helse.legeerklaering.Enkeltdiagnose
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.helse.legeerklaering.Pasientopplysninger
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.formatName
import no.nav.syfo.get

fun Legeerklaring.toLegeerklaring(
    legeerklaringId: String,
    fellesformat: XMLEIFellesformat,
    signaturDato: LocalDateTime
) = Legeerklaering(
        id = legeerklaringId,
        arbeidsvurderingVedSykefravaer = legeerklaringGjelder[0].typeLegeerklaring.toInt() == LegeerklaeringType.Arbeidsevnevurdering.type,
        arbeidsavklaringspenger = legeerklaringGjelder[0].typeLegeerklaring.toInt() == LegeerklaeringType.Arbeidsavklaringspenger.type,
        yrkesrettetAttforing = legeerklaringGjelder[0].typeLegeerklaring.toInt() == LegeerklaeringType.YrkesrettetAttfoering.type,
        uforepensjon = legeerklaringGjelder[0].typeLegeerklaring.toInt() == LegeerklaeringType.Ufoerepensjon.type,
        pasient = pasientopplysninger.toPasient(),
        sykdomsopplysninger = mapLegeerklaeringToSykdomDiagnose(diagnoseArbeidsuforhet),
        plan = Plan(
                utredning = planUtredBehandle?.henvistUtredning?.let {
                        Henvisning(
                                tekst = it.spesifikasjon,
                                dato = it.henvistDato.toGregorianCalendar().toZonedDateTime(),
                                antattVentetIUker = it.antattVentetid.toInt()
                        )
                },
                behandling = planUtredBehandle?.henvistBehandling?.let {
                        Henvisning(
                                tekst = it.spesifikasjon,
                                dato = it.henvistDato.toGregorianCalendar().toZonedDateTime(),
                                antattVentetIUker = it.antattVentetid.toInt()
                        )
                },
                utredningsplan = planUtredBehandle?.utredningsPlan,
                behandlingsplan = planUtredBehandle?.behandlingsPlan,
                vurderingAvTidligerePlan = planUtredBehandle?.nyVurdering,
                narSporreOmNyeLegeopplysninger = planUtredBehandle?.nyeLegeopplysninger,
                videreBehandlingIkkeAktueltGrunn = planUtredBehandle?.ikkeVidereBehandling
        ),
        forslagTilTiltak = ForslagTilTiltak(
                behov = forslagTiltak.aktueltTiltak.isEmpty(),
                kjopAvHelsetjenester = TypeTiltak.KjoepHelsetjenester in forslagTiltak.aktueltTiltak,
                reisetilskudd = TypeTiltak.Reisetilskudd in forslagTiltak.aktueltTiltak,
                aktivSykmelding = TypeTiltak.AktivSykemelding in forslagTiltak.aktueltTiltak,
                hjelpemidlerArbeidsplassen = TypeTiltak.HjelpemidlerArbeidsplass in forslagTiltak.aktueltTiltak,
                arbeidsavklaringspenger = TypeTiltak.Arbeidsavklaringspenger in forslagTiltak.aktueltTiltak,
                friskmeldingTilArbeidsformidling = TypeTiltak.FriskemeldingTilArbeidsformidling in forslagTiltak.aktueltTiltak,
                andreTiltak = forslagTiltak.aktueltTiltak.find { it.typeTiltak == TypeTiltak.AndreTiltak }?.hvilkeAndreTiltak,
                naermereOpplysninger = forslagTiltak.opplysninger,
                tekst = forslagTiltak.begrensningerTiltak ?: forslagTiltak.begrunnelseIkkeTiltak
        ),
        funksjonsOgArbeidsevne = FunksjonsOgArbeidsevne(
                vurderingFunksjonsevne = vurderingFunksjonsevne.funksjonsevne,
                inntektsgivendeArbeid = ArbeidssituasjonType.InntektsgivendeArbeid in vurderingFunksjonsevne.arbeidssituasjon,
                hjemmearbeidende = ArbeidssituasjonType.Hjemmearbeidende in vurderingFunksjonsevne.arbeidssituasjon,
                student = ArbeidssituasjonType.Student in vurderingFunksjonsevne.arbeidssituasjon,
                annetArbeid = vurderingFunksjonsevne.arbeidssituasjon?.find {
                        it.arbeidssituasjon?.let {
                                it.toInt() == ArbeidssituasjonType.Annet?.type
                        } ?: false
                }?.annenArbeidssituasjon ?: "",
                kravTilArbeid = vurderingFunksjonsevne?.kravArbeid,
                kanGjenopptaTidligereArbeid = vurderingFunksjonsevne.vurderingArbeidsevne?.gjenopptaArbeid?.toInt() == 1,
                kanGjenopptaTidligereArbeidNa = vurderingFunksjonsevne.vurderingArbeidsevne?.narGjenopptaArbeid?.toInt() == 1,
                kanGjenopptaTidligereArbeidEtterBehandling = vurderingFunksjonsevne.vurderingArbeidsevne?.narGjenopptaArbeid?.toInt() == 2,
                kanTaAnnetArbeid = vurderingFunksjonsevne.vurderingArbeidsevne?.taAnnetArbeid?.toInt() == 1,
                kanTaAnnetArbeidNa = vurderingFunksjonsevne.vurderingArbeidsevne?.narTaAnnetArbeid?.toInt() == 1,
                kanTaAnnetArbeidEtterBehandling = vurderingFunksjonsevne.vurderingArbeidsevne?.narTaAnnetArbeid?.toInt() == 2,
                kanIkkeGjenopptaNaverendeArbeid = vurderingFunksjonsevne.vurderingArbeidsevne?.ikkeGjore,
                kanIkkeTaAnnetArbeid = vurderingFunksjonsevne.vurderingArbeidsevne?.hensynAnnetYrke
        ),
        prognose = Prognose(
                vilForbedreArbeidsevne = prognose.bedreArbeidsevne?.toInt() == 1,
                anslattVarighetSykdom = prognose.antattVarighet,
                anslattVarighetFunksjonsnedsetting = prognose.varighetFunksjonsnedsettelse,
                anslattVarighetNedsattArbeidsevne = prognose.varighetNedsattArbeidsevne
        ),
        arsakssammenheng = arsakssammenhengLegeerklaring,
        andreOpplysninger = andreOpplysninger?.opplysning,
        kontakt = Kontakt(
                skalKontakteBehandlendeLege = KontaktType.BehandlendeLege in kontakt,
                skalKontakteArbeidsgiver = KontaktType.Arbeidsgiver in kontakt,
                skalKontakteBasisgruppe = KontaktType.Basisgruppe in kontakt,
                kontakteAnnenInstans = kontakt.find { it.kontakt?.toInt() == KontaktType.AnnenInstans.type }?.annenInstans,
                onskesKopiAvVedtak = andreOpplysninger?.onskesKopi?.let { it.toInt() == 1 } ?: false
        ),
        tilbakeholdInnhold = when (forbeholdLegeerklaring?.tilbakeholdInnhold?.toInt()) {
                2 -> true
                else -> false
        },
        pasientenBurdeIkkeVite = forbeholdLegeerklaring.borTilbakeholdes,
        signatur = Signatur(
                dato = ZonedDateTime.now(),
                navn = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.healthcareProfessional?.formatName() ?: "",
                adresse = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.address?.streetAdr,
                postnummer = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.address?.postalCode,
                poststed = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.address?.city,
                signatur = "",
                tlfNummer = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.healthcareProfessional?.teleCom?.firstOrNull()?.teleAddress?.v ?: ""
        ),
        signaturDato = signaturDato
)

fun Pasientopplysninger.toPasient(): Pasient {
        val patient = pasient
        val postalAddress = patient.arbeidsforhold?.virksomhet?.virksomhetsAdr?.postalAddress?.firstOrNull()
        return Pasient(
                fornavn = patient.navn.fornavn,
                mellomnavn = patient.navn.mellomnavn,
                etternavn = patient.navn.etternavn,
                fnr = patient.fodselsnummer,
                navKontor = patient.trygdekontor,
                adresse = patient.personAdr[0].postalAddress[0].streetAddress,
                postnummer = patient.personAdr[0].postalAddress[0].postalCode.let {
                        if (it == null || it.isEmpty()) null else it.toInt()
                },
                poststed = patient.personAdr[0].postalAddress[0].city,
                yrke = patient.arbeidsforhold?.yrkesbetegnelse,
                arbeidsgiver = Arbeidsgiver(
                        navn = patient.arbeidsforhold?.virksomhet?.virksomhetsBetegnelse,
                        adresse = postalAddress?.streetAddress,
                        postnummer = postalAddress?.postalCode.let {
                                if (it == null || it.isEmpty()) null else it.toInt()
                        },
                        poststed = postalAddress?.city
                )
        )
}

fun mapLegeerklaeringToSykdomDiagnose(diagnose: DiagnoseArbeidsuforhet): Sykdomsopplysninger = Sykdomsopplysninger(
        hoveddiagnose = mapEnkeltDiagnoseToDiagnose(diagnose.diagnoseKodesystem.enkeltdiagnose.first()),
        bidiagnose = diagnose.diagnoseKodesystem.enkeltdiagnose.drop(1).map { mapEnkeltDiagnoseToDiagnose(it) },
        arbeidsuforFra = diagnose.arbeidsuforFra?.toGregorianCalendar()?.toZonedDateTime(),
        sykdomshistorie = diagnose.symptomerBehandling,
        statusPresens = diagnose.statusPresens,
        borNavKontoretVurdereOmDetErEnYrkesskade = diagnose.vurderingYrkesskade?.borVurderes?.toInt() == 1
)

fun mapEnkeltDiagnoseToDiagnose(enkeltdiagnose: Enkeltdiagnose?): Diagnose =
        Diagnose(tekst = enkeltdiagnose?.diagnose, kode = enkeltdiagnose?.kodeverdi)

enum class LegeerklaeringType(val type: Int) {
        Arbeidsevnevurdering(1),
        Arbeidsavklaringspenger(2),
        YrkesrettetAttfoering(3),
        Ufoerepensjon(4)
}

enum class TypeTiltak(val typeTiltak: Int) {
        KjoepHelsetjenester(1),
        Reisetilskudd(2),
        AktivSykemelding(3),
        HjelpemidlerArbeidsplass(4),
        Arbeidsavklaringspenger(5),
        FriskemeldingTilArbeidsformidling(6),
        AndreTiltak(7)
}

enum class ArbeidssituasjonType(val type: Int) {
        InntektsgivendeArbeid(1),
        Hjemmearbeidende(2),
        Student(3),
        Annet(4)
}

enum class KontaktType(val type: Int) {
        BehandlendeLege(1),
        Arbeidsgiver(2),
        Basisgruppe(4),
        AnnenInstans(5)
}

operator fun Iterable<Arbeidssituasjon>.contains(arbeidssituasjonType: ArbeidssituasjonType): Boolean =
        any {
                it.arbeidssituasjon?.let {
                        it.toInt() == arbeidssituasjonType.type
                } ?: false
        }

operator fun Iterable<AktueltTiltak>.contains(typeTiltak: TypeTiltak) =
        any { it.typeTiltak.toInt() == typeTiltak.typeTiltak }

operator fun Iterable<no.nav.helse.legeerklaering.Kontakt>.contains(kontaktType: KontaktType): Boolean =
        any { it.kontakt.toInt() == kontaktType.type }
