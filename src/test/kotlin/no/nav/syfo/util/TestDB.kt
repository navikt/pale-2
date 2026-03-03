package no.nav.syfo.util

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import org.testcontainers.containers.PostgreSQLContainer

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:14")

class TestDB private constructor() {

    companion object {
        var database: DatabaseInterface
        private val psqlContainer: PsqlContainer =
            PsqlContainer()
                .withCommand("postgres", "-c", "wal_level=logical")
                .withExposedPorts(5432)
                .withUsername("username")
                .withPassword("password")
                .withDatabaseName("database")

        init {
            psqlContainer.start()
            val env = EnvironmentVariables(
        databaseUsername = psqlContainer.username,
        databasePassword = psqlContainer.password,
        dbHost = psqlContainer.host,
        dbPort = psqlContainer.firstMappedPort.toString(),
        dbName = psqlContainer.databaseName,
        cluster = "test",
        applicationPort = 0,
        applicationName = "test",
    )
            database = Database(env)
        }
    }
}
