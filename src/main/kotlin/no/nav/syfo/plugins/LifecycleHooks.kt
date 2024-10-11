package no.nav.syfo.plugins

import io.ktor.server.application.*
import no.nav.syfo.ApplicationState

fun Application.configureLifecycleHooks(applicationState: ApplicationState) {

    this.monitor.subscribe(ApplicationStarted) { applicationState.ready = true }
    this.monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
        applicationState.alive = false
    }
}
