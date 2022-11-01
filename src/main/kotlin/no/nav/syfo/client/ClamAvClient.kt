package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import no.nav.syfo.vedlegg.model.Vedlegg
import java.util.Base64

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
                        append(it.description + it.type,  Base64.getMimeDecoder().decode(it.content.content))
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
