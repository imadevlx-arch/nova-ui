package app.nova.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.nova.player.data.Item
import app.nova.player.data.Kind
import app.nova.player.ui.theme.NovaColors
import app.nova.player.ui.theme.NovaType
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel) {
    BackHandler(enabled = vm.openSeries != null) { vm.closeSeries() }
    BackHandler(enabled = vm.openSeries == null && vm.selectedCategory != null) { vm.selectCategory(null) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NovaColors.SapphireDeep.copy(alpha = 0.6f), NovaColors.Abyss))),
    ) {
        if (maxWidth < 720.dp) CompactHome(vm) else WideHome(vm)
    }
}

// ---------- Téléphone / portrait ----------

@Composable
private fun CompactHome(vm: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("NOVA", style = NovaType.RailLogo)
            Spacer(Modifier.weight(1f))
            NovaButton(if (menu) "Fermer" else "Compte", { menu = !menu }, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp))
        }
        if (menu) {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(NovaColors.GlassFill, RoundedCornerShape(16.dp)).padding(14.dp)) {
                vm.account?.let { BasicText(it.label, style = BodyStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                ExpiryLine(vm)
                Spacer(Modifier.height(10.dp))
                Row {
                    NovaButton("Recharger", { menu = false; vm.reload() })
                    Spacer(Modifier.width(10.dp))
                    NovaButton("Déconnexion", { vm.logout() })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindTabs(vm, Modifier.weight(1f))
        }
        if (vm.kind == Kind.SERIES && vm.openSeries != null) {
            SeriesPanel(vm, vm.openSeries!!, compact = true)
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Chip("Tout", vm.selectedCategory == null) { vm.selectCategory(null) } }
                items(vm.categories) { c -> Chip(c.name, vm.selectedCategory == c.id) { vm.selectCategory(c.id) } }
            }
            Content(vm, channelMin = 150.dp, posterMin = 108.dp, edge = 16.dp)
        }
    }
}

@Composable
private fun KindTabs(vm: AppViewModel, cell: Modifier) {
    Tab("Direct", vm.kind == Kind.LIVE, cell) { vm.selectKind(Kind.LIVE) }
    Tab("Films", vm.kind == Kind.MOVIE, cell) { vm.selectKind(Kind.MOVIE) }
    if (vm.supportsSeries) Tab("Séries", vm.kind == Kind.SERIES, cell) { vm.selectKind(Kind.SERIES) }
}

@Composable
private fun Tab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Pressable(
        onClick, modifier,
        color = if (selected) NovaColors.Jade else NovaColors.GlassFill,
        focusedColor = if (selected) NovaColors.Jade else NovaColors.Sapphire.copy(alpha = 0.55f),
    ) {
        BasicText(
            text,
            style = LabelStyle.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = if (selected) NovaColors.Abyss else NovaColors.TextPrimary),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    NovaButton(text, onClick, selected = selected, corner = 20.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp))
}

// ---------- TV / paysage large ----------

@Composable
private fun WideHome(vm: AppViewModel) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(170.dp).fillMaxHeight().background(NovaColors.GlassFill).padding(horizontal = 16.dp, vertical = 28.dp)) {
            BasicText("NOVA", style = NovaType.RailLogo)
            Spacer(Modifier.height(28.dp))
            val cell = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            KindTabs(vm, cell)
            Spacer(Modifier.weight(1f))
            ExpiryLine(vm)
            Spacer(Modifier.height(12.dp))
            NovaButton("Recharger", { vm.reload() }, modifier = cell)
            NovaButton("Déconnexion", { vm.logout() }, modifier = cell)
        }
        if (vm.kind == Kind.SERIES && vm.openSeries != null) {
            SeriesPanel(vm, vm.openSeries!!, compact = false)
        } else {
            LazyColumn(
                Modifier.width(230.dp).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { CategoryRow("Tout", vm.selectedCategory == null) { vm.selectCategory(null) } }
                items(vm.categories) { c -> CategoryRow(c.name, vm.selectedCategory == c.id) { vm.selectCategory(c.id) } }
            }
            Box(Modifier.padding(top = 16.dp)) { Content(vm, channelMin = 170.dp, posterMin = 120.dp, edge = 20.dp) }
        }
    }
}

