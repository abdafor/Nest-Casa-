package it.casa.pannello.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.casa.pannello.ui.theme.GlassFocusable
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt
import it.casa.pannello.weather.WeatherData
import it.casa.pannello.weather.WeatherUi

/** Voci di dettaglio brevi (si dispongono su più righe se manca spazio: mai tagliate). */
fun weatherDetails(w: WeatherData): List<String> = buildList {
    add("Percepita ${w.feels}°")
    add("Min ${w.min}°")
    add("Max ${w.max}°")
    w.rainPercent?.let { add("💧 $it%") }
    add("💨 ${w.windKmh} km/h")
    add("🌅 ${w.sunrise}")
    add("🌇 ${w.sunset}")
}

/** Scheda meteo della home: sfondo animato, icona e temperatura grandi. OK = schermo intero. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherCard(
    weather: WeatherUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    GlassFocusable(onClick, modifier.heightIn(min = 150.dp), focusRequester = focusRequester) {
        when (weather) {
            is WeatherUi.Ok -> {
                val w = weather.data
                WeatherBackground(w.kind, w.isDay, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.34f)))
                Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(w.emoji, 60.sp, ellipsis = false, wrap = false)
                    Spacer(Modifier.width(8.dp))
                    Txt("${w.temp}°", 64.sp, weight = FontWeight.Bold, ellipsis = false, wrap = false)
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("${w.place} · ${w.description}", 22.sp, weight = FontWeight.SemiBold, ellipsis = false)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            weatherDetails(w).forEach { Txt(it, 17.sp, color = Sage.InkSoft, ellipsis = false, wrap = false) }
                        }
                    }
                }
            }
            WeatherUi.Loading -> Txt("Meteo…", 24.sp, Modifier.padding(28.dp), color = Sage.InkSoft)
            WeatherUi.Error -> Txt("Meteo non disponibile", 22.sp, Modifier.padding(28.dp), color = Sage.InkSoft, ellipsis = false)
        }
    }
}
