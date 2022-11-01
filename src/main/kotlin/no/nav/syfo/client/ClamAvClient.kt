package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
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
                        append(it.description + it.type,  Base64.getMimeDecoder().decode(it.content.content),
                            Headers.build {
                                append(HttpHeaders.ContentType, it.content.contentType)
                                append(HttpHeaders.ContentDisposition, "filename=${it.description + it.type}")
                            })

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
