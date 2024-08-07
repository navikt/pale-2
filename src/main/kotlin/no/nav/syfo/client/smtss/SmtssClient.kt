package no.nav.syfo.client.smtss

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.accesstoken.AccessTokenClientV2
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

class SmtssClient(
    private val endpointUrl: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient,
) {
    suspend fun findBestTssIdEmottak(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
        legeerklaringId: String,
        samhandlerOrgnummer: String?,
    ): String? {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        val httpResponse =
            httpClient.get("$endpointUrl/api/v1/samhandler/emottak") {
                accept(ContentType.Application.Json)
                header("samhandlerFnr", samhandlerFnr)
                header("samhandlerOrgName", samhandlerOrgName)
                samhandlerOrgnummer?.let { header("samhandlerOrgnummer", it) }
                header("Authorization", "Bearer $accessToken")
                header("requestId", legeerklaringId)
            }
        return if (httpResponse.status == HttpStatusCode.OK) {
            httpResponse.body<TSSident>().tssid
        } else {
            log.info(
                "smtss responded with an error code {} for {}",
                httpResponse.status,
                StructuredArguments.fields(loggingMeta),
            )
            null
        }
    }

    suspend fun findBestTssInfotrygdId(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
        legeerklaringId: String,
    ): String? {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        val httpResponse =
            httpClient.get("$endpointUrl/api/v1/samhandler/infotrygd") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("samhandlerFnr", samhandlerFnr)
                header("samhandlerOrgName", samhandlerOrgName)
                header("Authorization", "Bearer $accessToken")
                header("requestId", legeerklaringId)
            }
        return if (httpResponse.status == HttpStatusCode.OK) {
            val tssid = httpResponse.body<TSSident>().tssid
            tssid
        } else {
            log.info(
                "smtss responded with an error code {} for {}",
                httpResponse.status,
                StructuredArguments.fields(loggingMeta),
            )
            null
        }
    }

    suspend fun findBestTssIdArena(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
        legeerklaringId: String,
    ): String? {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        val httpResponse =
            httpClient.get("$endpointUrl/api/v1/samhandler/arena") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("samhandlerFnr", samhandlerFnr)
                header("samhandlerOrgName", samhandlerOrgName)
                header("Authorization", "Bearer $accessToken")
                header("requestId", legeerklaringId)
            }
        return if (httpResponse.status == HttpStatusCode.OK) {
            val tssid = httpResponse.body<TSSident>().tssid
            tssid
        } else {
            log.info(
                "smtss responded with an error code {} for {}",
                httpResponse.status,
                StructuredArguments.fields(loggingMeta),
            )
            null
        }
    }
}

data class TSSident(
    val tssid: String,
)
