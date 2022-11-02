package no.nav.syfo.services

import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.Status
import no.nav.syfo.log
import no.nav.syfo.metrics.VEDLEGG_OVER_300_MEGABYTE_COUNTER
import no.nav.syfo.vedlegg.model.Vedlegg
import java.util.Base64

class VirusScanService(
    private val clamAvClient: ClamAvClient
) {

    suspend fun vedleggContainsVirus(vedlegg: List<Vedlegg>): Boolean {
        logVedleggOver300MegaByteMetric(vedlegg)

        log.info("Scanning vedlegg for virus, numbers of vedlegg: " + vedlegg.size)
        val scanResultMayContainVirus = clamAvClient.virusScanVedlegg(vedlegg).filter { it.Result != Status.OK }
        scanResultMayContainVirus.map {
            log.warn("Vedlegg may contain virus, filename: " + it.Filename)
        }
        return scanResultMayContainVirus.isNotEmpty()
    }
}

fun logVedleggOver300MegaByteMetric(vedlegg: List<Vedlegg>) {
    vedlegg
        .filter { fileSizeLagerThan300MegaBytes(Base64.getMimeDecoder().decode(it.content.content)) }
        .forEach { _ ->
            VEDLEGG_OVER_300_MEGABYTE_COUNTER.inc()
        }
}

fun fileSizeLagerThan300MegaBytes(file: ByteArray): Boolean {
    return (file.size / 1024) / 1024 > 300
}
