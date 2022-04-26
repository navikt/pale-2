package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.model.ValidationResult

class Pale2ReglerClient(private val endpointUrl: String, private val client: HttpClient) {
    suspend fun executeRuleValidation(payload: ReceivedLegeerklaering): ValidationResult {
        return client.post("$endpointUrl/v1/rules/validate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(payload)
        }.body<ValidationResult>()
    }
}
