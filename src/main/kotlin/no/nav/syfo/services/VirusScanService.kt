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
        val scanResultMayContainVirus = clamAvClient.virusScanVedlegg(vedlegg).filter { it.Result != Status.OK }
        scanResultMayContainVirus.map {
            log.warn("Vedlegg may contain virus, filename: " + it.Filename)
        }
        return scanResultMayContainVirus.isNotEmpty()
    }
}
