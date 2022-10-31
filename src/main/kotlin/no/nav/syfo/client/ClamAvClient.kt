package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import no.nav.syfo.log
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
        log.info("status description is:" + httpResponse.status.description)
        log.info("status value is:" + httpResponse.status.value)
        log.info("body is: " + httpResponse.body<Any?>().toString())
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
