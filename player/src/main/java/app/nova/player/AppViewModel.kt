package app.nova.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nova.player.data.Account
import app.nova.player.data.AccountStatus
import app.nova.player.data.AccountStore
import app.nova.player.data.ActivationClient
import app.nova.player.data.Catalog
import app.nova.player.data.Category
import app.nova.player.data.CredentialParser
import app.nova.player.data.Episode
import app.nova.player.data.Item
import app.nova.player.data.Kind
import app.nova.player.data.NovaException
import app.nova.player.data.catalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class Player(val request: PlayRequest) : Screen
}

/** Ce qu'on lit, et la liste dans laquelle on zappe (chaînes de la catégorie courante). */
data class PlayRequest(val title: String, val url: String, val live: Boolean, val playlist: List<Item> = emptyList(), val index: Int = 0)

data class LoginState(val loading: Boolean = false, val error: String? = null, val info: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AccountStore(app)

    var screen by mutableStateOf<Screen>(Screen.Login); private set
    var login by mutableStateOf(LoginState()); private set
    var account by mutableStateOf<Account?>(null); private set
    var status by mutableStateOf<AccountStatus?>(null); private set
    private var catalog: Catalog? = null

    // Accueil
    var kind by mutableStateOf(Kind.LIVE); private set
    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var selectedCategory by mutableStateOf<String?>(null); private set
    var items by mutableStateOf<List<Item>>(emptyList()); private set
    var homeLoading by mutableStateOf(false); private set
    var homeError by mutableStateOf<String?>(null); private set
    private var allItems: List<Item> = emptyList()
    private var loadJob: Job? = null

    // Séries
    var openSeries by mutableStateOf<Item?>(null); private set
    var episodes by mutableStateOf<List<Episode>?>(null); private set

    val supportsSeries: Boolean get() = catalog?.supportsSeries == true

    init {
        store.load()?.let { saved ->
            // Compte déjà enregistré : on ouvre directement l'accueil, la vérification se fait en fond.
            enter(saved)
            viewModelScope.launch { runCatching { status = catalog?.status() } }
        }
    }

    // ---- Connexion ----

    fun loginWithText(text: String) {
        val parsed = CredentialParser.parse(text)
        if (parsed == null) {
            login = LoginState(error = "Je n'ai pas reconnu de lien ni d'identifiants dans ce texte.")
            return
        }
        loginWith(parsed)
    }

    fun loginWithXtream(server: String, username: String, password: String) {
        if (server.isBlank() || username.isBlank() || password.isBlank()) {
            login = LoginState(error = "Remplis les trois champs.")
            return
        }
        // Le client a collé un lien complet dans le champ serveur ? On l'exploite.
        val fromLink = CredentialParser.parse(server)
        if (fromLink is Account.Xtream) return loginWith(fromLink)
        loginWith(Account.Xtream(CredentialParser.normalizeServer(server), username.trim(), password.trim()))
    }

    fun loginWithCode(code: String) {
        login = LoginState(loading = true)
        viewModelScope.launch {
            try {
                loginWith(ActivationClient.resolve(code))
            } catch (e: NovaException) {
                login = LoginState(error = e.message)
            }
        }
    }

    fun loginWith(candidate: Account) {
        login = LoginState(loading = true)
        viewModelScope.launch {
            try {
                val c = candidate.catalog()
                val s = c.status()
                store.save(candidate)
                status = s
                enter(candidate, c)
                login = LoginState()
            } catch (e: NovaException) {
                login = LoginState(error = e.message)
            } catch (e: Exception) {
                login = LoginState(error = "Connexion impossible : ${e.javaClass.simpleName}")
            }
        }
    }

    fun clearLoginError() { login = login.copy(error = null) }

    fun logout() {
        store.clear()
        account = null
        catalog = null
        status = null
        allItems = emptyList()
        items = emptyList()
        categories = emptyList()
        screen = Screen.Login
    }

    private fun enter(a: Account, c: Catalog = a.catalog()) {
        account = a
        catalog = c
        screen = Screen.Home
        selectKind(Kind.LIVE, force = true)
    }

    // ---- Accueil ----

    fun selectKind(k: Kind, force: Boolean = false) {
        if (k == kind && !force) return
        kind = k
        selectedCategory = null
        openSeries = null
        reload()
    }

    fun reload() {
        val c = catalog ?: return
        val k = kind
        loadJob?.cancel()
        homeLoading = true
        homeError = null
        categories = emptyList()
        items = emptyList()
        loadJob = viewModelScope.launch {
            try {
                val cats = async { c.categories(k) }
                val list = async { c.items(k) }
                allItems = list.await()
                // On masque les catégories vides : moins de bruit à la télécommande.
                val used = allItems.mapTo(HashSet()) { it.categoryId }
                categories = cats.await().filter { it.id in used }.distinctBy { it.id }
                applyFilter()
            } catch (e: NovaException) {
                homeError = e.message
            } catch (e: Exception) {
                homeError = "Chargement impossible : ${e.javaClass.simpleName}"
            } finally {
                homeLoading = false
            }
        }
    }

    fun selectCategory(id: String?) {
        selectedCategory = id
        applyFilter()
    }

    private fun applyFilter() {
        val id = selectedCategory
        items = if (id == null) allItems else allItems.filter { it.categoryId == id }
    }

    // ---- Lecture ----

    fun open(item: Item) {
        val c = catalog ?: return
        when (item.kind) {
            Kind.SERIES -> openSeries(item)
            Kind.LIVE -> {
                val list = items
                screen = Screen.Player(PlayRequest(item.name, c.streamUrl(item), live = true, playlist = list, index = list.indexOf(item).coerceAtLeast(0)))
            }
            Kind.MOVIE -> screen = Screen.Player(PlayRequest(item.name, c.streamUrl(item), live = false))
        }
    }

    /** Zapping : +1 / -1 dans la liste courante, en boucle. */
    fun zap(current: PlayRequest, delta: Int): PlayRequest? {
        val c = catalog ?: return null
        if (current.playlist.isEmpty()) return null
        val i = Math.floorMod(current.index + delta, current.playlist.size)
        val item = current.playlist[i]
        return current.copy(title = item.name, url = c.streamUrl(item), index = i).also { screen = Screen.Player(it) }
    }

    fun playEpisode(series: Item, ep: Episode) {
        screen = Screen.Player(PlayRequest("${series.name} · S${ep.season}E${ep.number}", ep.url, live = false))
    }

    fun closePlayer() { screen = Screen.Home }

    private fun openSeries(item: Item) {
        val c = catalog ?: return
        openSeries = item
        episodes = null
        viewModelScope.launch {
            episodes = try { c.episodes(item) } catch (e: Exception) { emptyList() }
        }
    }

    fun closeSeries() {
        openSeries = null
        episodes = null
    }
}
