package com.rdr.roast.driver

import com.rdr.roast.domain.TemperatureSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Data source that never connects and holds an error message.
 * Used when the requested driver (e.g. Phidget 1048) is not available.
 */
class ErrorDataSource(private val message: String) : RoastDataSource {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Error(message))
    override fun connectionState(): StateFlow<ConnectionState> = _connectionState

    override fun connect() {
        _connectionState.value = ConnectionState.Error(message)
    }

    override fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun sampleFlow(): Flow<TemperatureSample> = emptyFlow()
}
