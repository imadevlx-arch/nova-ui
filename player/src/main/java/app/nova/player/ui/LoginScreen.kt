package app.nova.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.player.AppViewModel
import app.nova.player.data.ActivationClient
import app.nova.player.data.PhoneLinkServer
import app.nova.player.ui.theme.NovaColors
import app.nova.player.ui.theme.NovaFonts
import app.nova.player.ui.theme.NovaSpace
import app.nova.player.ui.theme.NovaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Method(val label: String) { CODE("Code"), PHONE("Téléphone"), XTREAM("Xtream"), M3U("Lien M3U") }

@Composable
fun LoginScreen(vm: AppViewModel) {
    val tv = LocalContext.current.isTv()
    // Sur téléphone, la connexion « depuis le téléphone » n'a pas de sens : on colle directement.
    val methods = remember(tv) {
        Method.entries.filter { (it != Method.CODE || ActivationClient.enabled) && (it != Method.PHONE || tv) }
    }
    var method by rememberSaveable { mutableStateOf(methods.first()) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(NovaColors.SapphireDeep, NovaColors.Abyss), radius = 1400f),
        ),
    ) {
        val compact = maxWidth < 720.dp
        val methodButtons: @Composable (Modifier) -> Unit = { cell ->
            methods.forEachIndexed { i, m ->
                NovaButton(
                    text = m.label,
                    onClick = { method = m; vm.clearLoginError() },
                    selected = method == m,
                    modifier = cell.then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                )
            }
        }
        val panel: @Composable () -> Unit = {
            when (method) {
                Method.CODE -> CodePanel(vm)
                Method.PHONE -> PhonePanel(vm)
                Method.XTREAM -> XtreamPanel(vm, compact)
                Method.M3U -> M3uPanel(vm, compact)
            }
            Spacer(Modifier.height(16.dp))
            val state = vm.login
            when {
                state.loading -> BasicText("Connexion au serveur…", style = BodyStyle.copy(color = NovaColors.Jade))
                state.error != null -> ErrorBanner(state.error)
            }
        }
        if (compact) {
            // Téléphone : tout empilé, défilable (portrait comme paysage étroit)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                BasicText("NOVA", style = NovaType.Wordmark.copy(color = NovaColors.Jade))
                Spacer(Modifier.height(8.dp))
                BasicText("Connexion", style = NovaType.Hero.copy(fontSize = 34.sp))
                Spacer(Modifier.height(4.dp))
                BasicText("Choisis comment tu as reçu ton abonnement.", style = MutedStyle.copy(fontSize = 15.sp))
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { methodButtons(Modifier.weight(1f)) }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().background(NovaColors.GlassFill, RoundedCornerShape(20.dp)).padding(20.dp)) { panel() }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = NovaSpace.ContentX, vertical = 40.dp)) {
                Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    BasicText("NOVA", style = NovaType.Wordmark.copy(color = NovaColors.Jade))
                    Spacer(Modifier.height(10.dp))
                    BasicText("Connexion", style = NovaType.Hero.copy(fontSize = 46.sp))
                    Spacer(Modifier.height(6.dp))
                    BasicText("Choisis comment tu as reçu ton abonnement.", style = MutedStyle.copy(fontSize = 15.sp))
                    Spacer(Modifier.height(28.dp))
                    methodButtons(Modifier.width(220.dp).padding(vertical = 5.dp))
                }
                Spacer(Modifier.width(48.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight().background(NovaColors.GlassFill, RoundedCornerShape(24.dp)).padding(36.dp),
                    verticalArrangement = Arrangement.Center,
                ) { panel() }
            }
        }
    }
}

@Composable
private fun CodePanel(vm: AppViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    PanelTitle("Code d'activation", "Tape le code donné par ton revendeur.")
    NovaTextField(
        value = code,
        onValueChange = { v -> code = v.filter { it.isLetterOrDigit() }.uppercase().take(12) },
        label = "Code",
        keyboardType = KeyboardType.Ascii,
        imeAction = ImeAction.Go,
        onDone = { vm.loginWithCode(code) },
        textStyle = BodyStyle.copy(fontFamily = NovaFonts.Display, fontSize = 30.sp, letterSpacing = 6.sp),
        modifier = Modifier.width(380.dp),
    )
    Spacer(Modifier.height(18.dp))
    NovaButton("Valider", onClick = { vm.loginWithCode(code) }, primary = true)
}

