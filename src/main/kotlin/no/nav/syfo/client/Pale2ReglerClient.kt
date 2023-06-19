package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta

class Pale2ReglerClient(
    private val endpointUrl: String,
    private val client: HttpClient,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
) {
    suspend fun executeRuleValidation(
        payload: ReceivedLegeerklaering,
        loggingMeta: LoggingMeta
    ): ValidationResult {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        val httpResponse =
            client.post("$endpointUrl/v1/rules/validate") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                setBody(payload)
            }
        if (httpResponse.status == HttpStatusCode.OK) {
            return httpResponse.body<ValidationResult>()
        } else {
            log.error(
                "Pale-2-regler svarte med feilkode {} for {}",
                httpResponse.status,
                StructuredArguments.fields(loggingMeta),
            )
            throw IOException("Pale-2-regler  svarte med feilkode ${httpResponse.status}")
        }
    }
}
