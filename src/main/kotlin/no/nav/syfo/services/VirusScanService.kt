package no.nav.syfo.services

import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.Result
import no.nav.syfo.vedlegg.model.Vedlegg

class VirusScanService(
    private val clamAvClient: ClamAvClient
) {

    suspend fun vedleggContainsVirus(vedlegg: List<Vedlegg>): Boolean {
        return clamAvClient.virusScanVedlegg(vedlegg).any {
            it.result == Result.FOUND
        }
    }
}
