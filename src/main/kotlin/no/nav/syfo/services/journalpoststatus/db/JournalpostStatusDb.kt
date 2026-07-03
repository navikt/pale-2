package no.nav.syfo.services.journalpoststatus.db

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.services.journalpoststatus.model.ArenaPayload
import no.nav.syfo.services.journalpoststatus.model.JournalpostStatus
import no.nav.syfo.services.journalpoststatus.model.JournalpostStatusType
import no.nav.syfo.services.journalpoststatus.model.ProcessingStatusType

fun DatabaseInterface.persistJournalpostStatus(journalpostStatus: JournalpostStatus) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
            insert into journalpost_status(
                reference_id,
                legeerklaring_id,
                journalpost_id,
                msg_id,
                processing_status,
                journalpost_status,
                arena_payload,
                mottatt_dato,
                oppdatert_dato
                )
            values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?);
            """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, journalpostStatus.referenceId)
                preparedStatement.setString(2, journalpostStatus.legeerklaringId)
                preparedStatement.setString(3, journalpostStatus.journalpostId)
                preparedStatement.setString(4, journalpostStatus.msgId)
                preparedStatement.setString(5, journalpostStatus.processingStatus.name)
                preparedStatement.setString(6, journalpostStatus.journalpostStatus?.name)
                preparedStatement.setString(
                    7,
                    journalpostStatus.arenaPayload?.let { objectMapper.writeValueAsString(it) },
                )
                preparedStatement.setTimestamp(
                    8,
                    Timestamp.valueOf(journalpostStatus.mottattDato),
                )
                preparedStatement.setTimestamp(
                    9,
                    journalpostStatus.oppdatertDato?.let { Timestamp.valueOf(it) },
                )
                preparedStatement.executeUpdate()
            }
        connection.commit()
    }
}

fun DatabaseInterface.updateProcessingStatus(
    referenceId: String,
    processingStatus: ProcessingStatusType,
) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
                update journalpost_status
                set processing_status = ?, oppdatert_dato = ?
                where reference_id = ?;
                """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, processingStatus.name)
                preparedStatement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
                preparedStatement.setString(3, referenceId)
                preparedStatement.executeUpdate()
            }
        connection.commit()
    }
}

fun DatabaseInterface.updateArenaPayload(referenceId: String, arenaPayload: ArenaPayload) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
                update journalpost_status
                set arena_payload = ?::jsonb
                where reference_id = ?;
                """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, objectMapper.writeValueAsString(arenaPayload))
                preparedStatement.setString(2, referenceId)
                preparedStatement.executeUpdate()
            }
        connection.commit()
    }
}

fun DatabaseInterface.getJournalpostStatusByReferenceId(
    referenceId: String
): List<JournalpostStatus> {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
                 select *
                 from journalpost_status
                 where reference_id = ?;
                """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, referenceId)
                return preparedStatement.executeQuery().toList { toJournalpostStatus() }
            }
    }
}

fun DatabaseInterface.updateJournalpostStatus(
    referenceId: String,
    journalpostId: String,
    journalpostStatus: JournalpostStatusType,
) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
                update journalpost_status
                set journalpost_status = ?, journalpost_id = ?, oppdatert_dato = ?
                where reference_id = ?;
                """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, journalpostStatus.name)
                preparedStatement.setString(2, journalpostId)
                preparedStatement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()))
                preparedStatement.setString(4, referenceId)
                preparedStatement.executeUpdate()
            }
        connection.commit()
    }
}

fun ResultSet.toJournalpostStatus(): JournalpostStatus =
    JournalpostStatus(
        legeerklaringId = getString("legeerklaring_id"),
        referenceId = getString("reference_id"),
        journalpostId = getString("journalpost_id"),
        msgId = getString("msg_id"),
        processingStatus = ProcessingStatusType.valueOf(getString("processing_status")),
        journalpostStatus = getString("journalpost_status")?.let { JournalpostStatusType.valueOf(it) },
        arenaPayload =
            getString("arena_payload")?.let { objectMapper.readValue(it, ArenaPayload::class.java) },
        mottattDato = getTimestamp("mottatt_dato").toLocalDateTime(),
        oppdatertDato = getTimestamp("oppdatert_dato")?.toLocalDateTime(),
    )
