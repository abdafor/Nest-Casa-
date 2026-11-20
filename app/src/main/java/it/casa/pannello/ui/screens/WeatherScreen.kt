package it.casa.pannello.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.casa.pannello.ui.theme.Corners
import it.casa.pannello.ui.theme.FocusGuard
import it.casa.pannello.ui.theme.GlassButton
import it.casa.pannello.ui.theme.GlassFocusable
import it.casa.pannello.ui.theme.GlassPanel
import it.casa.pannello.ui.theme.PanelBackground
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt
import it.casa.pannello.ui.weather.WeatherBackground
import it.casa.pannello.ui.weather.weatherDetails
import it.casa.pannello.weather.WeatherUi

/**
 * Meteo a schermo intero: situazione attuale, prossime 24 ore (scorri con ◀ ▶)
 * e prossimi 5 giorni. Indietro = home.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherScreen(weather: WeatherUi, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val first = remember { FocusRequester() }
    val data = (weather as? WeatherUi.Ok)?.data

    Box(Modifier.fillMaxSize()) {
        if (data != null) {
            WeatherBackground(data.kind, data.isDay, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.30f)))
        } else PanelBackground { }

        FocusGuard(first, Modifier.fillMaxSize()) {
            if (data == null) {
                Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Txt(if (weather is WeatherUi.Loading) "Meteo in caricamento…" else "Meteo non disponibile", 36.sp, weight = FontWeight.Bold)
                    GlassButton("‹  Indietro", onBack, focusRequester = first)
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(data.emoji, 88.sp, ellipsis = false, wrap = false)
                        Spacer(Modifier.width(12.dp))
                        Txt("${data.temp}°", 96.sp, weight = FontWeight.Bold, ellipsis = false, wrap = false)
                        Spacer(Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Txt("${data.place} · ${data.description}", 32.sp, weight = FontWeight.Bold, ellipsis = false)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                weatherDetails(data).forEach {
                                    GlassPanel(shape = Corners.Pill) {
                                        Txt(it, 20.sp, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), ellipsis = false, wrap = false)
                                    }
                                }
                            }
                        }
                    }

                    Txt("Prossime ore", 24.sp, weight = FontWeight.SemiBold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    ) {
                        itemsIndexed(data.hours) { i, h ->
                            GlassFocusable(
                                onClick = {},
                                modifier = Modifier.width(100.dp).height(132.dp),
                                focusRequester = if (i == 0) first else null,
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Txt(h.time, 19.sp, color = Sage.InkSoft, weight = FontWeight.Medium, maxLines = 1, ellipsis = false)
                                    Txt(h.emoji, 34.sp, ellipsis = false, wrap = false)
                                    Txt("${h.temp}°", 28.sp, weight = FontWeight.Bold, maxLines = 1, ellipsis = false)
                                    Txt(h.rain?.let { "💧 $it%" } ?: " ", 16.sp, color = Sage.InkSoft, maxLines = 1, ellipsis = false)
                                }
                            }
                        }
                    }

                    Txt("Prossimi 5 giorni", 24.sp, weight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        data.days.forEach { d ->
                            GlassFocusable(onClick = {}, modifier = Modifier.weight(1f).height(140.dp)) {
                                Column(
                                    Modifier.fillMaxSize().padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Txt(d.label, 21.sp, weight = FontWeight.SemiBold, maxLines = 1, ellipsis = false, align = TextAlign.Center)
                                    Txt(d.emoji, 38.sp, ellipsis = false, wrap = false)
                                    Txt("${d.max}° / ${d.min}°", 21.sp, weight = FontWeight.Bold, maxLines = 1, ellipsis = false)
                                    Txt(d.rain?.let { "💧 $it%" } ?: " ", 16.sp, color = Sage.InkSoft, maxLines = 1, ellipsis = false)
                                }
                            }
                        }
                    }
                    GlassButton("‹  Indietro", onBack, Modifier.padding(horizontal = 14.dp))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
