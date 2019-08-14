package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.LoggingMeta
import no.nav.syfo.VaultCredentials
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import org.apache.commons.text.similarity.LevenshteinDistance
import java.util.Date
import kotlin.math.max

@KtorExperimentalAPI
class SarClient(
    private val endpointUrl: String,
    private val credentials: VaultCredentials
) {

    private val kuhrSarClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(BasicAuth) {
            this.username = credentials.serviceuserUsername
            this.password = credentials.serviceuserPassword
        }
    }

    suspend fun getSamhandler(ident: String): List<Samhandler> = retry("get_samhandler") {
        kuhrSarClient.get<List<Samhandler>>("$endpointUrl/rest/sar/samh") {
            accept(ContentType.Application.Json)
            parameter("ident", ident)
        }
    }
}

data class Samhandler(
    val samh_id: String,
    val navn: String,
    val samh_type_kode: String,
    val behandling_utfall_kode: String,
    val unntatt_veiledning: String,
    val godkjent_manuell_krav: String,
    val ikke_godkjent_for_refusjon: String,
    val godkjent_egenandel_refusjon: String,
    val godkjent_for_fil: String,
    val endringslogg_tidspunkt_siste: Date?,
    val samh_praksis: List<SamhandlerPraksis>
)

data class SamhandlerPraksis(
    val refusjon_type_kode: String,
    val laerer: String?,
    val lege_i_spesialisering: String?,
    val tidspunkt_resync_periode: Date?,
    val tidspunkt_registrert: Date?,
    val samh_praksis_status_kode: String,
    val telefonnr: String?,
    val arbeids_kommune_nr: String?,
    val arbeids_postnr: String?,
    val arbeids_adresse_linje_1: String?,
    val arbeids_adresse_linje_2: String?,
    val arbeids_adresse_linje_3: String?,
    val arbeids_adresse_linje_4: String?,
    val arbeids_adresse_linje_5: String?,
    val her_id: String?,
    val post_adresse_linje_1: String?,
    val post_adresse_linje_2: String?,
    val post_adresse_linje_3: String?,
    val post_adresse_linje_4: String?,
    val post_adresse_linje_5: String?,
    val post_kommune_nr: String?,
    val post_postnr: String?,
    val tss_ident: String,
    val navn: String?,
    val ident: String?,
    val samh_praksis_type_kode: String?,
    val samh_id: String,
    val samh_praksis_id: String,
    val samh_praksis_periode: List<SamhandlerPeriode>
)

data class SamhandlerPeriode(
    val slettet: String,
    val gyldig_fra: Date,
    val gyldig_til: Date?,
    val samh_praksis_id: String,
    val samh_praksis_periode_id: String
)

data class SamhandlerPraksisMatch(val samhandlerPraksis: SamhandlerPraksis, val percentageMatch: Double)

fun calculatePercentageStringMatch(str1: String?, str2: String): Double {
    val maxDistance = max(str1?.length!!, str2.length).toDouble()
    val distance = LevenshteinDistance().apply(str2, str1).toDouble()
    return (maxDistance - distance) / maxDistance
}

fun List<SamhandlerPeriode>.formaterPerioder() = joinToString(",", "periode(", ") ") { periode ->
    "${periode.gyldig_fra} -> ${periode.gyldig_til}"
}

fun List<Samhandler>.formaterPraksis() = flatMap { it.samh_praksis }
    .joinToString(",", "praksis(", ") ") { praksis ->
        "${praksis.navn}: ${praksis.samh_praksis_status_kode} ${praksis.samh_praksis_periode.formaterPerioder()}"
    }

fun findBestSamhandlerPraksis(
    samhandlere: List<Samhandler>,
    orgName: String,
    herId: String?,
    loggingMeta: LoggingMeta
): SamhandlerPraksisMatch? {
    val aktiveSamhandlere = samhandlere.flatMap { it.samh_praksis }
        .filter { praksis -> praksis.samh_praksis_status_kode == "aktiv" }
        .filter {
            it.samh_praksis_periode
                .filter { periode -> periode.gyldig_fra <= Date() }
                .filter { periode -> periode.gyldig_til == null || periode.gyldig_til >= Date() }
                .any()
        }
        .filter { !it.navn.isNullOrEmpty() }

    if (aktiveSamhandlere.isEmpty()) {
        log.info("Fant ingen aktive samhandlere. {}  Meta: {}, {} ",
            keyValue("praksis Informasjo", samhandlere.formaterPraksis()),
            keyValue("antall praksiser", samhandlere.size),
            StructuredArguments.fields(loggingMeta))
    }

    if (!herId.isNullOrEmpty() && aktiveSamhandlere.isNotEmpty()) {
        val samhandlerByHerId = aktiveSamhandlere.find {
            it.her_id == herId
        }
        if (samhandlerByHerId != null) {
            log.info("Fant samhandler basert på herid. herid: $herId, {}, {}",
                keyValue("praksis Informasjo", samhandlere.formaterPraksis()),
                StructuredArguments.fields(loggingMeta))
            return SamhandlerPraksisMatch(samhandlerByHerId, 100.0)
        }
    }

    return aktiveSamhandlere
        .map { samhandlerPraksis ->
            SamhandlerPraksisMatch(samhandlerPraksis, calculatePercentageStringMatch(samhandlerPraksis.navn, orgName) * 100)
        }.maxBy { it.percentageMatch }
}
