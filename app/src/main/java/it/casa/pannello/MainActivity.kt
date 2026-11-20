package it.casa.pannello

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import it.casa.pannello.data.SecureStore
import it.casa.pannello.ui.screens.FullscreenScreen
import it.casa.pannello.ui.screens.HomeScreen
import it.casa.pannello.ui.screens.PhoneSetupScreen
import it.casa.pannello.ui.screens.AccountScreen
import it.casa.pannello.ui.screens.CamerasScreen
import it.casa.pannello.ui.screens.CitySettingsScreen
import it.casa.pannello.ui.screens.InfoScreen
import it.casa.pannello.ui.screens.MenuScreen
import it.casa.pannello.ui.screens.WeatherScreen
import it.casa.pannello.ui.theme.PanelBackground
import it.casa.pannello.ui.theme.PanelTheme
import it.casa.pannello.weather.WeatherClient
import it.casa.pannello.weather.WeatherUi
import kotlinx.coroutines.delay

/** Schermate dell'app. Per aggiungerne una: nuovo caso qui + ramo nel `when` di [App]. */
sealed interface Screen {
    data object Home : Screen
    data class Fullscreen(val cameraId: String) : Screen
    data object Weather : Screen
    data object Menu : Screen
    data object Cameras : Screen
    data object Account : Screen
    data object City : Screen
    data object Info : Screen
    data object Phone : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pannello sempre acceso finché l'app è in primo piano.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val store = SecureStore(applicationContext)
        setContent { PanelTheme { App(store) } }
    }
}

@Composable
private fun App(store: SecureStore) {
    val settings by store.settings.collectAsState()
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val weather by rememberWeather(settings.city)
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    when (val screen = stack.last()) {
        is Screen.Fullscreen -> {
            val cam = settings.cameras.firstOrNull { it.id == screen.cameraId }
            if (cam == null) pop() else FullscreenScreen(cam, settings.credentials, onBack = ::pop)
        }
        else -> PanelBackground {
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.Home -> HomeScreen(
                        settings = settings,
                        weather = weather,
                        // Senza utente/password si apre direttamente la pagina Account.
                        onOpenCamera = { push(if (settings.credentials.isComplete) Screen.Fullscreen(it.id) else Screen.Account) },
                        onOpenSettings = { push(Screen.Menu) },
                        onOpenWeather = { push(Screen.Weather) },
                    )
                    Screen.Weather -> WeatherScreen(weather, onBack = ::pop)
                    Screen.Menu -> MenuScreen(
                        onBack = ::pop,
                        onCameras = { push(Screen.Cameras) },
                        onAccount = { push(Screen.Account) },
                        onWeather = { push(Screen.City) },
                        onPhone = { push(Screen.Phone) },
                        onInfo = { push(Screen.Info) },
                    )
                    Screen.Cameras -> CamerasScreen(settings, store, onBack = ::pop)
                    Screen.Account -> AccountScreen(settings, store, onBack = ::pop)
                    Screen.City -> CitySettingsScreen(settings, store, onBack = ::pop)
                    Screen.Info -> InfoScreen(onBack = ::pop)
                    Screen.Phone -> PhoneSetupScreen(store, onBack = ::pop)
                    is Screen.Fullscreen -> Unit
                }
            }
        }
    }
}

/** Meteo aggiornato ogni 30 minuti (ogni 2 se l'ultimo tentativo è fallito). */
@Composable
private fun rememberWeather(city: String): State<WeatherUi> = produceState<WeatherUi>(WeatherUi.Loading, city) {
    while (true) {
        val result = WeatherClient.fetch(city)
        result.onSuccess { value = WeatherUi.Ok(it) }
        result.onFailure { if (value !is WeatherUi.Ok) value = WeatherUi.Error }
        delay(if (result.isSuccess) 30 * 60_000L else 2 * 60_000L)
    }
}
