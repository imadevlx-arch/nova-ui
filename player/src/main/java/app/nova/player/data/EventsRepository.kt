package app.nova.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Competition(
    val name: String,
    val league: String,
    val sport: String,
    val priority: Int,
    val hours: Double,
    val channels: List<String>,
)

data class EventsConfig(val competitions: List<Competition>, val eventCategoryWords: List<String>)

data class SportEvent(
    val id: String,
    val title: String,
    val home: String?,
    val away: String?,
    val competition: Competition,
    val start: Long,
    val end: Long,
    val homeBadge: String?,
    val awayBadge: String?,
) {
    fun isLive(now: Long = System.currentTimeMillis()) = now in start..end
}

/** Un événement et les chaînes de la playlist du client qui le diffusent, la meilleure en premier. */
data class EventRow(val event: SportEvent, val channels: List<Item>)

/**
 * Événements sportifs (foot + MMA) : calendrier TheSportsDB (clé gratuite),
 * diffuseurs lus dans events-config.json (copie GitHub à jour, sinon copie embarquée),
 * puis recherche des chaînes correspondantes dans la playlist du client.
 */
object EventsRepository {

    private const val CONFIG_URL = "https://raw.githubusercontent.com/imadevlx-arch/nova-ui/main/player/src/main/assets/events-config.json"
    private const val TSDB = "https://www.thesportsdb.com/api/v1/json/123"
    private const val CACHE_MS = 20 * 60 * 1000L

    private var cachedEvents: Pair<Long, List<SportEvent>>? = null

    suspend fun config(context: Context): EventsConfig {
        val remote = runCatching { Http.get(CONFIG_URL) { it.body!!.string() } }.getOrNull()
        val text = remote?.takeIf { it.trimStart().startsWith("{") }
            ?: withContext(Dispatchers.IO) { context.assets.open("events-config.json").bufferedReader().use { it.readText() } }
        return parseConfig(text)
    }

    private fun parseConfig(text: String): EventsConfig {
        val o = JSONObject(text)
        val comps = o.getJSONArray("competitions")
        val list = (0 until comps.length()).map { i ->
            val c = comps.getJSONObject(i)
            val ch = c.getJSONArray("channels")
            Competition(
                name = c.getString("name"),
                league = c.getString("league"),
                sport = c.optString("sport", "foot"),
                priority = c.optInt("priority", 50),
                hours = c.optDouble("hours", 2.2),
                channels = (0 until ch.length()).map { ch.getString(it) },
            )
        }
        val words = o.optJSONArray("eventCategoryWords")
        return EventsConfig(list, words?.let { w -> (0 until w.length()).map { w.getString(it) } } ?: emptyList())
    }

    /** Événements en cours ou à venir dans les 36 h. En direct d'abord, puis par heure. */
    suspend fun events(config: EventsConfig, force: Boolean = false): List<SportEvent> {
        val now = System.currentTimeMillis()
        cachedEvents?.let { (at, list) -> if (!force && now - at < CACHE_MS) return list }

        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val dates = listOf(-1, 0, 1).map { day.format(Date(now + it * 86_400_000L)) }
        val gate = Semaphore(3) // API gratuite : 30 requêtes / minute
        val all = coroutineScope {
            config.competitions.flatMap { comp ->
                // La veille seulement pour les soirées longues (UFC la nuit)
                dates.filterIndexed { i, _ -> i > 0 || comp.hours > 3 }.map { d ->
                    async { gate.withPermit { runCatching { fetchDay(comp, d) }.getOrElse { emptyList() } } }
                }
            }.awaitAll().flatten()
        }
        val list = all
            .distinctBy { it.id }
            .filter { it.end > now && it.start < now + 36 * 3_600_000L }
            .sortedWith(compareByDescending<SportEvent> { it.isLive(now) }.thenBy { it.start }.thenByDescending { it.competition.priority })
        cachedEvents = now to list
        return list
    }

