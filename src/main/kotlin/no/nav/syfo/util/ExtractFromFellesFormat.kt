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
): String? {
    val phoneNumber =
        healthcareProfessional
            ?.teleCom
            ?.find {
                it.teleAddress?.v?.contains("tel:") == true &&
                    (it?.typeTelecom
                        ?.v
                        ?.contains(
                            "HP",
                        ) == true || it?.typeTelecom?.dn?.contains("Hovedtelefon") == true)
            }
            ?.teleAddress
            ?.v
            ?.removePrefix("tel:")

    val email =
        healthcareProfessional
            ?.teleCom
            ?.find { it.teleAddress?.v?.contains("mailto:") == true }
            ?.teleAddress
            ?.v
            ?.removePrefix("mailto:")

    return if (phoneNumber != null) {
        phoneNumber
    } else if (email != null) {
        email
    } else {
        healthcareProfessional?.teleCom?.firstOrNull()?.teleAddress?.v
    }
}
