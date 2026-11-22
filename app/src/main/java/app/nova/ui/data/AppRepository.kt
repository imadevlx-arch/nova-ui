package app.nova.ui.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Une app installée, prête à afficher. `banner` = visuel TV 16:9 ; `icon` = repli. */
data class TvApp(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    suspend fun loadApps(): List<TvApp> = withContext(Dispatchers.IO) {
        val apps = LinkedHashMap<String, TvApp>()
        // Les apps TV d'abord (elles ont une bannière), puis les apps « téléphone » installées en sideload.
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, apps)
        collect(Intent.CATEGORY_LAUNCHER, apps)
        apps.values.sortedBy { it.label.lowercase() }
    }

    private fun collect(category: String, into: MutableMap<String, TvApp>) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        for (info in resolved) {
            val activity = info.activityInfo ?: continue
            val pkg = activity.packageName
            if (pkg == context.packageName || into.containsKey(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val banner = runCatching { activity.loadBanner(pm) ?: activity.applicationInfo?.loadBanner(pm) }.getOrNull()
            val icon = runCatching { info.loadIcon(pm) }.getOrNull()
            into[pkg] = TvApp(
                packageName = pkg,
                label = label,
                banner = banner?.toScaledImageBitmap(maxEdge = 640),
                icon = icon?.toScaledImageBitmap(maxEdge = 192),
            )
        }
    }

    fun launchIntent(packageName: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Réduit les gros visuels pour économiser la RAM des Fire Stick, en gardant le ratio. */
private fun Drawable.toScaledImageBitmap(maxEdge: Int): ImageBitmap {
    val w = if (intrinsicWidth > 0) intrinsicWidth else maxEdge
    val h = if (intrinsicHeight > 0) intrinsicHeight else maxEdge
    val scale = minOf(1f, maxEdge.toFloat() / max(w, h))
    val tw = max(1, (w * scale).toInt())
    val th = max(1, (h * scale).toInt())
    return toBitmap(tw, th).asImageBitmap()
}
