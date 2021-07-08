package no.nav.syfo.services

import com.ctc.wstx.exc.WstxException
import net.logstash.logback.argument.StructuredArguments
import no.nav.emottak.subscription.StartSubscriptionRequest
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender
import no.nav.syfo.client.SamhandlerPraksis
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.senderMarshaller
import java.io.ByteArrayOutputStream
import java.io.IOException

// This functionality is only necessary due to sending out dialogMelding and oppfølginsplan to doctor
suspend fun startSubscription(
    subscriptionEmottak: SubscriptionPort,
    samhandlerPraksis: SamhandlerPraksis,
    msgHead: XMLMsgHead,
    receiverBlock: XMLMottakenhetBlokk,
    loggingMeta: LoggingMeta
) {
    log.info("SamhandlerPraksis is found, name: ${samhandlerPraksis.navn},  {}", StructuredArguments.fields(loggingMeta))
    retry(
        callName = "start_subscription_emottak",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions = *arrayOf(IOException::class, WstxException::class)
    ) {
        subscriptionEmottak.startSubscription(
            StartSubscriptionRequest().apply {
                key = samhandlerPraksis.tss_ident
                data = convertSenderToBase64(msgHead.msgInfo.sender)
                partnerid = receiverBlock.partnerReferanse.toInt()
            }
        )
    }
}

fun samhandlerPraksisErLegevakt(samhandlerPraksis: SamhandlerPraksis): Boolean =
    !samhandlerPraksis.samh_praksis_type_kode.isNullOrEmpty() && (
        samhandlerPraksis.samh_praksis_type_kode == "LEVA" ||
            samhandlerPraksis.samh_praksis_type_kode == "LEKO"
        )

fun convertSenderToBase64(sender: XMLSender): ByteArray =
    ByteArrayOutputStream().use {
        senderMarshaller.marshal(sender, it)
        it
    }.toByteArray()
