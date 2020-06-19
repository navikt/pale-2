package no.nav.syfo.client.sts

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking

@KtorExperimentalAPI
class StsOidcClient(
    username: String,
    password: String,
    private val stsUrl: String = "http://security-token-service/rest/v1/sts/token"
) {
    private var tokenExpires: Long = 0
    private val oidcClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(Auth) {
            basic {
                this.username = username
                this.password = password
                this.sendWithoutRequest = true
            }
        }
    }

    private var oidcToken: OidcToken = runBlocking { oidcToken() }

    suspend fun oidcToken(): OidcToken {
        if (tokenExpires < System.currentTimeMillis()) {
            oidcToken = newOidcToken()
            tokenExpires = System.currentTimeMillis() + (oidcToken.expires_in - 600) * 1000
        }
        return oidcToken
    }

    private suspend fun newOidcToken(): OidcToken =
        oidcClient.get(stsUrl) {
            parameter("grant_type", "client_credentials")
            parameter("scope", "openid")
        }
}
