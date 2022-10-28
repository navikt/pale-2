package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import no.nav.syfo.vedlegg.model.Vedlegg

class ClamAvClient(
    private val httpClient: HttpClient,
    private val endpointUrl: String
) {
    suspend fun virusScanVedlegg(vedlegg: List<Vedlegg>): List<ScanResult> {
        val httpResponse = httpClient.submitForm(
            url = "$endpointUrl/scan",
            formParameters = Parameters.build {
                vedlegg.map {
                    append(it.description + it.type, it.content.content)
                }
            }
        )
        return httpResponse.body<List<ScanResult>>()
    }
}

data class ScanResult(
    val filename: String,
    val result: Result,
)

enum class Result {
    FOUND, OK
}
