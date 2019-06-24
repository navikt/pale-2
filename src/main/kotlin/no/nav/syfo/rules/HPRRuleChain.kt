package no.nav.syfo.rules

import no.nhn.schemas.reg.hprv2.Person as HPRPerson

enum class HPRRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<HPRPerson>) -> Boolean
) : Rule<RuleData<HPRPerson>> {
    @Description("Behandler er ikke gyldig i HPR på konsultasjonstidspunkt")
    BEHANDLER_IKKE_GYLDIG_I_HPR(
            1402,
            Status.INVALID,
            "Den som skrev sykmeldingen manglet autorisasjon.",
            "Behandler er ikke gyldig i HPR på konsultasjonstidspunkt", { (_, doctor) ->
        doctor.godkjenninger?.godkjenning != null && !doctor.godkjenninger.godkjenning.any {
            it?.autorisasjon?.isAktiv != null && it.autorisasjon.isAktiv
        }
    }),

    @Description("Behandler har ikke gyldig autorisasjon i HPR")
    BEHANDLER_MANGLER_AUTORISASJON_I_HPR(
            1403,
            Status.INVALID,
            "Den som skrev sykmeldingen manglet autorisasjon.",
            "Behandler har ikke gyldig autorisasjon i HPR", { (_, doctor) ->
        doctor.godkjenninger?.godkjenning != null && !doctor.godkjenninger.godkjenning.any {
            it?.autorisasjon?.isAktiv != null &&
            it.autorisasjon.isAktiv &&
                    it.autorisasjon?.oid != null
                    it.autorisasjon.oid == 7704 &&
                    it.autorisasjon?.verdi != null &&
                    it.autorisasjon.verdi in arrayOf("1", "17", "4", "3", "2", "14", "18")
        }
    }),

    @Description("Behandler finnes i HPR men er ikke lege, kiropraktor, manuellterapeut, fysioterapeut eller tannlege")
    BEHANDLER_IKKE_LE_KI_MT_TL_FT_I_HPR(
            1407,
            Status.INVALID,
            "Den som skrev sykmeldingen manglet autorisasjon.",
            "Behandler finnes i HPR men er ikke lege, kiropraktor, manuellterapeut, fysioterapeut eller tannlege", { (_, doctor) ->
        doctor.godkjenninger?.godkjenning != null &&
                !doctor.godkjenninger.godkjenning.any {
                    it?.helsepersonellkategori?.isAktiv != null &&
                    it.autorisasjon?.isAktiv == true &&
                    it.helsepersonellkategori.isAktiv != null &&
                    it.helsepersonellkategori.verdi != null &&
                    it.helsepersonellkategori.let { it.isAktiv && it.verdi in listOf("LE", "KI", "MT", "TL", "FT") }
        }
    }),
}
