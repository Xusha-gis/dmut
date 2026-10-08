package uz.notgis.documate

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** PDFBox (offline): parolli PDF ni ochish + matn qidirish uchun sahifa matnlari. */
object PdfBox {

    @Volatile private var inited = false

    private fun init(ctx: Context) {
        if (!inited) {
            synchronized(this) {
                if (!inited) {
                    try {
                        PDFBoxResourceLoader.init(ctx.applicationContext)
                    } catch (_: Exception) { }
                    inited = true
                }
            }
        }
    }

    /** Parol to'g'rimi — faqat tekshiradi. */
    fun checkPassword(ctx: Context, uri: Uri, password: String): Boolean {
        init(ctx)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val doc = PDDocument.load(ins, password)
                doc.close()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parolli PDF ni vaqtinchalik ochiq (decrypted) nusxaga aylantiradi.
     * Keyin uni oddiy PdfRenderer bilan tez render qilish mumkin.
     */
    fun decryptedCopy(ctx: Context, uri: Uri, password: String): File? {
        init(ctx)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val doc = PDDocument.load(ins, password)
                try {
                    doc.isAllSecurityToBeRemoved = true
                    val out = File.createTempFile("pdf_dec_", ".pdf", ctx.cacheDir)
                    doc.save(out)
                    out
                } finally {
                    doc.close()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Qidiruv uchun sahifa matnlari (limit bilan, xotira tejash uchun). */
    fun pageTexts(ctx: Context, uri: Uri, password: String? = null, maxPages: Int = 200): List<String>? {
        init(ctx)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val doc = if (password.isNullOrEmpty()) PDDocument.load(ins) else PDDocument.load(ins, password)
                try {
                    val n = minOf(doc.numberOfPages, maxPages)
                    val out = ArrayList<String>(n)
                    val stripper = PDFTextStripper()
                    for (i in 1..n) {
                        stripper.startPage = i
                        stripper.endPage = i
                        out.add(runCatching { stripper.getText(doc) }.getOrDefault(""))
                    }
                    out
                } finally {
                    doc.close()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Decrypted vaqtinchalik fayl yoki content uri dan matnlar. */
    fun pageTextsFromFile(ctx: Context, file: File, maxPages: Int = 200): List<String>? {
        init(ctx)
        return try {
            val doc = PDDocument.load(file)
            try {
                val n = minOf(doc.numberOfPages, maxPages)
                val out = ArrayList<String>(n)
                val stripper = PDFTextStripper()
                for (i in 1..n) {
                    stripper.startPage = i
                    stripper.endPage = i
                    out.add(runCatching { stripper.getText(doc) }.getOrDefault(""))
                }
                out
            } finally {
                doc.close()
            }
        } catch (e: Exception) {
            null
        }
    }
}
