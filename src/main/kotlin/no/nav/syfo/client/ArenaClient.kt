package no.nav.syfo.client

import java.math.BigInteger
import no.nav.helse.arenainfo.ArenaEiaInfo
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.syfo.formatName
import no.nav.syfo.model.PaleConstant
import no.nav.syfo.util.extractLegeerklaering

fun createArenaInfo(
    fellesformat: XMLEIFellesformat,
    tssId: String?,
    navkontor: String?,
    mottakid: String,
    healthcareProfessional: XMLHealthcareProfessional?,
    fnrbehandler: String
): ArenaEiaInfo = ArenaEiaInfo().apply {
    val legeerklaering = extractLegeerklaering(fellesformat)
    val hcp = healthcareProfessional
    ediloggId = mottakid
    hendelseStatus = PaleConstant.tilvurdering.string
    version = PaleConstant.versjon2_0.string
    skjemaType = "LE"
    mappeType = findMappeTypeInLegeerklaering(legeerklaering.legeerklaringGjelder.first().typeLegeerklaring)
    pasientData = ArenaEiaInfo.PasientData().apply {
        fnr = legeerklaering.pasientopplysninger.pasient.fodselsnummer
        isSperret = when (legeerklaering.forbeholdLegeerklaring?.tilbakeholdInnhold?.toInt()) {
            2 -> true
            else -> false
        }
        tkNummer = navkontor
    }
    legeData = ArenaEiaInfo.LegeData().apply {
        navn = hcp?.formatName() ?: ""
        fnr = fnrbehandler
        tssid = tssId
    }
    eiaData = ArenaEiaInfo.EiaData().apply {
        systemSvar.add(
            ArenaEiaInfo.EiaData.SystemSvar().apply {
                meldingsPrioritet = 4.toBigInteger()
                meldingsNr = 245.toBigInteger()
                meldingsTekst = "Legeerklæring er mottatt."
                meldingsType = "3"
            }
        )
    }
}

fun findMappeTypeInLegeerklaering(typeLegeerklaring: BigInteger): String =
    when (typeLegeerklaring) {
        4.toBigInteger() -> PaleConstant.mappetypeUP.string
        3.toBigInteger() -> PaleConstant.mappetypeYA.string
        2.toBigInteger() -> PaleConstant.mappetypeRP.string
        else -> { PaleConstant.mappetypeSP.string }
    }
