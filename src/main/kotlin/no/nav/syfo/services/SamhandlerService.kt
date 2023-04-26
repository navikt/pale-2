package no.nav.syfo.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.log
import no.nav.syfo.metrics.IKKE_OPPDATERT_PARTNERREG
import no.nav.syfo.util.LoggingMeta

class SamhandlerService(
    private val smtssClient: SmtssClient,
    private val emottakSubscriptionClient: EmottakSubscriptionClient,
) {

    suspend fun findSamhandlerPraksisAndHandleEmottakSubscription(
        fnrLege: String,
        legekontorOrgName: String,
        msgHead: XMLMsgHead,
        receiverBlock: XMLMottakenhetBlokk,
        loggingMeta: LoggingMeta,
        legeerklaringId: String,
    ): String? {
        val tssIdEmottak = smtssClient.findBestTssIdEmottak(fnrLege, legekontorOrgName, loggingMeta, legeerklaringId)

        handleEmottakSubscription(
            tssIdEmottak,
            receiverBlock,
            msgHead,
            loggingMeta,
        )

        if (!tssIdEmottak.isNullOrEmpty()) {
            return tssIdEmottak
        }

        return smtssClient.findBestTssInfotrygdId(fnrLege, legekontorOrgName, loggingMeta, legeerklaringId)
    }

    suspend fun handleEmottakSubscription(
        tssIdEmottak: String?,
        receiverBlock: XMLMottakenhetBlokk,
        msgHead: XMLMsgHead,
        loggingMeta: LoggingMeta,
    ) {
        if (tssIdEmottak.isNullOrEmpty()) {
            log.info(
                "tssIdEmottak is null or empty, subscription_emottak is not created, {}",
                StructuredArguments.fields(loggingMeta),
            )
            IKKE_OPPDATERT_PARTNERREG.inc()
        } else {
            if (!receiverBlock.partnerReferanse.isNullOrEmpty() && receiverBlock.partnerReferanse.isNotBlank()) {
                emottakSubscriptionClient.startSubscription(
                    tssIdEmottak,
                    msgHead,
                    receiverBlock,
                    msgHead.msgInfo.msgId,
                    loggingMeta,
                )
            } else {
                log.info(
                    "PartnerReferanse is empty or blank, subscription_emottak is not created, {}",
                    StructuredArguments.fields(loggingMeta),
                )
                IKKE_OPPDATERT_PARTNERREG.inc()
            }
        }
    }
}
