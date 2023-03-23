package no.nav.syfo.service.duplicationcheck

import io.mockk.clearAllMocks
import no.nav.syfo.services.duplicationcheck.DuplicationCheckService
import no.nav.syfo.services.duplicationcheck.model.Duplicate
import no.nav.syfo.services.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.util.TestDB
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class DuplicationCheckServiceTest {

    private val duplicationCheckService = DuplicationCheckService(TestDB.database)

    @BeforeEach
    internal fun setup() {
        clearAllMocks()
    }

    @Test
    fun `Should return duplicationCheck if sha256Legeerklaering is in database`() {
        val sha256HealthInformation = "asdsad"
        val mottakId = "1231-213"
        val legeerklaringId = "1231-213-21312-s3442355"
        val orgNumber = "234355"

        val duplicationCheck = DuplicateCheck(
            legeerklaringId,
            sha256HealthInformation,
            mottakId,
            "12-33",
            LocalDateTime.now(),
            orgNumber,
        )

        duplicationCheckService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationCheckService.getDuplicationCheck(sha256HealthInformation, mottakId)

        assertEquals(isDuplicat?.legeerklaringId, duplicationCheck.legeerklaringId)
        assertEquals(isDuplicat?.sha256Legeerklaering, duplicationCheck.sha256Legeerklaering)
        assertEquals(isDuplicat?.mottakId, duplicationCheck.mottakId)
        assertEquals(isDuplicat?.msgId, duplicationCheck.msgId)
        assertEquals(isDuplicat?.mottattDate?.toLocalDate(), duplicationCheck.mottattDate.toLocalDate())
        assertEquals(isDuplicat?.orgNumber, orgNumber)
    }

    @Test
    fun `Should return null if sha256Legeerklaering is not database`() {
        val sha256Legeerklaering = "asdsadff11"
        val mottakId = "1231-213"
        val legeerklaringId = "1231-213-21312-s123124-1sv443"

        val duplicationCheck = DuplicateCheck(
            legeerklaringId,
            sha256Legeerklaering,
            mottakId,
            "12-33",
            LocalDateTime.now(),
            null,
        )

        duplicationCheckService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationCheckService.getDuplicationCheck("1231", "1334")

        assertEquals(null, isDuplicat)
    }

    @Test
    fun `Should persist duplicate in database`() {
        val sha256Legeerklaering = "asdsadff11"
        val mottakId = "1231-213"
        val legeerklaringId = "1231-213-21312-s123124-1sv443"
        val duplicateLegeerklaringId = "1231-213-21312-s1324-1sv443"

        val duplicate = Duplicate(
            legeerklaringId,
            sha256Legeerklaering,
            mottakId,
            LocalDateTime.now(),
            duplicateLegeerklaringId,
        )

        Assertions.assertDoesNotThrow { duplicationCheckService.persistDuplication(duplicate) }
    }
}
