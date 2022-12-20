package no.nav.syfo.services.duplicationcheck.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.services.duplicationcheck.model.DuplicationCheckModel
import java.sql.ResultSet
import java.sql.Timestamp

fun DatabaseInterface.persistSha256(duplicationCheckModel: DuplicationCheckModel) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            insert into duplikatsjekk(
                sha256_legeerklaering,
                mottak_id,
                msg_id,
                mottatt_date
                )
            values (?, ?, ?, ?)
            """
        ).use { preparedStatement ->
            preparedStatement.setString(1, duplicationCheckModel.sha256Legeerklaering)
            preparedStatement.setString(2, duplicationCheckModel.mottakId)
            preparedStatement.setString(3, duplicationCheckModel.msgId)
            preparedStatement.setTimestamp(4, Timestamp.valueOf(duplicationCheckModel.mottattDate))
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.extractDuplicationCheckBySha256HealthInformation(sha256HealthInformation: String): DuplicationCheckModel? {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * 
                 from duplikatsjekk 
                 where sha256_legeerklaering=?;
                """
        ).use { preparedStatement ->
            preparedStatement.setString(1, sha256HealthInformation)
            return preparedStatement.executeQuery().toList { toDuplicationCheck() }.firstOrNull()
        }
    }
}

fun DatabaseInterface.extractDuplicationCheckByMottakId(mottakId: String): List<DuplicationCheckModel> {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * 
                 from duplikatsjekk 
                 where mottak_id=?;
                """
        ).use { preparedStatement ->
            preparedStatement.setString(1, mottakId)
            return preparedStatement.executeQuery().toList { toDuplicationCheck() }
        }
    }
}

fun ResultSet.toDuplicationCheck(): DuplicationCheckModel =
    DuplicationCheckModel(
        sha256Legeerklaering = getString("sha256_legeerklaering"),
        mottakId = getString("mottak_id"),
        msgId = getString("msg_id"),
        mottattDate = getTimestamp("mottatt_date").toLocalDateTime()
    )
