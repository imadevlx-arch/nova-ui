package app.nova.player.data

import app.nova.player.BuildConfig
import org.json.JSONObject

/**
 * Code d'activation Nova : le revendeur associe des identifiants à un code court
 * (backend/activation-worker), le client tape seulement ce code sur sa TV.
 */
object ActivationClient {
    val enabled: Boolean get() = BuildConfig.ACTIVATION_BASE_URL.isNotBlank()

    suspend fun resolve(code: String): Account {
        val clean = code.filter { it.isLetterOrDigit() }.uppercase()
        if (clean.length < 4) throw NovaException("Code trop court.")
        val base = BuildConfig.ACTIVATION_BASE_URL.trimEnd('/')
        return try {
            Http.get("$base/v1/code/$clean") { r ->
                val o = JSONObject(r.body!!.string())
                when (o.optString("type")) {
                    "xtream" -> Account.Xtream(
                        CredentialParser.normalizeServer(o.getString("server")),
                        o.getString("username"),
                        o.getString("password"),
                    )
                    "m3u" -> Account.M3u(o.getString("url"))
                    else -> throw NovaException("Code invalide.")
                }
            }
        } catch (e: NovaException) {
            if (e.message?.contains("404") == true) throw NovaException("Code inconnu ou expiré.") else throw e
        }
    }
}
