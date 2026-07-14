package com.dumblauncher.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.ZoneId
class ScreenTimeRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    private val launcherPackages: Set<String> by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(homeIntent, 0)
        }
        resolved.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    private val countablePackageCache = HashMap<String, Boolean>()

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openScreenTime() {
        if (!hasUsageAccess()) {
            openUsageAccessSettings()
            return
        }
        val pm = context.packageManager
        val wellbeingPackages = listOf(
            "com.google.android.apps.wellbeing",
            "com.samsung.android.forest",
            "com.huawei.wellbeing",
            "com.coloros.digitalwellbeing",
        )
        for (pkg in wellbeingPackages) {
            val launch = pm.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch != null && startIfResolvable(launch)) return
        }
        val componentIntents = listOf(
            ComponentName(
                "com.google.android.apps.wellbeing",
                "com.google.android.apps.wellbeing.ui.DashboardActivity",
            ),
            ComponentName(
                "com.google.android.apps.wellbeing",
                "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity",
            ),
        )
        for (component in componentIntents) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startIfResolvable(intent)) return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent("android.settings.action.APP_USAGE_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startIfResolvable(intent)) return
        }
        openUsageAccessSettings()
    }

    private fun startIfResolvable(intent: Intent): Boolean {
        val pm = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, 0)
        } ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun readTodayScreenTimeText(): String? =
        if (hasUsageAccess()) formatDuration(queryTodayTotalMs()) else null

    fun observeTodayScreenTime(): Flow<String?> = callbackFlow {
        val zone = ZoneId.systemDefault()
        var trackedDate = LocalDate.now(zone)

        fun emitNow() {
            val today = LocalDate.now(zone)
            if (today != trackedDate) {
                Log.d(TAG, "Local date rolled over: $trackedDate -> $today")
                trackedDate = today
            }
            trySend(readTodayScreenTimeText())
        }

        emitNow()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                emitNow()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.distinctUntilChanged()

    private fun startOfTodayMs(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun queryTodayTotalMs(): Long {
        val start = startOfTodayMs()
        val end = System.currentTimeMillis()
        if (end <= start) return 0L

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val filteredMs = sumForegroundMsFromEvents(usm, start, end)
        val rawAggregateMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            usm.queryAndAggregateUsageStats(start, end)
                .values
                .sumOf { it.totalTimeInForeground }
        } else {
            0L
        }

        Log.d(
            TAG,
            "queryTodayTotalMs start=$start end=$end " +
                "filteredMs=$filteredMs rawAggregateMs=$rawAggregateMs " +
                "text=${formatDuration(filteredMs)}",
        )
        return filteredMs
    }

    /**
     * Sum today's foreground time by walking usage events once, tracking a single
     * foreground app at a time. Matches Digital Wellbeing by excluding system packages,
     * launchers, and overlays from the total.
     */
    private fun sumForegroundMsFromEvents(
        usm: UsageStatsManager,
        startMs: Long,
        endMs: Long,
    ): Long {
        val events = usm.queryEvents(startMs, endMs)
        val event = UsageEvents.Event()
        var foregroundPkg: String? = null
        var foregroundSince = 0L
        var totalMs = 0L

        fun closeSession(untilMs: Long) {
            val pkg = foregroundPkg ?: return
            if (!countsTowardScreenTime(pkg)) return
            val sessionStart = foregroundSince.coerceAtLeast(startMs)
            val sessionEnd = untilMs.coerceAtMost(endMs)
            if (sessionEnd > sessionStart) {
                totalMs += sessionEnd - sessionStart
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val timestamp = event.timeStamp

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED,
                -> {
                    if (isResumeEvent(event.eventType)) {
                        closeSession(timestamp)
                        foregroundPkg = pkg
                        foregroundSince = timestamp
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                -> {
                    if (isPauseEvent(event.eventType) && pkg == foregroundPkg) {
                        closeSession(timestamp)
                        foregroundPkg = null
                    }
                }
            }
        }

        closeSession(endMs)
        return totalMs
    }

    private fun countsTowardScreenTime(packageName: String): Boolean {
        return countablePackageCache.getOrPut(packageName) {
            countsTowardScreenTimeUncached(packageName)
        }
    }

    private fun countsTowardScreenTimeUncached(packageName: String): Boolean {
        if (packageName in ALWAYS_EXCLUDED_PACKAGES) return false
        if (packageName in launcherPackages) return false
        if (packageName == context.packageName) return false

        val appInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull() ?: return false

        val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val isUpdatedSystem = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        if (isSystem && !isUpdatedSystem) return false

        if (packageName.contains(".overlay", ignoreCase = true)) return false
        return true
    }

    private fun isResumeEvent(type: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            type == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }

    private fun isPauseEvent(type: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type == UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            type == UsageEvents.Event.MOVE_TO_BACKGROUND
        }
    }

    companion object {
        private const val TAG = "ScreenTimeRepository"

        private val ALWAYS_EXCLUDED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.inputmethod.latin",
            "com.google.android.apps.wellbeing",
        )

        fun formatDuration(ms: Long): String {
            val totalMinutes = (ms / 60_000L).coerceAtLeast(0)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        }
    }
}
