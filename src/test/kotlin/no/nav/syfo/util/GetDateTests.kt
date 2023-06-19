package no.nav.syfo.util

import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class GetDateTests {

    @Test
    internal fun `Convert without offset`() {
        val it = "2021-02-02T12:00:00"
        val localDateTime = getLocalDateTime(it)
        Assertions.assertEquals(LocalDateTime.parse("2021-02-02T11:00:00"), localDateTime)
    }

    @Test
    internal fun `Convert with offset pluss 01 00`() {
        val it = "2021-02-02T12:00:00+01:00"
        val localDateTime = getLocalDateTime(it)
        Assertions.assertEquals(LocalDateTime.parse("2021-02-02T11:00:00"), localDateTime)
    }

    @Test
    internal fun `Convert with offset pluss 02 00`() {
        val it = "2021-02-02T12:00:00+02:00"
        val localDateTime = getLocalDateTime(it)
        Assertions.assertEquals(LocalDateTime.parse("2021-02-02T10:00:00"), localDateTime)
    }

    @Test
    internal fun `Convert with Z offset`() {
        val it = "2021-02-02T12:00:00Z"
        val localDateTime = getLocalDateTime(it)
        Assertions.assertEquals(LocalDateTime.parse("2021-02-02T12:00:00"), localDateTime)
    }
}
