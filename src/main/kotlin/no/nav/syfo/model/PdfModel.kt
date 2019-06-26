package no.nav.syfo.model

import java.time.ZonedDateTime

data class PdfPayload(
    val arbeidsvurderingVedSykefravaer: Boolean,
    val arbeidsavklaringsPenger: Boolean,
    val yrkesrettetAttfoering: Boolean,
    val ufoerepensjon: Boolean,
    val pasient: Pasient,
    val sykdomsOpplysninger: SykdomsOpplysninger,
    val plan: Plan?,
    val forslagTilTiltak: ForslagTilTiltak,
    val funksjonsOgArbeidsevne: FunksjonsOgArbeidsevne,
    val prognose: Prognose,
    val aarsaksSammenheng: String?,
    val andreOpplysninger: String?,
    val kontakt: Kontakt,
    val pasientenBurdeIkkeVite: String?,
    val signatur: Signatur,
    val validationResult: ValidationResult
)

data class Pasient(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val foedselsnummer: String,
    val navKontor: String,
    val adresse: String,
    val postnummer: Int?,
    val poststed: String,
    val yrke: String?,
    val arbeidsgiver: Arbeidsgiver
)

data class Arbeidsgiver(
    val navn: String?,
    val adresse: String?,
    val postnummer: Int?,
    val poststed: String?
)

data class SykdomsOpplysninger(
    val hoveddiagnose: Diagnose?,
    val bidiagnose: List<Diagnose?>,
    val arbeidsufoerFra: ZonedDateTime?,
    val sykdomsHistorie: String,
    val statusPresens: String,
    val boerNavKontoretVurdereOmDetErEnYrkesskade: Boolean
)

data class Diagnose(
    val tekst: String?,
    val kode: String?
)

data class Plan(
    val utredning: Henvisning?,
    val behandling: Henvisning?,
    val utredningsplan: String?,
    val behandlingsplan: String?,
    val vurderingAvTidligerePlan: String?,
    val naarSpoerreOmNyeLegeopplysninger: String?,
    val videreBehandlingIkkeAktuellGrunn: String?
)

data class Henvisning(
    val tekst: String,
    val dato: ZonedDateTime,
    val antattVentetIUker: Int
)

data class ForslagTilTiltak(
    val behov: Boolean,
    val kjoepAvHelsetjenester: Boolean,
    val reisetilskudd: Boolean,
    val aktivSykMelding: Boolean,
    val hjelpemidlerArbeidsplassen: Boolean,
    val arbeidsavklaringsPenger: Boolean,
    val friskemeldingTilArbeidsformidling: Boolean,
    val andreTiltak: String?,
    val naermereOpplysninger: String,
    val tekst: String
)

data class FunksjonsOgArbeidsevne(
    val vurderingFunksjonsevne: String,
    val iIntektsgivendeArbeid: Boolean,
    val hjemmearbeidende: Boolean,
    val student: Boolean,
    val annetArbeid: String,
    val kravTilArbeid: String?,
    val kanGjenopptaTidligereArbeid: Boolean,
    val kanGjenopptaTidligereArbeidNaa: Boolean,
    val kanGjenopptaTidligereArbeidEtterBehandling: Boolean,
    val kanIkkeINaaverendeArbeid: String?,
    val kanTaAnnetArbeid: Boolean,
    val kanTaAnnetArbeidNaa: Boolean,
    val kanTaAnnetArbeidEtterBehandling: Boolean,
    val kanIkkeIAnnetArbeid: String?
)

data class Prognose(
    val vilForbedreArbeidsevne: Boolean,
    val anslaatVarighetSykdom: String?,
    val anslaatVarighetFunksjonsNedsetting: String?,
    val anslaatVarighetNedsattArbeidsevne: String?
)

data class Kontakt(
    val skalKontakteBehandlendeLege: Boolean,
    val skalKontakteArbeidsgiver: Boolean,
    val skalKontakteBasisgruppe: Boolean,
    val kontakteAnnenInstans: String?,
    val oenskesKopiAvVedtak: Boolean
)

data class Signatur(
    val dato: ZonedDateTime,
    val navn: String?,
    val adresse: String?,
    val postnummer: Int?,
    val poststed: String?,
    val signatur: String?,
    val tlfNummer: String?
)
