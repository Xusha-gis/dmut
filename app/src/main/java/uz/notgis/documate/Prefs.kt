package uz.notgis.documate

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "documate")

object Keys {
    val TREE = stringPreferencesKey("tree_uri")
    val FAVS = stringSetPreferencesKey("favs")
    val THEME = stringPreferencesKey("theme") // system | light | dark
    val LANG = stringPreferencesKey("lang") // uz | ru | en
    val PIN_HASH = stringPreferencesKey("pin_hash")
    val LOCK = stringPreferencesKey("lock") // 0 | 1
}

data class UserSettings(
    val tree: String? = null,
    val favs: Set<String> = emptySet(),
    val theme: String = "system",
    val lang: String = "uz",
    val lockOn: Boolean = false,
    val hasPin: Boolean = false,
    val loaded: Boolean = false,
)

/** Hujjatning oxirgi sahifasi uchun kalit (uri hash orqali). */
fun lastPageKey(docUri: String): androidx.datastore.preferences.core.Preferences.Key<Int> =
    androidx.datastore.preferences.core.intPreferencesKey("last_" + docUri.hashCode())
