package com.stugram.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stugram.app.core.socket.ChatSocketManager
import com.stugram.app.data.remote.model.ProfileModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val Context.legacyAuthDataStore by preferencesDataStore(name = "auth_prefs")

class TokenManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val ACCESS_TOKEN = stringPreferencesKey("access_token") // legacy plaintext migration source only
    private val REFRESH_TOKEN = stringPreferencesKey("refresh_token") // legacy plaintext migration source only
    private val CURRENT_USER = stringPreferencesKey("current_user") // legacy plaintext migration source only
    private val LEGACY_SESSIONS_JSON = stringPreferencesKey("sessions_json")
    private val LEGACY_ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
    private val gson = Gson()
    private val migrationMutex = Mutex()
    private val prefs: SharedPreferences? by lazy { createEncryptedPrefs() }
    private val _sessions = sharedSessions
    private val _activeSessionId = sharedActiveSessionId

    data class StoredSession(
        val id: String,
        val user: ProfileModel,
        val accessToken: String,
        val refreshToken: String
    )

    init {
        if (_sessions.value.isEmpty()) {
            publishSecureState(readSessionsFromSecurePrefs(), readActiveSessionIdFromSecurePrefs())
        }
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            ensureMigrated()
        }
    }

    val sessions: Flow<List<StoredSession>> = _sessions
    val activeSessionId: Flow<String?> = _activeSessionId
    private val activeSession: Flow<StoredSession?> = combine(_sessions, _activeSessionId) { items, activeId ->
        items.firstOrNull { it.id == activeId } ?: items.firstOrNull()
    }
    val accessToken: Flow<String?> = activeSession.map { it?.accessToken }
    val refreshToken: Flow<String?> = activeSession.map { it?.refreshToken }
    val currentUser: Flow<ProfileModel?> = activeSession.map { it?.user }

    suspend fun getAccessToken(): String? {
        ensureMigrated()
        return accessToken.first()
    }

    suspend fun getRefreshToken(): String? {
        ensureMigrated()
        return refreshToken.first()
    }

    suspend fun getCurrentUser(): ProfileModel? {
        ensureMigrated()
        return currentUser.first()
    }

    suspend fun saveTokens(access: String, refresh: String) {
        ensureMigrated()
        val current = _sessions.value
        val activeId = _activeSessionId.value
        val updated = current.map {
            if (it.id == activeId) it.copy(accessToken = access, refreshToken = refresh) else it
        }
        if (updated.any { it.id == activeId }) {
            writeSessions(updated, activeId)
        }
    }

    suspend fun saveSession(user: ProfileModel, access: String, refresh: String) {
        ensureMigrated()
        val existing = _sessions.value
        val targetSession = StoredSession(
            id = existing.firstOrNull { it.user.id == user.id }?.id ?: "s_${System.currentTimeMillis()}",
            user = user,
            accessToken = access,
            refreshToken = refresh
        )
        writeSessions(upsertSession(existing, targetSession), targetSession.id)
    }

    suspend fun addAccountSession(user: ProfileModel, access: String, refresh: String) {
        saveSession(user, access, refresh)
    }

    suspend fun switchToSession(sessionId: String): Boolean {
        ensureMigrated()
        val target = _sessions.value.firstOrNull { it.id == sessionId } ?: return false
        writeSessions(_sessions.value, target.id)
        ChatSocketManager.resetAuthenticatedSession()
        return true
    }

    suspend fun removeActiveSession(): Boolean {
        ensureMigrated()
        val currentActiveId = _activeSessionId.value
        if (currentActiveId.isNullOrBlank()) {
            clearSession()
            return true
        }

        val remaining = _sessions.value.filterNot { it.id == currentActiveId }
        writeSessions(remaining, remaining.firstOrNull()?.id)
        ChatSocketManager.resetAuthenticatedSession()
        return true
    }

    suspend fun updateCurrentUser(user: ProfileModel) {
        ensureMigrated()
        val activeId = _activeSessionId.value
        val updated = _sessions.value.map { if (it.id == activeId) it.copy(user = user) else it }
        writeSessions(updated, activeId)
    }

    suspend fun clearTokens() {
        clearSession()
    }

    suspend fun clearSession() {
        prefs?.edit()?.clear()?.apply()
        clearLegacyPlaintext()
        ChatSocketManager.resetAuthenticatedSession()
        publishSecureState(emptyList(), null)
    }

    private suspend fun ensureMigrated() {
        migrationMutex.withLock {
            val securePrefs = prefs ?: run {
                publishSecureState(emptyList(), null)
                return
            }
            val legacyPrefs = appContext.legacyAuthDataStore.data.first()
            val secureSessions = readSessionsFromSecurePrefs()
            val migrated = securePrefs.getBoolean(KEY_MIGRATED, false)
            val hasLegacyTokens = !legacyPrefs[ACCESS_TOKEN].isNullOrBlank() ||
                !legacyPrefs[REFRESH_TOKEN].isNullOrBlank() ||
                !legacyPrefs[LEGACY_SESSIONS_JSON].isNullOrBlank()
            if (migrated && (secureSessions.isNotEmpty() || !hasLegacyTokens)) {
                publishSecureState(secureSessions, readActiveSessionIdFromSecurePrefs())
                if (hasLegacyTokens) clearLegacyPlaintext()
                return
            }

            val legacySessions = parseSessions(legacyPrefs[LEGACY_SESSIONS_JSON])
            val legacyActiveId = legacyPrefs[LEGACY_ACTIVE_SESSION_ID]
            val sessionsToMigrate = if (legacySessions.isNotEmpty()) {
                legacySessions
            } else {
                val user = parseUser(legacyPrefs[CURRENT_USER])
                val access = legacyPrefs[ACCESS_TOKEN]
                val refresh = legacyPrefs[REFRESH_TOKEN]
                if (user != null && !access.isNullOrBlank() && !refresh.isNullOrBlank()) {
                    listOf(StoredSession("s_${System.currentTimeMillis()}", user, access, refresh))
                } else {
                    emptyList()
                }
            }
            val activeToMigrate = legacyActiveId
                ?.takeIf { id -> sessionsToMigrate.any { it.id == id } }
                ?: sessionsToMigrate.firstOrNull()?.id
            securePrefs.edit()
                .putString(KEY_SESSIONS_JSON, serializeSessions(sessionsToMigrate))
                .apply {
                    if (activeToMigrate != null) putString(KEY_ACTIVE_SESSION_ID, activeToMigrate) else remove(KEY_ACTIVE_SESSION_ID)
                }
                .putBoolean(KEY_MIGRATED, true)
                .apply()
            clearLegacyPlaintext()
            publishSecureState(sessionsToMigrate, activeToMigrate)
        }
    }

    private suspend fun clearLegacyPlaintext() {
        appContext.legacyAuthDataStore.edit { it.clear() }
    }

    private fun writeSessions(items: List<StoredSession>, activeId: String?) {
        val securePrefs = prefs ?: return publishSecureState(emptyList(), null)
        securePrefs.edit()
            .putString(KEY_SESSIONS_JSON, serializeSessions(items))
            .apply {
                if (activeId != null) putString(KEY_ACTIVE_SESSION_ID, activeId) else remove(KEY_ACTIVE_SESSION_ID)
            }
            .putBoolean(KEY_MIGRATED, true)
            .apply()
        publishSecureState(items, activeId)
    }

    private fun publishSecureState(items: List<StoredSession>, activeId: String?) {
        _sessions.value = items
        _activeSessionId.value = activeId
    }

    private fun readSessionsFromSecurePrefs(): List<StoredSession> =
        parseSessions(prefs?.getString(KEY_SESSIONS_JSON, null))

    private fun readActiveSessionIdFromSecurePrefs(): String? =
        prefs?.getString(KEY_ACTIVE_SESSION_ID, null)

    private fun parseSessions(raw: String?): List<StoredSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<StoredSession>>() {}.type
            gson.fromJson<List<StoredSession>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun serializeSessions(items: List<StoredSession>): String = gson.toJson(items)

    private fun parseUser(raw: String?): ProfileModel? =
        runCatching { gson.fromJson(raw, ProfileModel::class.java) }.getOrNull()

    private fun upsertSession(items: List<StoredSession>, session: StoredSession): List<StoredSession> {
        val updated = items.map { existing ->
            if (existing.user.id == session.user.id) session.copy(id = existing.id) else existing
        }
        return if (updated.any { it.user.id == session.user.id }) updated else updated + session
    }

    private fun createEncryptedPrefs(): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.onFailure {
        Log.w("TokenManager", "Secure token storage unavailable; forcing signed-out state.")
    }.getOrNull()

    private companion object {
        val sharedSessions = MutableStateFlow<List<StoredSession>>(emptyList())
        val sharedActiveSessionId = MutableStateFlow<String?>(null)

        const val SECURE_PREFS_NAME = "secure_auth_prefs_v1"
        const val KEY_SESSIONS_JSON = "secure_sessions_json"
        const val KEY_ACTIVE_SESSION_ID = "secure_active_session_id"
        const val KEY_MIGRATED = "secure_tokens_migrated"
    }
}
