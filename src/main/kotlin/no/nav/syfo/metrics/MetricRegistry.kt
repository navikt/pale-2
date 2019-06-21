package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Summary

const val METRICS_NS = "pale_2"

val REQUEST_TIME: Summary = Summary.build()
        .namespace(METRICS_NS)
        .name("request_time_ms")
        .help("Request time in milliseconds.").register()

val INCOMING_MESSAGE_COUNTER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("incoming_message_count")
        .help("Counts the number of incoming messages")
        .register()

val APPREC_COUNTER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("apprec_count")
        .help("Counts the number of apprec messages")
        .register()

val RULE_HIT_COUNTER: Counter = Counter.Builder()
        .namespace(METRICS_NS)
        .name("rule_hit_counter")
        .labelNames("rule_name")
        .help("Registers a counter for each rule in the rule set")
        .register()

val INVALID_MESSAGE_NO_NOTICE: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("invalid_message_no_notice_count")
        .help("Counts the number of messages, that has not enough information to be sendt to the rule engine ")
        .register()