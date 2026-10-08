package uz.notgis.documate

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Sort(val label: String) {
    DATE("sana"), NAME("nom"), SIZE("hajm");

    fun next(): Sort = entries[(ordinal + 1) % entries.size]
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext

    val settings: StateFlow<UserSettings> = ctx.dataStore.data
        .map { p ->
            UserSettings(
                tree = p[Keys.TREE],
                favs = p[Keys.FAVS] ?: emptySet(),
                theme = p[Keys.THEME] ?: "system",
                lang = p[Keys.LANG] ?: "uz",
                loaded = true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private val _files = MutableStateFlow<List<DocFile>>(emptyList())
    val files: StateFlow<List<DocFile>> = _files.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(Sort.DATE)
    val typeFilter = MutableStateFlow<FType?>(null)

    /** Hozir ochilgan hujjat (null bo'lsa — asosiy ekran). */
    val openDoc = MutableStateFlow<DocFile?>(null)

    val visible: StateFlow<List<DocFile>> =
        combine(_files, query, sort, typeFilter) { all, q, s, t ->
            val list = all.filter {
                (t == null || it.type == t) && (q.isBlank() || it.name.contains(q, ignoreCase = true))
            }
            when (s) {
                Sort.DATE -> list.sortedByDescending { it.modified }
                Sort.NAME -> list.sortedBy { it.name.lowercase() }
                Sort.SIZE -> list.sortedByDescending { it.size }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Barcha fayllarga kirish ruxsati bormi (Android 11+ da All-files, eskida Read). */
    fun hasAllAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    init {
        viewModelScope.launch {
            settings.map { it.tree }.distinctUntilChanged().collect { refreshAuto() }
        }
    }

    /** Ruxsat bo'lsa hammasini, bo'lmasa tanlangan papkani o'qiydi. */
    fun refresh() {
        viewModelScope.launch { refreshAuto() }
    }

    private val _allAccess = MutableStateFlow(false)
    val allAccess: StateFlow<Boolean> = _allAccess.asStateFlow()

    private suspend fun refreshAuto() {
        _allAccess.value = hasAllAccess()
        if (_allAccess.value) {
            loadAll()
        } else {
            val tree = settings.value.tree
            if (tree != null) load(tree) else _files.value = emptyList()
        }
    }

    private suspend fun load(tree: String) {
        _loading.value = true
        _error.value = null
        try {
            _files.value = withContext(Dispatchers.IO) { FileScanner.scan(ctx, Uri.parse(tree)) }
        } catch (e: Exception) {
            _files.value = emptyList()
            _error.value = "Papkani o'qib bo'lmadi. Uni qaytadan tanlang."
        } finally {
            _loading.value = false
        }
    }

    fun setTree(uri: Uri) {
        val cr = ctx.contentResolver
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            cr.takePersistableUriPermission(uri, rw)
        } catch (e: SecurityException) {
            try {
                cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
        }
        viewModelScope.launch { ctx.dataStore.edit { it[Keys.TREE] = uri.toString() } }
    }

    fun toggleFav(doc: DocFile) {
        val key = doc.uri.toString()
        viewModelScope.launch {
            ctx.dataStore.edit { p ->
                val cur = p[Keys.FAVS] ?: emptySet()
                p[Keys.FAVS] = if (key in cur) cur - key else cur + key
            }
        }
    }

    fun setTheme(value: String) {
        viewModelScope.launch { ctx.dataStore.edit { it[Keys.THEME] = value } }
    }

    fun setLang(value: String) {
        viewModelScope.launch { ctx.dataStore.edit { it[Keys.LANG] = value } }
    }

    private suspend fun loadAll() {
        _loading.value = true
        _error.value = null
        try {
            _files.value = withContext(Dispatchers.IO) { FileScanner.scanAllMedia(ctx) }
        } catch (e: Exception) {
            _files.value = emptyList()
            _error.value = "Fayllarni o'qib bo'lmadi."
        } finally {
            _loading.value = false
        }
    }

    private class Found : Exception()

    suspend fun getLastPage(docUri: String): Int {
        var v = 0
        try {
            ctx.dataStore.data.collect { p -> v = p[lastPageKey(docUri)] ?: 0; throw Found() }
        } catch (e: Found) { }
        return v
    }

    fun saveLastPage(docUri: String, page: Int) {
        viewModelScope.launch { ctx.dataStore.edit { it[lastPageKey(docUri)] = page } }
    }
}
