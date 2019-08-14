package no.nav.syfo.services

import com.ctc.wstx.exc.WstxException
import java.io.IOException
import no.nav.syfo.Environment
import no.nav.syfo.VaultCredentials
import no.nav.syfo.helpers.retry
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.pip.diskresjonskode.DiskresjonskodePortType
import no.nav.tjeneste.pip.diskresjonskode.meldinger.WSHentDiskresjonskodeRequest

class DiskresjonskodeService(env: Environment, credentials: VaultCredentials) {
    private val diskresjonskodeService: DiskresjonskodePortType = createPort(env.diskresjonskodeEndpointUrl) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceURL) }
    }

    suspend fun hentDiskresjonskode(ident: String): String = retry(
            callName = "hent_diskresjonskode",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L, 60000L),
            legalExceptions = *arrayOf(IOException::class, WstxException::class)
    ) {
        diskresjonskodeService.hentDiskresjonskode(WSHentDiskresjonskodeRequest().withIdent(ident)).diskresjonskode
    }
}
