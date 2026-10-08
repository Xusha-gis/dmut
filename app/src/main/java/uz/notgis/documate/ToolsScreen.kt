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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Rasm → PDF: sahifa o'lchami RASM o'lchamiga moslashadi (A4 ga majburlanmaydi).
 * Katta tomon 1440 pt dan oshsa proporsional kichraytiriladi. EXIF burilishi hisobga olinadi.
 */
object ImageToPdf {
    private const val MAX_SIDE = 2000
    private const val MAX_PAGE = 1440

    fun build(ctx: Context, images: List<Uri>, out: Uri) {
        val doc = PdfDocument()
        try {
            var n = 0
            for (uri in images) {
                val bmp = decode(ctx, uri) ?: continue
                n++
                // sahifa = rasm o'lchami (proporsiya saqlanadi, oq hoshiya yo'q)
                val k = min(1f, MAX_PAGE / max(bmp.width, bmp.height).toFloat())
                val pw = max(1, (bmp.width * k).toInt())
                val ph = max(1, (bmp.height * k).toInt())
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
                page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, pw.toFloat(), ph.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
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
private fun ToolCell(
    title: String,
    body: String,
    t: L,
    badge: String,
    color: Long,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(color)),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!enabled) {
                Text(
                    t.soon,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    t.start,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
            status = t.need2
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

    // --- rezyume shabloni ---
    var showTpl by remember { mutableStateOf(false) }
    var cvName by remember { mutableStateOf("") }
    var cvPhone by remember { mutableStateOf("") }
    var cvExp by remember { mutableStateOf("") }
    val tplSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        if (out != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(out)?.use {
                        Templates.rezyume(
                            it,
                            cvName.ifEmpty { "—" },
                            cvPhone.ifEmpty { "—" },
                            cvExp.ifEmpty { "—" },
                        )
                    }
                }
                withContext(Dispatchers.Main) { status = "Rezyume PDF ${t.saved}." }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        ScreenTitle(t.tools)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ToolCell(t.toolsImagePdf, t.toolsImagePdfBody, t, badge = "JPG", color = 0xFF7B61FF, enabled = !busy, onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
            item {
                ToolCell(t.toolsCamera, t.toolsCameraBody, t, badge = "CAM", color = 0xFF0F9D58, enabled = !busy, onClick = {
                    try {
                        val f = File.createTempFile("scan_", ".jpg", ctx.cacheDir)
                        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", f)
                        photoUri = u
                        busy = true
                        camera.launch(u)
                    } catch (e: Exception) {
                        busy = false
                        status = t.camFail
                    }
                })
            }
            item {
                ToolCell(t.toolsMerge, t.toolsMergeBody, t, badge = "PDF", color = 0xFFD64545, enabled = !busy, onClick = {
                    mergePicker.launch(arrayOf("application/pdf"))
                })
            }
            item {
                ToolCell(t.toolsText, t.toolsTextBody, t, badge = "TXT", color = 0xFF6B6459, enabled = !busy, onClick = {
                    textPicker.launch(arrayOf("text/*"))
                })
            }
            item {
                ToolCell(t.toolsTemplates, t.toolsTemplatesBody, t, badge = "CV", color = 0xFF2B579A, enabled = !busy, onClick = {
                    showTpl = true
                })
            }
            item {
                ToolCell(t.editorTitle, t.editorBody, t, badge = "PRO", color = 0xFF8C8574, enabled = false, onClick = {})
            }
            item {
                ToolCell(t.ocrTitle, t.ocrNote, t, badge = "OCR", color = 0xFF8C8574, enabled = false, onClick = {})
            }
            if (showTpl) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t.toolsTemplates, style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(value = cvName, onValueChange = { cvName = it }, label = { Text(t.cvName) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            OutlinedTextField(value = cvPhone, onValueChange = { cvPhone = it }, label = { Text(t.cvPhone) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            OutlinedTextField(value = cvExp, onValueChange = { cvExp = it }, label = { Text(t.cvExp) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3)
                            Button(onClick = { tplSaver.launch("Rezyume.pdf") }, modifier = Modifier.padding(top = 8.dp)) { Text(t.start) }
                            TextButton(onClick = { showTpl = false }) { Text("✕") }
                        }
                    }
                }
            }
            status?.let {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (busy) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(t.creating, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
