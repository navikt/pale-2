package no.nav.syfo.kafka.vedlegg.model

data class Vedlegg(
    val content: Content,
    val type: String,
    val description: String
)

data class VedleggKafkaMessage(
    val vedlegg: Vedlegg,
    val behandler: BehandlerInfo,
    val pasientAktorId: String,
    val msgId: String,
    val pasientFnr: String,
    val source: String = "pale-2"
)

data class Content(val contentType: String, val content: String)

data class BehandlerInfo(val fornavn: String?, val etternavn: String?, val fnr: String?)
