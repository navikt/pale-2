package no.nav.syfo.services.journalpoststatus

import jakarta.jms.Session
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.syfo.ApplicationState
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.services.arenaservice.ArenaService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

class JournalfoeringHendelseConsumerService(
    private val applicationState: ApplicationState,
    private val env: EnvironmentVariables,
    private val kafkaConsumer: KafkaConsumer<String, JournalfoeringHendelseRecord>,
    private val journalpostStatusService: JournalpostStatusService,
) {
    private val log = LoggerFactory.getLogger(JournalfoeringHendelseConsumerService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val mutex = Mutex()

    suspend fun stop() = mutex.withLock {
        job?.cancelAndJoin()
        job = null
    }

    suspend fun start() = mutex.withLock {
        if (job != null) {
            log.info("journalføring consumer is already running")
            return@withLock
        }
        job =
            scope.launch {
                try {
                    connectionFactory(env)
                        .createConnection(env.mqUser.username, env.mqUser.password)
                        .use { connection ->
                            connection.start()
                            val session =
                                connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
                            val arenaProducer = session.producerForQueue(env.arenaQueueName)
                            val arenaService = ArenaService(session, arenaProducer)

                            kafkaConsumer.subscribe(listOf(env.journalfoeringHendelseTopic))
                            runPollLoop(arenaService)
                        }
                } catch (ex: CancellationException) {
                    log.info("Journalføring consumer stoppet: ${ex.message}")
                    throw ex
                } catch (ex: Exception) {
                    log.error(
                        "En uhåndtert feil oppstod i journalføring consumer, applikasjonen restarter",
                        ex,
                    )
                    applicationState.alive = false
                    applicationState.ready = false
                } finally {
                    kafkaConsumer.close()
                }
            }
    }

    private suspend fun runPollLoop(arenaService: ArenaService) = coroutineScope {
        while (applicationState.ready && isActive) {
            val records = kafkaConsumer.poll(Duration.ofMillis(1000))
            records.forEach { record ->
                journalpostStatusService.handleJournalfoeringHendelse(record.value(), arenaService)
            }
            if (!records.isEmpty) {
                kafkaConsumer.commitSync()
            }
        }
    }
}
