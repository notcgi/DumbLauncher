package com.dumblauncher.app.data

data class LaunchableApp(
    /** Original system-provided app name from PackageManager. */
    val systemLabel: String,
    /** Label shown in UI: custom rename if set, otherwise [systemLabel]. */
    val displayLabel: String,
    val packageName: String,
    val activityName: String,
) {
    val key: String get() = "$packageName/$activityName"

    /**
     * Normalized label + layout-swap + translit forms for All Apps search.
     * Includes variants for both the system name and the displayed (possibly renamed) name.
     */
    val searchKeys: List<String> by lazy {
        LinkedHashSet<String>().apply {
            addAll(SearchTransliteration.variants(systemLabel))
            addAll(SearchTransliteration.variants(displayLabel))
        }.toList()
    }
}
