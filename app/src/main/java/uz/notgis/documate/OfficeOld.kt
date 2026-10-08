package uz.notgis.documate

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.apache.poi.hslf.usermodel.HSLFPictureShape
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.ss.usermodel.DataFormatter
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Eski binary Office formatlari (.doc/.xls/.ppt) — Apache POI, offline.
 * Natija mavjud UI modellariga o'giriladi (DocContent/SheetData/SlideContent),
 * shuning uchun ko'ruvchilar qayta ishlatiladi.
 */
object OfficeOld {

    private const val MAX_ROWS = 300
    private const val MAX_COLS = 30
    private const val MAX_IMG = 30

    private fun bytes(ctx: Context, uri: Uri, max: Int = 60 * 1024 * 1024): ByteArray? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > max) return null
                    out.write(buf, 0, n)
                }
                out.toByteArray().takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeImg(raw: ByteArray?): android.graphics.Bitmap? {
        if (raw == null || raw.isEmpty()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    /** .doc — matn paragraflari + rasmlar (oxirida galereya). */
    fun readDoc(ctx: Context, uri: Uri): OfficeXml.DocContent? {
        val data = bytes(ctx, uri) ?: return null
        return try {
            val blocks = ArrayList<OfficeXml.Block>()
            data.inputStream().use { ins ->
                WordExtractor(ins).use { ex ->
                    ex.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        .take(5000).forEach { blocks.add(OfficeXml.Block.P(it)) }
                }
            }
            // Rasmlar — hujjat oxirida galereya (HWPF da aniq o'rinni topish qiyin)
            try {
                data.inputStream().use { ins ->
                    HWPFDocument(ins).use { doc ->
                        doc.picturesTable.allPictures.take(MAX_IMG).forEach { pic ->
                            decodeImg(pic.content)?.let { blocks.add(OfficeXml.Block.Img(it)) }
                        }
                    }
                }
            } catch (e: Exception) {
            }
            if (blocks.isEmpty()) null else OfficeXml.DocContent(blocks)
        } catch (e: Exception) {
            null
        }
    }

    /** .xls — formatlangan qiymatlar (DataFormatter: sana/raqam to'g'ri chiqadi). */
    fun readXls(ctx: Context, uri: Uri, sheetLabel: String): List<OfficeXml.SheetData>? {
        val data = bytes(ctx, uri) ?: return null
        return try {
            data.inputStream().use { ins ->
                HSSFWorkbook(ins).use { wb ->
                    val fmt = DataFormatter()
                    val out = ArrayList<OfficeXml.SheetData>()
                    for (s in 0 until minOf(wb.numberOfSheets, 20)) {
                        val sheet = wb.getSheetAt(s)
                        val rows = ArrayList<List<String>>()
                        var r = 0
                        for (row in sheet) {
                            if (r++ > MAX_ROWS) break
                            val byCol = HashMap<Int, String>()
                            var maxCol = -1
                            for (cell in row) {
                                val c = cell.columnIndex
                                if (c < 0 || c > MAX_COLS) continue
                                maxCol = maxOf(maxCol, c)
                                byCol[c] = try {
                                    fmt.formatCellValue(cell)
                                } catch (e: Exception) {
                                    cell.toString()
                                }
                            }
                            if (maxCol >= 0) {
                                val line = (0..maxCol).map { byCol[it] ?: "" }
                                if (line.any { it.isNotBlank() }) rows.add(line)
                            }
                        }
                        if (rows.isNotEmpty()) {
                            val name = wb.getSheetName(s).ifBlank { "$sheetLabel ${s + 1}" }
                            out.add(OfficeXml.SheetData(name, rows))
                        }
                    }
                    out.ifEmpty { null }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** .ppt — har slayd matni + rasmlari. */
    fun readPpt(ctx: Context, uri: Uri, slideLabel: String): List<OfficeXml.SlideContent>? {
        val data = bytes(ctx, uri) ?: return null
        return try {
            data.inputStream().use { ins ->
                HSLFSlideShow(ins).use { ss ->
                    val out = ArrayList<OfficeXml.SlideContent>()
                    ss.slides.forEachIndexed { idx, slide ->
                        if (idx > 300) return@forEachIndexed
                        val texts = slide.placeholders
                            .filterIsInstance<HSLFTextShape>()
                            .mapNotNull { it.text?.trim() }
                            .filter { it.isNotEmpty() }
                        val imgs = slide.shapes
                            .filterIsInstance<HSLFPictureShape>()
                            .take(MAX_IMG)
                            .mapNotNull {
                                try {
                                    decodeImg(it.pictureData?.data)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        val text = texts.joinToString("\n").trim()
                        if (text.isNotBlank() || imgs.isNotEmpty()) {
                            out.add(OfficeXml.SlideContent("$slideLabel ${idx + 1}\n$text".trim(), imgs))
                        }
                    }
                    out.ifEmpty { null }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** .csv — oddiy jadval (vergul yoki nuqtali-vergul ajratuvchi). */
    fun readCsv(ctx: Context, uri: Uri, sheetLabel: String): List<OfficeXml.SheetData>? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val br = BufferedReader(InputStreamReader(ins, Charsets.UTF_8))
                val lines = ArrayList<String>()
                var line: String?
                while (br.readLine().also { line = it } != null && lines.size < 500) {
                    if (!line.isNullOrBlank()) lines.add(line!!)
                }
                if (lines.isEmpty()) return null
                val sample = lines.take(5).joinToString("\n")
                val semi = sample.count { it == ';' }
                val comma = sample.count { it == ',' }
                val sep = if (semi >= comma) ';' else ','
                val rows = lines.map { ln ->
                    parseCsvLine(ln, sep).take(MAX_COLS + 1)
                }.filter { r -> r.any { it.isNotBlank() } }
                if (rows.isEmpty()) null
                else listOf(OfficeXml.SheetData(sheetLabel, rows))
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Qo'shtirnoqli CSV maydonlarini to'g'ri ajratadi. */
    private fun parseCsvLine(ln: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQ = false
        var i = 0
        while (i < ln.length) {
            val c = ln[i]
            when {
                c == '"' -> {
                    if (inQ && i + 1 < ln.length && ln[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else inQ = !inQ
                }
                c == sep && !inQ -> {
                    out.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString().trim())
        return out
    }
}
