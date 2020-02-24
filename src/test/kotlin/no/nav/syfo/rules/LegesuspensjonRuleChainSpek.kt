package no.nav.syfo.rules

import no.nav.helse.legeerklaering.Legeerklaring
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object LegesuspensjonRuleChainSpek : Spek({

    fun ruleData(
        legeerklaring: Legeerklaring,
        suspended: Boolean
    ): RuleData<Boolean> = RuleData(legeerklaring, suspended)

    describe("Testing validation rules and checking the rule outcomes") {
        it("Should check rule BEHANDLER_SUSPENDERT, should trigger rule") {
            val legeerklaring = Legeerklaring()
            val suspended = true

            LegesuspensjonRuleChain.BEHANDLER_SUSPENDERT(ruleData(legeerklaring, suspended)) shouldEqual true
        }

        it("Should check rule BEHANDLER_SUSPENDERT, should NOT trigger rule") {
            val legeerklaring = Legeerklaring()
            val suspended = false

            LegesuspensjonRuleChain.BEHANDLER_SUSPENDERT(ruleData(legeerklaring, suspended)) shouldEqual false
        }
    }
})
