package no.nav.syfo.model

import java.time.LocalDateTime

data class RuleMetadata(
    val signatureDate: LocalDateTime,
    val receivedDate: LocalDateTime,
    val patientPersonNumber: String,
    val legekontorOrgnr: String?,
    val tssid: String?,
    val avsenderfnr: String
)
