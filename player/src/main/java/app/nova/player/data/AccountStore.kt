package app.nova.player.data

import android.content.Context
import org.json.JSONObject

/** Compte actif stocké localement (jamais envoyé ailleurs que vers le serveur IPTV du client). */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("nova_accounts", Context.MODE_PRIVATE)

    fun load(): Account? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            when (o.getString("type")) {
                "xtream" -> Account.Xtream(o.getString("server"), o.getString("username"), o.getString("password"))
                "m3u" -> Account.M3u(o.getString("url"))
                else -> null
            }
        }.getOrNull()
    }

    fun save(account: Account) {
        val o = JSONObject()
        when (account) {
            is Account.Xtream -> o.put("type", "xtream").put("server", account.server)
                .put("username", account.username).put("password", account.password)
            is Account.M3u -> o.put("type", "m3u").put("url", account.url)
        }
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object { const val KEY = "active" }
}

fun Account.catalog(): Catalog = when (this) {
    is Account.Xtream -> XtreamCatalog(this)
    is Account.M3u -> M3uCatalog(this)
}
