@file:OptIn(ExperimentalMaterial3Api::class)

package uz.notgis.documate

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun openExternal(ctx: android.content.Context, doc: DocFile) {
    val ext = doc.name.substringAfterLast('.', "").lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(doc.uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Mos ilova topilmadi.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun OfficeScreen(doc: DocFile, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val data by produceState<OfficeXml.Result?>(null, doc.uri) {
        value = withContext(Dispatchers.IO) { OfficeXml.read(ctx, doc.uri, doc.name) }
    }
    var loaded by remember { mutableStateOf(false) }
    // data null bo'lsa-yu yuklanish tugagan bo'lsa -> eski format yoki xato
    val done = remember(data) { data != null }

    ViewerFrame(title = doc.name, onBack = onBack, actions = {
        OutlinedButton(onClick = { openExternal(ctx, doc) }) { Text("Tashqi") }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (data == null) {
                // hali yuklanmoqda yoki fallback
                androidx.compose.runtime.LaunchedEffect(data) {
                    kotlinx.coroutines.delay(2500)
                    loaded = true
                }
                if (!loaded) {
                    Column(Modifier.fillMaxSize().padding(32.dp)) {
                        CircularProgressIndicator()
                        Text("Ochilmoqda…", modifier = Modifier.padding(top = 12.dp))
                    }
                } else {
                    LegacyFallback(doc, onBack)
                }
            } else {
                when (val d = data) {
                    is OfficeXml.Result.Text -> DocTextView(d)
                    is OfficeXml.Result.Slides -> SlidesView(d)
                    is OfficeXml.Result.Sheet -> SheetsView(d)
                }
            }
        }
    }
}

@Composable
private fun LegacyFallback(doc: DocFile, onBack: () -> Unit) {
    val ctx = LocalContext.current
    EmptyState(
        title = "${doc.type.tab} — eski format",
        body = "Bu fayl eski binary formatda (.doc/.xls/.ppt) yoki shikastlangan. Ichki ko'ruvchi faqat docx/xlsx/pptx ni ochadi. Tashqi ilovada oching.",
        button = "Boshqa ilovada ochish",
        onClick = { openExternal(ctx, doc) },
    )
}

@Composable
private fun SearchBar(q: String, onQ: (String) -> Unit) {
    OutlinedTextField(
        value = q, onValueChange = onQ, singleLine = true,
        placeholder = { Text("Matn ichidan qidirish") },
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    )
}

@Composable
private fun DocTextView(d: OfficeXml.Result.Text) {
    var q by remember { mutableStateOf("") }
    val list = remember(d, q) {
        if (q.isBlank()) d.paragraphs else d.paragraphs.filter { it.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        SearchBar(q) { q = it }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.size) { i ->
                Text(
                    list[i],
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SlidesView(d: OfficeXml.Result.Slides) {
    var q by remember { mutableStateOf("") }
    val list = remember(d, q) {
        if (q.isBlank()) d.slides else d.slides.filter { it.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        SearchBar(q) { q = it }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.size) { i ->
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(list[i], modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetsView(d: OfficeXml.Result.Sheet) {
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        SearchBar(q) { q = it }
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
                val rows = if (q.isBlank()) sh.rows else sh.rows.filter { r -> r.any { it.contains(q, ignoreCase = true) } }
                item(key = sh.name + "_body") {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                        rows.take(500).forEach { r ->
                            Row {
                                r.take(20).forEach { c ->
                                    Text(
                                        if (c.isEmpty()) "—" else c,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.bodySmall,
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
