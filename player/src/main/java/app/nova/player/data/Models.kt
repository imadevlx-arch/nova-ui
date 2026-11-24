package app.nova.player.data

/** Un compte IPTV enregistré sur l'appareil. */
sealed interface Account {
    val label: String

    data class Xtream(val server: String, val username: String, val password: String) : Account {
        override val label: String get() = "$username · ${server.substringAfter("://")}"
    }

    data class M3u(val url: String) : Account {
        override val label: String get() = url.substringAfter("://").substringBefore("/")
    }
}

enum class Kind { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

data class Item(
    val id: String,
    val name: String,
    val image: String?,
    val categoryId: String,
    val kind: Kind,
    /** URL directe (M3U) ; null pour Xtream, l'URL est construite à la lecture. */
    val url: String? = null,
    val ext: String? = null,
    /** Date d'ajout sur le serveur (ms), 0 si inconnue. Sert aux « Tendances / Nouveautés ». */
    val added: Long = 0,
    val rating: Double = 0.0,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
)

/** Infos d'abonnement renvoyées par le serveur Xtream. */
data class AccountStatus(val active: Boolean, val expiresAt: Long?, val maxConnections: Int?, val message: String?)

/** Catalogue unifié : Xtream et M3U exposent la même surface à l'UI. */
interface Catalog {
    suspend fun status(): AccountStatus
    suspend fun categories(kind: Kind): List<Category>
    suspend fun items(kind: Kind): List<Item>
    suspend fun episodes(series: Item): List<Episode>
    fun streamUrl(item: Item): String
    val supportsSeries: Boolean
}

class NovaException(message: String, cause: Throwable? = null) : Exception(message, cause)
