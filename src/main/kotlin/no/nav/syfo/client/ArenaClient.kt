package no.nav.syfo.client

import no.nav.helse.arenainfo.ArenaEiaInfo
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.syfo.PaleConstant
import no.nav.syfo.extractLegeerklaering
import no.nav.syfo.formatName
import java.math.BigInteger

fun createArenaInfo(
    fellesformat: XMLEIFellesformat,
    tssId: String?,
    sperrekode: Int? = null,
    navkontor: String?,
    mottakid: String,
    healthcareProfessional: XMLHealthcareProfessional,
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
        if (sperrekode != null && (sperrekode == 6 || sperrekode == 7)) {
            spesreg = sperrekode
        }
    }
    legeData = ArenaEiaInfo.LegeData().apply {
        navn = hcp?.formatName() ?: ""
        fnr = fnrbehandler
        tssid = tssId
    }
}

fun findMappeTypeInLegeerklaering(typeLegeerklaring: BigInteger): String =
    when (typeLegeerklaring) {
        4.toBigInteger() -> PaleConstant.mappetypeUP.string
        3.toBigInteger() -> PaleConstant.mappetypeYA.string
        2.toBigInteger() -> PaleConstant.mappetypeRP.string
        else -> { PaleConstant.mappetypeSP.string }
    }