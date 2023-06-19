package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Summary

const val METRICS_NS = "pale2"

val REQUEST_TIME: Summary =
    Summary.build()
        .namespace(METRICS_NS)
        .name("request_time_ms")
        .help("Request time in milliseconds.")
        .register()

val INCOMING_MESSAGE_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("incoming_message_count")
        .help("Counts the number of incoming messages")
        .register()

val APPREC_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("apprec_count")
        .help("Counts the number of apprec messages")
        .register()

val INVALID_MESSAGE_NO_NOTICE: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("invalid_message_no_notice_count")
        .help(
            "Counts the number of messages, that has not enough information to be sendt to the rule engine "
        )
        .register()

val FOR_MANGE_TEGN: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("for_mange_tegn_count")
        .help("Antall meldinger som har for mange tegn i fritekstfelt")
        .register()

val TEST_FNR_IN_PROD: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("test_fnr_in_prod")
        .help("Counts the number of messages that contains a test fnr i prod")
        .register()

val MELDING_FEILET: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("melding_feilet_count")
        .help("Antall meldinger som er sendt til feilkø")
        .register()

val VEDLEGG_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("legeerkleringer_med_vedlegg")
        .help("Antall legeerkleringer som inneholder vedlegg")
        .register()

val VEDLEGG_VIRUS_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("legeerkleringer_may_contain_virus_in_vedlegg")
        .help("Number of legeerkleringer that may contain virus in vedlegg")
        .register()

val VEDLEGG_OVER_300_MEGABYTE_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("legeerkleringer_over_300_megabyte_vedlegg")
        .help("Number of vedlegg that is over 300 megabyte")
        .register()

val IKKE_OPPDATERT_PARTNERREG: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("ikke_oppdatert_partnerreg")
        .help("Antall Legærkleringer der vi ikke oppdaterte partnerregister")
        .register()

val DUPLICATE_LEGEERKLERING: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("duplicate_legeerkleringe")
        .help("Antall Legærkleringer som er like")
        .register()
