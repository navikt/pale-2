package no.nav.syfo.util

import io.mockk.every
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.Environment
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import java.sql.Connection

class TestDB private constructor() {

    companion object {
        var database: DatabaseInterface
        private val embeddedPostgres = EmbeddedPostgres.builder().start()
        private val postgresConnection = embeddedPostgres.postgresDatabase.connection

        init {
            val mockEnv = mockk<Environment>(relaxed = true)
            every { mockEnv.dbPort } returns embeddedPostgres.port.toString()
            every { mockEnv.databaseUsername } returns "postgres"
            every { mockEnv.databasePassword } returns "password"
            every { mockEnv.dbName } returns "postgres"
            database = Database(mockEnv)
        }

        fun stop() {
            postgresConnection.close()
            embeddedPostgres.close()
            database.connection.dropData()
        }
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM duplikatsjekk").executeUpdate()
        connection.commit()
    }
}
