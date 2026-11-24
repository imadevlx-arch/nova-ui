package app.nova.player.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager

/** TV (Fire TV, Android TV) ou téléphone/tablette : adapte les méthodes de connexion et les gestes. */
fun Context.isTv(): Boolean {
    val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature("amazon.hardware.fire_tv") ||
        !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}
