package no.nav.syfo.services.journalpoststatus

import java.sql.SQLException
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.services.journalpoststatus.db.getJournalpostStatusByReferenceId
import no.nav.syfo.services.journalpoststatus.db.persistJournalpostStatus
import no.nav.syfo.services.journalpoststatus.db.updateJournalpostStatus
import no.nav.syfo.services.journalpoststatus.db.updateProcessingStatus
import no.nav.syfo.services.journalpoststatus.model.ArenaPayload
import no.nav.syfo.services.journalpoststatus.model.JournalpostStatus
import no.nav.syfo.services.journalpoststatus.model.JournalpostStatusType
import no.nav.syfo.services.journalpoststatus.model.ProcessingStatusType
import no.nav.syfo.util.TestDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class JournalpostStatusDbTest {

    private val database = TestDB.database

    private fun mottatt(
        legeerklaringId: String = UUID.randomUUID().toString(),
        referenceId: String = UUID.randomUUID().toString(),
    ): JournalpostStatus {
        val legeerklaering = testLegeerklaering(legeerklaringId)
        return JournalpostStatus(
            legeerklaringId = legeerklaringId,
            referenceId = referenceId,
            journalpostId = null,
            msgId = "msg-$legeerklaringId",
            processingStatus = ProcessingStatusType.MOTTATT,
            journalpostStatus = null,
            arenaPayload =
                ArenaPayload(
                    tssId = "tss-123",
                    ediLoggId = referenceId,
                    fnrLege = "01010112345",
                    behandlerName = "Navn Navnesen",
                    legeerklaering = legeerklaering,
                ),
            mottattDato = LocalDateTime.now(),
            oppdatertDato = null,
        )
    }

    @Test
    fun `persists and reads back the jsonb arena payload`() {
        val entry = mottatt()
        database.persistJournalpostStatus(entry)

        val stored = database.getJournalpostStatusByReferenceId(entry.referenceId).single()

        assertEquals(entry.legeerklaringId, stored.legeerklaringId)
        assertEquals(entry.referenceId, stored.referenceId)
        assertNull(stored.journalpostId)
        assertEquals(ProcessingStatusType.MOTTATT, stored.processingStatus)
        assertNull(stored.journalpostStatus)
        val entryPayload = entry.arenaPayload!!
        val storedPayload = stored.arenaPayload!!
        assertEquals(entryPayload.tssId, storedPayload.tssId)
        assertEquals(entryPayload.behandlerName, storedPayload.behandlerName)
        assertEquals(
            entryPayload.legeerklaering.pasient.fnr,
            storedPayload.legeerklaering.pasient.fnr,
        )
    }

    @Test
    fun `updateJournalpostStatus sets journalpostStatus and journalpostId`() {
        val entry = mottatt()
        database.persistJournalpostStatus(entry)

        database.updateJournalpostStatus(
            entry.referenceId,
            "9999",
            JournalpostStatusType.SENDT_ARENA,
        )

        val stored = database.getJournalpostStatusByReferenceId(entry.referenceId).single()
        assertEquals(JournalpostStatusType.SENDT_ARENA, stored.journalpostStatus)
        assertEquals("9999", stored.journalpostId)
        assertEquals(ProcessingStatusType.MOTTATT, stored.processingStatus)
    }

    @Test
    fun `updateProcessingStatus does not touch journalpost fields`() {
        val entry = mottatt()
        database.persistJournalpostStatus(entry)
        database.updateJournalpostStatus(
            entry.referenceId,
            "9999",
            JournalpostStatusType.SENDT_ARENA,
        )

        database.updateProcessingStatus(entry.referenceId, ProcessingStatusType.SENDT_TIL_TOPIC)

        val stored = database.getJournalpostStatusByReferenceId(entry.referenceId).single()
        assertEquals(ProcessingStatusType.SENDT_TIL_TOPIC, stored.processingStatus)
        assertEquals(JournalpostStatusType.SENDT_ARENA, stored.journalpostStatus)
        assertEquals("9999", stored.journalpostId)
    }

    @Test
    fun `second insert with same referenceId throws`() {
        val entry = mottatt()
        database.persistJournalpostStatus(entry)

        assertThrows(SQLException::class.java) {
            database.persistJournalpostStatus(mottatt(referenceId = entry.referenceId))
        }

        val stored = database.getJournalpostStatusByReferenceId(entry.referenceId).single()
        assertEquals(entry.legeerklaringId, stored.legeerklaringId)
    }

    @Test
    fun `returns empty list for unknown referenceId`() {
        assertEquals(
            emptyList<JournalpostStatus>(),
            database.getJournalpostStatusByReferenceId("does-not-exist"),
        )
    }
}
