package no.nav.syfo.rules

import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.syfo.metrics.RULE_HIT_COUNTER

data class RuleData<T>(val legeerklaring: Legeerklaring, val metadata: T)

interface Rule<in T> {
    val name: String
    val ruleId: Int?
    val messageForSender: String?
    val messageForUser: String?
    val status: Status
    val predicate: (T) -> Boolean
    operator fun invoke(input: T) = predicate(input)
}

inline fun <reified T, reified R : Rule<RuleData<T>>> List<R>.executeFlow(legeerklaring: Legeerklaring, value: T): List<Rule<Any>> =
    filter { it.predicate(RuleData(legeerklaring, value)) }
        .map { it as Rule<Any> }
        .onEach { RULE_HIT_COUNTER.labels(it.name).inc() }

inline fun <reified T, reified R : Rule<RuleData<T>>> Array<R>.executeFlow(legeerklaring: Legeerklaring, value: T): List<Rule<Any>> = toList().executeFlow(legeerklaring, value)

@Retention(AnnotationRetention.RUNTIME)
annotation class Description(val description: String)

data class ValidationResult(
    val status: Status,
    val ruleHits: List<RuleInfo>
)

data class RuleInfo(
    val ruleName: String,
    val messageForSender: String,
    val messageForUser: String
)

enum class Status {
    OK,
    MANUAL_PROCESSING,
    INVALID
}