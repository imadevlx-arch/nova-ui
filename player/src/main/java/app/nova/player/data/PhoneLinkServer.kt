package app.nova.player.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Connexion depuis le téléphone : la TV affiche un QR code, le client l'ouvre sur son téléphone
 * (même Wi-Fi) et colle le lien / les identifiants reçus par WhatsApp. Aucune saisie à la télécommande.
 * Serveur HTTP minimal, local uniquement, actif seulement tant que l'écran de connexion est ouvert.
 */
class PhoneLinkServer(private val onReceived: (String) -> Unit) {

    private var socket: ServerSocket? = null
    private var job: Job? = null

    /** Démarre et renvoie l'URL à afficher, ou null si pas de réseau local. */
    fun start(scope: CoroutineScope): String? {
        val ip = localIp() ?: return null
        val server = (PORTS.firstNotNullOfOrNull { runCatching { ServerSocket(it) }.getOrNull() }) ?: return null
        socket = server
        job = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                launch { runCatching { handle(client) } }
            }
        }
        return "http://$ip:${server.localPort}"
    }

    fun stop() {
        job?.cancel()
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) {
        client.use { s -> serve(s) }
    }

    private fun serve(s: Socket) {
        s.soTimeout = 10_000
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        var length = 0
        while (true) {
            val h = reader.readLine() ?: break
            if (h.isEmpty()) break
            if (h.startsWith("Content-Length:", ignoreCase = true)) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val out = s.getOutputStream()
        val (status, html) = if (requestLine.startsWith("POST") && length in 1..MAX_BODY) {
            val buf = CharArray(length)
            var read = 0
            while (read < length) {
                val n = reader.read(buf, read, length - read)
                if (n < 0) break
                read += n
            }
            val body = String(buf, 0, read)
            val data = body.split('&').firstOrNull { it.startsWith("data=") }?.substringAfter('=')
                ?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty()
            if (data.isNotBlank()) onReceived(data)
            "200 OK" to page(sent = true)
        } else {
            "200 OK" to page(sent = false)
        }
        val bytes = html.toByteArray(Charsets.UTF_8)
        out.write("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun page(sent: Boolean) = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Nova Player</title>
        <style>
        body{margin:0;font-family:system-ui,sans-serif;background:#050B1C;color:#EAF2FF;padding:24px}
        h1{font-size:22px;margin:8px 0 4px}p{color:#9FB3D1;font-size:15px;line-height:1.4}
        textarea{width:100%;box-sizing:border-box;height:180px;border-radius:14px;border:1px solid #1d3a6e;
        background:#0B2A66;color:#EAF2FF;padding:14px;font-size:15px}
        button{width:100%;margin-top:14px;padding:16px;border:0;border-radius:14px;background:#00F5A0;
        color:#050B1C;font-weight:700;font-size:17px}
        .ok{background:#0B2A66;border:1px solid #00F5A0;border-radius:14px;padding:16px;margin-bottom:16px}
        </style></head><body>
        <h1>Nova Player</h1>
        ${if (sent) "<div class='ok'>Envoyé à ta TV ✓ Regarde l'écran.</div>" else ""}
        <p>Colle ici le lien ou le message reçu de ton fournisseur (lien M3U, ou serveur + identifiant + mot de passe).</p>
        <form method="post"><textarea name="data" placeholder="http://serveur:8080/get.php?username=…&amp;password=…" required></textarea>
        <button type="submit">Envoyer à la TV</button></form>
        </body></html>
    """.trimIndent()

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }
    }.getOrNull()

    private companion object {
        val PORTS = listOf(8765, 8766, 8767, 0)
        const val MAX_BODY = 16_384
    }
}
