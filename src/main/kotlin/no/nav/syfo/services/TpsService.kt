package no.nav.syfo.services

import com.ctc.wstx.exc.WstxException
import java.io.IOException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest

suspend fun fetchDiskresjonsKode(personV3: PersonV3, fnr: String, loggingMeta: LoggingMeta): String? =
    try {
        retry(callName = "tps_hent_person",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
            legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            personV3.hentPerson(HentPersonRequest()
                .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(fnr)))
            ).person?.diskresjonskode?.value
        }
    } catch (hentPersonPersonIkkeFunnet: HentPersonPersonIkkeFunnet) {
        log.error("Fant ikke diskresjonskode for bruker, {}", StructuredArguments.fields(loggingMeta))
        null
    }
