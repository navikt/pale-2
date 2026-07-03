package no.nav.syfo.services.arenaservice

import jakarta.jms.MessageProducer
import jakarta.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.arena.createArenaInfo
import no.nav.syfo.log
import no.nav.syfo.services.journalpoststatus.model.ArenaPayload
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.arenaMarshaller
import no.nav.syfo.util.toString

class ArenaService(
    private val session: Session,
    private val arenaProducer: MessageProducer,
) {
    fun sendArenaInfo(arenaPayload: ArenaPayload, loggingMeta: LoggingMeta) {
        val info =
            createArenaInfo(
                arenaPayload.tssId,
                arenaPayload.ediLoggId,
                arenaPayload.fnrLege,
                arenaPayload.legeerklaering,
                arenaPayload.behandlerName,
            )
        arenaProducer.send(
            session.createTextMessage().apply { text = arenaMarshaller.toString(info) },
        )
        log.info("Legeerklæring sendt til arena, {}", fields(loggingMeta))
    }
}
