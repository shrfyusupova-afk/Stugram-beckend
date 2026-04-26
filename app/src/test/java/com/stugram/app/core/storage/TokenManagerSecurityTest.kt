package com.stugram.app.core.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.stugram.app.data.remote.model.ProfileModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TokenManagerSecurityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val gson = Gson()

    @Before
    fun setUp() {
        runBlocking {
            val tokenManager = TokenManager(context)
            tokenManager.clearSession()
            tokenManager.getAccessToken()
        }
    }

    @Test
    fun encryptedTokenStorageSavesReadsAndClearsTokens() {
        runBlocking {
        val tokenManager = TokenManager(context)
        tokenManager.saveSession(testUser("u1"), "access-secret", "refresh-secret")

        assertEquals("access-secret", tokenManager.getAccessToken())
        assertEquals("refresh-secret", tokenManager.getRefreshToken())

        tokenManager.clearSession()

        assertNull(tokenManager.getAccessToken())
        assertNull(tokenManager.getRefreshToken())
        assertTrue(tokenManager.sessions.first().isEmpty())
        }
    }

    @Test
    fun legacyPlaintextTokensMigrateAndLegacyStoreIsCleared() {
        runBlocking {
        val legacyAccess = stringPreferencesKey("access_token")
        val legacyRefresh = stringPreferencesKey("refresh_token")
        val legacyUser = stringPreferencesKey("current_user")
        context.legacyAuthDataStore.edit { prefs ->
            prefs[legacyAccess] = "legacy-access"
            prefs[legacyRefresh] = "legacy-refresh"
            prefs[legacyUser] = gson.toJson(testUser("legacy-user"))
        }

        val tokenManager = TokenManager(context)

        assertEquals("legacy-access", tokenManager.getAccessToken())
        assertEquals("legacy-refresh", tokenManager.getRefreshToken())
        val legacyPrefs = context.legacyAuthDataStore.data.first()
        assertNull(legacyPrefs[legacyAccess])
        assertNull(legacyPrefs[legacyRefresh])
        }
    }

    private fun testUser(id: String): ProfileModel =
        ProfileModel(
            id = id,
            identity = "$id@example.com",
            username = id,
            fullName = "User $id"
        )
}
