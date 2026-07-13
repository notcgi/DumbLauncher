package com.dumblauncher.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dumblauncher.app.data.AppsRepository
import com.dumblauncher.app.data.BatteryRepository
import com.dumblauncher.app.data.ClockRepository
import com.dumblauncher.app.data.ClockState
import com.dumblauncher.app.data.FavoritesSettings
import com.dumblauncher.app.data.FavoritesStore
import com.dumblauncher.app.data.LaunchableApp
import com.dumblauncher.app.data.ScreenTimeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val clock: ClockState = ClockState("--:--", ""),
    val batteryPercent: Int = 0,
    val screenTimeText: String? = null,
    val favorites: List<LaunchableApp> = emptyList(),
    val favoriteCount: Int = FavoritesStore.DEFAULT_COUNT,
    val hasUsageAccess: Boolean = false,
)

data class AllAppsUiState(
    val apps: List<LaunchableApp> = emptyList(),
    val query: String = "",
)

data class SettingsUiState(
    val favoriteCount: Int = FavoritesStore.DEFAULT_COUNT,
    val hideSelf: Boolean = true,
    val favoriteKeys: List<String> = emptyList(),
    val allApps: List<LaunchableApp> = emptyList(),
    val hiddenApps: List<LaunchableApp> = emptyList(),
    val renamedApps: List<LaunchableApp> = emptyList(),
    val hasUsageAccess: Boolean = false,
)

