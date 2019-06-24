package no.nav.syfo.model

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
