package com.rdr.roast.driver

import com.rdr.roast.domain.TemperatureSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val deviceName: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

interface RoastDataSource {
    fun connect()
    fun disconnect()
    fun sampleFlow(): Flow<TemperatureSample>
    fun connectionState(): StateFlow<ConnectionState>
}
