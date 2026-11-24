package app.nova.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.nova.player.ui.HomeScreen
import app.nova.player.ui.LoginScreen
import app.nova.player.ui.PlayerScreen
import app.nova.player.ui.theme.NovaColors
import app.nova.player.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            NovaTheme {
                Box(Modifier.fillMaxSize().background(NovaColors.Abyss)) {
                    AnimatedContent(
                        targetState = vm.screen,
                        contentKey = { it::class },
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { screen ->
                        when (screen) {
                            Screen.Login -> LoginScreen(vm)
                            Screen.Home -> HomeScreen(vm)
                            is Screen.Player -> PlayerScreen(vm, screen.request)
                        }
                    }
                }
            }
        }
    }
}
