package no.nav.syfo.rules

import no.nav.syfo.metrics.RULE_HIT_COUNTER
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.Status

data class RuleData<T>(val legeerklaring: Legeerklaering, val metadata: T)

interface Rule<in T> {
    val name: String
    val ruleId: Int?
    val messageForSender: String?
    val messageForUser: String?
    val status: Status
    val predicate: (T) -> Boolean
    operator fun invoke(input: T) = predicate(input)
}

inline fun <reified T, reified R : Rule<RuleData<T>>> List<R>.executeFlow(legeerklaring: Legeerklaering, value: T): List<Rule<Any>> =
    filter { it.predicate(RuleData(legeerklaring, value)) }
        .map { it as Rule<Any> }
        .onEach { RULE_HIT_COUNTER.labels(it.name).inc() }

inline fun <reified T, reified R : Rule<RuleData<T>>> Array<R>.executeFlow(legeerklaring: Legeerklaering, value: T): List<Rule<Any>> = toList().executeFlow(legeerklaring, value)
