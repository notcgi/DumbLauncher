package com.dumblauncher.app.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Calendar

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

    fun observeTodayScreenTime(): Flow<String?> = flow {
        while (true) {
            emit(readTodayScreenTimeText())
            delay(60_000L)
        }
    }.flowOn(Dispatchers.Default)

    private fun queryTodayTotalMs(): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: return 0L
        return stats.sumOf { it.totalTimeInForeground }
    }

    companion object {
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
