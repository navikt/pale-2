package no.nav.syfo.pdl

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentIdenter
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PdlServiceTest {
    private val pdlClient = mockkClass(PdlClient::class)
    private val accessTokenClientV2 = mockkClass(AccessTokenClientV2::class)
    private val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, "littaScope")

    private val loggingMeta = LoggingMeta("sykmeldingId", "journalpostId", "hendelsesId")

    @BeforeEach
    internal fun `Set up`() {
        clearAllMocks()
    }

    @Test
    internal fun `Hent person fra pdl uten fortrolig adresse`() {

        coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"

        runBlocking {
            val person = pdlService.getPdlPerson("01245678901", loggingMeta)
            person?.navn?.fornavn shouldBeEqualTo "fornavn"
            person?.navn?.mellomnavn shouldBeEqualTo null
            person?.navn?.etternavn shouldBeEqualTo "etternavn"
            person?.aktorId shouldBeEqualTo "987654321"
        }
    }

    @Test
    internal fun `Skal feile når person ikke finnes`() {
        coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null, null), errors = null)
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"

        runBlocking {
            val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
            pdlPerson shouldBe null
        }
    }

    @Test
    internal fun `Skal feile når navn er tom liste`() {
        coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
            ResponseData(
                hentPerson = HentPerson(
                    navn = emptyList(), adressebeskyttelse = null
                ),
                hentIdenter = HentIdenter(emptyList())
            ),
            errors = null
        )

        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"

        runBlocking {
            val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
            pdlPerson shouldBe null
        }
    }

    @Test
    internal fun `Skal feile når navn ikke finnes`() {
        coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
            ResponseData(
                hentPerson = HentPerson(
                    navn = null, adressebeskyttelse = null
                ),
                hentIdenter = HentIdenter(listOf(PdlIdent(ident = "987654321", gruppe = "foo")))
            ),
            errors = null
        )

        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"

        runBlocking {
            val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
            pdlPerson shouldBe null
        }
    }

    @Test
    internal fun `Skal feile når identer ikke finnes`() {
        coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
            ResponseData(
                hentPerson = HentPerson(
                    navn = listOf(Navn("fornavn", "mellomnavn", "etternavn")),
                    adressebeskyttelse = null
                ),
                hentIdenter = HentIdenter(emptyList())
            ),
            errors = null
        )

        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"

        runBlocking {
            val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
            pdlPerson shouldBe null
        }
    }
}