@Composable
private fun PhonePanel(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf<String?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val server = PhoneLinkServer { text -> scope.launch(Dispatchers.Main) { vm.loginWithText(text) } }
        scope.launch {
            val u = withContext(Dispatchers.IO) { server.start(scope) }
            url = u
            unavailable = u == null
        }
        onDispose { server.stop() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            PanelTitle("Depuis ton téléphone", "Aucune saisie à la télécommande.")
            Step("1", "Connecte ton téléphone au même Wi-Fi que la TV.")
            Step("2", "Scanne le QR code (ou ouvre l'adresse ci-dessous).")
            Step("3", "Colle le lien ou le message reçu de ton fournisseur, puis Envoyer.")
            Spacer(Modifier.height(14.dp))
            when {
                url != null -> BasicText(url!!, style = BodyStyle.copy(fontFamily = NovaFonts.Display, fontSize = 18.sp, color = NovaColors.Jade))
                unavailable -> ErrorBanner("Pas de réseau local détecté. Utilise Xtream ou Lien M3U.")
            }
        }
        Spacer(Modifier.width(24.dp))
        val qr = remember(url) { url?.let { qrBitmap(it) } }
        Box(
            Modifier.size(200.dp).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp)).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (qr != null) Image(qr, contentDescription = "QR code de connexion", modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun XtreamPanel(vm: AppViewModel, compact: Boolean) {
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    PanelTitle("Xtream Codes", "Astuce : colle un lien complet dans « Serveur », le reste se remplit tout seul.")
    NovaTextField(server, { server = it }, "Serveur (ex. http://monserveur.com:8080)", Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    val userField: @Composable (Modifier) -> Unit = { m -> NovaTextField(user, { user = it }, "Identifiant", m, keyboardType = KeyboardType.Ascii) }
    val passField: @Composable (Modifier) -> Unit = { m ->
        NovaTextField(
            pass, { pass = it }, "Mot de passe", m,
            password = true, keyboardType = KeyboardType.Password, imeAction = ImeAction.Go,
            onDone = { vm.loginWithXtream(server, user, pass) },
        )
    }
    if (compact) {
        userField(Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp)); passField(Modifier.fillMaxWidth())
    } else Row {
        userField(Modifier.weight(1f)); Spacer(Modifier.width(14.dp)); passField(Modifier.weight(1f))
    }
    Spacer(Modifier.height(18.dp))
    ButtonRow(compact) {
        NovaButton("Se connecter", onClick = {
            // Lien complet collé dans « Serveur » et autres champs vides : on se débrouille.
            if (user.isBlank() && pass.isBlank()) vm.loginWithText(server) else vm.loginWithXtream(server, user, pass)
        }, primary = true)
        Spacer(Modifier.width(12.dp))
        PasteButton { pasted -> vm.loginWithText(pasted) }
    }
}

@Composable
private fun M3uPanel(vm: AppViewModel, compact: Boolean) {
    var link by rememberSaveable { mutableStateOf("") }
    PanelTitle("Lien M3U", "Les liens get.php sont convertis en Xtream : films et séries en plus.")
    NovaTextField(link, { link = it }, "Lien de la playlist", Modifier.fillMaxWidth(), imeAction = ImeAction.Go, onDone = { vm.loginWithText(link) })
    Spacer(Modifier.height(18.dp))
    ButtonRow(compact) {
        NovaButton("Charger", onClick = { vm.loginWithText(link) }, primary = true)
        Spacer(Modifier.width(12.dp))
        PasteButton { pasted -> link = pasted; vm.loginWithText(pasted) }
    }
}

@Composable
private fun ButtonRow(compact: Boolean, content: @Composable () -> Unit) {
    if (compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    else Row(verticalAlignment = Alignment.CenterVertically) { content() }
}

/** Colle le presse-papiers (lien ou message WhatsApp copié) et lance la connexion. */
@Composable
private fun PasteButton(onPaste: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    NovaButton("Coller et connecter", onClick = {
        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(onPaste)
    })
}

@Composable
private fun PanelTitle(title: String, subtitle: String) {
    BasicText(title, style = TitleStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold))
    Spacer(Modifier.height(6.dp))
    BasicText(subtitle, style = MutedStyle.copy(fontSize = 14.sp))
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).background(NovaColors.Jade.copy(alpha = 0.16f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            BasicText(n, style = LabelStyle.copy(color = NovaColors.Jade, fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.width(12.dp))
        BasicText(text, style = BodyStyle)
    }
}
