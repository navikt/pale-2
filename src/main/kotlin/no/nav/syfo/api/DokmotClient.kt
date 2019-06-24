package no.nav.syfo.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.MottaInngaaendeForsendelse
import no.nav.syfo.model.MottaInngaandeForsendelseResultat

@KtorExperimentalAPI
class DokmotClient constructor(private val url: String, private val stsClient: StsOidcClient) {
    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
    }

            suspend fun createJournalpost(
                mottaInngaaendeForsendelse: MottaInngaaendeForsendelse
            ): MottaInngaandeForsendelseResultat = retry("dokmotinngaaende") {
                httpClient.post<MottaInngaandeForsendelseResultat>(url) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                    body = mottaInngaaendeForsendelse
                }
            }
        }