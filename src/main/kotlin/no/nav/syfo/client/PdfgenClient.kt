package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.response.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import java.time.ZonedDateTime
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.legeerklaering.AktueltTiltak
import no.nav.helse.legeerklaering.DiagnoseArbeidsuforhet
import no.nav.helse.legeerklaering.Enkeltdiagnose
import no.nav.helse.legeerklaering.ForslagTiltak
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.helse.legeerklaering.PlanUtredBehandle
import no.nav.helse.legeerklaering.VurderingFunksjonsevne
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.ArbeidssituasjonType
import no.nav.syfo.KontaktType
import no.nav.syfo.LegeerklaeringType
import no.nav.syfo.contains
import no.nav.syfo.formatName
import no.nav.syfo.get
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ForslagTilTiltak
import no.nav.syfo.model.FunksjonsOgArbeidsevne
import no.nav.syfo.model.Henvisning
import no.nav.syfo.model.Kontakt
import no.nav.syfo.model.Pasient
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.Plan
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.Signatur
import no.nav.syfo.model.SykdomsOpplysninger
import no.nav.syfo.model.ValidationResult

@KtorExperimentalAPI
class PdfgenClient(
    private val url: String,
    private val httpClient: HttpClient
) {

    suspend fun createPDF(payload: PdfPayload): ByteArray = retry("pdfgen") {
        httpClient.call(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }.response.readBytes()
    }

    fun createPdfPayload(
        legeerklaring: Legeerklaring,
        plan: PlanUtredBehandle,
        forslagTiltak: ForslagTiltak,
        typeLegeerklaering: Int,
        funksjonsevne: VurderingFunksjonsevne,
        prognose: no.nav.helse.legeerklaering.Prognose,
        healthcareProfessional: XMLHealthcareProfessional?,
        fellesformat: XMLEIFellesformat,
        validationResult: ValidationResult
    ): PdfPayload = PdfPayload(
        arbeidsvurderingVedSykefravaer = typeLegeerklaering == LegeerklaeringType.Arbeidsevnevurdering.type,
        arbeidsavklaringsPenger = typeLegeerklaering == LegeerklaeringType.Arbeidsavklaringspenger.type,
        yrkesrettetAttfoering = typeLegeerklaering == LegeerklaeringType.YrkesrettetAttfoering.type,
        ufoerepensjon = typeLegeerklaering == LegeerklaeringType.Ufoerepensjon.type,
        pasient = legeerklaeringToPasient(legeerklaring),
        sykdomsOpplysninger = mapLegeerklaeringToSykdomDiagnose(legeerklaring.diagnoseArbeidsuforhet),
        plan = Plan(
            utredning = plan?.henvistUtredning?.let {
                Henvisning(
                    tekst = it.spesifikasjon,
                    dato = it.henvistDato.toGregorianCalendar().toZonedDateTime(),
                    antattVentetIUker = it.antattVentetid.toInt()
                )
            },
            behandling = plan?.henvistBehandling?.let {
                Henvisning(
                    tekst = it.spesifikasjon,
                    dato = it.henvistDato.toGregorianCalendar().toZonedDateTime(),
                    antattVentetIUker = it.antattVentetid.toInt()
                )
            },
            utredningsplan = plan?.utredningsPlan,
            behandlingsplan = plan?.behandlingsPlan,
            vurderingAvTidligerePlan = plan?.nyVurdering,
            naarSpoerreOmNyeLegeopplysninger = plan?.nyeLegeopplysninger,
            videreBehandlingIkkeAktuellGrunn = plan?.ikkeVidereBehandling
        ),
        forslagTilTiltak = ForslagTilTiltak(
            behov = forslagTiltak.aktueltTiltak.isEmpty(),
            kjoepAvHelsetjenester = TypeTiltak.KjoepHelsetjenester in forslagTiltak.aktueltTiltak,
            reisetilskudd = TypeTiltak.Reisetilskudd in forslagTiltak.aktueltTiltak,
            aktivSykMelding = TypeTiltak.AktivSykemelding in forslagTiltak.aktueltTiltak,
            hjelpemidlerArbeidsplassen = TypeTiltak.HjelpemidlerArbeidsplass in forslagTiltak.aktueltTiltak,
            arbeidsavklaringsPenger = TypeTiltak.Arbeidsavklaringspenger in forslagTiltak.aktueltTiltak,
            friskemeldingTilArbeidsformidling = TypeTiltak.FriskemeldingTilArbeidsformidling in forslagTiltak.aktueltTiltak,
            andreTiltak = forslagTiltak.aktueltTiltak.find { it.typeTiltak == TypeTiltak.AndreTiltak }?.hvilkeAndreTiltak,
            naermereOpplysninger = forslagTiltak.opplysninger,
            tekst = forslagTiltak.begrensningerTiltak ?: forslagTiltak.begrunnelseIkkeTiltak
        ),
        funksjonsOgArbeidsevne = FunksjonsOgArbeidsevne(
            vurderingFunksjonsevne = funksjonsevne.funksjonsevne,
            iIntektsgivendeArbeid = ArbeidssituasjonType.InntektsgivendeArbeid in funksjonsevne.arbeidssituasjon,
            hjemmearbeidende = ArbeidssituasjonType.Hjemmearbeidende in funksjonsevne.arbeidssituasjon,
            student = ArbeidssituasjonType.Student in funksjonsevne.arbeidssituasjon,
            annetArbeid = funksjonsevne.arbeidssituasjon?.find {
                it.arbeidssituasjon?.let {
                    it.toInt() == ArbeidssituasjonType.Annet?.type
                } ?: false
            }?.annenArbeidssituasjon ?: "",
            kravTilArbeid = funksjonsevne?.kravArbeid,
            kanGjenopptaTidligereArbeid = funksjonsevne.vurderingArbeidsevne?.gjenopptaArbeid?.toInt() == 1,
            kanGjenopptaTidligereArbeidNaa = funksjonsevne.vurderingArbeidsevne?.narGjenopptaArbeid?.toInt() == 1,
            kanGjenopptaTidligereArbeidEtterBehandling = funksjonsevne.vurderingArbeidsevne?.narGjenopptaArbeid?.toInt() == 2,
            kanTaAnnetArbeid = funksjonsevne.vurderingArbeidsevne?.taAnnetArbeid?.toInt() == 1,
            kanTaAnnetArbeidNaa = funksjonsevne.vurderingArbeidsevne?.narTaAnnetArbeid?.toInt() == 1,
            kanTaAnnetArbeidEtterBehandling = funksjonsevne.vurderingArbeidsevne?.narTaAnnetArbeid?.toInt() == 2,
            kanIkkeINaaverendeArbeid = funksjonsevne.vurderingArbeidsevne?.ikkeGjore,
            kanIkkeIAnnetArbeid = funksjonsevne.vurderingArbeidsevne?.hensynAnnetYrke
        ),
        prognose = Prognose(
            vilForbedreArbeidsevne = prognose.bedreArbeidsevne?.toInt() == 1,
            anslaatVarighetSykdom = prognose.antattVarighet,
            anslaatVarighetFunksjonsNedsetting = prognose.varighetFunksjonsnedsettelse,
            anslaatVarighetNedsattArbeidsevne = prognose.varighetNedsattArbeidsevne
        ),
        aarsaksSammenheng = legeerklaring.arsakssammenhengLegeerklaring,
        andreOpplysninger = legeerklaring.andreOpplysninger?.opplysning,
        kontakt = Kontakt(
            skalKontakteBehandlendeLege = KontaktType.BehandlendeLege in legeerklaring.kontakt,
            skalKontakteArbeidsgiver = KontaktType.Arbeidsgiver in legeerklaring.kontakt,
            skalKontakteBasisgruppe = KontaktType.Basisgruppe in legeerklaring.kontakt,
            kontakteAnnenInstans = legeerklaring.kontakt.find { it.kontakt?.toInt() == KontaktType.AnnenInstans.type }?.annenInstans,
            oenskesKopiAvVedtak = legeerklaring.andreOpplysninger?.onskesKopi?.let { it.toInt() == 1 } ?: false
        ),
        pasientenBurdeIkkeVite = legeerklaring.forbeholdLegeerklaring.borTilbakeholdes,
        signatur = Signatur(
            dato = ZonedDateTime.now(),
            navn = healthcareProfessional?.formatName() ?: "",
            adresse = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.address?.streetAdr,
            postnummer = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.address?.postalCode,
            poststed = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.address?.city,
            signatur = "",
            tlfNummer = healthcareProfessional?.teleCom?.firstOrNull()?.teleAddress?.v ?: ""
        ),
        validationResult = validationResult
    )

    fun legeerklaeringToPasient(legeerklaering: Legeerklaring): Pasient {
        val patient = legeerklaering.pasientopplysninger.pasient
        val postalAddress = patient.arbeidsforhold?.virksomhet?.virksomhetsAdr?.postalAddress?.firstOrNull()
        return Pasient(
            fornavn = patient.navn.fornavn,
            mellomnavn = patient.navn.mellomnavn,
            etternavn = patient.navn.etternavn,
            foedselsnummer = patient.fodselsnummer,
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

    fun mapLegeerklaeringToSykdomDiagnose(diagnose: DiagnoseArbeidsuforhet): SykdomsOpplysninger = SykdomsOpplysninger(
        hoveddiagnose = mapEnkeltDiagnoseToDiagnose(diagnose.diagnoseKodesystem.enkeltdiagnose.first()),
        bidiagnose = diagnose.diagnoseKodesystem.enkeltdiagnose.drop(1).map { mapEnkeltDiagnoseToDiagnose(it) },
        arbeidsufoerFra = diagnose.arbeidsuforFra?.toGregorianCalendar()?.toZonedDateTime(),
        sykdomsHistorie = diagnose.symptomerBehandling,
        statusPresens = diagnose.statusPresens,
        boerNavKontoretVurdereOmDetErEnYrkesskade = diagnose.vurderingYrkesskade?.borVurderes?.toInt() == 1
    )

    fun mapEnkeltDiagnoseToDiagnose(enkeltdiagnose: Enkeltdiagnose?): Diagnose =
        Diagnose(tekst = enkeltdiagnose?.diagnose, kode = enkeltdiagnose?.kodeverdi)

    enum class TypeTiltak(val typeTiltak: Int) {
        KjoepHelsetjenester(1),
        Reisetilskudd(2),
        AktivSykemelding(3),
        HjelpemidlerArbeidsplass(4),
        Arbeidsavklaringspenger(5),
        FriskemeldingTilArbeidsformidling(6),
        AndreTiltak(7)
    }

    operator fun Iterable<AktueltTiltak>.contains(typeTiltak: TypeTiltak) =
        any { it.typeTiltak.toInt() == typeTiltak.typeTiltak }
}
