package no.nav.syfo.services.duplicationcheck.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.services.duplicationcheck.model.Duplicate
import no.nav.syfo.services.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.services.duplicationcheck.model.DuplikatsjekkModel
import java.sql.ResultSet
import java.sql.Timestamp

fun DatabaseInterface.persistSha256(duplikatsjekkModel: DuplikatsjekkModel) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            insert into duplikatsjekk(
                sha256_legeerklaering,
                mottak_id,
                msg_id,
                mottatt_date
                )
            values (?, ?, ?, ?) on conflict (sha256_legeerklaering) do nothing;
            """
        ).use { preparedStatement ->
            preparedStatement.setString(1, duplikatsjekkModel.sha256Legeerklaering)
            preparedStatement.setString(2, duplikatsjekkModel.mottakId)
            preparedStatement.setString(3, duplikatsjekkModel.msgId)
            preparedStatement.setTimestamp(4, Timestamp.valueOf(duplikatsjekkModel.mottattDate))
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.persistDuplicateCheck(duplicateCheck: DuplicateCheck) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            insert into duplicatecheck(
                legeerklaring_id,
                sha256_legeerklaering,
                mottak_id,
                msg_id,
                mottatt_date,
                org_number
                )
            values (?, ?, ?, ?, ?, ?);
            """
        ).use { preparedStatement ->
            preparedStatement.setString(1, duplicateCheck.legeerklaringId)
            preparedStatement.setString(2, duplicateCheck.sha256Legeerklaering)
            preparedStatement.setString(3, duplicateCheck.mottakId)
            preparedStatement.setString(4, duplicateCheck.msgId)
            preparedStatement.setTimestamp(5, Timestamp.valueOf(duplicateCheck.mottattDate))
            preparedStatement.setString(6, duplicateCheck.orgNumber)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.persistDuplicate(duplicate: Duplicate) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            insert into duplicate(
                legeerklaring_id,
                mottak_id,
                msg_id,
                mottatt_date,
                duplicate_legeerklaring_id
                )
            values (?, ?, ?, ?, ?);
            """
        ).use { preparedStatement ->
            preparedStatement.setString(1, duplicate.legeerklaringId)
            preparedStatement.setString(2, duplicate.mottakId)
            preparedStatement.setString(3, duplicate.msgId)
            preparedStatement.setTimestamp(4, Timestamp.valueOf(duplicate.mottattDate))
            preparedStatement.setString(5, duplicate.duplicateLegeerklaringId)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.extractDuplikatsjekkBySha256Legeerklaering(sha256Legeerklaering: String): DuplikatsjekkModel? {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * 
                 from duplikatsjekk 
                 where sha256_legeerklaering=?;
                """
        ).use { preparedStatement ->
            preparedStatement.setString(1, sha256Legeerklaering)
            return preparedStatement.executeQuery().toList { toDuplikatsjekkModel() }.firstOrNull()
        }
    }
}

fun DatabaseInterface.extractDuplicationCheckBySha256Legeerklaering(sha256Legeerklaering: String): DuplicateCheck? {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * 
                 from duplicatecheck 
                 where sha256_legeerklaering=?;
                """
        ).use { preparedStatement ->
            preparedStatement.setString(1, sha256Legeerklaering)
            return preparedStatement.executeQuery().toList { toDuplicateCheck() }.firstOrNull()
        }
    }
}

fun DatabaseInterface.extractDuplikatsjekkByMottakId(mottakId: String): List<DuplikatsjekkModel> {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * 
                 from duplikatsjekk 
                 where mottak_id=?;
                """
        ).use { preparedStatement ->
            preparedStatement.setString(1, mottakId)
            return preparedStatement.executeQuery().toList { toDuplikatsjekkModel() }
        }
    }
}

fun DatabaseInterface.extractDuplicateCheckByMottakId(mottakId: String): List<DuplicateCheck> {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * 
                 from duplicatecheck 
                 where mottak_id=?;
                """
        ).use { preparedStatement ->
            preparedStatement.setString(1, mottakId)
            return preparedStatement.executeQuery().toList { toDuplicateCheck() }
        }
    }
}

fun ResultSet.toDuplikatsjekkModel(): DuplikatsjekkModel =
    DuplikatsjekkModel(
        sha256Legeerklaering = getString("sha256_legeerklaering"),
        mottakId = getString("mottak_id"),
        msgId = getString("msg_id"),
        mottattDate = getTimestamp("mottatt_date").toLocalDateTime()
    )
fun ResultSet.toDuplicateCheck(): DuplicateCheck =
    DuplicateCheck(
        legeerklaringId = getString("legeerklaring_id"),
        sha256Legeerklaering = getString("sha256_legeerklaering"),
        mottakId = getString("mottak_id"),
        msgId = getString("msg_id"),
        mottattDate = getTimestamp("mottatt_date").toLocalDateTime(),
        orgNumber = getString("org_number")
    )
