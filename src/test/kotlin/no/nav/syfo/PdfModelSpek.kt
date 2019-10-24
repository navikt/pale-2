package no.nav.syfo

import java.time.ZonedDateTime
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
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Signatur
import no.nav.syfo.model.Status
import no.nav.syfo.model.SykdomsOpplysninger
import no.nav.syfo.model.ValidationResult
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PdfModelSpek : Spek({
    describe("Generate a few pdf models") {
        it("Creates a static pdfpayload") {
            val pdfPayload = PdfPayload(
                    arbeidsvurderingVedSykefravaer = true,
                    arbeidsavklaringsPenger = true,
                    yrkesrettetAttfoering = false,
                    ufoerepensjon = true,
                    pasient = Pasient(
                        fornavn = "Test",
                        mellomnavn = "Testerino",
                        etternavn = "Testsen",
                        foedselsnummer = "0123456789",
                        navKontor = "NAV Stockholm",
                        adresse = "Oppdiktet veg 99",
                        postnummer = 9999,
                        poststed = "Stockholm",
                        yrke = "Taco spesialist",
                        arbeidsgiver = Arbeidsgiver(
                            navn = "NAV IKT",
                            adresse = "Sannergata 2",
                            postnummer = 557,
                            poststed = "Oslo"
                        )
                    ),
                    sykdomsOpplysninger = SykdomsOpplysninger(
                        hoveddiagnose = Diagnose(
                            tekst = "Fysikalsk behandling/rehabilitering",
                            kode = "-57"
                        ),
                        bidiagnose = listOf(Diagnose(
                            tekst = "Engstelig for hjertesykdom",
                            kode = "K24"
                        )),
                        arbeidsufoerFra = ZonedDateTime.now().minusDays(3),
                        sykdomsHistorie = "Tekst",
                        statusPresens = "Tekst",
                        boerNavKontoretVurdereOmDetErEnYrkesskade = true
                    ),
                    plan = Plan(
                        utredning = null,
                        behandling = Henvisning(
                            tekst = "2 timer i uken med svømming",
                            dato = ZonedDateTime.now(),
                            antattVentetIUker = 1
                        ),
                        utredningsplan = "Tekst",
                        behandlingsplan = "Tekst",
                        vurderingAvTidligerePlan = "Tekst",
                        naarSpoerreOmNyeLegeopplysninger = "Tekst",
                        videreBehandlingIkkeAktuellGrunn = "Tekst"
                    ),
                    forslagTilTiltak = ForslagTilTiltak(
                        behov = true,
                        kjoepAvHelsetjenester = true,
                        reisetilskudd = false,
                        aktivSykMelding = false,
                        hjelpemidlerArbeidsplassen = true,
                        arbeidsavklaringsPenger = true,
                        friskemeldingTilArbeidsformidling = false,
                        andreTiltak = "Trenger taco i lunsjen",
                        naermereOpplysninger = "Tacoen må bestå av ordentlige råvarer",
                        tekst = "Pasienten har store problemer med fordøying av annen mat enn Taco"

                    ),
                    funksjonsOgArbeidsevne = FunksjonsOgArbeidsevne(
                        vurderingFunksjonsevne = "Kan ikke spise annet enn Taco",
                        iIntektsgivendeArbeid = false,
                        hjemmearbeidende = false,
                        student = false,
                        annetArbeid = "Reisende taco tester",
                        kravTilArbeid = "Kun taco i kantina",
                        kanGjenopptaTidligereArbeid = true,
                        kanGjenopptaTidligereArbeidNaa = true,
                        kanGjenopptaTidligereArbeidEtterBehandling = true,
                        kanTaAnnetArbeid = true,
                        kanTaAnnetArbeidNaa = true,
                        kanTaAnnetArbeidEtterBehandling = true,
                        kanIkkeINaaverendeArbeid = "Spise annen mat enn Taco",
                        kanIkkeIAnnetArbeid = "Spise annen mat enn Taco"
                    ),
                    prognose = Prognose(
                        vilForbedreArbeidsevne = true,
                        anslaatVarighetSykdom = "1 uke",
                        anslaatVarighetFunksjonsNedsetting = "2 uker",
                        anslaatVarighetNedsattArbeidsevne = "4 uker"
                    ),
                    aarsaksSammenheng = "Funksjonsnedsettelsen har stor betydning for at arbeidsevnen er nedsatt",
                    andreOpplysninger = "Tekst",
                    kontakt = Kontakt(
                        skalKontakteBehandlendeLege = true,
                        skalKontakteArbeidsgiver = true,
                        skalKontakteBasisgruppe = false,
                        kontakteAnnenInstans = null,
                        oenskesKopiAvVedtak = true
                    ),
                    pasientenBurdeIkkeVite = null,
                    signatur = Signatur(
                        dato = ZonedDateTime.now().minusDays(1),
                        navn = "Lege Legesen",
                        adresse = "Legeveien 33",
                        postnummer = "9999",
                        poststed = "Stockholm",
                        signatur = "Lege Legesen",
                        tlfNummer = "98765432"
                    ),
                validationResult = ValidationResult(
                    status = Status.INVALID, ruleHits = listOf(
                        RuleInfo(
                            ruleName = "PASIENT_YNGRE_ENN_13",
                            messageForUser = "Pasienten er under 13 år. Sykmelding kan ikke benyttes.",
                            messageForSender = "Pasienten er under 13 år. Sykmelding kan ikke benyttes."
                        ),
                        RuleInfo(
                            ruleName = "PASIENT_ELDRE_ENN_70",
                            messageForUser = "Sykmelding kan ikke benyttes etter at du har fylt 70 år",
                            messageForSender = "Pasienten er over 70 år. Sykmelding kan ikke benyttes."
                        )
                    )
                )
            )
            println(objectMapper.writeValueAsString(pdfPayload))
        }
    }
})