enum class LauncherDestination {
    Home,
    AllApps,
    Settings,
}

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(
    private val appsRepository: AppsRepository,
    private val favoritesStore: FavoritesStore,
    clockRepository: ClockRepository,
    batteryRepository: BatteryRepository,
    private val screenTimeRepository: ScreenTimeRepository,
) : ViewModel() {

    private val settings = favoritesStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, FavoritesSettings())

    private val apps = settings
        .flatMapLatest { appsRepository.observeApps(it.hideSelf) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Keep favorites/custom labels resilient to app updates (e.g. launcher activity renamed).
        viewModelScope.launch {
            combine(apps, settings) { appList, favSettings ->
                val migratedFavorites = migrateKeys(
                    keys = favSettings.favoriteKeys,
                    apps = appList,
                )
                val migratedLabels = migrateLabels(
                    labels = favSettings.customLabels,
                    apps = appList,
                )
                MigrationResult(
                    favorites = migratedFavorites,
                    labels = migratedLabels,
                    favoritesChanged = migratedFavorites != favSettings.favoriteKeys,
                    labelsChanged = migratedLabels != favSettings.customLabels,
                )
            }
                .distinctUntilChanged()
                .collect { result ->
                    if (result.favoritesChanged) {
                        favoritesStore.setFavoriteKeys(result.favorites)
                    }
                    if (result.labelsChanged) {
                        favoritesStore.setCustomLabels(result.labels)
                    }
                }
        }
    }

    private val clock = clockRepository.observeClock()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ClockState("--:--", ""))

    private val battery = batteryRepository.observeBatteryPercent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val usageAccessRefresh = MutableStateFlow(0L)

    private val screenTime = merge(
        screenTimeRepository.observeTodayScreenTime(),
        usageAccessRefresh.map { screenTimeRepository.readTodayScreenTimeText() },
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _destination = MutableStateFlow(LauncherDestination.Home)
    val destination: StateFlow<LauncherDestination> = _destination

    val allAppsQuery = MutableStateFlow("")

    private val homeBase: StateFlow<HomeUiState> = combine(
        clock,
        battery,
        screenTime,
        apps,
        settings,
    ) { clockState, batteryPercent, screenTimeText, appList, favSettings ->
        val customized = AppsRepository.applyCustomizations(
            apps = appList,
            hiddenKeys = favSettings.hiddenAppKeys,
            customLabels = favSettings.customLabels,
        )
        val byKey = customized.associateBy { it.key }
        val favorites = favSettings.favoriteKeys.mapNotNull { byKey[it] }
            .take(favSettings.favoriteCount)
        HomeUiState(
            clock = clockState,
            batteryPercent = batteryPercent,
            screenTimeText = screenTimeText,
            favorites = favorites,
            favoriteCount = favSettings.favoriteCount,
            hasUsageAccess = screenTimeRepository.hasUsageAccess(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    val homeState: StateFlow<HomeUiState> = combine(homeBase, usageAccessRefresh) { state, _ ->
        state.copy(
            hasUsageAccess = screenTimeRepository.hasUsageAccess(),
            screenTimeText = screenTimeRepository.readTodayScreenTimeText() ?: state.screenTimeText,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    val allAppsState: StateFlow<AllAppsUiState> = combine(apps, allAppsQuery, settings) { appList, query, favSettings ->
        val visible = AppsRepository.applyCustomizations(
            apps = appList,
            hiddenKeys = favSettings.hiddenAppKeys,
            customLabels = favSettings.customLabels,
        )
        AllAppsUiState(
            apps = AppsRepository.filterByQuery(visible, query),
            query = query,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AllAppsUiState())

    val settingsState: StateFlow<SettingsUiState> = combine(
        combine(apps, settings) { appList, favSettings -> appList to favSettings },
        usageAccessRefresh,
    ) { (appList, favSettings), _ ->
        val customized = AppsRepository.applyCustomizations(
            apps = appList,
            hiddenKeys = favSettings.hiddenAppKeys,
            customLabels = favSettings.customLabels,
        )
        val allIncludingHidden = AppsRepository.applyCustomizations(
            apps = appList,
            hiddenKeys = favSettings.hiddenAppKeys,
            customLabels = favSettings.customLabels,
            includeHidden = true,
        )
        val byKey = allIncludingHidden.associateBy { it.key }
        val hiddenApps = favSettings.hiddenAppKeys.mapNotNull { byKey[it] }
        val renamedApps = favSettings.customLabels.keys.mapNotNull { byKey[it] }
        SettingsUiState(
            favoriteCount = favSettings.favoriteCount,
            hideSelf = favSettings.hideSelf,
            favoriteKeys = favSettings.favoriteKeys,
            allApps = customized,
            hiddenApps = hiddenApps,
            renamedApps = renamedApps,
            hasUsageAccess = screenTimeRepository.hasUsageAccess(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun launch(app: LaunchableApp) {
        appsRepository.launch(app)
    }

    fun navigateTo(destination: LauncherDestination) {
        if (destination == LauncherDestination.AllApps) {
            clearAllAppsQuery()
        }
        _destination.value = destination
    }

    /** System Home / launcher: show main home and clear all-apps search. */
    fun goHome() {
        clearAllAppsQuery()
        _destination.value = LauncherDestination.Home
    }

    fun setAllAppsQuery(query: String) {
        allAppsQuery.value = query
    }

    fun clearAllAppsQuery() {
        allAppsQuery.value = ""
    }

    fun setFavoriteCount(count: Int) {
        viewModelScope.launch { favoritesStore.setFavoriteCount(count) }
    }

    fun setHideSelf(hide: Boolean) {
        viewModelScope.launch { favoritesStore.setHideSelf(hide) }
    }

    fun toggleFavorite(app: LaunchableApp) {
        viewModelScope.launch {
            val current = settings.value
            val keys = migrateKeys(current.favoriteKeys, apps.value).toMutableList()
            if (keys.contains(app.key)) {
                keys.remove(app.key)
            } else if (keys.size < current.favoriteCount) {
                keys.add(app.key)
            }
            favoritesStore.setFavoriteKeys(keys)
        }
    }

    fun moveFavorite(key: String, direction: Int) {
        viewModelScope.launch { favoritesStore.moveFavorite(key, direction) }
    }

    fun hideApp(app: LaunchableApp) {
        viewModelScope.launch { favoritesStore.hideApp(app.key) }
    }

    fun renameApp(app: LaunchableApp, newLabel: String) {
        viewModelScope.launch { favoritesStore.setCustomLabel(app.key, newLabel) }
    }

    fun uninstallApp(app: LaunchableApp) {
        appsRepository.uninstall(app.packageName)
    }

    fun unhideApp(key: String) {
        viewModelScope.launch { favoritesStore.unhideApp(key) }
    }

    fun resetAppLabel(key: String) {
        viewModelScope.launch { favoritesStore.removeCustomLabel(key) }
    }

    fun openClock() {
        appsRepository.launchClock()
    }

    fun openCalendar() {
        appsRepository.launchCalendar()
    }

    fun openScreenTime() {
        screenTimeRepository.openScreenTime()
    }

    fun openUsageAccessSettings() {
        screenTimeRepository.openUsageAccessSettings()
    }

    fun refreshUsageAccess() {
        usageAccessRefresh.value = System.currentTimeMillis()
    }

    class Factory(
        private val appsRepository: AppsRepository,
        private val favoritesStore: FavoritesStore,
        private val clockRepository: ClockRepository,
        private val batteryRepository: BatteryRepository,
        private val screenTimeRepository: ScreenTimeRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LauncherViewModel(
                appsRepository,
                favoritesStore,
                clockRepository,
                batteryRepository,
                screenTimeRepository,
            ) as T
        }
    }
}

private data class MigrationResult(
    val favorites: List<String>,
    val labels: Map<String, String>,
    val favoritesChanged: Boolean,
    val labelsChanged: Boolean,
)

private fun migrateKeys(keys: List<String>, apps: List<LaunchableApp>): List<String> {
    if (keys.isEmpty() || apps.isEmpty()) return keys

    val availableKeys = apps.asSequence().map { it.key }.toSet()
    val preferredKeyByPackage = LinkedHashMap<String, String>().apply {
        for (app in apps) {
            putIfAbsent(app.packageName, app.key)
        }
    }

    val out = ArrayList<String>(keys.size)
    val seen = HashSet<String>(keys.size)

    fun add(key: String) {
        if (seen.add(key)) out.add(key)
    }

    for (key in keys) {
        if (key in availableKeys) {
            add(key)
            continue
        }
        val pkg = key.substringBefore('/', missingDelimiterValue = "")
        val migrated = if (pkg.isNotBlank()) preferredKeyByPackage[pkg] else null
        if (migrated != null) add(migrated)
        // else: drop orphan key (uninstalled app / no longer launchable)
    }
    return out
}

private fun migrateLabels(labels: Map<String, String>, apps: List<LaunchableApp>): Map<String, String> {
    if (labels.isEmpty() || apps.isEmpty()) return labels

    val availableKeys = apps.asSequence().map { it.key }.toSet()
    val preferredKeyByPackage = LinkedHashMap<String, String>().apply {
        for (app in apps) {
            putIfAbsent(app.packageName, app.key)
        }
    }

    var changed = false
    val out = LinkedHashMap<String, String>(labels.size)
    for ((key, label) in labels) {
        if (key in availableKeys) {
            out[key] = label
            continue
        }

        val pkg = key.substringBefore('/', missingDelimiterValue = "")
        val migratedKey = if (pkg.isNotBlank()) preferredKeyByPackage[pkg] else null
        if (migratedKey == null) {
            changed = true
            continue
        }
        if (!out.containsKey(migratedKey)) {
            out[migratedKey] = label
        }
        changed = true
    }
    return if (changed) out else labels
}
