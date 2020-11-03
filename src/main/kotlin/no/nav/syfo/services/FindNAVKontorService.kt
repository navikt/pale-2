package no.nav.syfo.services

import com.ctc.wstx.exc.WstxException
import io.ktor.util.KtorExperimentalAPI
import java.io.IOException
import java.lang.IllegalStateException
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.client.Norg2Client
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.virksomhet.person.v3.binding.HentGeografiskTilknytningPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse

@KtorExperimentalAPI
class FindNAVKontorService(
    private val personV3: PersonV3,
    private val norg2Client: Norg2Client
) {

    suspend fun finnLokaltNavkontor(pasientFNR: String, diskresjonskode: String?, loggingMeta: LoggingMeta): String {
        val geografiskTilknytning = fetchGeografiskTilknytningAsync(pasientFNR, loggingMeta)

        return if (geografiskTilknytning == null || geografiskTilknytning.geografiskTilknytning?.geografiskTilknytning.isNullOrEmpty()) {
            log.warn(
                "GeografiskTilknytning er tomt eller null, benytter NAV Utland:$NAV_OPPFOLGING_UTLAND_KONTOR_NR, {}",
                fields(loggingMeta)
            )
            NAV_OPPFOLGING_UTLAND_KONTOR_NR
        } else {
            norg2Client.getLocalNAVOffice(
                geografiskTilknytning.geografiskTilknytning.geografiskTilknytning,
                diskresjonskode
            ).enhetNr
        }
    }

    private suspend fun fetchGeografiskTilknytningAsync(
        personFNR: String,
        loggingMeta: LoggingMeta
    ): HentGeografiskTilknytningResponse? =
        try {
            retry(
                callName = "tps_hent_geografisktilknytning",
                retryIntervals = arrayOf(500L, 1000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class, IllegalStateException::class)
            ) {
                personV3.hentGeografiskTilknytning(
                    HentGeografiskTilknytningRequest().withAktoer(
                        PersonIdent().withIdent(
                            NorskIdent()
                                .withIdent(personFNR)
                                .withType(Personidenter().withValue("FNR"))
                        )
                    )
                )
            }
        } catch (hentGeografiskTilknytningPersonIkkeFunnet: HentGeografiskTilknytningPersonIkkeFunnet) {
            log.error("Fant ikke geografisk tilknytning for bruker, {}", fields(loggingMeta))
            null
        }
}
