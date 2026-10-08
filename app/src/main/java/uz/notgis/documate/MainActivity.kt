@file:OptIn(ExperimentalMaterial3Api::class)

package uz.notgis.documate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(vm) }
    }
}

private enum class Tab { HOME, TOOLS, FAVS, SETTINGS }

@Composable
private fun App(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val open by vm.openDoc.collectAsStateWithLifecycle()
    val t = stringsFor(settings.lang)
    val dark = when (settings.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val activity = LocalContext.current as ComponentActivity
    SideEffect {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    DocuMateTheme(dark) {
        LockGate(vm, t) {
            val current = open
            if (current != null) {
                BackHandler { vm.openDoc.value = null }
                val back = { vm.openDoc.value = null }
                when (current.type) {
                    FType.PDF -> ReaderScreen(vm, current, back)
                    FType.TXT -> TextScreen(current, back)
                    FType.DOC, FType.XLS, FType.PPT -> OfficeScreen(current, back)
                }
            } else {
                MainScaffold(vm, t)
            }
        }
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel, t: L) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setTree(uri)
    }
    val launchPicker = { pickFolder.launch(null) }

    data class TabItem(val id: Tab, val label: String, val icon: ImageVector)
    val tabs = listOf(
        TabItem(Tab.HOME, t.home, Icons.Filled.Home),
        TabItem(Tab.TOOLS, t.tools, Icons.Filled.Build),
        TabItem(Tab.FAVS, t.favs, Icons.Filled.Star),
        TabItem(Tab.SETTINGS, t.settings, Icons.Filled.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.id,
                        onClick = { tab = item.id },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.HOME -> HomeScreen(vm, padding, onOpen = { vm.openDoc.value = it }, onPickFolder = launchPicker)
            Tab.TOOLS -> ToolsScreen(vm, padding)
            Tab.FAVS -> FavoritesScreen(vm, padding, onOpen = { vm.openDoc.value = it })
            Tab.SETTINGS -> SettingsScreen(vm, padding, onPickFolder = launchPicker)
        }
    }
}
