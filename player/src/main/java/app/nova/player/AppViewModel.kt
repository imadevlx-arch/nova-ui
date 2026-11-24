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
import app.nova.player.data.EventRow
import app.nova.player.data.EventsRepository
import app.nova.player.data.Item
import app.nova.player.data.Kind
import app.nova.player.data.NovaException
import app.nova.player.data.ResumeEntry
import app.nova.player.data.ResumeStore
import app.nova.player.data.UpdateInfo
import app.nova.player.data.Updater
import app.nova.player.data.catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class Player(val request: PlayRequest) : Screen
}

/** Onglets principaux (HOME, LIVE, MOVIE, SERIES) + pages « Tout voir » (EVENTS, RECENTS) ouvertes depuis l'accueil. */
enum class Section(val label: String, val kind: Kind?, val tab: Boolean) {
    HOME("Accueil", null, true),
    LIVE("Direct", Kind.LIVE, true),
    MOVIE("Films", Kind.MOVIE, true),
    SERIES("Séries", Kind.SERIES, true),
    EVENTS("Événements", null, false),
    RECENTS("Reprendre", null, false),
}

/**
 * Ce qu'on lit. playlist = liste dans laquelle on zappe (catégorie courante, ou chaînes d'un événement).
 * resumeKey non nul = film/épisode : position sauvegardée et reprise à startPosition.
 */
data class PlayRequest(
    val title: String,
    val url: String,
    val live: Boolean,
    val playlist: List<Item> = emptyList(),
    val index: Int = 0,
    val resumeKey: String? = null,
    val startPosition: Long = 0,
    val prefix: String? = null,
)

