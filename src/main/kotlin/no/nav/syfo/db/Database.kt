package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.pool.HikariPool
import java.net.ConnectException
import java.net.SocketException
import java.sql.Connection
import java.sql.ResultSet
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.log
import org.flywaydb.core.Flyway

class Database(private val env: EnvironmentVariables, retries: Long = 30, sleepTime: Long = 1_000) :
    DatabaseInterface {
    private val dataSource: HikariDataSource

    override val connection: Connection
        get() = dataSource.connection

    init {
        var current = 0
        var connected = false
        var tempDatasource: HikariDataSource? = null
        while (!connected && current++ < retries) {
            log.info("trying to connet to db current try $current")
            try {
                tempDatasource =
                    HikariDataSource(
                        HikariConfig().apply {
                            jdbcUrl = "jdbc:postgresql://${env.dbHost}:${env.dbPort}/${env.dbName}"
                            username = env.databaseUsername
                            password = env.databasePassword
                            maximumPoolSize = 10
                            minimumIdle = 3
                            idleTimeout = 10000
                            maxLifetime = 300000
                            isAutoCommit = false
                            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                            validate()
                        },
                    )
                connected = true
            } catch (ex: HikariPool.PoolInitializationException) {
                if (ex.cause?.cause is ConnectException || ex.cause?.cause is SocketException) {
                    log.info("Could not connect to db")
                    Thread.sleep(sleepTime)
                } else {
                    throw ex
                }
            }
        }
        if (tempDatasource == null) {
            log.error("Could not connect to DB")
            throw RuntimeException("Could not connect to DB")
        }
        dataSource = tempDatasource

        runFlywayMigrations()
    }

    private fun runFlywayMigrations() =
        Flyway.configure().run {
            locations("db")
            dataSource(
                "jdbc:postgresql://${env.dbHost}:${env.dbPort}/${env.dbName}",
                env.databaseUsername,
                env.databasePassword,
            )
            load().migrate()
        }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) =
    mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }
