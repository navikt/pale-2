package no.nav.syfo.db

import java.sql.Connection

interface DatabaseInterface {
    val connection: Connection
}
