package com.piyushos.app.ai.files

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Shared helpers for OOXML (PPTX/XLSX) generation - pure JVM, no Android deps. */
object Ooxml {

    fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun zip(entries: Map<String, ByteArray>, out: File) {
        ZipOutputStream(out.outputStream()).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    fun safeName(s: String?, limit: Int = 40): String {
        val clean = (s ?: "").filter { it.isLetterOrDigit() || it in " -_" }.trim()
        return clean.replace(" ", "_").take(limit).ifEmpty { "file" }
    }

    fun stamp(): String {
        val f = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
        return f.format(java.util.Date())
    }

    fun xml(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)
}