data class LoginState(val loading: Boolean = false, val error: String? = null, val info: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AccountStore(app)
    private val resume = ResumeStore(app)

    var screen by mutableStateOf<Screen>(Screen.Login); private set
    var login by mutableStateOf(LoginState()); private set
    var account by mutableStateOf<Account?>(null); private set
    var status by mutableStateOf<AccountStatus?>(null); private set
    private var catalog: Catalog? = null

    // Accueil
    var section by mutableStateOf(Section.HOME); private set
    val kind: Kind get() = section.kind ?: Kind.LIVE
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

    // Récents
    var recents by mutableStateOf<List<ResumeEntry>>(emptyList()); private set

    // Accueil : tendances
    var trendingMovies by mutableStateOf<List<Item>>(emptyList()); private set
    var newSeries by mutableStateOf<List<Item>>(emptyList()); private set
    private var trendingJob: Job? = null

    // Événements
    var events by mutableStateOf<List<EventRow>>(emptyList()); private set
    var eventsLoading by mutableStateOf(false); private set
    var eventsError by mutableStateOf<String?>(null); private set
    private var eventsJob: Job? = null

    // Mise à jour intégrée
    var update by mutableStateOf<UpdateInfo?>(null); private set
    var updateProgress by mutableStateOf<Float?>(null); private set
    var updateError by mutableStateOf<String?>(null); private set

    val supportsSeries: Boolean get() = catalog?.supportsSeries == true

    /** Préfixe des clés de reprise : un compte = ses propres récents. */
    private val accountPrefix: String get() = (account?.label?.hashCode() ?: 0).toString(16) + ":"

    init {
        viewModelScope.launch { update = Updater.check() }
        store.load()?.let { saved ->
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
        events = emptyList()
        trendingMovies = emptyList()
        newSeries = emptyList()
        screen = Screen.Login
    }

    private fun enter(a: Account, c: Catalog = a.catalog()) {
        account = a
        catalog = c
        screen = Screen.Home
        selectSection(Section.HOME, force = true)
    }

    // ---- Sections ----

    fun selectSection(s: Section, force: Boolean = false) {
        if (s == section && !force) return
        section = s
        selectedCategory = null
        openSeries = null
        when (s) {
            Section.HOME -> loadHome()
            Section.RECENTS -> refreshRecents()
            Section.EVENTS -> loadEvents()
            else -> reload()
        }
    }

    fun reload() {
        val c = catalog ?: return
        if (section.kind == null) {
            when (section) {
                Section.EVENTS -> loadEvents(force = true)
                Section.HOME -> loadHome(force = true)
                else -> refreshRecents()
            }
            return
        }
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
            Kind.MOVIE -> playResumable(
                key = accountPrefix + "m:" + item.id,
                title = item.name, subtitle = "Film", image = item.image, url = c.streamUrl(item),
            )
        }
    }

    fun playEpisode(series: Item, ep: Episode) {
        playResumable(
            key = accountPrefix + "e:" + series.id + ":" + ep.id,
            title = series.name, subtitle = "S${ep.season} · É${ep.number} — ${ep.title}", image = series.image, url = ep.url,
        )
    }

    private fun playResumable(key: String, title: String, subtitle: String?, image: String?, url: String) {
        val start = resume.get(key)?.position ?: 0
        resume.touch(key, title, subtitle, image, url)
        val shown = if (subtitle != null && subtitle != "Film") "$title · $subtitle" else title
        screen = Screen.Player(PlayRequest(shown, url, live = false, resumeKey = key, startPosition = start))
    }

    fun playRecent(entry: ResumeEntry) {
        playResumable(entry.key, entry.title, entry.subtitle, entry.image, entry.url)
    }

    fun savePosition(key: String, position: Long, duration: Long) = resume.savePosition(key, position, duration)

    fun removeRecent(entry: ResumeEntry) {
        resume.remove(entry.key)
        refreshRecents()
    }

    private fun refreshRecents() {
        val prefix = accountPrefix
        recents = resume.all().filter { it.key.startsWith(prefix) }
    }

    /** Zapping : +1 / -1 dans la liste courante, en boucle. */
    fun zap(current: PlayRequest, delta: Int): PlayRequest? {
        val c = catalog ?: return null
        if (current.playlist.isEmpty()) return null
        val i = Math.floorMod(current.index + delta, current.playlist.size)
        val item = current.playlist[i]
        val title = current.prefix?.let { "$it · ${item.name}" } ?: item.name
        return current.copy(title = title, url = c.streamUrl(item), index = i).also { screen = Screen.Player(it) }
    }

    fun closePlayer() {
        screen = Screen.Home
        if (section == Section.RECENTS || section == Section.HOME) refreshRecents()
    }

    fun installUpdate() {
        val info = update ?: return
        if (updateProgress != null) return
        updateError = null
        updateProgress = 0f
        viewModelScope.launch {
            try {
                Updater.downloadAndInstall(getApplication(), info) { p -> updateProgress = p }
            } catch (e: Exception) {
                updateError = e.message ?: "Mise à jour impossible."
            } finally {
                updateProgress = null
            }
        }
    }

    // ---- Accueil ----

    /** Accueil : reprendre + événements + tendances, chargés en parallèle. */
    fun loadHome(force: Boolean = false) {
        refreshRecents()
        loadEvents(force)
        val c = catalog ?: return
        if (!force && trendingMovies.isNotEmpty()) return
        trendingJob?.cancel()
        trendingJob = viewModelScope.launch {
            runCatching { trendingMovies = withContext(Dispatchers.Default) { trending(c.items(Kind.MOVIE)) } }
            if (c.supportsSeries) runCatching { newSeries = withContext(Dispatchers.Default) { trending(c.items(Kind.SERIES)) } }
        }
    }

    /** Récemment ajoutés au serveur, les mieux notés d'abord parmi les 120 plus récents. */
    private fun trending(all: List<Item>): List<Item> {
        if (all.none { it.added > 0 }) return all.takeLast(30).reversed() // M3U : les ajouts sont en fin de liste
        val recent = all.filter { it.image != null }.sortedByDescending { it.added }.take(120)
        val good = recent.filter { it.rating >= 6.0 }
        return (if (good.size >= 12) good else recent).take(30)
    }

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

    // ---- Événements ----

    fun loadEvents(force: Boolean = false) {
        val c = catalog ?: return
        if (!force && events.isNotEmpty()) return
        eventsJob?.cancel()
        eventsLoading = true
        eventsError = null
        eventsJob = viewModelScope.launch {
            try {
                val config = EventsRepository.config(getApplication())
                val list = EventsRepository.events(config, force)
                val channels = c.items(Kind.LIVE)
                val catNames = runCatching { c.categories(Kind.LIVE) }.getOrElse { emptyList() }.associate { it.id to it.name }
                events = withContext(Dispatchers.Default) {
                    val index = EventsRepository.ChannelIndex(channels, catNames, config.eventCategoryWords)
                    list.map { EventRow(it, EventsRepository.match(it, index)) }
                }
            } catch (e: NovaException) {
                eventsError = e.message
            } catch (e: Exception) {
                eventsError = "Événements indisponibles : ${e.javaClass.simpleName}"
            } finally {
                eventsLoading = false
            }
        }
    }

    /** Lance la meilleure chaîne ; ▲▼ passe aux autres chaînes candidates. */
    fun openEvent(row: EventRow, channelIndex: Int = 0) {
        val c = catalog ?: return
        if (row.channels.isEmpty()) return
        val i = channelIndex.coerceIn(0, row.channels.lastIndex)
        val ch = row.channels[i]
        screen = Screen.Player(
            PlayRequest(
                title = "${row.event.title} · ${ch.name}",
                url = c.streamUrl(ch),
                live = true,
                playlist = row.channels,
                index = i,
                prefix = row.event.title,
            ),
        )
    }
}
