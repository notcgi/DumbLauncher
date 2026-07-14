package com.dumblauncher.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        val totalMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            usm.queryAndAggregateUsageStats(start, end)
                .values
                .sumOf { it.totalTimeInForeground }
        } else {
            sumForegroundMsFromEvents(usm, start, end)
        }

        Log.d(
            TAG,
            "queryTodayTotalMs start=$start end=$end totalMs=$totalMs text=${formatDuration(totalMs)}",
        )
        return totalMs
    }

    /**
     * Sum foreground durations from usage events within [startMs, endMs].
     * Used on API 26–27 where [UsageStatsManager.queryAndAggregateUsageStats] is unavailable.
     */
    private fun sumForegroundMsFromEvents(
        usm: UsageStatsManager,
        startMs: Long,
        endMs: Long,
    ): Long {
        val events = usm.queryEvents(startMs, endMs)
        val event = UsageEvents.Event()
        val activeSince = HashMap<String, Long>()
        var totalMs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED,
                -> {
                    if (isResumeEvent(event.eventType)) {
                        activeSince[pkg] = event.timeStamp.coerceAtLeast(startMs)
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                -> {
                    if (isPauseEvent(event.eventType)) {
                        val sessionStart = activeSince.remove(pkg) ?: continue
                        totalMs += (event.timeStamp.coerceAtMost(endMs) - sessionStart).coerceAtLeast(0)
                    }
                }
            }
        }

        for ((_, sessionStart) in activeSince) {
            totalMs += (endMs - sessionStart).coerceAtLeast(0)
        }
        return totalMs
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
