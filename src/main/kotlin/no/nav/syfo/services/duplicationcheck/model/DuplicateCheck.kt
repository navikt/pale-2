package no.nav.syfo.services.duplicationcheck.model

import java.time.LocalDateTime

data class DuplicateCheck(
    val legeerklaringId: String,
    val sha256Legeerklaering: String,
    val mottakId: String,
    val msgId: String,
    val mottattDate: LocalDateTime,
    val orgNumber: String?,
)
