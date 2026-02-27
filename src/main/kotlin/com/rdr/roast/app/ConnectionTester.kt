package com.rdr.roast.app

import com.rdr.roast.driver.ConnectionState
import com.rdr.roast.driver.RoastDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tests connection to a roaster using the given [MachineConfig] without persisting settings.
 * Creates a temporary data source, connects, waits for Connected or Error (with timeout), then disconnects.
 */
object ConnectionTester {

    private const val TIMEOUT_MS = 5000L
    private const val POLL_INTERVAL_MS = 50L

    /**
     * Runs a one-off connection test. Call from a background dispatcher.
     * @return [Result.success] with [ConnectionState.Connected] on success,
     *         [Result.failure] with error message on failure or timeout.
     */
    suspend fun test(config: MachineConfig): Result<ConnectionState> {
        val source: RoastDataSource = DataSourceFactory.create(config)
        try {
            source.connect()
            val state = withTimeoutOrNull(TIMEOUT_MS) {
                while (true) {
                    val current = source.connectionState().first()
                    when (current) {
                        is ConnectionState.Connected -> return@withTimeoutOrNull current
                        is ConnectionState.Error -> return@withTimeoutOrNull current
                        else -> delay(POLL_INTERVAL_MS)
                    }
                }
            }
            return when (val s = state) {
                is ConnectionState.Connected -> Result.success(s)
                is ConnectionState.Error -> Result.failure(Exception(s.message))
                else -> Result.failure(Exception("Таймаут подключения (${TIMEOUT_MS / 1000} сек)"))
            }
        } finally {
            source.disconnect()
        }
    }
}
