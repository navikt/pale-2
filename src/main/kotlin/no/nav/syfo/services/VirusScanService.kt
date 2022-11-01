package no.nav.syfo.services

import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.Status
import no.nav.syfo.log
import no.nav.syfo.vedlegg.model.Vedlegg

class VirusScanService(
    private val clamAvClient: ClamAvClient
) {

    suspend fun vedleggContainsVirus(vedlegg: List<Vedlegg>): Boolean {
        log.info("Scanning vedlegg for virus, numbers of vedlegg: " + vedlegg.size)
        return clamAvClient.virusScanVedlegg(vedlegg).any {
            it.Result != Status.OK.apply {
                log.warn("vedlegg may conatins virus filename: " + it.Filename)
            }
        }
    }
}
