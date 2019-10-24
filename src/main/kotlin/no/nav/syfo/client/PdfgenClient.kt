package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.response.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.PdfPayload

@KtorExperimentalAPI
class PdfgenClient(
    private val url: String,
    private val httpClient: HttpClient
) {

    suspend fun createPDF(payload: PdfPayload): ByteArray = retry("pdfgen") {
        httpClient.call(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }.response.readBytes()
    }
}
