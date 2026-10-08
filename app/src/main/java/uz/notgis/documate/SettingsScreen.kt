@file:OptIn(ExperimentalMaterial3Api::class)

package uz.notgis.documate

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

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
    "«Barcha fayllarni boshqarish» ruxsati so'ralmaydi — faqat siz tanlagan papka o'qiladi.",
)

@Composable
fun SettingsScreen(vm: AppViewModel, padding: PaddingValues, onPickFolder: () -> Unit) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val t = stringsFor(st.lang)
    val folder = st.tree?.let { Uri.parse(it).lastPathSegment ?: it } ?: t.notChosen
    var pin by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
        Text(folder, modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onPickFolder, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
            Text(if (st.tree == null) t.pickFolder else t.changeFolder)
        }

        SectionLabel(t.lock)
        Text(t.lockBody, modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
            label = { Text(t.enterPin) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth(),
        )
        Row(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (pin.length >= 4) {
                    vm.setPin(pin)
                    pin = ""
                    pinMsg = t.pinSaved
                } else pinMsg = t.enterPin
            }) { Text(t.setPin) }
            if (st.lockOn || st.hasPin) {
                OutlinedButton(onClick = {
                    vm.removeLock()
                    pinMsg = null
                }) { Text("✕") }
            }
        }
        pinMsg?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary) }
        Text(
            if (st.lockOn) t.lock + ": yoqilgan" else t.lock + ": o'chirilgan",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.labelMedium,
        )

        SectionLabel(t.privacy)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PRIVACY_UZ.forEach { Text("— $it", style = MaterialTheme.typography.bodyMedium) }
        }

        SectionLabel(t.about)
        Text(
            "DocuMate 0.2.0 — offline. PDF parol/qidiruv/eslash, docx/xlsx/pptx ichki ko'ruvchi, kamera skaneri, PDF birlashtirish, shablonlar, 3 til, PIN qulf.",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
