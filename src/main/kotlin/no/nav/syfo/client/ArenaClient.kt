package no.nav.syfo.client

import no.nav.helse.arenainfo.ArenaEiaInfo
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.PaleConstant
import no.nav.syfo.model.Pasient

fun createArenaInfo(
    tssId: String?,
    navkontor: String?,
    mottakid: String,
    fnrbehandler: String,
    legeerklaering: Legeerklaering
): ArenaEiaInfo = ArenaEiaInfo().apply {
    ediloggId = mottakid
    hendelseStatus = PaleConstant.tilvurdering.string
    version = PaleConstant.versjon2_0.string
    skjemaType = "LE"
    mappeType = findMappeTypeInLegeerklaering(legeerklaering)
    pasientData = ArenaEiaInfo.PasientData().apply {
        fnr = legeerklaering.pasient.fnr
        tkNummer = navkontor
    }
    legeData = ArenaEiaInfo.LegeData().apply {
        navn = legeerklaering.pasient.formatName()
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

fun Pasient.formatName(): String =
    if (mellomnavn == null) {
        "$etternavn $fornavn"
    } else {
        "$etternavn $fornavn $mellomnavn"
    }

fun findMappeTypeInLegeerklaering(legeerklaering: Legeerklaering): String =
    if (legeerklaering.uforepensjon) {
        PaleConstant.mappetypeUP.string
    } else if (legeerklaering.yrkesrettetAttforing) {
        PaleConstant.mappetypeYA.string
    } else if (legeerklaering.arbeidsavklaringspenger) {
        PaleConstant.mappetypeRP.string
    } else {
        PaleConstant.mappetypeSP.string
    }
