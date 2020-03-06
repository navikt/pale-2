package no.nav.syfo.util

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Marshaller.JAXB_ENCODING
import javax.xml.bind.Unmarshaller
import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.arenainfo.ArenaEiaInfo
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(
    XMLEIFellesformat::class.java,
    XMLMsgHead::class.java,
    Legeerklaring::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller().apply {
    setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
    setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
}

val apprecJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLAppRec::class.java)
val apprecMarshaller: Marshaller = apprecJaxBContext.createMarshaller()

val arenaEiaInfoJaxBContext: JAXBContext = JAXBContext.newInstance(ArenaEiaInfo::class.java)
val arenaMarshaller: Marshaller = arenaEiaInfoJaxBContext.createMarshaller()

val senderMarshaller: Marshaller = JAXBContext.newInstance(XMLSender::class.java).createMarshaller()
    .apply { setProperty(JAXB_ENCODING, "ISO-8859-1") }

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}
