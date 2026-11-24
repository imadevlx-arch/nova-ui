package app.nova.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.player.AppViewModel
import app.nova.player.data.EventRow
import app.nova.player.data.ResumeEntry
import app.nova.player.ui.theme.NovaColors
import app.nova.player.ui.theme.NovaFonts
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- Récents ----------

@Composable
fun RecentsPanel(vm: AppViewModel, posterMin: Dp, edge: Dp) {
    val list = vm.recents
    if (list.isEmpty()) {
        EmptyState("Rien en cours", "Lance un film ou un épisode : il apparaîtra ici, prêt à reprendre à la seconde près.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        BasicText(
            "Reprendre · appui long pour retirer",
            style = MutedStyle,
            modifier = Modifier.padding(start = edge, top = 10.dp, bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(posterMin),
            contentPadding = PaddingValues(start = edge, end = edge, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(list) { entry -> RecentCard(entry, onClick = { vm.playRecent(entry) }, onLongClick = { vm.removeRecent(entry) }) }
        }
    }
}

@Composable
internal fun RecentCard(entry: ResumeEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column {
        Pressable(
            onClick, Modifier.fillMaxWidth().aspectRatio(2f / 3f), onLongClick = onLongClick,
            color = NovaColors.SapphireDeep, focusedColor = NovaColors.Sapphire, focusedScale = 1.07f, glow = true,
        ) {
            BasicText(entry.title, maxLines = 3, style = LabelStyle.copy(fontSize = 12.sp, textAlign = TextAlign.Center, color = NovaColors.TextMuted), modifier = Modifier.align(Alignment.Center).padding(8.dp))
            if (entry.image != null) AsyncImage(entry.image, entry.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Dégradé + barre de progression en bas de l'affiche
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(46.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6050B1C)))),
            )
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp)) {
                BasicText(remaining(entry), style = LabelStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x40FFFFFF), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(entry.progress.coerceAtLeast(0.02f)).fillMaxHeight().background(NovaColors.Jade, RoundedCornerShape(2.dp)))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LabelStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        entry.subtitle?.takeIf { it != "Film" }?.let {
            BasicText(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MutedStyle.copy(fontSize = 11.sp))
        }
    }
}

private fun remaining(e: ResumeEntry): String {
    if (e.duration <= 0) return if (e.position > 0) "Reprendre à ${clock(e.position)}" else "À commencer"
    val left = ((e.duration - e.position) / 60_000).coerceAtLeast(1)
    return if (left >= 60) "Reste ${left / 60} h ${"%02d".format(left % 60)}" else "Reste $left min"
}

fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// ---------- Événements ----------

@Composable
fun EventsPanel(vm: AppViewModel, edge: Dp) {
    when {
        vm.eventsLoading && vm.events.isEmpty() -> EmptyState("Chargement…", "Récupération du programme et recherche des chaînes dans ta liste.")
        vm.eventsError != null && vm.events.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ErrorBanner(vm.eventsError!!)
            Spacer(Modifier.height(12.dp))
            NovaButton("Réessayer", { vm.loadEvents(force = true) }, primary = true)
        }
        vm.events.isEmpty() -> EmptyState("Aucun grand rendez-vous", "Pas de match ni de soirée UFC dans les 36 prochaines heures.")
        else -> {
            val now = System.currentTimeMillis()
            val groups = vm.events.groupBy { dayLabel(it, now) }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = edge, end = edge, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groups.forEach { (label, rows) ->
                    item { BasicText(label, style = TitleStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                    items(rows) { row -> EventCard(row, now) { vm.openEvent(row) } }
                }
            }
        }
    }
}

private fun dayLabel(row: EventRow, now: Long): String {
    if (row.event.isLive(now)) return "En direct"
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = row.event.start
    return when (cal.get(Calendar.DAY_OF_YEAR)) {
        today -> "Aujourd'hui"
        (today % 365) + 1 -> "Demain"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date(row.event.start)).replaceFirstChar { it.uppercase() }
    }
}

