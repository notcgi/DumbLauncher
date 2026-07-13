package com.dumblauncher.app.data

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
) {
    val key: String get() = "$packageName/$activityName"

    /** Normalized label + layout-swap + translit forms for All Apps search. */
    val searchKeys: List<String> by lazy { SearchTransliteration.variants(label) }
}
