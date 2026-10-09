package pe.aphid.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pe.aphid.core.domain.repository.SettingsRepository
import pe.aphid.core.domain.repository.UserSettings
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aphid_settings")

/** Preferencias en DataStore. Se guardan como un único JSON versionable. */
@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext private val context: Context) : SettingsRepository {
    private val key = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString(UserSettings.serializer(), it) }.getOrNull() } ?: UserSettings()
    }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString(UserSettings.serializer(), it) }.getOrNull() } ?: UserSettings()
            prefs[key] = json.encodeToString(UserSettings.serializer(), transform(current))
        }
    }

    /** Versión de datos semilla cargada (para actualizaciones de crops_v2.json). */
    private val seedKey = stringPreferencesKey("seed_versions")
    val seedVersions: Flow<String?> = context.dataStore.data.map { it[seedKey] }
    suspend fun setSeedVersions(v: String) {
        context.dataStore.edit { it[seedKey] = v }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
