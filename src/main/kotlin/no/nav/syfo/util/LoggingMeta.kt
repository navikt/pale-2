package no.nav.syfo.util

data class LoggingMeta(
    val mottakId: String,
    val orgNr: String?,
    val msgId: String
)

class TrackableException(override val cause: Throwable) : RuntimeException()

suspend fun <O> wrapExceptions(block: suspend () -> O): O {
    try {
        return block()
    } catch (e: Exception) {
        throw TrackableException(e)
    }
}
