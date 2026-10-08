package uz.notgis.documate

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.TextPaint
import java.io.IOException

/** Kichik offline PDF asboblari: birlashtirish, matn->PDF. */
object PdfTools {

    /** Bir nechta PDF ni bitta PDF ga birlashtiradi (raster ko'rinishda — vektor saqlanmaydi). */
    fun merge(ctx: Context, sources: List<Uri>, out: Uri) {
        if (sources.size < 2) throw IOException("Kamida 2 ta PDF kerak")
        val doc = PdfDocument()
        try {
            var n = 0
            for (uri in sources) {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: continue
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                try {
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        try {
                            val scale = 2f
                            val w = (page.width * 1.2f).toInt().coerceAtLeast(100)
                            val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(100)
                            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            n++
                            val info = PdfDocument.PageInfo.Builder(w, h, n).create()
                            val p = doc.startPage(info)
                            p.canvas.drawBitmap(bmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                            doc.finishPage(p)
                            bmp.recycle()
                        } finally {
                            page.close()
                        }
                    }
                } finally {
                    renderer.close()
                    pfd.close()
                }
            }
            if (n == 0) throw IOException("PDF o'qilmadi")
            ctx.contentResolver.openOutputStream(out)?.use { doc.writeTo(it) } ?: throw IOException("Yozilmadi")
        } finally {
            doc.close()
        }
    }

    /** Oddiy matnni A4 PDF ga aylantiradi. */
    fun textToPdf(text: String, out: java.io.OutputStream) {
        if (text.isBlank()) throw IOException("Bo'sh matn")
        val doc = PdfDocument()
        try {
            val paint = TextPaint().apply {
                typeface = Typeface.MONOSPACE
                textSize = 11f
                color = android.graphics.Color.BLACK
            }
            val pageW = 595
            val pageH = 842
            val margin = 40f
            val maxW = pageW - margin * 2
            // so'zlar bo'yicha o'rash
            val lines = ArrayList<String>()
            text.lines().forEach { raw ->
                if (raw.isEmpty()) {
                    lines.add("")
                    return@forEach
                }
                var cur = ""
                raw.split(" ").forEach { w ->
                    val cand = if (cur.isEmpty()) w else "$cur $w"
                    if (paint.measureText(cand) <= maxW) cur = cand
                    else {
                        lines.add(cur)
                        cur = w
                    }
                }
                lines.add(cur)
            }
            val lineH = paint.fontSpacing
            val perPage = ((pageH - margin * 2) / lineH).toInt()
            var idx = 0
            var pno = 0
            while (idx < lines.size) {
                pno++
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, pno).create()
                val page = doc.startPage(info)
                var y = margin + (-paint.ascent())
                for (k in 0 until perPage) {
                    if (idx >= lines.size) break
                    page.canvas.drawText(lines[idx], margin, y, paint)
                    y += lineH
                    idx++
                }
                doc.finishPage(page)
                if (pno > 500) break
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    fun textToPdf(ctx: Context, text: String, out: Uri) {
        ctx.contentResolver.openOutputStream(out)?.use { textToPdf(text, it) }
            ?: throw IOException("Yozilmadi")
    }
}
