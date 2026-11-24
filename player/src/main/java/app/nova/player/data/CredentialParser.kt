package app.nova.player.data

import android.net.Uri

/**
 * Transforme ce que le client colle (lien M3U, lien get.php, lien player_api, URL de flux,
 * bloc de texte WhatsApp avec « Username: … Password: … ») en compte utilisable.
 * Objectif : le client colle n'importe quoi, on se débrouille.
 */
object CredentialParser {

    fun normalizeServer(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (!s.contains("://")) s = "http://$s"
        val uri = Uri.parse(s)
        val host = uri.host ?: return s
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "${uri.scheme ?: "http"}://$host$port"
    }

    fun parse(input: String): Account? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val url = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
        val host = field(text, "host|url|server|serveur|portal|dns") ?: url
        val user = field(text, "username|user|utilisateur|login|identifiant")
        val pass = field(text, "password|pass|mot de passe|mdp")

        // 1) Une URL qui contient déjà les identifiants (get.php, flux…) gagne.
        val fromLink = url?.let(::fromUrl)
        if (fromLink is Account.Xtream) return fromLink

        // 2) Bloc texte WhatsApp : « Host/URL : … », « User : … », « Pass : … »
        if (host != null && user != null && pass != null) {
            return Account.Xtream(normalizeServer(host), user, pass)
        }
        if (fromLink != null) return fromLink

        // 3) URL sans schéma (ex. « monserveur.com:8080/get.php?... »)
        if (url == null && Regex("""^[\w.-]+\.\w{2,}(:\d+)?(/\S*)?$""").matches(text)) {
            return fromUrl("http://$text")
        }
        return null
    }

    private fun fromUrl(url: String): Account? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        val user = uri.getQueryParameter("username")
        val pass = uri.getQueryParameter("password")
        val path = uri.path.orEmpty()

        // get.php / player_api.php / xmltv.php avec identifiants → Xtream (plus riche que le M3U brut)
        if (!user.isNullOrBlank() && !pass.isNullOrBlank() &&
            (path.endsWith("get.php") || path.endsWith("player_api.php") || path.endsWith("xmltv.php"))
        ) {
            return Account.Xtream(normalizeServer(url), user, pass)
        }

        // URL de flux Xtream : /live/user/pass/123.ts, /movie/user/pass/1.mkv, /user/pass/123
        val seg = uri.pathSegments
        if (seg.size >= 4 && seg[0] in setOf("live", "movie", "series")) {
            return Account.Xtream(normalizeServer(url), seg[1], seg[2])
        }

        return Account.M3u(url)
    }

    private fun field(text: String, keys: String): String? =
        Regex("""(?im)^\s*[^\w\n]*\s*(?:$keys)\s*[:=]\s*(\S+)""").find(text)?.groupValues?.get(1)
}
