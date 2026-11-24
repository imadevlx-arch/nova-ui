package app.nova.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    /** Beaucoup de panels filtrent les user-agents inconnus : on se présente comme un lecteur courant. */
    const val USER_AGENT = "IPTVSmartersPro"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    suspend fun <T> get(url: String, block: (Response) -> T): T = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(Request.Builder().url(url).build()).execute()
        } catch (e: IOException) {
            throw NovaException("Serveur injoignable. Vérifie l'adresse et ta connexion.", e)
        } catch (e: IllegalArgumentException) {
            throw NovaException("Adresse invalide.", e)
        }
        response.use {
            when {
                it.code == 401 || it.code == 403 -> throw NovaException("Identifiants refusés par le serveur (${it.code}).")
                it.code == 404 -> throw NovaException("Adresse introuvable sur ce serveur (404).")
                !it.isSuccessful -> throw NovaException("Le serveur a répondu ${it.code}.")
            }
            block(it)
        }
    }
}