@Composable
private fun CategoryRow(name: String, selected: Boolean, onClick: () -> Unit) {
    NovaButton(name, onClick, selected = selected, corner = 12.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ExpiryLine(vm: AppViewModel) {
    vm.status?.expiresAt?.let {
        BasicText("Expire le " + SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(it)), style = MutedStyle)
    }
    vm.status?.message?.let { BasicText(it, style = MutedStyle.copy(color = Color(0xFFFF8A9A))) }
}

// ---------- Contenu commun ----------

@Composable
private fun Content(vm: AppViewModel, channelMin: Dp, posterMin: Dp, edge: Dp) {
    Box(Modifier.fillMaxSize()) {
        when {
            vm.homeLoading -> Centered("Chargement…")
            vm.homeError != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorBanner(vm.homeError!!)
                Spacer(Modifier.height(14.dp))
                NovaButton("Réessayer", { vm.reload() }, primary = true)
            }
            vm.items.isEmpty() -> Centered("Rien ici.")
            else -> {
                val live = vm.kind == Kind.LIVE
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (live) channelMin else posterMin),
                    contentPadding = PaddingValues(start = edge, end = edge, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(vm.items) { item ->
                        if (live) ChannelCard(item) { vm.open(item) } else PosterCard(item) { vm.open(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText(text, style = MutedStyle.copy(fontSize = 16.sp)) }
}

@Composable
private fun ChannelCard(item: Item, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 10f), color = NovaColors.SapphireDeep, focusedColor = NovaColors.Sapphire, focusedScale = 1.07f, glow = true) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (item.image != null) {
                    AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.8f))
                } else {
                    BasicText(item.name.take(2).uppercase(), style = NovaType.GridTitle.copy(color = NovaColors.TextMuted))
                }
            }
            BasicText(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LabelStyle.copy(fontSize = 12.sp, textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun PosterCard(item: Item, onClick: () -> Unit) {
    Column {
        Pressable(onClick, Modifier.fillMaxWidth().aspectRatio(2f / 3f), color = NovaColors.SapphireDeep, focusedColor = NovaColors.Sapphire, focusedScale = 1.07f, glow = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(item.name, maxLines = 3, style = LabelStyle.copy(fontSize = 12.sp, textAlign = TextAlign.Center, color = NovaColors.TextMuted), modifier = Modifier.padding(8.dp))
                if (item.image != null) {
                    AsyncImage(model = item.image, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LabelStyle.copy(fontSize = 12.sp))
    }
}

@Composable
private fun SeriesPanel(vm: AppViewModel, series: Item, compact: Boolean) {
    Column(Modifier.fillMaxSize().padding(if (compact) 16.dp else 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(if (compact) 96.dp else 180.dp).aspectRatio(2f / 3f).background(NovaColors.SapphireDeep, RoundedCornerShape(14.dp))) {
                if (series.image != null) AsyncImage(series.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                BasicText(series.name, style = TitleStyle.copy(fontSize = if (compact) 20.sp else 28.sp, fontWeight = FontWeight.Bold), maxLines = 3)
                Spacer(Modifier.height(10.dp))
                NovaButton("← Retour", { vm.closeSeries() }, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        val eps = vm.episodes
        when {
            eps == null -> BasicText("Chargement des épisodes…", style = MutedStyle)
            eps.isEmpty() -> BasicText("Aucun épisode disponible.", style = MutedStyle)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(eps) { ep ->
                    NovaButton("S${ep.season} · E${ep.number}   ${ep.title}", onClick = { vm.playEpisode(series, ep) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
