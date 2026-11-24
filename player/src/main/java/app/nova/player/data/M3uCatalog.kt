package app.nova.player.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Playlist M3U / M3U Plus, lue ligne par ligne. Films détectés par /movie/ ou extension vidéo. */
class M3uCatalog(private val account: Account.M3u) : Catalog {

    override val supportsSeries = false
    private var parsed: Parsed? = null
    private val lock = Mutex()

    private class Parsed(val items: Map<Kind, List<Item>>, val categories: Map<Kind, List<Category>>)

    private suspend fun load(): Parsed = lock.withLock {
        parsed?.let { return it }
        val result = Http.get(account.url) { r ->
            val reader = r.body!!.byteStream().bufferedReader()
            val items = mapOf(Kind.LIVE to ArrayList<Item>(), Kind.MOVIE to ArrayList())
            val groups = mapOf(Kind.LIVE to LinkedHashSet<String>(), Kind.MOVIE to LinkedHashSet())
            var pending: String? = null
            var index = 0
            var sawHeader = false
            reader.useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    when {
                        line.isEmpty() -> Unit
                        line.startsWith("#EXTM3U") -> sawHeader = true
                        line.startsWith("#EXTINF") -> pending = line
                        line.startsWith("#") -> Unit
                        else -> {
                            val info = pending.orEmpty()
                            pending = null
                            val kind = if (isMovie(line)) Kind.MOVIE else Kind.LIVE
                            val group = attr(info, "group-title")?.ifBlank { null } ?: "Autres"
                            groups.getValue(kind) += group
                            items.getValue(kind) += Item(
                                id = (index++).toString(),
                                name = info.substringAfterLast(',', "").trim().ifBlank { "Chaîne $index" },
                                image = attr(info, "tvg-logo")?.takeIf { it.startsWith("http") },
                                categoryId = group,
                                kind = kind,
                                url = line,
                            )
                        }
                    }
                }
            }
            if (!sawHeader && index == 0) throw NovaException("Ce lien ne renvoie pas une playlist M3U.")
            Parsed(items, groups.mapValues { (_, g) -> g.map { Category(it, it) } })
        }
        parsed = result
        result
    }

    override suspend fun status(): AccountStatus {
        val p = load()
        val count = p.items.values.sumOf { it.size }
        if (count == 0) throw NovaException("Playlist vide.")
        return AccountStatus(active = true, expiresAt = null, maxConnections = null, message = null)
    }

    override suspend fun categories(kind: Kind) = load().categories[kind].orEmpty()
    override suspend fun items(kind: Kind) = load().items[kind].orEmpty()
    override suspend fun episodes(series: Item) = emptyList<Episode>()
    override fun streamUrl(item: Item) = item.url!!

    private fun isMovie(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return "/movie/" in path || path.endsWith(".mkv") || path.endsWith(".mp4") || path.endsWith(".avi")
    }

    private fun attr(info: String, name: String): String? =
        Regex("""$name="([^"]*)"""").find(info)?.groupValues?.get(1)
}
