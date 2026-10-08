@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package uz.notgis.documate

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun openExternal(ctx: android.content.Context, doc: DocFile, noAppMsg: String = "Mos ilova topilmadi.") {
    val ext = doc.name.substringAfterLast('.', "").lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(doc.uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, noAppMsg, Toast.LENGTH_LONG).show()
    }
}

private fun colLetter(i: Int): String {
    var n = i
    var s = ""
    do {
        s = ('A' + (n % 26)).toString() + s
        n = n / 26 - 1
    } while (n >= 0)
    return s
}

@Composable
fun OfficeScreen(vm: AppViewModel, doc: DocFile, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by vm.settings.collectAsStateWithLifecycle()
    val t = stringsFor(st.lang)
    var scale by remember { mutableFloatStateOf(1f) }

    ViewerFrame(title = doc.name, onBack = onBack, backDesc = t.back, actions = {
        TextButton(onClick = { scale = maxOf(0.7f, scale - 0.2f) }) { Text("A-", fontSize = 13.sp) }
        TextButton(onClick = { scale = minOf(2.5f, scale + 0.2f) }) { Text("A+", fontSize = 15.sp) }
        OutlinedButton(onClick = { openExternal(ctx, doc, t.noApp) }) { Text(t.ext) }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (doc.type) {
                FType.DOC -> DocRichView(doc, t, scale)
                FType.XLS -> SheetTableView(doc, t, scale)
                FType.PPT -> SlidesRichView(doc, t, scale)
                else -> LegacyFallback(t, doc)
            }
        }
    }
}

@Composable
private fun LegacyFallback(t: L, doc: DocFile) {
    val ctx = LocalContext.current
    EmptyState(
        title = "${doc.type.tab} ${t.oldSuffix}",
        body = t.oldBody,
        button = t.openExternal,
        onClick = { openExternal(ctx, doc, t.noApp) },
    )
}

@Composable
private fun SearchBar(t: L, q: String, onQ: (String) -> Unit) {
    OutlinedTextField(
        value = q, onValueChange = onQ, singleLine = true,
        placeholder = { Text(t.searchIn) },
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    )
}

@Composable
private fun Loading(t: L) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        CircularProgressIndicator()
        Text(t.opening, modifier = Modifier.padding(top = 12.dp))
    }
}

// ---------- Word: matn + rasmlar tartibda ----------

