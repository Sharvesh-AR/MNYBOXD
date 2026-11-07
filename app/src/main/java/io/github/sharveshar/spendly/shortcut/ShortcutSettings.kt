package io.github.sharveshar.spendly.shortcut

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** System-side state of the Quick Add shortcuts. Read fresh whenever Settings is shown. */
object ShortcutSettings {

    /** True when the user has switched [ButtonShortcutService] on in Accessibility settings. */
    fun isButtonShortcutOn(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val ours = ComponentName(context, ButtonShortcutService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
            val service = info.resolveInfo?.serviceInfo
            service?.packageName == ours.packageName && service.name == ours.className
        }
    }

    /** Android offers no public way for apps to open their own service's page, only the list. */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** App info, where Android 13+ hides "Allow restricted settings" for sideloaded apps. */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isAppIconOn(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(launcherEntry(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Shows or hides the "Add expense" entry in the app list. Off is the manifest default. */
    fun setAppIconOn(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            launcherEntry(context),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun launcherEntry(context: Context) = ComponentName(context, QuickAddLauncherActivity::class.java)
}
