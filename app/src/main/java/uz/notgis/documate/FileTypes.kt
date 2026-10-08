package uz.notgis.documate

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class FType(val tab: String, val ext: String, val color: Long) {
    PDF("PDF", "PDF", 0xFFD64545),
    DOC("Word", "DOC", 0xFF2B579A),
    XLS("Excel", "XLS", 0xFF1E7B4C),
    PPT("PPT", "PPT", 0xFFD97706),
    TXT("TXT", "TXT", 0xFF6B6459),
}

fun typeOf(name: String): FType? = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> FType.PDF
    "doc", "docx", "rtf", "odt" -> FType.DOC
    "xls", "xlsx", "csv", "ods" -> FType.XLS
    "ppt", "pptx", "odp" -> FType.PPT
    "txt", "md", "log" -> FType.TXT
    else -> null
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToInt()} KB"
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f", mb).replace('.', ',') + " MB"
}

fun formatDate(ms: Long): String {
    if (ms <= 0L) return "—"
    return SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(ms))
}
