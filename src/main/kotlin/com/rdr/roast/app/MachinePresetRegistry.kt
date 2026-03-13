package com.rdr.roast.app

import java.util.concurrent.ConcurrentHashMap

data class MachinePresetEntry(
    val brand: String,
    val model: String,
    val resourcePath: String
)

object MachinePresetRegistry {

    private val index: Map<String, List<MachinePresetEntry>> by lazy { loadIndex() }
    private val cache = ConcurrentHashMap<String, AsetPreset>()

    fun brands(): List<String> = index.keys.sorted()

    fun models(brand: String): List<MachinePresetEntry> =
        index[brand]?.sortedBy { it.model } ?: emptyList()

    fun allEntries(): List<MachinePresetEntry> =
        index.values.flatten().sortedWith(compareBy({ it.brand }, { it.model }))

    fun getPreset(entry: MachinePresetEntry): AsetPreset =
        cache.getOrPut(entry.resourcePath) { loadAndParse(entry.resourcePath) }

    fun getPreset(brand: String, model: String): AsetPreset? {
        val entry = models(brand).find { it.model == model } ?: return null
        return getPreset(entry)
    }

    fun loadIndex(): Map<String, List<MachinePresetEntry>> {
        val stream = javaClass.getResourceAsStream("/com/rdr/roast/machines-index.txt")
            ?: return emptyMap()
        val entries = mutableListOf<MachinePresetEntry>()
        stream.bufferedReader().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val entry = pathToEntry(trimmed) ?: continue
                entries.add(entry)
            }
        }
        return entries.groupBy { it.brand }
    }

    private fun pathToEntry(resourcePath: String): MachinePresetEntry? {
        val prefix = "com/rdr/roast/machines/"
        val relative = if (resourcePath.startsWith(prefix)) resourcePath.removePrefix(prefix) else resourcePath
        val slash = relative.lastIndexOf('/')
        if (slash < 0) return null
        val brand = relative.substring(0, slash)
        val filename = relative.substring(slash + 1)
        if (!filename.endsWith(".aset")) return null
        val model = filename.removeSuffix(".aset").replace('_', ' ')
        return MachinePresetEntry(brand, model, resourcePath)
    }

    private fun loadAndParse(resourcePath: String): AsetPreset {
        val stream = javaClass.getResourceAsStream("/$resourcePath")
            ?: throw IllegalStateException("Machine preset not found: $resourcePath")
        return stream.use { AsetParser.parse(it) }
    }
}
