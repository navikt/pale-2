package no.nav.syfo.application

import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykdomsopplysninger
import no.nav.syfo.model.ValidationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ForStoreFritekstfeltTest {
    @Test
    internal fun `For stor statusPresens gir riktige verdier`() {
        val sykdomsopplysninger = Sykdomsopplysninger(
            hoveddiagnose = null,
            bidiagnose = emptyList(),
            arbeidsuforFra = null,
            sykdomshistorie = "historie",
            statusPresens = "test1".repeat(3002),
            borNavKontoretVurdereOmDetErEnYrkesskade = false,
            yrkesSkadeDato = null
        )

        val fritekstfelt = getFritekstfelt(sykdomsopplysninger)
        val validationResults = getValidationResult(sykdomsopplysninger)
        val forkortedeSykdomsopplysninger = getForkortedeSykdomsopplysninger(sykdomsopplysninger)

        assertEquals("Punkt 2.6 Status presens", fritekstfelt)
        assertEquals(
            ValidationResult(
                Status.INVALID,
                listOf(
                    RuleInfo(
                        "FOR_MANGE_TEGN_STATUSPRESENS",
                        "Punkt 2.6 Status presens har mer enn 15 000 tegn",
                        "Punkt 2.6 Status presens har mer enn 15 000 tegn",
                        Status.INVALID
                    )
                )
            ),
            validationResults
        )
        assertEquals(
            Sykdomsopplysninger(
                hoveddiagnose = null,
                bidiagnose = emptyList(),
                arbeidsuforFra = null,
                sykdomshistorie = "historie",
                statusPresens = "FOR STOR",
                borNavKontoretVurdereOmDetErEnYrkesskade = false,
                yrkesSkadeDato = null
            ),
            forkortedeSykdomsopplysninger
        )
    }

    @Test
    internal fun `For stor sykdomshistorie gir riktige verdier`() {
        val sykdomsopplysninger = Sykdomsopplysninger(
            hoveddiagnose = null,
            bidiagnose = emptyList(),
            arbeidsuforFra = null,
            sykdomshistorie = "test1".repeat(3002),
            statusPresens = "statusPresens",
            borNavKontoretVurdereOmDetErEnYrkesskade = false,
            yrkesSkadeDato = null
        )

        val fritekstfelt = getFritekstfelt(sykdomsopplysninger)
        val validationResults = getValidationResult(sykdomsopplysninger)
        val forkortedeSykdomsopplysninger = getForkortedeSykdomsopplysninger(sykdomsopplysninger)

        assertEquals("Punkt 2.5 Sykehistorie med symptomer og behandling", fritekstfelt)
        assertEquals(
            ValidationResult(
                Status.INVALID,
                listOf(
                    RuleInfo(
                        "FOR_MANGE_TEGN_SYMPTOMER",
                        "Punkt 2.5 Sykehistorie med symptomer og behandling har mer enn 15 000 tegn",
                        "Punkt 2.5 Sykehistorie med symptomer og behandling har mer enn 15 000 tegn",
                        Status.INVALID
                    )
                )
            ),
            validationResults
        )
        assertEquals(
            Sykdomsopplysninger(
                hoveddiagnose = null,
                bidiagnose = emptyList(),
                arbeidsuforFra = null,
                sykdomshistorie = "FOR STOR",
                statusPresens = "statusPresens",
                borNavKontoretVurdereOmDetErEnYrkesskade = false,
                yrkesSkadeDato = null
            ),
            forkortedeSykdomsopplysninger
        )
    }

    @Test
    internal fun `For stor statusPresens og sykdomshistorie gir riktige verdier`() {
        val sykdomsopplysninger = Sykdomsopplysninger(
            hoveddiagnose = null,
            bidiagnose = emptyList(),
            arbeidsuforFra = null,
            sykdomshistorie = "test1".repeat(3002),
            statusPresens = "test1".repeat(3002),
            borNavKontoretVurdereOmDetErEnYrkesskade = false,
            yrkesSkadeDato = null
        )

        val fritekstfelt = getFritekstfelt(sykdomsopplysninger)
        val validationResults = getValidationResult(sykdomsopplysninger)
        val forkortedeSykdomsopplysninger = getForkortedeSykdomsopplysninger(sykdomsopplysninger)

        assertEquals("Punkt 2.6 Status presens og punkt 2.5 Sykehistorie med symptomer og behandling", fritekstfelt)
        assertEquals(
            ValidationResult(
                Status.INVALID,
                listOf(
                    RuleInfo(
                        "FOR_MANGE_TEGN_STATUSPRESENS_SYMPTOMER",
                        "Punkt 2.6 Status presens og punkt 2.5 Sykehistorie med symptomer og behandling har mer enn 15 000 tegn",
                        "Punkt 2.6 Status presens og punkt 2.5 Sykehistorie med symptomer og behandling har mer enn 15 000 tegn",
                        Status.INVALID
                    )
                )
            ),
            validationResults
        )

        assertEquals(
            Sykdomsopplysninger(
                hoveddiagnose = null,
                bidiagnose = emptyList(),
                arbeidsuforFra = null,
                sykdomshistorie = "FOR STOR",
                statusPresens = "FOR STOR",
                borNavKontoretVurdereOmDetErEnYrkesskade = false,
                yrkesSkadeDato = null
            ),
            forkortedeSykdomsopplysninger
        )
    }
}
