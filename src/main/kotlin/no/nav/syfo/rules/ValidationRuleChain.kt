package no.nav.syfo.rules

import no.nav.syfo.model.RuleMetadata
import no.nav.syfo.model.Status
import no.nav.syfo.validation.extractBornDate
import no.nav.syfo.validation.validatePersonAndDNumber
import no.nav.syfo.validation.validatePersonAndDNumber11Digits

enum class ValidationRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<RuleMetadata>) -> Boolean
) : Rule<RuleData<RuleMetadata>> {

    @Description("Pasienten sitt fødselsnummer eller D-nummer er ikke 11 tegn.")
    UGYLDIG_FNR_LENGDE_PASIENT(
            1002,
            Status.INVALID,
            "Pasienten sitt fødselsnummer eller D-nummer er ikke 11 tegn.",
            "Pasienten sitt fødselsnummer eller D-nummer er ikke 11 tegn.", { (_, metadata) ->
        !validatePersonAndDNumber11Digits(metadata.patientPersonNumber)
    }),

    @Description("Fødselsnummer/D-nummer kan passerer ikke modulus 11")
    UGYLDIG_FNR_PASIENT(
            1006,
            Status.INVALID,
            "Fødselsnummer/D-nummer kan passerer ikke modulus 11",
            "Pasientens fødselsnummer/D-nummer er ikke gyldig", { (_, metadata) ->
        !validatePersonAndDNumber(metadata.patientPersonNumber)
    }),

    @Description("Hele sykmeldingsperioden er før bruker har fylt 13 år. Pensjonsopptjening kan starte fra 13 år.")
    PASIENT_YNGRE_ENN_13(
            1101,
            Status.INVALID,
            "Pasienten er under 13 år. Sykmelding kan ikke benyttes.",
            "Pasienten er under 13 år. Sykmelding kan ikke benyttes.", { (_, metadata) ->
            metadata.signatureDate.toLocalDate() < extractBornDate(metadata.patientPersonNumber).plusYears(13)
    }),

    @Description("Organisasjonsnummeret som er oppgitt er ikke 9 tegn.")
    UGYLDIG_ORGNR_LENGDE(
            9999,
            Status.INVALID,
            "Den må ha riktig organisasjonsnummer.",
            "Feil format på organisasjonsnummer. Dette skal være 9 sifre..", { (_, metadata) ->
        metadata.legekontorOrgnr != null && metadata.legekontorOrgnr.length != 9
    }),
}
