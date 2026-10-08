@file:OptIn(ExperimentalMaterial3Api::class)

package uz.notgis.documate

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

private val PRIVACY_UZ = listOf(
    "Ilovada hisob yozuvi, analytics, reklama SDK yoki kuzatuv kodi yo'q.",
    "INTERNET ruxsati manifestda yo'q — hech qanday so'rov tashqariga chiqmaydi.",
    "Hujjatlar faqat qurilmada ochiladi va saqlanadi, serverga yuborilmaydi.",
    "Fayllar ruxsati faqat hujjatlarni ko'rsatish uchun ishlatiladi.",
)

private val PRIVACY_RU = listOf(
    "Нет аккаунтов, аналитики, рекламы и трекеров.",
    "Нет разрешения INTERNET — данные не покидают устройство.",
    "Документы открываются и хранятся только на устройстве.",
    "Доступ к файлам используется только для показа документов.",
)

private val PRIVACY_EN = listOf(
    "No accounts, analytics, ads or trackers.",
    "No INTERNET permission — nothing leaves the device.",
    "Documents open and stay on-device only.",
    "File access is used only to show your documents.",
)

@Composable
private fun ExpandCard(title: String, body: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
            )
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { body() }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, padding: PaddingValues, onPickFolder: () -> Unit) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val t = stringsFor(st.lang)
    val folder = st.tree?.let { Uri.parse(it).lastPathSegment ?: it } ?: t.notChosen
    val allOk = remember(st.loaded) { vm.hasAllAccess() }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(t.settings)

        SectionLabel(t.theme)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to t.system, "light" to t.light, "dark" to t.dark).forEach { (key, label) ->
                FilterChip(selected = st.theme == key, onClick = { vm.setTheme(key) }, label = { Text(label) })
            }
        }

        SectionLabel(t.language)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("uz" to "O'zbek", "ru" to "Русский", "en" to "English").forEach { (key, label) ->
                FilterChip(selected = st.lang == key, onClick = { vm.setLang(key) }, label = { Text(label) })
            }
        }

        SectionLabel(t.folder)
        Text(
            if (allOk) t.allFiles else folder,
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onPickFolder, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
            Text(if (st.tree == null) t.pickFolder else t.changeFolder)
        }

        SectionLabel(t.privacy)
        ExpandCard(t.privacy) {
            val items = when (st.lang) {
                "ru" -> PRIVACY_RU
                "en" -> PRIVACY_EN
                else -> PRIVACY_UZ
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { Text("— $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }

        SectionLabel(t.about)
        ExpandCard("${t.about} • ${t.version} 0.4.0") {
            Text(
                t.aboutBody,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(Modifier.padding(bottom = 24.dp)) { }
    }
}
