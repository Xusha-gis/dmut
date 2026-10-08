package uz.notgis.documate

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipFile

/**
 * Yengil OOXML o'quvchi (Apache POI siz).
 * docx/xlsx/pptx — ZIP ichidagi XML lardan matn ajratadi. Hammasi offline.
 * Eski .doc/.xls/.ppt (OLE2) uchun null qaytadi — ular tashqi ilovada ochiladi.
 *
 * Nega POI emas? POI + transitive deps ~15 MB, dex/metod limiti va CI xotirani
 * zo'riqtiradi. Bu parser 95% zamonaviy fayllarni qoplaydi; POI keyingi
 * bosqichda murakkab formatlash uchun qo'shilishi mumkin.
 */
object OfficeXml {

    sealed interface Result {
        data class Text(val paragraphs: List<String>) : Result
        data class Sheet(val sheets: List<SheetData>) : Result
        data class Slides(val slides: List<String>) : Result
    }

    data class SheetData(val name: String, val rows: List<List<String>>)

    fun read(ctx: Context, uri: Uri, fileName: String): Result? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return try {
            when (ext) {
                "docx" -> readDocx(ctx, uri)?.let { Result.Text(it) }
                "xlsx", "csv" -> readXlsx(ctx, uri)?.let { Result.Sheet(it) }
                "pptx" -> readPptx(ctx, uri)?.let { Result.Slides(it) }
                else -> null // doc/xls/ppt/odt/ods/odp/rtf -> tashqi ilova
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun copyToTemp(ctx: Context, uri: Uri): File {
        val tmp = File.createTempFile("office", ".zip", ctx.cacheDir)
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            tmp.outputStream().use { ins.copyTo(it) }
        }
        return tmp
    }

    private fun extractTagText(xml: String, tag: String): List<String> {
        // <w:t>matn</w:t> yoki <a:t>matn</a:t> ko'rinishidagi teglar
        val out = ArrayList<String>()
        var i = 0
        while (true) {
            val s = xml.indexOf("<$tag", i)
            if (s < 0) break
            val gs = xml.indexOf('>', s)
            if (gs < 0) break
            val e = xml.indexOf("</$tag>", gs)
            if (e < 0) break
            var t = xml.substring(gs + 1, e)
            t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            if (t.isNotEmpty()) out.add(t)
            i = e + tag.length + 3
        }
        return out
    }

    private fun readDocx(ctx: Context, uri: Uri): List<String>? {
        val tmp = copyToTemp(ctx, uri)
        try {
            ZipFile(tmp).use { zip ->
                val e = zip.getEntry("word/document.xml") ?: return null
                val xml = zip.getInputStream(e).bufferedReader().readText()
                // Paragraflarni <w:p> bo'yicha bo'lish
                val paras = xml.split("<w:p").drop(1).map { p ->
                    extractTagText(p, "w:t").joinToString("")
                }.map { it.trim() }.filter { it.isNotEmpty() }
                if (paras.isEmpty()) return null
                return paras.take(5000)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun readPptx(ctx: Context, uri: Uri): List<String>? {
        val tmp = copyToTemp(ctx, uri)
        try {
            ZipFile(tmp).use { zip ->
                val slides = ArrayList<String>()
                var n = 1
                while (true) {
                    val e = zip.getEntry("ppt/slides/slide$n.xml") ?: break
                    val xml = zip.getInputStream(e).bufferedReader().readText()
                    val texts = extractTagText(xml, "a:t").joinToString("\n")
                    if (texts.isNotBlank()) slides.add("Slayd $n\n$texts")
                    n++
                    if (n > 300) break
                }
                if (slides.isEmpty()) return null
                return slides
            }
        } finally {
            tmp.delete()
        }
    }

    private fun readXlsx(ctx: Context, uri: Uri): List<SheetData>? {
        val tmp = copyToTemp(ctx, uri)
        try {
            ZipFile(tmp).use { zip ->
                // shared strings
                val shared = ArrayList<String>()
                zip.getEntry("xl/sharedStrings.xml")?.let { e ->
                    val xml = zip.getInputStream(e).bufferedReader().readText()
                    // har bir <si> — bitta string
                    xml.split("<si").drop(1).forEach { si ->
                        shared.add(extractTagText(si, "t").joinToString(""))
                    }
                }
                val out = ArrayList<SheetData>()
                // sheet nomlari
                val wbXml = zip.getEntry("xl/workbook.xml")?.let {
                    zip.getInputStream(it).bufferedReader().readText()
                } ?: ""
                val sheetNames = Regex("<sheet[^>]*name=\"([^\"]+)\"").findAll(wbXml).map { it.groupValues[1] }.toList()
                var n = 1
                while (true) {
                    val e = zip.getEntry("xl/worksheets/sheet$n.xml") ?: break
                    val xml = zip.getInputStream(e).bufferedReader().readText()
                    val rows = ArrayList<List<String>>()
                    xml.split("<row").drop(1).take(500).forEach { rowXml ->
                        val cells = ArrayList<String>()
                        // <c ...><v>idx</v></c> yoki <c t="inlineStr"><t>..</t></c> yoki <c t="s">
                        rowXml.split("<c ").drop(1).take(100).forEach { c ->
                            val tAttr = Regex("t=\"([^\"]+)\"").find(c)?.groupValues?.get(1)
                            when (tAttr) {
                                "s" -> {
                                    val idx = Regex("<v>(-?\\d+)</v>").find(c)?.groupValues?.get(1)?.toIntOrNull()
                                    cells.add(if (idx != null && idx in shared.indices) shared[idx] else "")
                                }
                                "inlineStr" -> cells.add(extractTagText(c, "t").joinToString(""))
                                "str", "e" -> cells.add(Regex("<v>([^<]*)</v>").find(c)?.groupValues?.get(1) ?: "")
                                else -> {
                                    // raqam yoki sana
                                    val v = Regex("<v>([^<]*)</v>").find(c)?.groupValues?.get(1)
                                    cells.add(v ?: extractTagText(c, "t").joinToString(""))
                                }
                            }
                        }
                        if (cells.any { it.isNotBlank() }) rows.add(cells)
                    }
                    val name = sheetNames.getOrNull(n - 1) ?: "Varaq $n"
                    if (rows.isNotEmpty()) out.add(SheetData(name, rows))
                    n++
                    if (n > 50) break
                }
                if (out.isEmpty()) return null
                return out
            }
        } finally {
            tmp.delete()
        }
    }
}
