package app.nova.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Un film ou un épisode lancé : de quoi l'afficher dans « Récents » et reprendre à la seconde près. */
data class ResumeEntry(
    val key: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val url: String,
    val position: Long,
    val duration: Long,
    val updatedAt: Long,
) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/**
 * Récents, par compte. Stockage local (SharedPreferences, JSON), 40 entrées max.
 * Un contenu terminé (> 95 %) sort de la liste.
 */
class ResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("nova_resume", Context.MODE_PRIVATE)
    private var cache: MutableList<ResumeEntry>? = null

    fun all(): List<ResumeEntry> = load().sortedByDescending { it.updatedAt }

    fun get(key: String): ResumeEntry? = load().firstOrNull { it.key == key }

    /** Appelé au lancement : le contenu apparaît tout de suite dans Récents. */
    fun touch(key: String, title: String, subtitle: String?, image: String?, url: String) {
        val list = load()
        val old = list.firstOrNull { it.key == key }
        list.removeAll { it.key == key }
        list += ResumeEntry(key, title, subtitle, image, url, old?.position ?: 0, old?.duration ?: 0, System.currentTimeMillis())
        persist(list)
    }

    fun savePosition(key: String, position: Long, duration: Long) {
        val list = load()
        val i = list.indexOfFirst { it.key == key }
        if (i < 0) return
        if (duration > 0 && position > duration * 0.95) {
            list.removeAt(i)
        } else {
            list[i] = list[i].copy(position = position, duration = duration, updatedAt = System.currentTimeMillis())
        }
        persist(list)
    }

    fun remove(key: String) {
        val list = load()
        if (list.removeAll { it.key == key }) persist(list)
    }

    fun clear(prefix: String) {
        val list = load()
        if (list.removeAll { it.key.startsWith(prefix) }) persist(list)
    }

    private fun load(): MutableList<ResumeEntry> {
        cache?.let { return it }
        val list = runCatching {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ResumeEntry(
                    key = o.getString("key"),
                    title = o.getString("title"),
                    subtitle = o.optString("subtitle").ifBlank { null },
                    image = o.optString("image").ifBlank { null },
                    url = o.getString("url"),
                    position = o.optLong("position"),
                    duration = o.optLong("duration"),
                    updatedAt = o.optLong("updatedAt"),
                )
            }.toMutableList()
        }.getOrElse { mutableListOf() }
        cache = list
        return list
    }

    private fun persist(list: MutableList<ResumeEntry>) {
        val trimmed = list.sortedByDescending { it.updatedAt }.take(MAX).toMutableList()
        cache = trimmed
        val arr = JSONArray()
        trimmed.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key).put("title", it.title).put("subtitle", it.subtitle ?: "")
                    .put("image", it.image ?: "").put("url", it.url).put("position", it.position)
                    .put("duration", it.duration).put("updatedAt", it.updatedAt),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX = 40
    }
}
