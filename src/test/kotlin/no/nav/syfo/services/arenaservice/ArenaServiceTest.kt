package no.nav.syfo.services.arenaservice

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.jms.MessageProducer
import jakarta.jms.Session
import jakarta.jms.TextMessage
import no.nav.syfo.services.journalpoststatus.model.ArenaPayload
import no.nav.syfo.services.journalpoststatus.testLegeerklaering
import no.nav.syfo.util.LoggingMeta
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ArenaServiceTest {

    @Test
    fun `builds arena xml from payload and sends it to the arena queue`() {
        val textMessage = mockk<TextMessage>(relaxed = true)
        val session = mockk<Session>()
        val producer = mockk<MessageProducer>(relaxed = true)
        every { session.createTextMessage() } returns textMessage

        val textSlot = slot<String>()
        every { textMessage.text = capture(textSlot) } answers {}

        val payload =
            ArenaPayload(
                tssId = "tss-1",
                ediLoggId = "edi-1",
                fnrLege = "01010112345",
                behandlerName = "Navn Navnesen",
                legeerklaering = testLegeerklaering(),
            )

        ArenaService(session, producer)
            .sendArenaInfo(
                payload,
                LoggingMeta("le-1", "edi-1", null, "msg-1"),
            )

        verify(exactly = 1) { producer.send(textMessage) }
        assertTrue(textSlot.isCaptured)
        assertTrue(textSlot.captured.contains("ArenaEiaInfo"))
        assertEquals(true, textSlot.captured.contains("edi-1"))
    }
}
