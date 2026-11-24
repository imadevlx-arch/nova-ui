package app.nova.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import app.nova.player.AppViewModel
import app.nova.player.data.Item
import app.nova.player.data.Kind
import app.nova.player.ui.theme.NovaColors
import app.nova.player.ui.theme.NovaMotion
import app.nova.player.ui.theme.NovaType
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel) {
    BackHandler(enabled = vm.openSeries != null) { vm.closeSeries() }
    BackHandler(enabled = vm.openSeries == null && vm.selectedCategory != null) { vm.selectCategory(null) }

    Row(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(NovaColors.SapphireDeep.copy(alpha = 0.55f), NovaColors.Abyss)))) {
        Rail(vm)
        if (vm.kind == Kind.SERIES && vm.openSeries != null) {
            SeriesPanel(vm, vm.openSeries!!)
        } else {
            CategoryColumn(vm)
            Content(vm)
        }
    }
}

@Composable
private fun Rail(vm: AppViewModel) {
    Column(
        Modifier
            .width(170.dp)
            .fillMaxHeight()
            .background(NovaColors.GlassFill)
            .padding(horizontal = 16.dp, vertical = 28.dp),
    ) {
        BasicText("NOVA", style = NovaType.RailLogo)
        Spacer(Modifier.height(28.dp))
        RailEntry("Direct", vm.kind == Kind.LIVE) { vm.selectKind(Kind.LIVE) }
        RailEntry("Films", vm.kind == Kind.MOVIE) { vm.selectKind(Kind.MOVIE) }
        if (vm.supportsSeries) RailEntry("Séries", vm.kind == Kind.SERIES) { vm.selectKind(Kind.SERIES) }
        Spacer(Modifier.weight(1f))
        vm.status?.expiresAt?.let {
            BasicText("Expire le", style = MutedStyle.copy(fontSize = 11.sp))
            BasicText(SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(it)), style = BodyStyle.copy(fontSize = 13.sp))
            Spacer(Modifier.height(12.dp))
        }
        vm.status?.message?.let { BasicText(it, style = MutedStyle.copy(color = Color(0xFFFF8A9A))); Spacer(Modifier.height(10.dp)) }
        RailEntry("Recharger", false) { vm.reload() }
        RailEntry("Déconnexion", false) { vm.logout() }
    }
}

@Composable
private fun RailEntry(text: String, selected: Boolean, onClick: () -> Unit) {
    NovaButton(text, onClick, selected = selected, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun CategoryColumn(vm: AppViewModel) {
    LazyColumn(
        Modifier.width(230.dp).fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { CategoryRow("Tout", vm.selectedCategory == null) { vm.selectCategory(null) } }
        items(vm.categories, key = { it.id }) { c ->
            CategoryRow(c.name, vm.selectedCategory == c.id) { vm.selectCategory(c.id) }
        }
    }
}

@Composable
private fun CategoryRow(name: String, selected: Boolean, onClick: () -> Unit) {
    NovaButton(
        name, onClick, selected = selected, corner = 12.dp,
        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Content(vm: AppViewModel) {
    Box(Modifier.fillMaxSize().padding(top = 28.dp, end = 28.dp)) {
        when {
            vm.homeLoading -> Centered("Chargement…")
            vm.homeError != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorBanner(vm.homeError!!)
                Spacer(Modifier.height(14.dp))
                NovaButton("Réessayer", { vm.reload() }, primary = true)
            }
            vm.items.isEmpty() -> Centered("Rien ici.")
            vm.kind == Kind.LIVE -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(vm.items) { item -> ChannelCard(item) { vm.open(item) } }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vm.items) { item -> PosterCard(item) { vm.open(item) } }
            }
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText(text, style = MutedStyle.copy(fontSize = 16.sp)) }
}

@Composable
private fun FocusCard(onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NovaColors.SapphireDeep,
            focusedContainerColor = NovaColors.Sapphire,
            pressedContainerColor = NovaColors.SapphireDeep,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f, pressedScale = NovaMotion.PressScale),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, NovaColors.GlassStroke), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, NovaColors.Jade), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(NovaColors.Jade.copy(alpha = 0.4f), 14.dp)),
    ) { content() }
}

@Composable
private fun ChannelCard(item: Item, onClick: () -> Unit) {
    FocusCard(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (item.image != null) {
                    AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.8f))
                } else {
                    BasicText(item.name.take(2).uppercase(), style = NovaType.GridTitle.copy(color = NovaColors.TextMuted))
                }
            }
            BasicText(
                item.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = LabelStyle.copy(fontSize = 12.sp, textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
private fun PosterCard(item: Item, onClick: () -> Unit) {
    Column {
        FocusCard(onClick, Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
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
private fun SeriesPanel(vm: AppViewModel, series: Item) {
    Row(Modifier.fillMaxSize().padding(28.dp)) {
        Box(Modifier.width(220.dp).aspectRatio(2f / 3f).background(NovaColors.SapphireDeep, RoundedCornerShape(16.dp))) {
            if (series.image != null) AsyncImage(series.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            BasicText(series.name, style = TitleStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Spacer(Modifier.height(16.dp))
            val eps = vm.episodes
            when {
                eps == null -> BasicText("Chargement des épisodes…", style = MutedStyle)
                eps.isEmpty() -> BasicText("Aucun épisode disponible.", style = MutedStyle)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(eps) { ep ->
                        NovaButton(
                            "S${ep.season} · E${ep.number}   ${ep.title}",
                            onClick = { vm.playEpisode(series, ep) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
