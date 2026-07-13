package com.dumblauncher.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "favorites",
)

data class FavoritesSettings(
    val favoriteCount: Int = FavoritesStore.DEFAULT_COUNT,
    val favoriteKeys: List<String> = emptyList(),
    val hideSelf: Boolean = true,
    val hiddenAppKeys: Set<String> = emptySet(),
    val customLabels: Map<String, String> = emptyMap(),
)

class FavoritesStore(private val context: Context) {

    val settings: Flow<FavoritesSettings> = context.favoritesDataStore.data.map { prefs ->
        val count = (prefs[KEY_COUNT] ?: DEFAULT_COUNT).coerceIn(MIN_COUNT, MAX_COUNT)
        val keys = prefs[KEY_FAVORITES]
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val hiddenKeys = prefs[KEY_HIDDEN_APPS]
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        val customLabels = prefs[KEY_CUSTOM_LABELS]
            ?.split(SEPARATOR)
            ?.mapNotNull { entry ->
                val parts = entry.split(LABEL_SEPARATOR, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            ?.toMap()
            .orEmpty()
        FavoritesSettings(
            favoriteCount = count,
            favoriteKeys = keys,
            hideSelf = prefs[KEY_HIDE_SELF] ?: true,
            hiddenAppKeys = hiddenKeys,
            customLabels = customLabels,
        )
    }

    suspend fun setFavoriteCount(count: Int) {
        context.favoritesDataStore.edit { prefs ->
            prefs[KEY_COUNT] = count.coerceIn(MIN_COUNT, MAX_COUNT)
        }
    }

    suspend fun setFavoriteKeys(keys: List<String>) {
        context.favoritesDataStore.edit { prefs ->
            prefs[KEY_FAVORITES] = keys.joinToString(SEPARATOR)
        }
    }

    suspend fun setHideSelf(hide: Boolean) {
        context.favoritesDataStore.edit { prefs ->
            prefs[KEY_HIDE_SELF] = hide
        }
    }

    suspend fun hideApp(key: String) {
        context.favoritesDataStore.edit { prefs ->
            val hidden = prefs[KEY_HIDDEN_APPS]
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                .orEmpty()
                .toMutableSet()
            hidden.add(key)
            prefs[KEY_HIDDEN_APPS] = hidden.joinToString(SEPARATOR)

            val favorites = prefs[KEY_FAVORITES]
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                .orEmpty()
                .filterNot { it == key }
            prefs[KEY_FAVORITES] = favorites.joinToString(SEPARATOR)
        }
    }

    suspend fun unhideApp(key: String) {
        context.favoritesDataStore.edit { prefs ->
            val hidden = prefs[KEY_HIDDEN_APPS]
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                .orEmpty()
                .toMutableSet()
            hidden.remove(key)
            prefs[KEY_HIDDEN_APPS] = hidden.joinToString(SEPARATOR)
        }
    }

    suspend fun setCustomLabel(key: String, label: String) {
        val trimmed = label.trim()
        context.favoritesDataStore.edit { prefs ->
            val labels = decodeCustomLabels(prefs[KEY_CUSTOM_LABELS]).toMutableMap()
            if (trimmed.isEmpty()) {
                labels.remove(key)
            } else {
                labels[key] = trimmed
            }
            prefs[KEY_CUSTOM_LABELS] = encodeCustomLabels(labels)
        }
    }

    suspend fun removeCustomLabel(key: String) {
        context.favoritesDataStore.edit { prefs ->
            val labels = decodeCustomLabels(prefs[KEY_CUSTOM_LABELS]).toMutableMap()
            labels.remove(key)
            prefs[KEY_CUSTOM_LABELS] = encodeCustomLabels(labels)
        }
    }

    suspend fun moveFavorite(key: String, direction: Int) {
        context.favoritesDataStore.edit { prefs ->
            val keys = prefs[KEY_FAVORITES]
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                .orEmpty()
                .toMutableList()
            val index = keys.indexOf(key)
            if (index < 0) return@edit
            val target = index + direction
            if (target !in keys.indices) return@edit
            val item = keys.removeAt(index)
            keys.add(target, item)
            prefs[KEY_FAVORITES] = keys.joinToString(SEPARATOR)
        }
    }

    companion object {
        const val MIN_COUNT = 3
        const val MAX_COUNT = 12
        const val DEFAULT_COUNT = 5

        private const val SEPARATOR = "\u001F"
        private const val LABEL_SEPARATOR = "\u001E"
        private val KEY_COUNT = intPreferencesKey("favorite_count")
        private val KEY_FAVORITES = stringPreferencesKey("favorite_keys")
        private val KEY_HIDE_SELF = booleanPreferencesKey("hide_self")
        private val KEY_HIDDEN_APPS = stringPreferencesKey("hidden_app_keys")
        private val KEY_CUSTOM_LABELS = stringPreferencesKey("custom_labels")

        private fun decodeCustomLabels(raw: String?): Map<String, String> =
            raw
                ?.split(SEPARATOR)
                ?.mapNotNull { entry ->
                    val parts = entry.split(LABEL_SEPARATOR, limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        parts[0] to parts[1]
                    } else {
                        null
                    }
                }
                ?.toMap()
                .orEmpty()

        private fun encodeCustomLabels(labels: Map<String, String>): String =
            labels.entries.joinToString(SEPARATOR) { (key, label) ->
                "$key$LABEL_SEPARATOR$label"
            }
    }
}
