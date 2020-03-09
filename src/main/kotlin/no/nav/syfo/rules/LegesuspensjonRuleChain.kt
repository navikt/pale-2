package no.nav.syfo.rules

import no.nav.syfo.model.Status

enum class LegesuspensjonRuleChain(
    override val ruleId: Int?,
    override val status: Status,
    override val messageForUser: String,
    override val messageForSender: String,
    override val predicate: (RuleData<Boolean>) -> Boolean
) : Rule<RuleData<Boolean>> {
    BEHANDLER_SUSPENDERT(
            1414,
            Status.INVALID,
            "Den som sendte legeerklæringen har mistet retten til å skrive legeerklæringer.",
            "Behandler er suspendert av NAV på konsultasjonstidspunkt", { (_, suspended) ->
        suspended
    }),
}
