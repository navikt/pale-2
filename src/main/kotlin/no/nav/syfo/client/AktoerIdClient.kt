package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.LoggingMeta
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.IdentInfoResult

@KtorExperimentalAPI
class AktoerIdClient(
    private val endpointUrl: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun getAktoerIds(
        personNumbers: List<String>,
        username: String,
        loggingMeta: LoggingMeta
    ): Map<String, IdentInfoResult> =
        retry("get_aktoerids") {
            httpClient.get<HttpResponse>("$endpointUrl/identer") {
                accept(ContentType.Application.Json)
                val oidcToken = stsClient.oidcToken()
                headers {
                    append("Authorization", "Bearer ${oidcToken.access_token}")
                    append("Nav-Consumer-Id", username)
                    append("Nav-Call-Id", loggingMeta.msgId)
                    append("Nav-Personidenter", personNumbers.joinToString(","))
                }
                parameter("gjeldende", "true")
                parameter("identgruppe", "AktoerId")
            }.call.response.receive<Map<String, IdentInfoResult>>()
        }
}