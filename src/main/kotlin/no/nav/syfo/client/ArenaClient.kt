package no.nav.syfo.client

import no.nav.helse.arenainfo.ArenaEiaInfo
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.PaleConstant
import no.nav.syfo.model.Pasient

fun createArenaInfo(
    tssId: String?,
    mottakid: String,
    fnrbehandler: String,
    legeerklaering: Legeerklaering,
): ArenaEiaInfo = ArenaEiaInfo().apply {
    ediloggId = mottakid
    hendelseStatus = PaleConstant.TILVURDERING.description
    version = PaleConstant.VERSJON2.description
    skjemaType = "LE"
    mappeType = findMappeTypeInLegeerklaering(legeerklaering)
    pasientData = ArenaEiaInfo.PasientData().apply {
        fnr = legeerklaering.pasient.fnr
        tkNummer = ""
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
            },
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
    when {
        legeerklaering.uforepensjon -> {
            PaleConstant.MAPPRETYPEUP.description
        }
        legeerklaering.yrkesrettetAttforing -> {
            PaleConstant.MAPPRETYPEYA.description
        }
        legeerklaering.arbeidsavklaringspenger -> {
            PaleConstant.MAPPRETYPERP.description
        }
        else -> {
            PaleConstant.MAPPRETYPESP.description
        }
    }
