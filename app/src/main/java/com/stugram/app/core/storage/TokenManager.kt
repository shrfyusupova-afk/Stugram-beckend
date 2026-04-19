package com.stugram.app.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stugram.app.data.remote.model.ProfileModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class TokenManager(private val context: Context) {
    private val ACCESS_TOKEN = stringPreferencesKey("access_token") // legacy
    private val REFRESH_TOKEN = stringPreferencesKey("refresh_token") // legacy
    private val CURRENT_USER = stringPreferencesKey("current_user") // legacy

    private val SESSIONS_JSON = stringPreferencesKey("sessions_json")
    private val ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
    private val gson = Gson()

    data class StoredSession(
        val id: String,
        val user: ProfileModel,
        val accessToken: String,
        val refreshToken: String
    )

    private fun parseSessions(raw: String?): List<StoredSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<StoredSession>>() {}.type
            gson.fromJson<List<StoredSession>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun serializeSessions(items: List<StoredSession>): String = gson.toJson(items)

    private fun upsertSession(items: List<StoredSession>, session: StoredSession): List<StoredSession> {
        val updated = items.map { existing ->
            if (existing.user.id == session.user.id) session.copy(id = existing.id) else existing
        }
        return if (updated.any { it.user.id == session.user.id }) {
            updated
        } else {
            updated + session
        }
    }

    val sessions: Flow<List<StoredSession>> = context.dataStore.data.map { prefs ->
        parseSessions(prefs[SESSIONS_JSON])
    }

    val activeSessionId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_SESSION_ID]
    }

    private val activeSession: Flow<StoredSession?> = context.dataStore.data.map { prefs ->
        val sessions = parseSessions(prefs[SESSIONS_JSON])
        val activeId = prefs[ACTIVE_SESSION_ID]
        sessions.firstOrNull { it.id == activeId } ?: sessions.firstOrNull()
    }

    val accessToken: Flow<String?> = activeSession.map { it?.accessToken } // multi-session
    val refreshToken: Flow<String?> = activeSession.map { it?.refreshToken }
    val currentUser: Flow<ProfileModel?> = activeSession.map { it?.user }

    suspend fun getAccessToken(): String? = accessToken.first()

    suspend fun getRefreshToken(): String? = refreshToken.first()

    suspend fun getCurrentUser(): ProfileModel? = currentUser.first()

    suspend fun saveTokens(access: String, refresh: String) {
        // Keep legacy keys in sync for safety; active session remains unchanged.
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = access
            prefs[REFRESH_TOKEN] = refresh
        }
    }

    suspend fun saveSession(user: ProfileModel, access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            val existing = parseSessions(prefs[SESSIONS_JSON])
            val targetSession = StoredSession(
                id = existing.firstOrNull { it.user.id == user.id }?.id ?: "s_${System.currentTimeMillis()}",
                user = user,
                accessToken = access,
                refreshToken = refresh
            )
            val finalList = upsertSession(existing, targetSession)
            prefs[ACTIVE_SESSION_ID] = targetSession.id
            prefs[SESSIONS_JSON] = serializeSessions(finalList)

            // legacy mirror
            prefs[ACCESS_TOKEN] = access
            prefs[REFRESH_TOKEN] = refresh
            prefs[CURRENT_USER] = gson.toJson(user)
        }
    }

    suspend fun addAccountSession(user: ProfileModel, access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            val existing = parseSessions(prefs[SESSIONS_JSON])
            val newSession = StoredSession(
                id = existing.firstOrNull { it.user.id == user.id }?.id ?: "s_${System.currentTimeMillis()}",
                user = user,
                accessToken = access,
                refreshToken = refresh
            )
            prefs[SESSIONS_JSON] = serializeSessions(upsertSession(existing, newSession))
            prefs[ACTIVE_SESSION_ID] = newSession.id

            // legacy mirror to active
            prefs[ACCESS_TOKEN] = access
            prefs[REFRESH_TOKEN] = refresh
            prefs[CURRENT_USER] = gson.toJson(user)
        }
    }

    suspend fun switchToSession(sessionId: String): Boolean {
        val sessions = sessions.first()
        val target = sessions.firstOrNull { it.id == sessionId } ?: return false
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_SESSION_ID] = target.id
            // legacy mirror to active
            prefs[ACCESS_TOKEN] = target.accessToken
            prefs[REFRESH_TOKEN] = target.refreshToken
            prefs[CURRENT_USER] = gson.toJson(target.user)
        }
        return true
    }

    /**
     * Removes the currently active session only (keeps other accounts/profiles).
     * This is required for multi-account logout behavior.
     */
    suspend fun removeActiveSession(): Boolean {
        val currentActiveId = activeSessionId.first()
        if (currentActiveId.isNullOrBlank()) {
            // Nothing active -> fallback to full clear
            clearSession()
            return true
        }

        context.dataStore.edit { prefs ->
            val existing = parseSessions(prefs[SESSIONS_JSON])
            val remaining = existing.filterNot { it.id == currentActiveId }
            prefs[SESSIONS_JSON] = serializeSessions(remaining)

            val nextActive = remaining.firstOrNull()
            if (nextActive != null) {
                prefs[ACTIVE_SESSION_ID] = nextActive.id
                prefs[ACCESS_TOKEN] = nextActive.accessToken
                prefs[REFRESH_TOKEN] = nextActive.refreshToken
                prefs[CURRENT_USER] = gson.toJson(nextActive.user)
            } else {
                // No sessions left
                prefs.remove(ACTIVE_SESSION_ID)
                prefs.remove(ACCESS_TOKEN)
                prefs.remove(REFRESH_TOKEN)
                prefs.remove(CURRENT_USER)
            }
        }
        return true
    }

    suspend fun updateCurrentUser(user: ProfileModel) {
        context.dataStore.edit { prefs ->
            val existing = parseSessions(prefs[SESSIONS_JSON])
            val activeId = prefs[ACTIVE_SESSION_ID]
            if (activeId != null) {
                prefs[SESSIONS_JSON] = serializeSessions(existing.map { if (it.id == activeId) it.copy(user = user) else it })
            }
            prefs[CURRENT_USER] = gson.toJson(user)
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
