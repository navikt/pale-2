package no.nav.syfo.services.virusscanservice

import java.util.Base64
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.clamav.ClamAvClient
import no.nav.syfo.client.clamav.Status
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.vedlegg.model.Vedlegg

class VirusScanService(
    private val clamAvClient: ClamAvClient,
) {

    suspend fun vedleggContainsVirus(vedlegg: List<Vedlegg>, loggingMeta: LoggingMeta): Boolean {
        val vedleggOver300MegaByte =
            vedlegg.filter {
                fileSizeLagerThan300MegaBytes(
                    Base64.getMimeDecoder().decode(it.content.content),
                    loggingMeta
                )
            }

        if (vedleggOver300MegaByte.isNotEmpty()) {
            logVedleggOver300MegaByteMetric(vedleggOver300MegaByte, loggingMeta)
        }

        val vedleggUnder300MegaByte =
            vedlegg.filter {
                !fileSizeLagerThan300MegaBytes(
                    Base64.getMimeDecoder().decode(it.content.content),
                    loggingMeta
                )
            }

        return if (vedleggUnder300MegaByte.isEmpty()) {
            false
        } else {
            log.info(
                "Scanning vedlegg for virus, numbers of vedlegg: ${vedleggUnder300MegaByte.size}" +
                    ", {}",
                StructuredArguments.fields(loggingMeta),
            )
            val scanResultMayContainVirus =
                clamAvClient.virusScanVedlegg(vedleggUnder300MegaByte).filter {
                    it.Result != Status.OK
                }
            scanResultMayContainVirus.map {
                log.warn(
                    "Vedlegg may contain virus, filename: ${it.Filename}, {}",
                    StructuredArguments.fields(loggingMeta)
                )
            }
            scanResultMayContainVirus.isNotEmpty()
        }
    }
}

fun logVedleggOver300MegaByteMetric(vedlegg: List<Vedlegg>, loggingMeta: LoggingMeta) {
    vedlegg.forEach {
        log.info(
            "Vedlegg is over 300 megabyte: ${it.description}, {}",
            StructuredArguments.fields(loggingMeta)
        )
    }
}

fun fileSizeLagerThan300MegaBytes(file: ByteArray, loggingMeta: LoggingMeta): Boolean {
    val filesizeMegaBytes = (file.size / 1024) / 1024
    log.info("File size MB is: $filesizeMegaBytes, {}", StructuredArguments.fields(loggingMeta))
    return (file.size / 1024) / 1024 > 300
}
