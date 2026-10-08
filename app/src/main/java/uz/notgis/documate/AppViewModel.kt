package uz.notgis.documate

import android.app.Application
import android.content.Intent
import android.net.Uri
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
                lockOn = (p[Keys.LOCK] ?: "0") == "1",
                hasPin = !p[Keys.PIN_HASH].isNullOrEmpty(),
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

    init {
        viewModelScope.launch {
            settings.map { it.tree }.distinctUntilChanged().collect { tree ->
                if (tree != null) load(tree) else _files.value = emptyList()
            }
        }
    }

    fun refresh() {
        val tree = settings.value.tree ?: return
        viewModelScope.launch { load(tree) }
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

    private fun sha256(s: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            ctx.dataStore.edit {
                it[Keys.PIN_HASH] = sha256(pin)
                it[Keys.LOCK] = "1"
            }
        }
    }

    fun removeLock() {
        viewModelScope.launch {
            ctx.dataStore.edit {
                it[Keys.LOCK] = "0"
                it.remove(Keys.PIN_HASH)
            }
        }
    }

    private class Found : Exception()

    /** PIN tekshirish (suspend, UI dan chaqiriladi). */
    suspend fun verifyPin(pin: String): Boolean {
        var saved: String? = null
        try {
            ctx.dataStore.data.collect { p -> saved = p[Keys.PIN_HASH]; throw Found() }
        } catch (e: Found) { /* kutilgan */ }
        return saved != null && saved == sha256(pin)
    }

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
