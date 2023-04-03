package no.nav.syfo.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.Samhandler
import no.nav.syfo.client.SamhandlerPraksisMatch
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.client.findBestSamhandlerPraksisEmottak
import no.nav.syfo.client.samhandlerpraksisIsLegevakt
import no.nav.syfo.log
import no.nav.syfo.metrics.IKKE_OPPDATERT_PARTNERREG
import no.nav.syfo.objectMapper
import no.nav.syfo.secureLog
import no.nav.syfo.util.LoggingMeta

class SamhandlerService(
    private val kuhrSarClient: SarClient,
    private val emottakSubscriptionClient: EmottakSubscriptionClient,
) {

    suspend fun findSamhandlerPraksisAndHandleEmottakSubscription(
        fnrLege: String,
        legekontorOrgName: String,
        legekontorOrgNumber: String?,
        legekontorHerId: String?,
        msgHead: XMLMsgHead,
        receiverBlock: XMLMottakenhetBlokk,
        loggingMeta: LoggingMeta,
    ): SamhandlerPraksisMatch? {
        val samhandlerInfo = kuhrSarClient.getSamhandler(fnrLege, msgHead.msgInfo.msgId)

        secureLog.info("samhandlerInfo: ${objectMapper.writeValueAsString(samhandlerInfo)} {}", StructuredArguments.fields(loggingMeta))

        handleEmottakSubscription(
            samhandlerInfo,
            legekontorOrgNumber,
            legekontorHerId,
            receiverBlock,
            msgHead,
            loggingMeta,
        )

        return findBestSamhandlerPraksis(
            samhandlerInfo,
            legekontorOrgName,
            legekontorHerId,
            legekontorOrgNumber,
            loggingMeta,
        )
    }

    suspend fun handleEmottakSubscription(
        samhandlerInfo: List<Samhandler>,
        legekontorOrgNumber: String?,
        legekontorHerId: String?,
        receiverBlock: XMLMottakenhetBlokk,
        msgHead: XMLMsgHead,
        loggingMeta: LoggingMeta,
    ) {
        val samhandlerPraksisMatchEmottak = findBestSamhandlerPraksisEmottak(
            samhandlerInfo,
            legekontorOrgNumber,
            legekontorHerId,
            loggingMeta,
        )

        if (samhandlerPraksisMatchEmottak?.percentageMatch != null && samhandlerPraksisMatchEmottak.percentageMatch == 999.0) {
            log.info(
                "SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created, {}",
                StructuredArguments.fields(loggingMeta),
            )
            IKKE_OPPDATERT_PARTNERREG.inc()
        } else {
            when (samhandlerPraksisMatchEmottak?.samhandlerPraksis) {
                null -> {
                    log.info("SamhandlerPraksis is Not found, subscription_emottak is not created {}", StructuredArguments.fields(loggingMeta))
                    IKKE_OPPDATERT_PARTNERREG.inc()
                }

                else -> if (!samhandlerpraksisIsLegevakt(samhandlerPraksisMatchEmottak.samhandlerPraksis) &&
                    !receiverBlock.partnerReferanse.isNullOrEmpty() &&
                    receiverBlock.partnerReferanse.isNotBlank()
                ) {
                    emottakSubscriptionClient.startSubscription(
                        samhandlerPraksisMatchEmottak.samhandlerPraksis,
                        msgHead,
                        receiverBlock,
                        msgHead.msgInfo.msgId,
                        loggingMeta,
                    )
                } else {
                    if (!receiverBlock.partnerReferanse.isNullOrEmpty() &&
                        receiverBlock.partnerReferanse.isNotBlank()
                    ) {
                        log.info(
                            "PartnerReferanse is empty or blank, subscription_emottak is not created, {}",
                            StructuredArguments.fields(loggingMeta),
                        )
                    } else {
                        log.info(
                            "SamhandlerPraksis is Legevakt, subscription_emottak is not created, {}",
                            StructuredArguments.fields(loggingMeta),
                        )
                    }
                    IKKE_OPPDATERT_PARTNERREG.inc()
                }
            }
        }
    }
}
