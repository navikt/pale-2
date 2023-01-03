package no.nav.syfo.services.duplicationcheck.model

import java.time.LocalDateTime

data class DuplikatsjekkModel(
    val sha256Legeerklaering: String,
    val mottakId: String,
    val msgId: String,
    val mottattDate: LocalDateTime
)
