package com.rdr.roast.app

import com.rdr.roast.domain.TemperatureSample
import com.rdr.roast.driver.RoastDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Centralized sampling collector used by recorder modes (RECORDING/BBP).
 */
class SamplingEngine(
    private val dataSource: RoastDataSource
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    fun start(onSample: (TemperatureSample) -> Unit) {
        stop()
        job = scope.launch {
            dataSource.sampleFlow().collect { onSample(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

