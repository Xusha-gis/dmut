@file:OptIn(ExperimentalMaterial3Api::class)

package uz.notgis.documate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Rasm → PDF: A4 sahifa, rasm markazga joylanadi, EXIF aylantirishi hisobga olinadi. */
object ImageToPdf {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MAX_SIDE = 2000

    fun build(ctx: Context, images: List<Uri>, out: Uri) {
        val doc = PdfDocument()
        try {
            var n = 0
            for (uri in images) {
                val bmp = decode(ctx, uri) ?: continue
                n++
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, n).create())
                val scale = min(PAGE_W / bmp.width.toFloat(), PAGE_H / bmp.height.toFloat())
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val left = (PAGE_W - dw) / 2f
                val top = (PAGE_H - dh) / 2f
                page.canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                bmp.recycle()
            }
            if (n == 0) throw IOException("Rasm o'qilmadi")
            val stream = ctx.contentResolver.openOutputStream(out) ?: throw IOException("Fayl yozilmadi")
            stream.use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val degrees = cr.openInputStream(uri)?.use { exifDegrees(it) } ?: 0
        val s = min(1f, MAX_SIDE / max(raw.width, raw.height).toFloat())
        val m = Matrix()
        m.postRotate(degrees.toFloat())
        m.postScale(s, s)
        val result = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (result !== raw) raw.recycle()
        return result
    }

    private fun exifDegrees(ins: InputStream): Int = try {
        when (ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
}

@Composable
private fun ToolCard(title: String, body: String, ready: Boolean, busy: Boolean = false, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (ready) {
                Button(onClick = onClick, enabled = !busy) { Text(if (busy) "…" else "Boshlash") }
            } else {
                Text("Tez orada", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun ToolsScreen(vm: AppViewModel, padding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by vm.settings.collectAsStateWithLifecycle()
    val t = stringsFor(st.lang)
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    // --- kamera skaneri ---
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var scanned by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val saverScan = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        if (out != null && scanned.isNotEmpty()) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { ImageToPdf.build(ctx, scanned, out) }.isSuccess }
                busy = false
                status = if (ok) "PDF ${t.saved}." else "PDF ${t.failed}."
                scanned = emptyList()
            }
        } else busy = false
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = photoUri
        if (ok && u != null) {
            scanned = scanned + u
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
            saverScan.launch("Skan_$stamp.pdf")
        } else busy = false
    }

    // --- galereya -> PDF ---
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        val images = picked
        if (out != null && images.isNotEmpty()) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { ImageToPdf.build(ctx, images, out) }.isSuccess }
                busy = false
                status = if (ok) "PDF ${t.saved} (${images.size})." else "PDF ${t.failed}."
                picked = emptyList()
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            picked = uris
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
            saver.launch("Rasm_$stamp.pdf")
        }
    }

    // --- PDF birlashtirish ---
    var toMerge by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val mergeSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        if (out != null && toMerge.size >= 2) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { PdfTools.merge(ctx, toMerge, out) }.isSuccess }
                busy = false
                status = if (ok) "PDF ${t.saved} (${toMerge.size})." else "PDF ${t.failed}."
                toMerge = emptyList()
            }
        } else busy = false
    }
    val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.size >= 2) {
            toMerge = uris
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
            mergeSaver.launch("Birlashtirilgan_$stamp.pdf")
        } else if (uris.isNotEmpty()) {
            status = "Kamida 2 ta PDF tanlang."
        }
    }

    // --- Matn -> PDF ---
    var textSrc by remember { mutableStateOf<Uri?>(null) }
    val textSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        val src = textSrc
        if (out != null && src != null) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val txt = ctx.contentResolver.openInputStream(src)?.bufferedReader()?.readText() ?: ""
                        PdfTools.textToPdf(ctx, txt.ifEmpty { " " }, out)
                    }.isSuccess
                }
                busy = false
                status = if (ok) "PDF ${t.saved}." else "PDF ${t.failed}."
                textSrc = null
            }
        }
    }
    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            textSrc = uri
            textSaver.launch("Matn_${System.currentTimeMillis()}.pdf")
        }
    }

    // --- shablonlar ---
    var showTpl by remember { mutableStateOf(false) }
    var tplName by remember { mutableStateOf("") }
    var tplBody by remember { mutableStateOf("") }
    val tplSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        if (out != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(out)?.use {
                        Templates.ariza(it, tplName.ifEmpty { "—" }, tplBody.ifEmpty { "—" }, SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date()))
                    }
                }
                withContext(Dispatchers.Main) { status = "Shablon PDF ${t.saved}." }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(t.tools)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCard(t.toolsImagePdf, t.toolsImagePdfBody, ready = true, busy = busy, onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            ToolCard(t.toolsCamera, t.toolsCameraBody, ready = true, busy = busy, onClick = {
                try {
                    val f = File.createTempFile("scan_", ".jpg", ctx.cacheDir)
                    val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", f)
                    photoUri = u
                    busy = true
                    camera.launch(u)
                } catch (e: Exception) {
                    busy = false
                    status = "Kamera ${t.failed}."
                }
            })
            ToolCard(t.toolsMerge, t.toolsMergeBody, ready = true, busy = busy, onClick = {
                mergePicker.launch(arrayOf("application/pdf"))
            })
            ToolCard(t.toolsText, t.toolsTextBody, ready = true, busy = busy, onClick = {
                textPicker.launch(arrayOf("text/*"))
            })
            // shablon kartasi (ichida mini forma)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(t.toolsTemplates, style = MaterialTheme.typography.titleMedium)
                    Text(t.toolsTemplatesBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                    if (!showTpl) {
                        Button(onClick = { showTpl = true }) { Text(t.start) }
                    } else {
                        OutlinedTextField(value = tplName, onValueChange = { tplName = it }, label = { Text("F.I.Sh") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = tplBody, onValueChange = { tplBody = it }, label = { Text("Matn") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3)
                        Button(onClick = { tplSaver.launch("Ariza.pdf") }, modifier = Modifier.padding(top = 8.dp)) { Text(t.start) }
                        TextButton(onClick = { showTpl = false }) { Text("✕") }
                    }
                }
            }
            ToolCard("PDF muharriri (to'liq)", "Sahifa o'chirish, aylantirish, highlight — keyingi bosqich.", ready = false)
            ToolCard("OCR (matn ajratish)", t.ocrNote, ready = false)
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
