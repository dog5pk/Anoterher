package com.anotether.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App preferences — stores ONLY non-sensitive UI state.
 *
 * What we store: whether the user has seen the privacy disclosure screen.
 * What we NEVER store: session keys, tokens, messages, user identity.
 *
 * DataStore is used over SharedPreferences for coroutine-safe access.
 */
class AppPreferences(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anotether_prefs")

    private object Keys {
        val HAS_SEEN_PRIVACY_DISCLOSURE = booleanPreferencesKey("has_seen_privacy_disclosure")
    }

    val hasSeenPrivacyDisclosure: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.HAS_SEEN_PRIVACY_DISCLOSURE] ?: false }

    suspend fun markPrivacyDisclosureSeen() {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_SEEN_PRIVACY_DISCLOSURE] = true
        }
    }
}
