package no.nav.syfo.rules

import no.nav.syfo.model.Status
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person as TPSPerson

enum class PostTPSRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<TPSPerson>) -> Boolean
) : Rule<RuleData<TPSPerson>> {
    @Description("Pasient er registrert med sperrekode 6, sperret adresse, strengt fortrolig. Kode 6 overstyrer oppfølgingsregler. Melding går ikke til Arena.")
    PASIENTEN_HAR_KODE_6(
            1305,
            Status.MANUAL_PROCESSING,
            "Pasient er registrert med sperrekode 6, sperret adresse, strengt fortrolig",
            "Pasient er registrert med sperrekode 6, sperret adresse, strengt fortrolig", { (_, patient) ->
        patient.diskresjonskode?.kodeverksRef == "SPSF"
    })
}
