package uz.notgis.documate

import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import java.io.OutputStream

/** Minimal shablonlar: Ariza + Rezyume. To'liq muharrir keyingi bosqichda. */
object Templates {

    fun ariza(out: OutputStream, kimdan: String, matn: String, sana: String) {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val c = page.canvas
            val title = TextPaint().apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val body = TextPaint().apply { textSize = 13f }
            c.drawText("ARIZA", 250f, 90f, title)
            c.drawText("Kimdan: $kimdan", 50f, 150f, body)
            var y = 200f
            matn.chunked(70).forEach { part ->
                c.drawText(part, 50f, y, body)
                y += 22f
            }
            c.drawText("Sana: $sana", 50f, y + 30f, body)
            c.drawText("Imzo: ___________", 50f, y + 60f, body)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    fun rezyume(out: OutputStream, ism: String, tel: String, tajriba: String) {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val c = page.canvas
            val title = TextPaint().apply { textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val head = TextPaint().apply { textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val body = TextPaint().apply { textSize = 13f }
            c.drawText(ism.ifEmpty { "F.I.Sh" }, 50f, 90f, title)
            c.drawText("Tel: $tel", 50f, 125f, body)
            c.drawText("Tajriba", 50f, 175f, head)
            var y = 205f
            tajriba.chunked(70).forEach { part ->
                c.drawText(part, 50f, y, body)
                y += 22f
            }
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
