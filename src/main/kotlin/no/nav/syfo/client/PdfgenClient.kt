package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.response.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.PdfModel
import no.nav.syfo.model.ValidationResult

@KtorExperimentalAPI
class PdfgenClient(
    private val url: String,
    private val httpClient: HttpClient
) {

    suspend fun createPDF(payload: PdfModel): ByteArray = retry("pdfgen") {
        httpClient.call(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }.response.readBytes()
    }

    fun createPdfPayload(
        legeerklaring: Legeerklaering,
        validationResult: ValidationResult
    ): PdfModel = PdfModel(
        legeerklaering = legeerklaring,
        validationResult = validationResult
    )
}
