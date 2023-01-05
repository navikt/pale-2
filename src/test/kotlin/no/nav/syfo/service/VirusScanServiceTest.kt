package no.nav.syfo.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.ScanResult
import no.nav.syfo.client.Status
import no.nav.syfo.services.VirusScanService
import no.nav.syfo.services.fileSizeLagerThan300MegaBytes
import no.nav.syfo.vedlegg.model.Content
import no.nav.syfo.vedlegg.model.Vedlegg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

internal class VirusScanServiceTest {

    private val clamAvClientMock = mockkClass(ClamAvClient::class)

    @BeforeEach
    internal fun `Set up`() {
        clearAllMocks()
    }

    @Test
    fun `Should return true if result contains FOUND`() {
        coEvery { clamAvClientMock.virusScanVedlegg(any()) } returns
            listOf(
                ScanResult("normalFile", Status.OK),
                ScanResult("eicar.com.txt", Status.FOUND)
            )
        val contentImage = getFileContentAsString("src/test/resources/doctor.jpeg")
        val contentText = getFileContentAsString("src/test/resources/base64EncodedData.txt")

        val vedleggBilde = Vedlegg(Content("Base64Container", contentImage), "image/jpeg", "Bilde av lege")
        val vedleggText = Vedlegg(Content("Base64Container", contentText), "text/plain", "eicar.com")

        runBlocking {
            val vedleggContainsVirus =
                VirusScanService(clamAvClientMock).vedleggContainsVirus(listOf(vedleggBilde, vedleggText))
            assertEquals(true, vedleggContainsVirus)
        }
    }

    @Test
    fun `Should return false if result only contains OK`() {
        coEvery { clamAvClientMock.virusScanVedlegg(any()) } returns
            listOf(
                ScanResult("normalFile", Status.OK),
                ScanResult("anotherNormalFile", Status.OK)
            )
        val contentImage = getFileContentAsString("src/test/resources/doctor.jpeg")

        val vedleggImage1 = Vedlegg(Content("Base64Container", contentImage), "image/jpeg", "Et bilde fra lege")
        val vedleggImage2 = Vedlegg(Content("Base64Container", contentImage), "image/jpeg", "Et til bilde fra lege")

        runBlocking {
            val vedleggContainsVirus =
                VirusScanService(clamAvClientMock).vedleggContainsVirus(listOf(vedleggImage1, vedleggImage2))
            assertEquals(vedleggContainsVirus, false)
        }
    }

    @Test
    fun `Should return true if result contains ERROR`() {
        coEvery { clamAvClientMock.virusScanVedlegg(any()) } returns
            listOf(
                ScanResult("normalFile", Status.OK),
                ScanResult("strangeFile", Status.ERROR)
            )

        val contentImage = getFileContentAsString("src/test/resources/doctor.jpeg")
        val vedleggImage1 = Vedlegg(Content("Base64Container", contentImage), "image/jpeg", "Bilde av lege")
        val vedleggImage2 = Vedlegg(Content("Base64Container", contentImage), "image/jpeg", "Samme lege")

        runBlocking {
            val vedleggContainsVirus =
                VirusScanService(clamAvClientMock).vedleggContainsVirus(listOf(vedleggImage1, vedleggImage2))
            assertEquals(vedleggContainsVirus, true)
        }
    }

    @Test
    fun `Should return false when file size is lower than 300 megabytes`() {
        val base64EncodedContent = getFileContentAsString("src/test/resources/base64EncodedData.txt")
        val vedlegg = Vedlegg(Content("Base64Container", base64EncodedContent), "image/jpeg", "image_of_file")
        val file = Base64.getMimeDecoder().decode(vedlegg.content.content)
        assertEquals(false, fileSizeLagerThan300MegaBytes(file))
    }

    fun getFileContentAsString(filepath: String): String {
        return Files.readAllBytes(Paths.get(filepath)).toString(StandardCharsets.UTF_16)
    }
}
