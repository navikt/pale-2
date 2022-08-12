package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.io.StringReader

class ExtractFromFellesFormatTest {

    @Test
    internal fun `Tester henting av telefonnummer fra telecom`() {

        val felleformatLe = fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/fellesformat_le.xml"))
        ) as XMLEIFellesformat
        val healthcareProfessional =
            felleformatLe.get<XMLMsgHead>().msgInfo.sender.organisation?.healthcareProfessional

        val tlfFromFromHealthcareProfessional = extractTlfFromHealthcareProfessional(healthcareProfessional)

        tlfFromFromHealthcareProfessional shouldBeEqualTo "98765432"
    }
}
