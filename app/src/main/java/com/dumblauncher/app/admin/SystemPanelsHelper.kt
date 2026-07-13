package com.dumblauncher.app.admin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.lang.reflect.InvocationTargetException

@Suppress("DEPRECATION", "PrivateApi", "DiscouragedPrivateApi")
object SystemPanelsHelper {

    private const val TAG = "SystemPanelsHelper"

    fun expandNotificationsPanel(context: Context): Boolean =
        expandPanel(context)

    private fun expandPanel(context: Context): Boolean {
        val appContext = context.applicationContext
        val strategies = buildList {
            add { invokeViaContextStatusBarService(appContext) }
            add { invokeViaStatusbarString(appContext) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add { invokeViaTypedSystemService(appContext) }
            }
            add { invokeViaLegacyExpand(appContext) }
            add { invokeViaIStatusBarService() }
            add { invokeViaExpandBroadcast(appContext) }
        }

        for ((index, strategy) in strategies.withIndex()) {
            try {
                if (strategy()) {
                    Log.i(TAG, "Opened notifications via strategy $index")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Strategy $index failed for notifications", e)
            }
        }

        Log.w(TAG, "All strategies failed for notifications on API ${Build.VERSION.SDK_INT}")
        return false
    }

    private fun invokeViaContextStatusBarService(context: Context): Boolean {
        val service = context.getSystemService(Context.STATUS_BAR_SERVICE) ?: return false
        return invokeOnStatusBarManager(service, "expandNotificationsPanel")
    }

    private fun invokeViaStatusbarString(context: Context): Boolean {
        val service = context.getSystemService("statusbar") ?: return false
        return invokeOnStatusBarManager(service, "expandNotificationsPanel")
    }

    private fun invokeViaTypedSystemService(context: Context): Boolean {
        val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
        val service = context.getSystemService(statusBarManagerClass) ?: return false
        return invokeOnStatusBarManager(service, "expandNotificationsPanel")
    }

    private fun invokeViaLegacyExpand(context: Context): Boolean {
        val service = context.getSystemService("statusbar") ?: return false
        return invokeOnStatusBarManager(service, "expand")
    }

    private fun invokeViaIStatusBarService(): Boolean {
        return try {
            val binder = getStatusBarBinder() ?: return false
            val stubClass = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
            val service = stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                ?: return false

            service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
            true
        } catch (e: InvocationTargetException) {
            Log.w(TAG, "IStatusBarService.expandNotificationsPanel rejected call", e.targetException ?: e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "IStatusBarService.expandNotificationsPanel failed", e)
            false
        }
    }

    private fun getStatusBarBinder(): IBinder? {
        val serviceManager = Class.forName("android.os.ServiceManager")
        return serviceManager
            .getMethod("getService", String::class.java)
            .invoke(null, Context.STATUS_BAR_SERVICE) as? IBinder
    }

    private fun invokeViaExpandBroadcast(context: Context): Boolean {
        return runCatching {
            context.sendBroadcast(Intent("android.intent.action.EXPAND_STATUS_BAR"))
            false
        }.getOrDefault(false)
    }

    private fun invokeOnStatusBarManager(
        service: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>? = null,
        arguments: Array<Any?>? = null,
    ): Boolean {
        return try {
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = if (parameterTypes != null) {
                statusBarManager.getMethod(methodName, *parameterTypes)
            } else {
                statusBarManager.getMethod(methodName)
            }
            method.isAccessible = true
            if (arguments != null) {
                method.invoke(service, *arguments)
            } else {
                method.invoke(service)
            }
            true
        } catch (e: InvocationTargetException) {
            Log.w(TAG, "StatusBarManager.$methodName rejected call", e.targetException ?: e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "StatusBarManager.$methodName failed", e)
            false
        }
    }
}
