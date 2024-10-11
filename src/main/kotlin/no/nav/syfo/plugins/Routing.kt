package no.nav.syfo.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.nais.isalive.naisIsAliveRoute
import no.nav.syfo.nais.isready.naisIsReadyRoute
import no.nav.syfo.nais.prometheus.naisPrometheusRoute

fun Application.configureRouting(applicationState: ApplicationState) {
    routing {
        naisIsAliveRoute(applicationState)
        naisIsReadyRoute(applicationState)
        naisPrometheusRoute()
    }
}
