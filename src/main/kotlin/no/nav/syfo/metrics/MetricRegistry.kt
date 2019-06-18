package no.nav.syfo.metrics

import io.prometheus.client.Counter

const val METRICS_NS = "pale_2"

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