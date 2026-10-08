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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        enableEdgeToEdge()
        setContent { App(vm) }
    }
}

private enum class Tab { HOME, TOOLS, FAVS, SETTINGS }

@Composable
private fun App(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val open by vm.openDoc.collectAsStateWithLifecycle()
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
        val ctx = LocalContext.current
        var crash by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            crash = withContext(Dispatchers.IO) { CrashReporter.read(ctx) }
        }
        val crashReport = crash
        if (crashReport != null) {
            CrashScreen(crashReport) {
                CrashReporter.clear(ctx)
                crash = null
            }
            return@DocuMateTheme
        }
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
            MainScaffold(vm)
        }
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val t = stringsFor(st.lang)
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setTree(uri)
    }
    val launchPicker = { pickFolder.launch(null) }

    // Sozlamalardan qaytganda (ruxsat berilgach) ro'yxatni yangilash
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    data class TabItem(val id: Tab, val label: String, val icon: ImageVector)
    val tabs = listOf(
        TabItem(Tab.HOME, t.navHome, Icons.Filled.Home),
        TabItem(Tab.TOOLS, t.navTools, Icons.Filled.Build),
        TabItem(Tab.FAVS, t.navFavs, Icons.Filled.Star),
        TabItem(Tab.SETTINGS, t.navSettings, Icons.Filled.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.id,
                        onClick = { tab = item.id },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = {
                            Text(
                                item.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
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
