package com.dumblauncher.app.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

class AppsRepository(private val context: Context) {

    fun observeApps(hideSelf: Boolean): Flow<List<LaunchableApp>> = callbackFlow {
        val emit = {
            trySend(loadApps(hideSelf))
        }
        emit()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                emit()
            }
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

    fun loadApps(hideSelf: Boolean): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.ResolveInfoFlags.of(0L)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, flags)
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val selfPackage = context.packageName
        return resolved
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName
                if (hideSelf && packageName == selfPackage) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                LaunchableApp(
                    label = label,
                    packageName = packageName,
                    activityName = activityInfo.name,
                )
            }
            .distinctBy { it.key }
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
    }

    fun launch(app: LaunchableApp) {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(app.packageName, app.activityName)
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }

    fun launchClock() {
        val pm = context.packageManager
        val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startIfResolvable(showAlarms)) return

        val clockPackages = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.huawei.deskclock",
            "com.coloros.alarmclock",
        )
        for (pkg in clockPackages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: continue
            if (startIfResolvable(intent)) return
        }
    }

    fun launchCalendar() {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val viewDay = Intent(Intent.ACTION_VIEW).apply {
            data = ContentUris.withAppendedId(CalendarContract.CONTENT_URI, now)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startIfResolvable(viewDay)) return

        val appCalendar = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startIfResolvable(appCalendar)) return

        val calendarPackages = listOf(
            "com.google.android.calendar",
            "com.android.calendar",
            "com.samsung.android.calendar",
            "com.huawei.calendar",
        )
        for (pkg in calendarPackages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: continue
            if (startIfResolvable(intent)) return
        }
    }

    private fun startIfResolvable(intent: Intent): Boolean {
        val pm = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, 0)
        } ?: return false
        intent.component = ComponentName(
            resolved.activityInfo.packageName,
            resolved.activityInfo.name,
        )
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        fun applyCustomizations(
            apps: List<LaunchableApp>,
            hiddenKeys: Set<String>,
            customLabels: Map<String, String>,
            includeHidden: Boolean = false,
        ): List<LaunchableApp> {
            return apps
                .filter { includeHidden || it.key !in hiddenKeys }
                .map { app ->
                    customLabels[app.key]?.let { custom -> app.copy(label = custom) } ?: app
                }
        }

        fun filterByQuery(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
            val q = query.trim().lowercase(Locale.getDefault())
            if (q.isEmpty()) return apps
            return apps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
        }
    }
}