    private suspend fun fetchDay(comp: Competition, date: String): List<SportEvent> {
        val url = "$TSDB/eventsday.php?d=$date&l=${URLEncoder.encode(comp.league, "UTF-8")}"
        return Http.get(url) { r ->
            val o = JSONObject(r.body!!.string())
            val arr = o.optJSONArray("events") ?: return@get emptyList<SportEvent>()
            (0 until arr.length()).mapNotNull { i ->
                val e = arr.getJSONObject(i)
                val start = parseTime(e.optString("strTimestamp"), e.optString("dateEvent"), e.optString("strTime")) ?: return@mapNotNull null
                val home = e.optString("strHomeTeam").ifBlank { null }?.takeIf { it != "null" }
                val away = e.optString("strAwayTeam").ifBlank { null }?.takeIf { it != "null" }
                SportEvent(
                    id = e.optString("idEvent"),
                    title = if (comp.sport == "foot" && home != null && away != null) "$home – $away" else e.optString("strEvent"),
                    home = home,
                    away = away,
                    competition = comp,
                    start = start,
                    end = start + (comp.hours * 3_600_000L).toLong(),
                    homeBadge = e.optString("strHomeTeamBadge").takeIf { it.startsWith("http") },
                    awayBadge = e.optString("strAwayTeamBadge").takeIf { it.startsWith("http") },
                )
            }
        }
    }

    private fun parseTime(ts: String, date: String, time: String): Long? {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        if (ts.length >= 19) runCatching { return f.parse(ts.take(19))!!.time }
        if (date.length == 10 && time.length >= 5) {
            val hms = if (time.length >= 8) time.take(8) else time.take(5) + ":00"
            runCatching { return f.parse("${date}T$hms")!!.time }
        }
        return null
    }

    // ---------- Correspondance événement → chaînes ----------

    class ChannelIndex(channels: List<Item>, categoryNames: Map<String, String>, eventWords: List<String>) {
        val entries: List<Triple<Item, String, Boolean>> = channels.map { item ->
            val cat = norm(categoryNames[item.categoryId].orEmpty())
            Triple(item, norm(item.name), eventWords.any { cat.contains(norm(it)) })
        }
    }

    fun match(event: SportEvent, index: ChannelIndex, limit: Int = 8): List<Item> {
        val homeTokens = teamTokens(event.home)
        val awayTokens = teamTokens(event.away)
        val keywords = event.competition.channels.map(::norm)
        return index.entries.asSequence()
            .mapNotNull { (item, name, isEventCategory) ->
                val padded = " $name "
                val homeHit = homeTokens.any { padded.contains(" $it ") }
                val awayHit = awayTokens.any { padded.contains(" $it ") }
                var score = when {
                    homeHit && awayHit -> 200                     // chaîne dédiée « PSG vs OM »
                    isEventCategory && (homeHit || awayHit) -> 150
                    else -> {
                        val k = keywords.indexOfFirst { kw -> padded.contains(" $kw ") || name.startsWith("$kw ") || name == kw }
                        if (k >= 0) 100 - k * 5 else 0
                    }
                }
                if (score == 0) return@mapNotNull null
                val tokens = name.split(' ')
                if (tokens.firstOrNull() in FR_PREFIX) score += 8
                if (tokens.firstOrNull() in FOREIGN_PREFIX) score -= 25
                if (tokens.any { it in setOf("fhd", "uhd", "4k") }) score += 3 else if ("hd" in tokens) score += 2
                item to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.name }
            .take(limit)
            .toList()
    }

    private val STOP = setOf("fc", "ac", "as", "sc", "cf", "afc", "club", "de", "del", "la", "le", "real", "sporting", "united", "city", "olympique", "stade", "racing", "union", "athletic", "atletico", "vs")
    private val FR_PREFIX = setOf("fr", "france", "french")
    private val FOREIGN_PREFIX = setOf("uk", "us", "usa", "ar", "es", "it", "de", "pt", "pl", "nl", "be", "ch", "ca", "tr", "ex", "ru", "af", "latino", "mx", "br")

    private fun teamTokens(team: String?): List<String> =
        team?.let { norm(it).split(' ').filter { t -> t.length >= 4 && t !in STOP } }.orEmpty()

    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9+]+"), " ")
            .trim()
}
