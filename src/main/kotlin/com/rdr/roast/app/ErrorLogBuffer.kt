package com.rdr.roast.app

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections

/**
 * In-memory buffer of recent error/warning log lines for the Support drawer.
 * Append from catch blocks or logging; optional file writing can be added later.
 */
object ErrorLogBuffer {

    private const val MAX_LINES = 500
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    private val lines = Collections.synchronizedList(mutableListOf<String>())
    private val observableLines: ObservableList<String> = FXCollections.synchronizedObservableList(FXCollections.observableArrayList<String>())

    /** Append a line (timestamped). Call from any thread; updates observable on JavaFX thread. */
    fun append(message: String) {
        val stamped = "${formatter.format(Instant.now())}  $message"
        synchronized(lines) {
            lines.add(stamped)
            while (lines.size > MAX_LINES) lines.removeAt(0)
        }
        Platform.runLater {
            observableLines.add(stamped)
            while (observableLines.size > MAX_LINES) observableLines.removeAt(0)
        }
    }

    /** Append from an exception (message + stack trace first line). */
    fun append(th: Throwable, context: String? = null) {
        val msg = buildString {
            if (context != null) append(context).append(": ")
            append(th.message ?: th.toString())
            th.stackTrace.getOrNull(0)?.let { append("  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})") }
        }
        append(msg)
    }

    /** Current lines (snapshot). */
    fun getLines(): List<String> = synchronized(lines) { lines.toList() }

    /** Observable list for binding to UI (updated on JavaFX thread). */
    fun getObservableLines(): ObservableList<String> = observableLines

    /** Clear buffer. */
    fun clear() {
        synchronized(lines) { lines.clear() }
        Platform.runLater { observableLines.clear() }
    }
}
