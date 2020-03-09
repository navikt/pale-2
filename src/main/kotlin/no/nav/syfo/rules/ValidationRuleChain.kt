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

    UGYLDIG_FNR_LENGDE_PASIENT(
            1002,
            Status.INVALID,
            "Pasienten sitt fødselsnummer eller D-nummer er ikke 11 tegn.",
            "Pasienten sitt fødselsnummer eller D-nummer er ikke 11 tegn.", { (_, metadata) ->
        !validatePersonAndDNumber11Digits(metadata.patientPersonNumber)
    }),

    UGYLDIG_FNR_PASIENT(
            1006,
            Status.INVALID,
            "Fødselsnummer/D-nummer kan passerer ikke modulus 11",
            "Pasientens fødselsnummer/D-nummer er ikke gyldig", { (_, metadata) ->
        !validatePersonAndDNumber(metadata.patientPersonNumber)
    }),

    PASIENT_YNGRE_ENN_13(
            1101,
            Status.INVALID,
            "Pasienten er under 13 år. Sykmelding kan ikke benyttes.",
            "Pasienten er under 13 år. Sykmelding kan ikke benyttes.", { (_, metadata) ->
            metadata.signatureDate.toLocalDate() < extractBornDate(metadata.patientPersonNumber).plusYears(13)
    }),

    UGYLDIG_ORGNR_LENGDE(
            9999,
            Status.INVALID,
            "Den må ha riktig organisasjonsnummer.",
            "Feil format på organisasjonsnummer. Dette skal være 9 sifre..", { (_, metadata) ->
        metadata.legekontorOrgnr != null && metadata.legekontorOrgnr.length != 9
    }),

    UGYLDIG_FNR_AVSENDER(
        1006,
        Status.INVALID,
        "Fødselsnummer for den som sendte legeerklæringen, er ikke gyldig",
        "Avsenders fødselsnummer/D-nummer er ikke gyldig", { (_, metadata) ->
            !validatePersonAndDNumber(metadata.avsenderfnr)
        }),

    AVSENDER_FNR_ER_SAMME_SOM_PASIENT_FNR(
        9999,
        Status.INVALID,
        "Den som signert legeerklæringen er også pasient.",
        "Avsender fnr er det samme som pasient fnr", { (_, metadata) ->
            metadata.avsenderfnr.equals(metadata.patientPersonNumber)
        }),
}