@Composable
internal fun EventCard(row: EventRow, now: Long, onClick: () -> Unit) {
    val e = row.event
    val live = e.isLive(now)
    val playable = row.channels.isNotEmpty()
    Pressable(onClick, Modifier.fillMaxWidth(), color = if (live) NovaColors.Jade.copy(alpha = 0.08f) else NovaColors.GlassFill, focusedScale = 1.02f) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Heure ou badge DIRECT
            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                if (live) {
                    BasicText(
                        "DIRECT",
                        style = LabelStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NovaColors.Abyss),
                        modifier = Modifier.background(NovaColors.Jade, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else {
                    BasicText(
                        SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(e.start)),
                        style = BodyStyle.copy(fontFamily = NovaFonts.Display, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Badges(e.homeBadge, e.awayBadge)
            Column(Modifier.weight(1f)) {
                BasicText(e.competition.name.uppercase(Locale.FRANCE), style = MutedStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                BasicText(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = BodyStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(3.dp))
                if (playable) {
                    BasicText(
                        "▶ ${row.channels.first().name}" + if (row.channels.size > 1) "  ·  +${row.channels.size - 1} chaîne(s) ▲▼" else "",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = LabelStyle.copy(fontSize = 12.sp, color = NovaColors.Jade),
                    )
                } else {
                    BasicText("Chaîne introuvable dans ta liste", style = MutedStyle.copy(fontSize = 12.sp, color = Color(0xFFFF8A9A)))
                }
            }
        }
    }
}

@Composable
private fun Badges(home: String?, away: String?) {
    if (home == null && away == null) return
    Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(home, away).forEach { url ->
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                if (url != null) AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
fun EmptyState(title: String, text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BasicText(title, style = TitleStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
        Spacer(Modifier.height(8.dp))
        BasicText(text, style = MutedStyle.copy(fontSize = 15.sp, textAlign = TextAlign.Center), modifier = Modifier.widthIn(max = 420.dp))
    }
}

// ---------- Accueil : tout en un coup d'œil ----------

@Composable
fun HomePanel(vm: AppViewModel, edge: Dp, compact: Boolean) {
    val posterW = if (compact) 112.dp else 140.dp
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        vm.update?.let { info ->
            item {
                Row(
                    Modifier.padding(horizontal = edge).fillMaxWidth()
                        .background(NovaColors.Jade.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText("Nouvelle version ${info.versionName}", style = BodyStyle.copy(fontWeight = FontWeight.Bold))
                        val p = vm.updateProgress
                        BasicText(
                            vm.updateError ?: if (p != null) "Téléchargement… ${(p * 100).toInt()} %" else "Corrections et nouveautés prêtes à installer.",
                            style = MutedStyle,
                        )
                    }
                    NovaButton("Mettre à jour", { vm.installUpdate() }, primary = true)
                }
            }
        }
        // 1. Reprendre
        if (vm.recents.isNotEmpty()) item {
            HomeRow("Reprendre", edge, onSeeAll = { vm.selectSection(app.nova.player.Section.RECENTS) }) {
                items(vm.recents.take(15)) { e ->
                    Box(Modifier.width(posterW)) { RecentCard(e, onClick = { vm.playRecent(e) }, onLongClick = { vm.removeRecent(e) }) }
                }
            }
        }
        // 2. Événements
        item {
            val rows = vm.events
            HomeRow(
                if (rows.any { it.event.isLive() }) "Événements · en direct" else "Événements",
                edge,
                onSeeAll = { vm.selectSection(app.nova.player.Section.EVENTS) },
            ) {
                when {
                    rows.isEmpty() && vm.eventsLoading -> item { RowNote("Chargement du programme…") }
                    rows.isEmpty() && vm.eventsError != null -> item { RowNote(vm.eventsError!!) }
                    rows.isEmpty() -> item { RowNote("Aucun grand match ni soirée UFC dans les 36 prochaines heures.") }
                    else -> items(rows.take(12)) { row ->
                        Box(Modifier.width(if (compact) 290.dp else 360.dp)) { EventCard(row, System.currentTimeMillis()) { vm.openEvent(row) } }
                    }
                }
            }
        }
        // 3. Tendances
        if (vm.trendingMovies.isNotEmpty()) item {
            HomeRow("Tendances films", edge, onSeeAll = { vm.selectSection(app.nova.player.Section.MOVIE) }) {
                items(vm.trendingMovies) { item -> Box(Modifier.width(posterW)) { PosterTile(item) { vm.open(item) } } }
            }
        }
        if (vm.newSeries.isNotEmpty()) item {
            HomeRow("Nouveautés séries", edge, onSeeAll = { vm.selectSection(app.nova.player.Section.SERIES) }) {
                items(vm.newSeries) { item ->
                    Box(Modifier.width(posterW)) { PosterTile(item) { vm.selectSection(app.nova.player.Section.SERIES); vm.open(item) } }
                }
            }
        }
    }
}

@Composable
private fun HomeRow(
    title: String,
    edge: Dp,
    onSeeAll: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = edge), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = TitleStyle.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            NovaButton("Tout voir", onSeeAll, padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = edge, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun RowNote(text: String) {
    BasicText(text, style = MutedStyle.copy(fontSize = 14.sp), modifier = Modifier.padding(vertical = 18.dp))
}

@Composable
fun PosterTile(item: app.nova.player.data.Item, onClick: () -> Unit) {
    Column {
        Pressable(onClick, Modifier.fillMaxWidth().aspectRatio(2f / 3f), color = NovaColors.SapphireDeep, focusedColor = NovaColors.Sapphire, focusedScale = 1.07f, glow = true) {
            BasicText(item.name, maxLines = 3, style = LabelStyle.copy(fontSize = 12.sp, textAlign = TextAlign.Center, color = NovaColors.TextMuted), modifier = Modifier.align(Alignment.Center).padding(8.dp))
            if (item.image != null) AsyncImage(item.image, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.rating > 0) {
                BasicText(
                    "★ " + "%.1f".format(item.rating),
                    style = LabelStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xCC050B1C), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LabelStyle.copy(fontSize = 12.sp))
    }
}
