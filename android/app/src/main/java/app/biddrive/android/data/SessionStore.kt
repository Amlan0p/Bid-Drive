package app.biddrive.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "biddrive_session")

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: UserDto) : SessionState
}

/**
 * Persists the server-issued token + cached profile in app-private DataStore.
 * Having a token locally never grants access by itself: every protected call is validated by the server.
 */
class SessionStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("user")

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Set when the server rejected our token, so the sign-in screen can explain why. */
    private val _expired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _expired.asStateFlow()

    @Volatile var token: String? = null
        private set

    init {
        scope.launch {
            val prefs = context.dataStore.data.first()
            val t = prefs[tokenKey]
            val u = prefs[userKey]?.let { runCatching { AppJson.decodeFromString(UserDto.serializer(), it) }.getOrNull() }
            if (t != null && u != null) {
                token = t
                _state.value = SessionState.SignedIn(u)
            } else {
                _state.value = SessionState.SignedOut
            }
        }
    }

    suspend fun save(auth: AuthResponse) {
        token = auth.token
        _expired.value = false
        context.dataStore.edit {
            it[tokenKey] = auth.token
            it[userKey] = AppJson.encodeToString(UserDto.serializer(), auth.user)
        }
        _state.value = SessionState.SignedIn(auth.user)
    }

    suspend fun updateUser(user: UserDto) {
        context.dataStore.edit { it[userKey] = AppJson.encodeToString(UserDto.serializer(), user) }
        if (_state.value is SessionState.SignedIn) _state.value = SessionState.SignedIn(user)
    }

    suspend fun clear(expired: Boolean = false) {
        token = null
        _expired.value = expired
        context.dataStore.edit { it.clear() }
        _state.value = SessionState.SignedOut
    }

    /** Called from the network layer (any thread). */
    fun onUnauthorized() {
        if (token == null) return
        token = null
        scope.launch { clear(expired = true) }
    }

    fun consumeExpiredFlag() { _expired.value = false }
}
