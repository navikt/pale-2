package no.nav.syfo.rules

import io.mockk.mockk
import no.nav.syfo.model.Legeerklaering
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object LegesuspensjonRuleChainSpek : Spek({

    val legeerklaring = mockk<Legeerklaering>()

    fun ruleData(
        legeerklaring: Legeerklaering,
        suspended: Boolean
    ): RuleData<Boolean> = RuleData(legeerklaring, suspended)

    describe("Testing validation rules and checking the rule outcomes") {
        it("Should check rule BEHANDLER_SUSPENDERT, should trigger rule") {
            val suspended = true

            LegesuspensjonRuleChain.BEHANDLER_SUSPENDERT(ruleData(legeerklaring, suspended)) shouldEqual true
        }

        it("Should check rule BEHANDLER_SUSPENDERT, should NOT trigger rule") {
            val suspended = false

            LegesuspensjonRuleChain.BEHANDLER_SUSPENDERT(ruleData(legeerklaring, suspended)) shouldEqual false
        }
    }
})
