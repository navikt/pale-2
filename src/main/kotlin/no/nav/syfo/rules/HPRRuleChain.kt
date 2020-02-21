package no.nav.syfo.rules

import no.nav.syfo.client.Behandler
import no.nav.syfo.model.Status

enum class HPRRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<Behandler>) -> Boolean
) : Rule<RuleData<Behandler>> {

    @Description("Behandler er ikke gyldig i HPR på konsultasjonstidspunkt")
    BEHANDLER_IKKE_GYLDIG_I_HPR(
        1402,
        Status.INVALID,
        "Den som skrev sykmeldingen manglet autorisasjon.",
        "Sykmeldingen kan ikke rettes, det må skrives en ny. Pasienten har fått beskjed om å vente på ny sykmelding fra deg. Grunnet følgende:" +
                "Behandler er ikke gyldig i HPR på konsultasjonstidspunkt", { (_, behandler) ->
            !behandler.godkjenninger.any {
                it.autorisasjon?.aktiv != null && it.autorisasjon.aktiv
            }
        }),

    @Description("Behandler har ikke gyldig autorisasjon i HPR")
    BEHANDLER_MANGLER_AUTORISASJON_I_HPR(
        1403,
        Status.INVALID,
        "Den som skrev sykmeldingen manglet autorisasjon.",
        "Behandler har ikke til gyldig autorisasjon i HPR", { (_, behandler) ->
            !behandler.godkjenninger.any {
                it.autorisasjon?.aktiv != null &&
                        it.autorisasjon.aktiv &&
                        it.autorisasjon.oid == 7704 &&
                        it.autorisasjon.verdi != null &&
                        it.autorisasjon.verdi in arrayOf("1", "17", "4", "2", "14", "18")
            }
        }),

    @Description("Behandler finnes i HPR men er ikke lege, kiropraktor, manuellterapeut eller tannlege")
    BEHANDLER_IKKE_LE_KI_MT_TL_FT_I_HPR(
        1407,
        Status.INVALID,
        "Den som skrev sykmeldingen manglet autorisasjon.",
        "Sykmeldingen kan ikke rettes, det må skrives en ny. Pasienten har fått beskjed om å vente på ny sykmelding fra deg. Grunnet følgende:" +
                "Behandler finnes i HPR men er ikke lege, kiropraktor, fysioterapeut, manuellterapeut eller tannlege", { (_, behandler) ->
            !behandler.godkjenninger.any {
                it.helsepersonellkategori?.aktiv != null &&
                        it.autorisasjon?.aktiv == true && it.helsepersonellkategori.verdi != null &&
                        harAktivHelsepersonellAutorisasjonsSom(behandler, listOf(
                            no.nav.syfo.model.HelsepersonellKategori.LEGE.verdi,
                            no.nav.syfo.model.HelsepersonellKategori.KIROPRAKTOR.verdi,
                            no.nav.syfo.model.HelsepersonellKategori.MANUELLTERAPEUT.verdi,
                            no.nav.syfo.model.HelsepersonellKategori.TANNLEGE.verdi,
                            no.nav.syfo.model.HelsepersonellKategori.FYSIOTERAPAEUT.verdi))
            }
        }),
}

fun harAktivHelsepersonellAutorisasjonsSom(behandler: Behandler, helsepersonerVerdi: List<String>): Boolean =
    behandler.godkjenninger.any { godkjenning ->
        godkjenning.helsepersonellkategori?.aktiv != null &&
                godkjenning.autorisasjon?.aktiv == true && godkjenning.helsepersonellkategori.verdi != null &&
                godkjenning.helsepersonellkategori.let {
                    it.aktiv && it.verdi in helsepersonerVerdi }
    }
