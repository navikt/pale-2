package no.nav.syfo.services.journalpoststatus.model

import java.time.LocalDateTime
import no.nav.syfo.model.Legeerklaering

enum class ProcessingStatusType {
    MOTTATT,
    APPREC_SENDT,
    SENDT_TIL_TOPIC,
    AVVIST,
}

enum class JournalpostStatusType {
    SENDT_ARENA,
    IGNORERT,
}

data class JournalpostStatus(
    val legeerklaringId: String,
    val referenceId: String,
    val journalpostId: String?,
    val msgId: String,
    val processingStatus: ProcessingStatusType,
    val journalpostStatus: JournalpostStatusType?,
    val arenaPayload: ArenaPayload?,
    val mottattDato: LocalDateTime,
    val oppdatertDato: LocalDateTime?,
)

data class ArenaPayload(
    val tssId: String?,
    val ediLoggId: String,
    val fnrLege: String,
    val behandlerName: String,
    val legeerklaering: Legeerklaering,
)
