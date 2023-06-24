package no.nav.syfo.client.emottaksubscription

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.ByteArrayOutputStream
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender
import no.nav.syfo.client.accesstoken.AccessTokenClientV2
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.senderMarshaller

class EmottakSubscriptionClient(
    private val endpointUrl: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient,
) {
    // This functionality is only necessary due to sending out dialogMelding and oppfølgingsplan to
    // doctor
    suspend fun startSubscription(
        tssIdent: String,
        msgHead: XMLMsgHead,
        receiverBlock: XMLMottakenhetBlokk,
        msgId: String,
        loggingMeta: LoggingMeta,
    ) {
        log.info("Oppdate subscription emottak for {}", StructuredArguments.fields(loggingMeta))

        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        httpClient.post("$endpointUrl/emottak/startsubscription") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $accessToken")
            header("Nav-Call-Id", msgId)
            setBody(
                StartSubscriptionRequest(
                    tssIdent = tssIdent,
                    sender = convertSenderToBase64(msgHead.msgInfo.sender),
                    partnerreferanse = receiverBlock.partnerReferanse.toInt(),
                ),
            )
        }
    }

    private fun convertSenderToBase64(sender: XMLSender): ByteArray =
        ByteArrayOutputStream()
            .use {
                senderMarshaller.marshal(sender, it)
                it
            }
            .toByteArray()
}

data class StartSubscriptionRequest(
    val tssIdent: String,
    val sender: ByteArray,
    val partnerreferanse: Int,
)
