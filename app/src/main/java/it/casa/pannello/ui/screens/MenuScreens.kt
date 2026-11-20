package it.casa.pannello.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.casa.pannello.data.Camera
import it.casa.pannello.data.CameraType
import it.casa.pannello.data.SecureStore
import it.casa.pannello.data.Settings
import it.casa.pannello.data.isValidHost
import it.casa.pannello.ui.theme.FocusGuard
import it.casa.pannello.ui.theme.GlassButton
import it.casa.pannello.ui.theme.GlassField
import it.casa.pannello.ui.theme.GlassFocusable
import it.casa.pannello.ui.theme.GlassPanel
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt
import java.util.UUID

/** Struttura comune delle pagine di impostazioni: titolo, contenuto, "Indietro" in fondo. */
@Composable
private fun Page(
    title: String,
    firstFocus: FocusRequester,
    onBack: () -> Unit,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FocusGuard(firstFocus, Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 780.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Txt(title, 44.sp, weight = FontWeight.Bold, ellipsis = false)
                if (hint != null) Txt(hint, 20.sp, color = Sage.InkSoft, ellipsis = false)
                content()
                GlassButton("‹  Indietro", onBack, Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Menu a elenco aperto dall'ingranaggio. */
@Composable
fun MenuScreen(
    onBack: () -> Unit,
    onCameras: () -> Unit,
    onAccount: () -> Unit,
    onWeather: () -> Unit,
    onPhone: () -> Unit,
    onInfo: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val first = remember { FocusRequester() }
    val items = listOf(
        Triple("📷", "Telecamere", onCameras),
        Triple("👤", "Account", onAccount),
        Triple("🌤️", "Meteo", onWeather),
        Triple("📱", "Impostazioni dal telefono", onPhone),
        Triple("ℹ️", "Info versione", onInfo),
    )
    Page("Impostazioni", first, onBack) {
        items.forEachIndexed { i, (icon, label, action) ->
            GlassFocusable(action, Modifier.fillMaxWidth(), focusRequester = if (i == 0) first else null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(icon, 30.sp, ellipsis = false, wrap = false)
                    Spacer(Modifier.width(18.dp))
                    Txt(label, 28.sp, Modifier.weight(1f), weight = FontWeight.SemiBold, ellipsis = false)
                    Txt("›", 32.sp, color = Sage.Deeper, weight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AccountScreen(settings: Settings, store: SecureStore, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val first = remember { FocusRequester() }
    var reveal by remember { mutableStateOf(false) }
    Page("Account", first, onBack, hint = "Account telecamera Tapo. Si salva da solo. Premi OK su un campo per scrivere.") {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassField("Utente", settings.user, { v -> store.update { it.copy(user = v) } }, focusRequester = first)
                GlassField(
                    "Password", settings.password, { v -> store.update { it.copy(password = v) } },
                    password = true, reveal = reveal, imeAction = ImeAction.Done,
                )
                GlassButton(if (reveal) "Nascondi password" else "Mostra password", { reveal = !reveal })
            }
        }
    }
}

@Composable
fun CitySettingsScreen(settings: Settings, store: SecureStore, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val first = remember { FocusRequester() }
    Page("Meteo", first, onBack, hint = "Scrivi la città: la previsione si aggiorna da sola.") {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                GlassField("Città", settings.city, { v -> store.update { it.copy(city = v) } }, imeAction = ImeAction.Done, focusRequester = first)
            }
        }
    }
}

@Composable
fun InfoScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val first = remember { FocusRequester() }
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    Page("Info versione", first, onBack) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("Il mio pannello", 32.sp, weight = FontWeight.Bold, ellipsis = false)
                Txt("Versione $version", 26.sp, ellipsis = false)
                Txt("Tutto resta in rete di casa. Nessuna immagine salvata, nessuna telemetria.", 20.sp, color = Sage.InkSoft, ellipsis = false)
                Txt("Per aggiornare: riapri il link in Downloader.", 20.sp, color = Sage.InkSoft, ellipsis = false)
            }
        }
        // Un elemento selezionabile in cima serve al telecomando: il pulsante Indietro sotto prende il focus.
        Spacer(Modifier.height(0.dp))
    }
}

/** Elenco telecamere + editor. */
@Composable
fun CamerasScreen(settings: Settings, store: SecureStore, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Camera?>(null) }
    var isNew by remember { mutableStateOf(false) }
    BackHandler { if (editing != null) editing = null else onBack() }

    val cam = editing
    if (cam != null) {
        CameraEditor(
            initial = cam,
            isNew = isNew,
            onSave = { c ->
                store.update { s ->
                    val list = if (isNew) s.cameras + c else s.cameras.map { if (it.id == c.id) c else it }
                    s.copy(cameras = list)
                }
                editing = null
            },
            onDelete = {
                store.update { s -> s.copy(cameras = s.cameras.filterNot { it.id == cam.id }) }
                editing = null
            },
            onCancel = { editing = null },
        )
    } else {
        val first = remember { FocusRequester() }
        Page("Telecamere", first, onBack) {
            settings.cameras.forEachIndexed { i, c ->
                GlassFocusable(
                    onClick = { isNew = false; editing = c },
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = if (i == 0) first else null,
                ) {
                    Row(Modifier.padding(horizontal = 26.dp, vertical = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt(c.name, 26.sp, weight = FontWeight.SemiBold, ellipsis = false)
                            Txt("${c.ip} · ${c.type.label}", 20.sp, color = Sage.InkSoft, ellipsis = false)
                        }
                        Txt("Modifica ›", 22.sp, color = Sage.Deeper, weight = FontWeight.SemiBold, wrap = false, ellipsis = false)
                    }
                }
            }
            GlassButton(
                "+  Aggiungi telecamera",
                { isNew = true; editing = Camera(UUID.randomUUID().toString(), "", "", CameraType.TAPO_PTZ) },
                Modifier.fillMaxWidth(),
                focusRequester = if (settings.cameras.isEmpty()) first else null,
            )
        }
    }
}

@Composable
private fun CameraEditor(
    initial: Camera,
    isNew: Boolean,
    onSave: (Camera) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var ip by remember { mutableStateOf(initial.ip) }
    var type by remember { mutableStateOf(initial.type) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    Page(if (isNew) "Nuova telecamera" else "Modifica telecamera", first, onCancel) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassField("Nome", name, { name = it }, focusRequester = first)
                GlassField("Indirizzo IP (es. 192.168.1.3)", ip, { ip = it }, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
                GlassButton("Tipo: ${type.label}   ›", { type = type.next() }, Modifier.fillMaxWidth())
            }
        }
        error?.let { Txt(it, 22.sp, color = Sage.Alert, weight = FontWeight.SemiBold, ellipsis = false) }
        GlassButton(
            "✓  Salva",
            {
                when {
                    name.isBlank() -> error = "Scrivi un nome per la camera"
                    !isValidHost(ip) -> error = "L'indirizzo IP non è valido"
                    else -> onSave(initial.copy(name = name.trim(), ip = ip.trim(), type = type))
                }
            },
            Modifier.fillMaxWidth(), primary = true,
        )
        if (!isNew) GlassButton("🗑  Elimina camera", onDelete, Modifier.fillMaxWidth())
    }
}
