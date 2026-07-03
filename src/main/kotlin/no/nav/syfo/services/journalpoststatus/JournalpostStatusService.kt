package no.nav.syfo.services.journalpoststatus

import java.time.LocalDateTime
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.ARENA_SENDT_VIA_JOURNALFOERING_COUNTER
import no.nav.syfo.metrics.JOURNALFOERING_IGNORERT_COUNTER
import no.nav.syfo.metrics.JOURNALFOERING_UMATCHET_COUNTER
import no.nav.syfo.services.arenaservice.ArenaService
import no.nav.syfo.services.journalpoststatus.db.getJournalpostStatusByReferenceId
import no.nav.syfo.services.journalpoststatus.db.persistJournalpostStatus
import no.nav.syfo.services.journalpoststatus.db.updateArenaPayload
import no.nav.syfo.services.journalpoststatus.db.updateJournalpostStatus
import no.nav.syfo.services.journalpoststatus.db.updateProcessingStatus
import no.nav.syfo.services.journalpoststatus.model.ArenaPayload
import no.nav.syfo.services.journalpoststatus.model.JournalpostStatus
import no.nav.syfo.services.journalpoststatus.model.JournalpostStatusType
import no.nav.syfo.services.journalpoststatus.model.ProcessingStatusType
import no.nav.syfo.util.LoggingMeta

const val JOURNALPOST_STATUS_JOURNALFOERT = "JOURNALFOERT"
const val TEMA_OPPFOELGING = "OPP"

class JournalpostStatusService(private val database: DatabaseInterface) {

    fun getByReferenceId(referenceId: String): JournalpostStatus? =
        database.getJournalpostStatusByReferenceId(referenceId).firstOrNull()

    fun insertMottatt(
        legeerklaringId: String,
        referenceId: String,
        msgId: String,
        mottattDato: LocalDateTime,
    ): JournalpostStatus {
        val journalpostStatus =
            JournalpostStatus(
                legeerklaringId = legeerklaringId,
                referenceId = referenceId,
                journalpostId = null,
                msgId = msgId,
                processingStatus = ProcessingStatusType.MOTTATT,
                journalpostStatus = null,
                arenaPayload = null,
                mottattDato = mottattDato,
                oppdatertDato = null,
            )
        database.persistJournalpostStatus(journalpostStatus)
        return journalpostStatus
    }

    fun updateProcessingStatus(referenceId: String, processingStatus: ProcessingStatusType) {
        database.updateProcessingStatus(referenceId, processingStatus)
    }

    fun updateArenaPayload(referenceId: String, arenaPayload: ArenaPayload) {
        database.updateArenaPayload(referenceId, arenaPayload)
    }

    fun handleJournalfoeringHendelse(
        record: JournalfoeringHendelseRecord,
        arenaService: ArenaService,
    ) {
        if (record.journalpostStatus != JOURNALPOST_STATUS_JOURNALFOERT) {
            return
        }

        val referenceId = record.kanalReferanseId ?: return
        val journalpostId = record.journalpostId.toString()

        val pendingEntries =
            database.getJournalpostStatusByReferenceId(referenceId).filter {
                it.journalpostStatus == null
            }

        if (pendingEntries.isEmpty()) {
            log.info("Fant ikke journalpoststatus for journalpostId $journalpostId og referanse: $referenceId")
            JOURNALFOERING_UMATCHET_COUNTER.inc()
            return
        }

        pendingEntries.forEach { entry ->

            log.info("Fant journalpoststatus med status ${entry.journalpostStatus} for journalpostId $journalpostId, referanse: $referenceId, legeerklaringId: ${entry.legeerklaringId}")
            val loggingMeta =
                LoggingMeta(
                    legeerklaringId = entry.legeerklaringId,
                    mottakId = entry.referenceId,
                    orgNr = null,
                    msgId = entry.msgId,
                )

            val arenaPayload = entry.arenaPayload
            if (record.temaNytt == TEMA_OPPFOELGING) {
                if (arenaPayload == null) {
                    database.updateJournalpostStatus(
                        entry.referenceId,
                        journalpostId,
                        JournalpostStatusType.IGNORERT,
                    )
                    JOURNALFOERING_IGNORERT_COUNTER.inc()
                    log.warn(
                        "Journalført legeerklæring mangler arena-payload, sendes ikke til arena {}",
                        fields(loggingMeta),
                    )
                } else {
                    arenaService.sendArenaInfo(arenaPayload, loggingMeta)
                    database.updateJournalpostStatus(
                        entry.referenceId,
                        journalpostId,
                        JournalpostStatusType.SENDT_ARENA,
                    )
                    ARENA_SENDT_VIA_JOURNALFOERING_COUNTER.inc()
                    log.info(
                        "Legeerklæring journalført med tema OPP, sendt til arena {}",
                        fields(loggingMeta),
                    )
                }
            } else {
                database.updateJournalpostStatus(
                    entry.referenceId,
                    journalpostId,
                    JournalpostStatusType.IGNORERT,
                )
                JOURNALFOERING_IGNORERT_COUNTER.inc()
                log.info(
                    "Legeerklæring journalført med tema ${record.temaNytt}, sendes ikke til arena {}",
                    fields(loggingMeta),
                )
            }
        }
    }
}
