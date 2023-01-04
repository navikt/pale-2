package no.nav.syfo.services.duplicationcheck.model

import java.time.LocalDateTime

data class Duplicate(
    val legeerklaringId: String,
    val mottakId: String,
    val msgId: String,
    val mottattDate: LocalDateTime,
    val duplicateLegeerklaringId: String
)
