@file:OptIn(ExperimentalMaterial3Api::class)

package uz.notgis.documate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ScreenTitle(text: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun EmptyState(title: String, body: String, button: String? = null, onClick: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        if (button != null) Button(onClick = onClick) { Text(button) }
    }
}

@Composable
fun FileRow(file: DocFile, fav: Boolean, onOpen: () -> Unit, onFav: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(file.type.color)),
            contentAlignment = Alignment.Center,
        ) {
            Text(file.type.ext, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${formatSize(file.size)} · ${formatDate(file.modified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                file.folder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        IconButton(onClick = onFav) {
            if (fav) {
                Icon(Icons.Filled.Star, contentDescription = "Sevimlidan olish", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Outlined.Star, contentDescription = "Sevimlilarga qo'shish")
            }
        }
    }
}

@Composable
fun FileList(
    files: List<DocFile>,
    favs: Set<String>,
    emptyText: String,
    onOpen: (DocFile) -> Unit,
    onFav: (DocFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                emptyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
    } else {
        LazyColumn(modifier.fillMaxSize()) {
            items(files, key = { it.uri.toString() }) { f ->
                FileRow(f, fav = f.uri.toString() in favs, onOpen = { onOpen(f) }, onFav = { onFav(f) })
            }
        }
    }
}

@Composable
fun HomeScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onOpen: (DocFile) -> Unit,
    onPickFolder: () -> Unit,
) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val files by vm.visible.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val filter by vm.typeFilter.collectAsStateWithLifecycle()
    val t = stringsFor(st.lang)

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        ScreenTitle(t.docs) {
            if (st.tree != null) {
                IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Yangilash") }
            }
        }

        if (!st.loaded) return@Column

        if (st.tree == null) {
            EmptyState(
                title = t.pickFolder,
                body = t.pickFolderBody,
                button = t.pickFolder,
                onClick = onPickFolder,
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            singleLine = true,
            placeholder = { Text(t.searchHint) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(selected = filter == null, onClick = { vm.typeFilter.value = null }, label = { Text(t.all) })
            }
            items(FType.entries.toList()) { ft ->
                FilterChip(
                    selected = filter == ft,
                    onClick = { vm.typeFilter.value = if (filter == ft) null else ft },
                    label = { Text(ft.tab) },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${files.size} ${t.filesCount}", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { vm.sort.value = sort.next() }) { Text("${t.sort}: ${sort.label}") }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }

        FileList(
            files = files,
            favs = st.favs,
            emptyText = t.notFound,
            onOpen = onOpen,
            onFav = vm::toggleFav,
        )
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel, padding: PaddingValues, onOpen: (DocFile) -> Unit) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val all by vm.files.collectAsStateWithLifecycle()
    val favFiles = all.filter { it.uri.toString() in st.favs }.sortedBy { it.name.lowercase() }
    val t = stringsFor(st.lang)

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        ScreenTitle(t.favs)
        FileList(
            files = favFiles,
            favs = st.favs,
            emptyText = t.notFound,
            onOpen = onOpen,
            onFav = vm::toggleFav,
        )
    }
}
