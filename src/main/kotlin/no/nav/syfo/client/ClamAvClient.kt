package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import no.nav.syfo.log
import no.nav.syfo.vedlegg.model.Vedlegg

class ClamAvClient(
    private val httpClient: HttpClient,
    private val endpointUrl: String
) {
    suspend fun virusScanVedlegg(vedlegg: List<Vedlegg>): List<ScanResult> {
        val httpResponse =
            httpClient.submitFormWithBinaryData(
                url = "$endpointUrl/scan",
                formData = formData {
                    vedlegg.map {
                        append(it.description + it.type, it.content.content)
                    }
                }
            )
        log.info("status description is:" + httpResponse.status.description)
        log.info("status value is:" + httpResponse.status.value)
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
