package no.nav.syfo.services

import redis.clients.jedis.Jedis
import java.util.concurrent.TimeUnit

fun updateRedis(
    jedis: Jedis,
    ediLoggId: String,
    sha256String: String,
) {
    jedis.setex(ediLoggId, TimeUnit.DAYS.toSeconds(7), ediLoggId)
    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7), ediLoggId)
}
