package no.nav.syfo.pdl

import io.ktor.client.HttpClient
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.client.accesstoken.AccessTokenClientV2
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService

class PdlFactory private constructor() {
    companion object {

        fun getPdlService(
            environmentVariables: EnvironmentVariables,
            httpClient: HttpClient,
            accessTokenClientV2: AccessTokenClientV2,
            pdlScope: String,
        ): PdlPersonService {
            return PdlPersonService(
                getPdlClient(httpClient, environmentVariables),
                accessTokenClientV2,
                pdlScope
            )
        }

        private fun getPdlClient(
            httpClient: HttpClient,
            environmentVariables: EnvironmentVariables
        ): PdlClient {
            return PdlClient(
                httpClient,
                environmentVariables.pdlGraphqlPath,
                PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText(),
            )
        }
    }
}
