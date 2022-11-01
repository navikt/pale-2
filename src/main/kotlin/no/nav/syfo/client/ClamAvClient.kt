package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
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
        val result = httpResponse.body<List<ScanResult>>()
        if (result.size != 1) {
            throw RuntimeException("Unexpected result size from virus scan request")
        }
        return result
    }
}

data class ScanResult(
    val filename: String,
    val result: Status,
)

enum class Status {
    FOUND, OK, ERROR
}
