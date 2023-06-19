package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.legeerklaering.Legeerklaring
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find { it.typeId.v == "ENH" }

fun extractOrganisationHerNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find { it.typeId.v == "HER" }

fun extractOrganisationRashNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find { it.typeId.v == "RSH" }

fun extractLegeerklaering(fellesformat: XMLEIFellesformat): Legeerklaring =
    fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as Legeerklaring

fun extractSenderOrganisationName(fellesformat: XMLEIFellesformat): String =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation?.organisationName ?: ""

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractPersonIdent(legeerklaering: Legeerklaring): String? =
    legeerklaering.pasientopplysninger.pasient.fodselsnummer

fun extractTlfFromHealthcareProfessional(
    healthcareProfessional: XMLHealthcareProfessional?
): String? =
    if (
        healthcareProfessional != null &&
            healthcareProfessional.teleCom?.size != 0 &&
            healthcareProfessional.teleCom?.firstOrNull()!!.teleAddress != null &&
            healthcareProfessional.teleCom.firstOrNull()!!.teleAddress?.v?.contains("tel:") == true
    ) {
        healthcareProfessional.teleCom.firstOrNull()!!.teleAddress?.v?.removePrefix("tel:")
    } else {
        healthcareProfessional?.teleCom?.firstOrNull()?.teleAddress?.v
    }
