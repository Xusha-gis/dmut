@file:OptIn(ExperimentalMaterial3Api::class)

package uz.notgis.documate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/** Android'ning o'zidagi PdfRenderer ustiga yupqa qobiq. Bir vaqtda faqat bitta sahifa ochiladi. */
class PdfSession private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val pageCount: Int = renderer.pageCount
    private val lock = Any()
    private var closed = false

    fun render(index: Int, targetWidth: Int): Bitmap? = synchronized(lock) {
        if (closed) return null
        val page = renderer.openPage(index)
        try {
            val w = targetWidth.coerceAtLeast(1)
            val h = (w.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally {
            page.close()
        }
    }

    fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            renderer.close()
            pfd.close()
        }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfSession {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Fayl ochilmadi")
            try {
                return PdfSession(pfd, PdfRenderer(pfd))
            } catch (e: Exception) {
                pfd.close()
                throw e
            }
        }

        fun openFile(file: File): PdfSession {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                return PdfSession(pfd, PdfRenderer(pfd))
            } catch (e: Exception) {
                pfd.close()
                throw e
            }
        }
    }
}

private val INVERT = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )
)

@Composable
private fun PdfPage(session: PdfSession, index: Int, widthPx: Int, night: Boolean) {
    val bmp by produceState<Bitmap?>(null, session, index, widthPx) {
        value = withContext(Dispatchers.IO) {
            try {
                session.render(index, widthPx)
            } catch (e: Exception) {
                null
            }
        }
    }
    val b = bmp
    if (b == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = "Sahifa ${index + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(b.width.toFloat() / b.height.toFloat()),
            colorFilter = if (night) ColorFilter.colorMatrix(INVERT) else null,
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
fun ReaderScreen(vm: AppViewModel, doc: DocFile, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by vm.settings.collectAsStateWithLifecycle()
    val t = stringsFor(st.lang)
    var session by remember { mutableStateOf<PdfSession?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var night by remember { mutableStateOf(false) }
    var needPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var decFile by remember { mutableStateOf<File?>(null) }
    val latest = rememberUpdatedState(session)
    val latestDec = rememberUpdatedState(decFile)

    // qidiruv
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var texts by remember { mutableStateOf<List<String>?>(null) }
    var hitPages by remember { mutableStateOf<List<Int>>(emptyList()) }
    var hitIdx by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }
    var showResume by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(doc.uri) {
        try {
            session = withContext(Dispatchers.IO) { PdfSession.open(ctx, doc.uri) }
        } catch (e: SecurityException) {
            needPassword = true
        } catch (e: Exception) {
            // PdfRenderer parolli faylda SecurityException yoki IOException beradi
            val msg = (e.message ?: "").lowercase()
            if (msg.contains("password") || msg.contains("encrypt") || msg.contains("protected")) {
                needPassword = true
            } else {
                // baribir parol ehtimolini tekshirish uchun parol ekranini ko'rsatmaymiz,
                // avval oddiy xato; lekin PdfBox orqali ham urinib ko'rish mumkin
                // Ko'p qurilmalarda shifrlangan PDF shu yerga tushadi:
                needPassword = true
                error = null
            }
        }
        // oxirgi sahifa
        val last = withContext(Dispatchers.IO) { vm.getLastPage(doc.uri.toString()) }
        if (last > 0) showResume = last
    }
    DisposableEffect(doc.uri) {
        onDispose {
            latest.value?.close()
            latestDec.value?.delete()
        }
    }

    // qidiruv matnlarini yuklash
    LaunchedEffect(searchOpen, session, decFile) {
        if (searchOpen && texts == null && session != null) {
            texts = withContext(Dispatchers.IO) {
                val df = decFile
                if (df != null) PdfBox.pageTextsFromFile(ctx, df)
                else PdfBox.pageTexts(ctx, doc.uri)
            }
        }
    }
    LaunchedEffect(query, texts) {
        val tx = texts
        if (!query.isBlank() && tx != null) {
            hitPages = tx.mapIndexedNotNull { i, s -> if (s.contains(query, ignoreCase = true)) i else null }
            hitIdx = 0
        } else {
            hitPages = emptyList()
        }
    }
    // sahifani eslab qolish (debounce 800ms)
    LaunchedEffect(session) {
        if (session != null) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .debounce(800)
                .collect { idx -> vm.saveLastPage(doc.uri.toString(), idx) }
        }
    }
    // davom etish
    LaunchedEffect(restored, session) {
        if (!restored && session != null) {
            restored = true
            // avtomatik sakramaymiz — banner orqali so'raymiz
        }
    }

    fun unlock() {
        if (password.isEmpty() || busy) return
        busy = true
        passError = false
        scope.launch(Dispatchers.IO) {
            val ok = PdfBox.checkPassword(ctx, doc.uri, password)
            if (!ok) {
                withContext(Dispatchers.Main) {
                    busy = false
                    passError = true
                }
                return@launch
            }
            val dec = PdfBox.decryptedCopy(ctx, doc.uri, password)
            withContext(Dispatchers.Main) {
                if (dec != null) {
                    try {
                        val s = PdfSession.openFile(dec)
                        decFile = dec
                        session = s
                        needPassword = false
                        error = null
                    } catch (e: Exception) {
                        passError = true
                        dec.delete()
                    }
                } else {
                    passError = true
                }
                busy = false
            }
        }
    }

    val steps = listOf(1f, 1.5f, 2f, 2.5f)
    val zoomLabel = if (zoom == zoom.toInt().toFloat()) "${zoom.toInt()}×" else "$zoom×"

    ViewerFrame(
        title = doc.name,
        onBack = {
            vm.saveLastPage(doc.uri.toString(), listState.firstVisibleItemIndex)
            onBack()
        },
        actions = {
            if (!needPassword && session != null) {
                TextButton(onClick = { searchOpen = !searchOpen }) { Text(if (searchOpen) "✕" else "🔍") }
                TextButton(onClick = { zoom = steps[(steps.indexOf(zoom) + 1) % steps.size] }) { Text(zoomLabel) }
                TextButton(onClick = { night = !night }) { Text(if (night) t.day else t.night) }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                needPassword && session == null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(t.passwordTitle, style = MaterialTheme.typography.titleLarge)
                    Text(t.passwordHint, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; passError = false },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { unlock() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (passError) Text(
                        t.passwordWrong,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = { unlock() }, enabled = !busy, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Text(if (busy) t.creating else t.open)
                    }
                }
                error != null -> Text(
                    error ?: "",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                session == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> {
                    val s = session!!
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val pageW = maxWidth * zoom
                        val widthPx = minOf(with(density) { (pageW - 16.dp).toPx() }.toInt(), 2200)
                        val hScroll = rememberScrollState()
                        Column(Modifier.fillMaxSize()) {
                            if (searchOpen) {
                                Row(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        singleLine = true,
                                        placeholder = { Text(t.searchPdf) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (hitPages.isNotEmpty()) {
                                        TextButton(onClick = {
                                            hitIdx = (hitIdx - 1 + hitPages.size) % hitPages.size
                                            scope.launch { listState.scrollToItem(hitPages[hitIdx]) }
                                        }) { Text("‹") }
                                        Text(
                                            "${hitIdx + 1}/${hitPages.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        TextButton(onClick = {
                                            hitIdx = (hitIdx + 1) % hitPages.size
                                            scope.launch { listState.scrollToItem(hitPages[hitIdx]) }
                                        }) { Text("›") }
                                        LaunchedEffect(hitIdx, hitPages) {
                                            if (hitPages.isNotEmpty()) listState.scrollToItem(hitPages[hitIdx])
                                        }
                                    } else if (query.isNotBlank() && texts != null) {
                                        Text("0", modifier = Modifier.padding(horizontal = 8.dp))
                                    }
                                }
                            }
                            val resume = showResume
                            if (resume != null && resume > 0 && resume < s.pageCount) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${t.continueLast}: ${resume + 1}-sahifa",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        TextButton(onClick = {
                                            scope.launch { listState.scrollToItem(resume) }
                                            showResume = null
                                        }) { Text(t.open) }
                                        TextButton(onClick = { showResume = null }) { Text("✕") }
                                    }
                                }
                            }
                            Box(
                                Modifier.fillMaxSize()
                                    .background(if (night) Color(0xFF111111) else MaterialTheme.colorScheme.surfaceVariant)
                                    .horizontalScroll(hScroll)
                            ) {
                                LazyColumn(
                                    Modifier.width(pageW).fillMaxHeight(),
                                    state = listState,
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(s.pageCount, key = { it }) { i -> PdfPage(s, i, widthPx, night) }
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                "${listState.firstVisibleItemIndex + 1} / ${s.pageCount}",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
