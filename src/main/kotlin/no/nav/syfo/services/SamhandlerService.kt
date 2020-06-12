package no.nav.syfo.services

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class SamhandlerService(
    private val kuhrSarClient: SarClient,
    private val subscriptionEmottak: SubscriptionPort
) {
    suspend fun finnTssIdentOgStartSubscription(
        fnrLege: String,
        legekontorOrgName: String,
        legekontorHerId: String?,
        receiverBlock: XMLMottakenhetBlokk,
        msgHead: XMLMsgHead,
        loggingMeta: LoggingMeta
    ): String {
        val samhandlerInfo = kuhrSarClient.getSamhandler(fnrLege)
        val samhandlerPraksisMatch = findBestSamhandlerPraksis(
            samhandlerInfo,
            legekontorOrgName,
            legekontorHerId,
            loggingMeta
        )

        val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

        if (samhandlerPraksisMatch?.percentageMatch != null && samhandlerPraksisMatch.percentageMatch == 999.0) {
            log.info(
                "SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created, {}",
                StructuredArguments.fields(loggingMeta)
            )
        } else {
            when (samhandlerPraksis) {
                null -> log.info(
                    "SamhandlerPraksis is Not found, {}",
                    StructuredArguments.fields(loggingMeta)
                )
                else -> if (!samhandlerPraksisErLegevakt(samhandlerPraksis) &&
                    !receiverBlock.partnerReferanse.isNullOrEmpty() &&
                    receiverBlock.partnerReferanse.isNotBlank()
                ) {
                    startSubscription(
                        subscriptionEmottak,
                        samhandlerPraksis,
                        msgHead,
                        receiverBlock,
                        loggingMeta
                    )
                } else {
                    log.info(
                        "SamhandlerPraksis is Legevakt or partnerReferanse is empty or blank, subscription_emottak is not created, {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                }
            }
        }
        return samhandlerPraksis?.tss_ident ?: ""
    }
}
