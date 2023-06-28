package no.nav.syfo

import java.io.StringReader
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getFileAsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DumpTopicTest {

    @Test
    internal fun `Skal kunne deserialisere melding fra dumptopic`() {
        val fellesformatOriginal =
            fellesformatUnmarshaller.unmarshal(
                StringReader(getFileAsString("src/test/resources/fellesformat_le.xml"))
            ) as XMLEIFellesformat
        val fellesformatSomString = fellesformatTilString(fellesformatOriginal)

        val fellesformat =
            fellesformatUnmarshaller.unmarshal(StringReader(fellesformatSomString))
                as XMLEIFellesformat

        val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
        val msgHead = fellesformat.get<XMLMsgHead>()
        val ediLoggId = receiverBlock.ediLoggId
        val msgId = msgHead.msgInfo.msgId
        val conversationRef = msgHead.msgInfo?.conversationRef

        assertEquals("FiktivTestdata0001", ediLoggId)
        assertEquals("05565fb0-cd7e-410d-bc1f-e1e918df2eac", msgId)
        assertEquals(null, conversationRef?.refToParent)
        assertEquals(null, conversationRef?.refToConversation)
    }

    @Test
    internal fun `Melding med conversationref`() {
        val fellesformatOriginal =
            fellesformatUnmarshaller.unmarshal(
                StringReader(getFileAsString("src/test/resources/fellesformat_conversationref.xml"))
            ) as XMLEIFellesformat
        val fellesformatSomString = fellesformatTilString(fellesformatOriginal)

        val fellesformat =
            fellesformatUnmarshaller.unmarshal(StringReader(fellesformatSomString))
                as XMLEIFellesformat

        val msgHead = fellesformat.get<XMLMsgHead>()
        val conversationRef = msgHead.msgInfo?.conversationRef

        assertEquals("47822e21-0151-4fb1-a223-99f0aba5d51b", conversationRef?.refToParent)
        assertEquals("96822e21-0151-4fb1-a243-99f4543551b", conversationRef?.refToConversation)
    }
}
