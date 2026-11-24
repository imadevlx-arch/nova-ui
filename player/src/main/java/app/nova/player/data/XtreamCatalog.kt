package app.nova.player.data

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStreamReader

/**
 * Client Xtream Codes (player_api.php). Lecture en streaming (JsonReader) :
 * les listes de 20 000+ chaînes passent sans saturer la mémoire d'une Fire Stick.
 */
class XtreamCatalog(private val account: Account.Xtream) : Catalog {

    override val supportsSeries = true
    private val cache = HashMap<Kind, List<Item>>()
    private val lock = Mutex()

    private fun api(action: String? = null, extra: String = ""): String {
        val base = "${account.server}/player_api.php?username=${enc(account.username)}&password=${enc(account.password)}"
        return if (action == null) base else "$base&action=$action$extra"
    }

    override suspend fun status(): AccountStatus = Http.get(api()) { response ->
        val body = response.body?.string().orEmpty()
        if (!body.trimStart().startsWith("{")) throw NovaException("Ce serveur ne répond pas comme un serveur Xtream.")
        val json = org.json.JSONObject(body)
        val info = json.optJSONObject("user_info") ?: throw NovaException("Identifiants incorrects.")
        val auth = info.opt("auth").toString()
        if (auth != "1" && auth != "true") throw NovaException("Identifiants incorrects.")
        val status = info.optString("status")
        val exp = info.optString("exp_date").toLongOrNull()?.times(1000)
        AccountStatus(
            active = status.equals("Active", ignoreCase = true),
            expiresAt = exp,
            maxConnections = info.optString("max_connections").toIntOrNull(),
            message = if (status.equals("Active", true)) null else "Abonnement : $status",
        )
    }

    override suspend fun categories(kind: Kind): List<Category> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_categories"
            Kind.MOVIE -> "get_vod_categories"
            Kind.SERIES -> "get_series_categories"
        }
        return Http.get(api(action)) { r ->
            readArray(JsonReader(InputStreamReader(r.body!!.byteStream()))) { o ->
                val id = o["category_id"] ?: return@readArray null
                Category(id, o["category_name"].orEmpty().ifBlank { "Sans nom" })
            }
        }
    }

    override suspend fun items(kind: Kind): List<Item> = lock.withLock {
        cache[kind]?.let { return it }
        val action = when (kind) {
            Kind.LIVE -> "get_live_streams"
            Kind.MOVIE -> "get_vod_streams"
            Kind.SERIES -> "get_series"
        }
        val list = Http.get(api(action)) { r ->
            readArray(JsonReader(InputStreamReader(r.body!!.byteStream()))) { o ->
                val id = (if (kind == Kind.SERIES) o["series_id"] else o["stream_id"]) ?: return@readArray null
                Item(
                    id = id,
                    name = o["name"].orEmpty().trim(),
                    image = (if (kind == Kind.SERIES) o["cover"] else o["stream_icon"])?.takeIf { it.startsWith("http") },
                    categoryId = o["category_id"].orEmpty(),
                    kind = kind,
                    ext = o["container_extension"],
                    added = ((if (kind == Kind.SERIES) o["last_modified"] else o["added"])?.toLongOrNull() ?: 0L) * 1000,
                    rating = (o["rating_5based"]?.toDoubleOrNull()?.times(2) ?: o["rating"]?.toDoubleOrNull()) ?: 0.0,
                )
            }
        }
        cache[kind] = list
        list
    }

    override suspend fun episodes(series: Item): List<Episode> =
        Http.get(api("get_series_info", "&series_id=${series.id}")) { r ->
            val json = org.json.JSONObject(r.body!!.string())
            val episodes = json.optJSONObject("episodes") ?: return@get emptyList<Episode>()
            val out = ArrayList<Episode>()
            episodes.keys().forEach { seasonKey ->
                val arr = episodes.optJSONArray(seasonKey) ?: return@forEach
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    val id = e.optString("id")
                    val ext = e.optString("container_extension").ifBlank { "mp4" }
                    out += Episode(
                        id = id,
                        title = e.optString("title").ifBlank { "Épisode ${e.optInt("episode_num")}" },
                        season = e.optInt("season", seasonKey.toIntOrNull() ?: 1),
                        number = e.optInt("episode_num"),
                        url = "${account.server}/series/${account.username}/${account.password}/$id.$ext",
                    )
                }
            }
            out.sortedWith(compareBy({ it.season }, { it.number }))
        }

    override fun streamUrl(item: Item): String {
        val u = account.username
        val p = account.password
        return when (item.kind) {
            // .ts : format natif des panels, le plus compatible ; Media3 le lit en progressif.
            Kind.LIVE -> "${account.server}/live/$u/$p/${item.id}.ts"
            Kind.MOVIE -> "${account.server}/movie/$u/$p/${item.id}.${item.ext ?: "mp4"}"
            Kind.SERIES -> error("Une série se lit épisode par épisode")
        }
    }

    private fun enc(s: String) = Uri.encode(s)

    /** Lit un tableau d'objets plats ; les valeurs sont toutes ramenées en chaîne. */
    private fun <T> readArray(reader: JsonReader, map: (Map<String, String?>) -> T?): List<T> = reader.use {
        val out = ArrayList<T>()
        if (it.peek() != JsonToken.BEGIN_ARRAY) return emptyList() // certains panels renvoient {} si vide
        it.beginArray()
        val fields = HashMap<String, String?>(16)
        while (it.hasNext()) {
            fields.clear()
            it.beginObject()
            while (it.hasNext()) {
                val name = it.nextName()
                fields[name] = when (it.peek()) {
                    JsonToken.STRING, JsonToken.NUMBER -> it.nextString()
                    JsonToken.BOOLEAN -> it.nextBoolean().toString()
                    else -> { it.skipValue(); null }
                }
            }
            it.endObject()
            map(fields)?.let(out::add)
        }
        it.endArray()
        out
    }
}
