@file:OptIn(ExperimentalMaterial3Api::class)

package uz.notgis.documate

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ViewerFrame(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    backDesc: String? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                    }
                },
                actions = actions,
            )
        },
        content = content,
    )
}

@Composable
fun TextScreen(vm: AppViewModel, doc: DocFile, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by collectAsStateWithLifecycle(vm.settings)
    val t = stringsFor(st.lang)
    val result by produceState<Result<List<String>>?>(null, doc.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(doc.uri)?.use { ins ->
                    ins.bufferedReader(Charsets.UTF_8).useLines { seq -> seq.take(50_000).toList() }
                } ?: emptyList()
            }
        }
    }

    ViewerFrame(title = doc.name, onBack = onBack, backDesc = t.back) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            val r = result
            when {
                r == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                r.isFailure -> Text(
                    t.readFail,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val lines = r.getOrNull() ?: emptyList()
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        items(lines.size) { i ->
                            Text(
                                if (lines[i].isEmpty()) " " else lines[i],
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}


