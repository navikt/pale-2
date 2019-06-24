package no.nav.syfo.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.VaultCredentials
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.helpers.retry

@KtorExperimentalAPI
class LegeSuspensjonClient(private val endpointUrl: String, private val credentials: VaultCredentials, private val stsClient: StsOidcClient) {
    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
    }

    suspend fun checkTherapist(therapistId: String, msgid: String, oppslagsdato: String): Suspendert = retry("lege_suspansjon") {
        httpClient.get<Suspendert>("$endpointUrl/api/v1/suspensjon/status") {
            accept(ContentType.Application.Json)
            val oidcToken = stsClient.oidcToken()
            headers {
                append("Nav-Call-Id", msgid)
                append("Nav-Consumer-Id", credentials.serviceuserUsername)
                append("Nav-Personident", therapistId)

                append("Authorization", "Bearer ${oidcToken.access_token}")
            }
            parameter("oppslagsdato", oppslagsdato)
        }
    }
}

data class Suspendert(val suspendert: Boolean)
