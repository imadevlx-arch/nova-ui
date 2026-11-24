package app.nova.player.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.nova.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/**
 * Mise à jour intégrée : compare la version installée à la release « nova-player-latest »
 * (versionCode = numéro de build CI), télécharge l'APK et ouvre l'installateur Android.
 */
object Updater {
    private const val RELEASE_API = "https://api.github.com/repos/imadevlx-arch/nova-ui/releases/tags/nova-player-latest"

    suspend fun check(): UpdateInfo? = runCatching {
        Http.get(RELEASE_API) { r ->
            val o = JSONObject(r.body!!.string())
            // Nom de release : « Nova Player (dernière version) — 0.1.N »
            val name = Regex("""0\.1\.(\d+)""").find(o.optString("name"))?.value ?: return@get null
            val code = name.substringAfterLast('.').toInt()
            val assets = o.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url") ?: return@get null
            if (code > BuildConfig.VERSION_CODE) UpdateInfo(code, name, apk) else null
        }
    }.getOrNull()

    /** Télécharge puis lance l'installation. onProgress reçoit 0..1. */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "nova-player.apk")
        withContext(Dispatchers.IO) {
            Http.client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw NovaException("Téléchargement impossible (${resp.code}).")
                val body = resp.body ?: throw NovaException("Téléchargement vide.")
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
