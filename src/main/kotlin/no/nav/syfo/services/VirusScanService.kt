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
        val vedleggOver300MegaByte = vedlegg
            .filter { fileSizeLagerThan300MegaBytes(Base64.getMimeDecoder().decode(it.content.content)) }

        if (vedleggOver300MegaByte.isNotEmpty()) {
            logVedleggOver300MegaByteMetric(vedleggOver300MegaByte)
        }

        val vedleggUnder300MegaByte = vedlegg
            .filter { !fileSizeLagerThan300MegaBytes(Base64.getMimeDecoder().decode(it.content.content)) }

        return if (vedleggUnder300MegaByte.isEmpty()) {
            false
        } else {
            log.info("Scanning vedlegg for virus, numbers of vedlegg: " + vedleggUnder300MegaByte.size)
            val scanResultMayContainVirus = clamAvClient.virusScanVedlegg(vedleggUnder300MegaByte).filter { it.Result != Status.OK }
            scanResultMayContainVirus.map {
                log.warn("Vedlegg may contain virus, filename: " + it.Filename)
            }
            scanResultMayContainVirus.isNotEmpty()
        }
    }
}

fun logVedleggOver300MegaByteMetric(vedlegg: List<Vedlegg>) {
    vedlegg
        .forEach {
            log.info("Vedlegg is over 300 megabyte: " + it.description)
            VEDLEGG_OVER_300_MEGABYTE_COUNTER.inc()
        }
}

fun fileSizeLagerThan300MegaBytes(file: ByteArray): Boolean {
    return (file.size / 1024) / 1024 > 300
}
