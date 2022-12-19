package no.nav.syfo.services.duplicationcheck

import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.objectMapper
import no.nav.syfo.services.duplicationcheck.db.extractDuplicationCheckByMottakId
import no.nav.syfo.services.duplicationcheck.db.extractDuplicationCheckBySha256HealthInformation
import no.nav.syfo.services.duplicationcheck.db.persistSha256
import no.nav.syfo.services.duplicationcheck.model.DuplicationCheckModel
import java.security.MessageDigest

class DuplicationCheckService(private val database: DatabaseInterface) {
    fun persistDuplicationCheck(
        duplicationCheckModel: DuplicationCheckModel
    ) {
        database.persistSha256(duplicationCheckModel)
    }

    fun getDuplicationCheck(sha256HealthInformation: String, mottakId: String): DuplicationCheckModel? {
        val duplicationCheckSha256HealthInformation =
            database.extractDuplicationCheckBySha256HealthInformation(sha256HealthInformation)
        if (duplicationCheckSha256HealthInformation != null) {
            return duplicationCheckSha256HealthInformation
        } else {
            val duplicationCheckMottakId = getLatestDuplicationCheck(
                database.extractDuplicationCheckByMottakId(mottakId)
            )
            if (duplicationCheckMottakId != null) {
                return duplicationCheckMottakId
            }
            return null
        }
    }
}

fun getLatestDuplicationCheck(duplicationCheckModels: List<DuplicationCheckModel>): DuplicationCheckModel? {
    return when (val latest = duplicationCheckModels.maxByOrNull { it.mottattDate }) {
        null -> null
        else -> latest
    }
}

fun sha256hashstring(legeerklaring: Legeerklaring): String =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(legeerklaring))
        .fold("") { str, it -> str + "%02x".format(it) }
