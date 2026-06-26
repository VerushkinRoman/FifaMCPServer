package ru.mcpserver.pipeline

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PipelineTools {
    fun saveRaw(text: String, extension: String = "txt"): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val dir = File("data")
        dir.mkdirs()
        val file = File(dir, "data_$timestamp.$extension")
        file.writeText(text)
        return file.absolutePath
    }
}
