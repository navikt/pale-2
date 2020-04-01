package no.nav.syfo.services

import com.ctc.wstx.exc.WstxException
import io.ktor.util.KtorExperimentalAPI
import java.io.IOException
import java.lang.IllegalStateException
import net.logstash.logback.argument.StructuredArguments
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

class FindNAVKontorService @KtorExperimentalAPI constructor(
    val pasientFNR: String,
    val personV3: PersonV3,
    val norg2Client: Norg2Client,
    val loggingMeta: LoggingMeta
) {

    @KtorExperimentalAPI
    suspend fun finnLokaltNavkontor(): String {
        val geografiskTilknytning = fetchGeografiskTilknytningAsync(personV3, pasientFNR)
        val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, pasientFNR)

        return if (geografiskTilknytning == null || geografiskTilknytning.geografiskTilknytning?.geografiskTilknytning.isNullOrEmpty()) {
            log.warn(
                "GeografiskTilknytning er tomt eller null, benytter nav oppfolings utland nr:$NAV_OPPFOLGING_UTLAND_KONTOR_NR,  {}",
                StructuredArguments.fields(loggingMeta)
            )
            NAV_OPPFOLGING_UTLAND_KONTOR_NR
        } else {
            norg2Client.getLocalNAVOffice(
                geografiskTilknytning.geografiskTilknytning.geografiskTilknytning,
                patientDiskresjonsKode
            ).enhetNr
        }
    }

    suspend fun fetchGeografiskTilknytningAsync(
        personV3: PersonV3,
        personFNR: String
    ): HentGeografiskTilknytningResponse? =
        try {
            retry(
                callName = "tps_hent_geografisktilknytning",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
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
            null
        }
}
