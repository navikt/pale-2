package no.nav.syfo.services.duplicationcheck

import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.objectMapper
import no.nav.syfo.services.duplicationcheck.db.extractDuplicateCheckByMottakId
import no.nav.syfo.services.duplicationcheck.db.extractDuplicationCheckBySha256Legeerklaering
import no.nav.syfo.services.duplicationcheck.db.persistDuplicate
import no.nav.syfo.services.duplicationcheck.db.persistDuplicateCheck
import no.nav.syfo.services.duplicationcheck.model.Duplicate
import no.nav.syfo.services.duplicationcheck.model.DuplicateCheck
import java.security.MessageDigest

class DuplicationCheckService(private val database: DatabaseInterface) {
    fun persistDuplicationCheck(
        duplicateCheck: DuplicateCheck
    ) {
        database.persistDuplicateCheck(duplicateCheck)
    }

    fun persistDuplication(
        duplicate: Duplicate
    ) {
        database.persistDuplicate(duplicate)
    }

    fun getDuplicationCheck(sha256Legeerklaering: String, mottakId: String): DuplicateCheck? {
        val duplicationCheckSha256Legeerklaering =
            database.extractDuplicationCheckBySha256Legeerklaering(sha256Legeerklaering)
        if (duplicationCheckSha256Legeerklaering != null) {
            return duplicationCheckSha256Legeerklaering
        } else {
            val duplicationCheckMottakId = getLatestDuplicationCheck(
                database.extractDuplicateCheckByMottakId(mottakId)
            )
            if (duplicationCheckMottakId != null) {
                return duplicationCheckMottakId
            }
            return null
        }
    }
}

fun getLatestDuplicationCheck(duplicateChecks: List<DuplicateCheck>): DuplicateCheck? {
    return when (val latest = duplicateChecks.maxByOrNull { it.mottattDate }) {
        null -> null
        else -> latest
    }
}

fun sha256hashstring(legeerklaring: Legeerklaring): String =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(legeerklaring))
        .fold("") { str, it -> str + "%02x".format(it) }
