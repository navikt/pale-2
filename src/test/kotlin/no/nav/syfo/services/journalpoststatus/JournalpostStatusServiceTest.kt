package no.nav.syfo.services.journalpoststatus

import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.services.arenaservice.ArenaService
import no.nav.syfo.services.journalpoststatus.db.getJournalpostStatusByReferenceId
import no.nav.syfo.services.journalpoststatus.model.ArenaPayload
import no.nav.syfo.services.journalpoststatus.model.JournalpostStatusType
import no.nav.syfo.services.journalpoststatus.model.ProcessingStatusType
import no.nav.syfo.util.TestDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class JournalpostStatusServiceTest {

    private val database = TestDB.database
    private val service = JournalpostStatusService(database)
    private val arenaService = mockk<ArenaService>(relaxed = true)

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    private fun insertPublisert(
        referenceId: String,
        withArenaPayload: Boolean = true,
        processingStatus: ProcessingStatusType = ProcessingStatusType.SENDT_TIL_TOPIC,
    ): String {
        val legeerklaringId = UUID.randomUUID().toString()
        service.insertMottatt(
            legeerklaringId = legeerklaringId,
            referenceId = referenceId,
            msgId = "msg-$legeerklaringId",
            mottattDato = LocalDateTime.now(),
        )
        if (withArenaPayload) {
            service.updateArenaPayload(
                referenceId,
                ArenaPayload(
                    tssId = "tss-1",
                    ediLoggId = referenceId,
                    fnrLege = "01010112345",
                    behandlerName = "Navn Navnesen",
                    legeerklaering = testLegeerklaering(legeerklaringId),
                ),
            )
        }
        if (processingStatus != ProcessingStatusType.MOTTATT) {
            service.updateProcessingStatus(referenceId, processingStatus)
        }
        return legeerklaringId
    }

    private fun record(
        kanalReferanseId: String,
        journalpostStatus: String = "JOURNALFOERT",
        temaNytt: String = "OPP",
    ) =
        JournalfoeringHendelseRecord(
            UUID.randomUUID().toString(),
            1,
            "EndeligJournalfoert",
            123456L,
            journalpostStatus,
            "SYK",
            temaNytt,
            "NAV_NO",
            kanalReferanseId,
            "",
        )

    @Test
    fun `journalfoert with tema OPP sends to arena and marks SENDT_ARENA`() {
        val referenceId = UUID.randomUUID().toString()
        val legeerklaringId = insertPublisert(referenceId)

        service.handleJournalfoeringHendelse(record(referenceId, temaNytt = "OPP"), arenaService)

        verify(exactly = 1) { arenaService.sendArenaInfo(any(), any()) }
        val stored = database.getJournalpostStatusByReferenceId(referenceId).single()
        assertEquals(JournalpostStatusType.SENDT_ARENA, stored.journalpostStatus)
        assertEquals("123456", stored.journalpostId)
        assertEquals(legeerklaringId, stored.legeerklaringId)
    }

    @Test
    fun `journalfoert with tema not OPP does not send and marks IGNORERT`() {
        val referenceId = UUID.randomUUID().toString()
        insertPublisert(referenceId)

        service.handleJournalfoeringHendelse(record(referenceId, temaNytt = "APP"), arenaService)

        verify(exactly = 0) { arenaService.sendArenaInfo(any(), any()) }
        val stored = database.getJournalpostStatusByReferenceId(referenceId).single()
        assertEquals(JournalpostStatusType.IGNORERT, stored.journalpostStatus)
    }

    @Test
    fun `non JOURNALFOERT status is ignored and entry stays pending`() {
        val referenceId = UUID.randomUUID().toString()
        insertPublisert(referenceId)

        service.handleJournalfoeringHendelse(
            record(referenceId, journalpostStatus = "MOTTATT", temaNytt = "OPP"),
            arenaService,
        )

        verify(exactly = 0) { arenaService.sendArenaInfo(any(), any()) }
        val stored = database.getJournalpostStatusByReferenceId(referenceId).single()
        assertNull(stored.journalpostStatus)
    }

    @Test
    fun `journalfoert entry stuck before processing status update is still sent to arena`() {
        val referenceId = UUID.randomUUID().toString()
        insertPublisert(referenceId, processingStatus = ProcessingStatusType.APPREC_SENDT)

        service.handleJournalfoeringHendelse(record(referenceId, temaNytt = "OPP"), arenaService)

        verify(exactly = 1) { arenaService.sendArenaInfo(any(), any()) }
        val stored = database.getJournalpostStatusByReferenceId(referenceId).single()
        assertEquals(JournalpostStatusType.SENDT_ARENA, stored.journalpostStatus)
    }

    @Test
    fun `journalfoert entry without arena payload is not sent to arena`() {
        val referenceId = UUID.randomUUID().toString()
        insertPublisert(referenceId, withArenaPayload = false)

        service.handleJournalfoeringHendelse(record(referenceId, temaNytt = "OPP"), arenaService)

        verify(exactly = 0) { arenaService.sendArenaInfo(any(), any()) }
        val stored = database.getJournalpostStatusByReferenceId(referenceId).single()
        assertEquals(JournalpostStatusType.IGNORERT, stored.journalpostStatus)
    }

    @Test
    fun `unmatched reference id does nothing`() {
        service.handleJournalfoeringHendelse(record("no-such-reference"), arenaService)

        verify(exactly = 0) { arenaService.sendArenaInfo(any(), any()) }
    }

    @Test
    fun `already sent entry is not sent again`() {
        val referenceId = UUID.randomUUID().toString()
        insertPublisert(referenceId)

        service.handleJournalfoeringHendelse(record(referenceId, temaNytt = "OPP"), arenaService)
        service.handleJournalfoeringHendelse(record(referenceId, temaNytt = "OPP"), arenaService)

        verify(exactly = 1) { arenaService.sendArenaInfo(any(), any()) }
    }
}