@Composable
private fun DocRichView(doc: DocFile, t: L, scale: Float) {
    val ctx = LocalContext.current
    val data by produceState<OfficeXml.DocContent?>(null, doc.uri) {
        value = withContext(Dispatchers.IO) { OfficeXml.readDocxRich(ctx, doc.uri) }
    }
    val d = data
    var loaded by remember { mutableStateOf(false) }
    if (d == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2500)
            loaded = true
        }
        if (!loaded) Loading(t) else LegacyFallback(t, doc)
        return
    }
    var q by remember { mutableStateOf("") }
    val blocks = remember(d, q) {
        if (q.isBlank()) d.blocks
        else d.blocks.filter {
            when (it) {
                is OfficeXml.Block.P -> it.text.contains(q, ignoreCase = true)
                is OfficeXml.Block.Img -> true
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        SearchBar(t, q) { q = it }
        LazyColumn(Modifier.fillMaxSize()) {
            items(blocks.size, key = { it }) { i ->
                when (val b = blocks[i]) {
                    is OfficeXml.Block.P -> Text(
                        b.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = (16 * scale).sp,
                    )
                    is OfficeXml.Block.Img -> Image(
                        bitmap = b.bmp.asImageBitmap(),
                        contentDescription = t.images,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// ---------- Excel: haqiqiy jadval ----------

@Composable
private fun SheetTableView(doc: DocFile, t: L, scale: Float) {
    val ctx = LocalContext.current
    val data by produceState<OfficeXml.Result.Sheet?>(null, doc.uri) {
        value = withContext(Dispatchers.IO) {
            OfficeXml.read(ctx, doc.uri, doc.name, t.sheetW) as? OfficeXml.Result.Sheet
        }
    }
    val d = data
    var loaded by remember { mutableStateOf(false) }
    if (d == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2500)
            loaded = true
        }
        if (!loaded) Loading(t) else LegacyFallback(t, doc)
        return
    }
    var q by remember { mutableStateOf("") }
    val hScroll = rememberScrollState()
    val border = MaterialTheme.colorScheme.outline
    // Qidiruv bo'yicha filtrlangan satrlar — LazyScope ichida remember() mumkin emas
    val filtered = remember(d, q) {
        d.sheets.associate { sh ->
            sh.name to if (q.isBlank()) sh.rows.take(300)
            else sh.rows.filter { r -> r.any { it.contains(q, ignoreCase = true) } }.take(300)
        }
    }
    Column(Modifier.fillMaxSize()) {
        SearchBar(t, q) { q = it }
        LazyColumn(Modifier.fillMaxSize()) {
            d.sheets.forEach { sh ->
                item(key = sh.name) {
                    Text(
                        sh.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                val rows = filtered[sh.name] ?: emptyList()
                val cols = (rows.maxOfOrNull { it.size } ?: 0).coerceAtMost(30)
                if (cols == 0) return@forEach
                stickyHeader(key = sh.name + "_h") {
                    Row(
                        Modifier
                            .horizontalScroll(hScroll)
                            .padding(horizontal = 12.dp),
                    ) {
                        Box(
                            Modifier
                                .widthIn(min = 40.dp)
                                .border(0.5.dp, border)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("", fontSize = (12 * scale).sp) }
                        for (c in 0 until cols) {
                            Box(
                                Modifier
                                    .widthIn(min = 90.dp)
                                    .border(0.5.dp, border)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    colLetter(c),
                                    fontSize = (12 * scale).sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                items(rows.size, key = { sh.name + "_$it" }) { ri ->
                    Row(
                        Modifier
                            .horizontalScroll(hScroll)
                            .padding(horizontal = 12.dp),
                    ) {
                        Box(
                            Modifier
                                .widthIn(min = 40.dp)
                                .border(0.5.dp, border)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("${ri + 1}", fontSize = (12 * scale).sp, color = MaterialTheme.colorScheme.primary) }
                        val row = rows[ri]
                        for (c in 0 until cols) {
                            Box(
                                Modifier
                                    .widthIn(min = 90.dp)
                                    .border(0.5.dp, border)
                                    .padding(6.dp),
                            ) {
                                Text(
                                    row.getOrNull(c)?.ifEmpty { "—" } ?: "—",
                                    fontSize = (13 * scale).sp,
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- PPT: slayd matni + rasmlari ----------

@Composable
private fun SlidesRichView(doc: DocFile, t: L, scale: Float) {
    val ctx = LocalContext.current
    val data by produceState<List<OfficeXml.SlideContent>?>(null, doc.uri) {
        value = withContext(Dispatchers.IO) { OfficeXml.readPptxRich(ctx, doc.uri, t.slide) }
    }
    val d = data
    var loaded by remember { mutableStateOf(false) }
    if (d == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2500)
            loaded = true
        }
        if (!loaded) Loading(t) else LegacyFallback(t, doc)
        return
    }
    var q by remember { mutableStateOf("") }
    val list = remember(d, q) {
        if (q.isBlank()) d else d.filter { it.text.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        SearchBar(t, q) { q = it }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.size) { i ->
                val s = list[i]
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(s.text, fontSize = (15 * scale).sp)
                        s.images.forEach { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = t.images,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Matn faylni PDF ga aylantirishda ishlatiladi. */
fun officePlainText(r: OfficeXml.Result?): String = when (r) {
    is OfficeXml.Result.Text -> r.paragraphs.joinToString("\n\n")
    is OfficeXml.Result.Slides -> r.slides.joinToString("\n\n---\n\n")
    is OfficeXml.Result.Sheet -> r.sheets.joinToString("\n\n") { sh ->
        sh.name + "\n" + sh.rows.joinToString("\n") { it.joinToString("\t") }
    }
    null -> ""
}
