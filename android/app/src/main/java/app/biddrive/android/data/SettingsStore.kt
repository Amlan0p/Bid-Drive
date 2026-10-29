package app.biddrive.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.biddrive.android.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "biddrive_settings")

/** Local app preferences (not account data): theme and whether the welcome page has been shown. */
class SettingsStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val themeKey = stringPreferencesKey("theme_mode")
    private val welcomeKey = booleanPreferencesKey("welcome_seen")

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)       // follow system theme by default
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** null until the stored value has been read. */
    private val _welcomeSeen = MutableStateFlow<Boolean?>(null)
    val welcomeSeen: StateFlow<Boolean?> = _welcomeSeen.asStateFlow()

    init {
        scope.launch {
            val prefs = context.settingsDataStore.data.first()
            _theme.value = prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
            _welcomeSeen.value = prefs[welcomeKey] == true
        }
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        scope.launch { context.settingsDataStore.edit { it[themeKey] = mode.name } }
    }

    fun markWelcomeSeen() {
        _welcomeSeen.value = true
        scope.launch { context.settingsDataStore.edit { it[welcomeKey] = true } }
    }
}
