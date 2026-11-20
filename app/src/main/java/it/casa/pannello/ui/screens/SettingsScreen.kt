package it.casa.pannello.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import it.casa.pannello.ui.theme.GlassButton
import it.casa.pannello.ui.theme.GlassField
import it.casa.pannello.ui.theme.GlassFocusable
import it.casa.pannello.ui.theme.GlassPanel
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt
import java.util.UUID

/** Impostazioni: account, città, elenco camere. Le modifiche si salvano da sole. */
@Composable
fun SettingsScreen(settings: Settings, store: SecureStore, onBack: () -> Unit, onPhone: () -> Unit) {
    // Camera in modifica: null = si vede l'elenco; altrimenti l'editor.
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Txt("Impostazioni", 44.sp, weight = FontWeight.Bold)
                Txt("Le modifiche si salvano da sole. Premi OK su un campo per scrivere.", 20.sp, color = Sage.InkSoft)

                GlassButton("📱  Impostazioni dal telefono", onPhone, Modifier.fillMaxWidth(), primary = true)

                var reveal by remember { mutableStateOf(false) }
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Txt("Account telecamera Tapo", 26.sp, weight = FontWeight.SemiBold)
                        GlassField("Utente", settings.user, { v -> store.update { it.copy(user = v) } })
                        GlassField(
                            "Password", settings.password, { v -> store.update { it.copy(password = v) } },
                            password = true, reveal = reveal,
                        )
                        GlassButton(if (reveal) "Nascondi password" else "Mostra password", { reveal = !reveal })
                    }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Txt("Meteo", 26.sp, weight = FontWeight.SemiBold)
                        GlassField("Città", settings.city, { v -> store.update { it.copy(city = v) } }, imeAction = ImeAction.Done)
                    }
                }

                Txt("Telecamere", 30.sp, weight = FontWeight.SemiBold)
                settings.cameras.forEach { c ->
                    GlassFocusable(onClick = { isNew = false; editing = c }, Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 26.dp, vertical = 16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Txt(c.name, 26.sp, weight = FontWeight.SemiBold, maxLines = 1)
                                Txt("${c.ip} · ${c.type.label}", 20.sp, color = Sage.InkSoft, maxLines = 1)
                            }
                            Txt("Modifica ›", 22.sp, color = Sage.Deeper, weight = FontWeight.SemiBold)
                        }
                    }
                }
                GlassButton(
                    "+  Aggiungi telecamera",
                    { isNew = true; editing = Camera(UUID.randomUUID().toString(), "", "", CameraType.TAPO_PTZ) },
                    Modifier.fillMaxWidth(),
                )
                GlassButton("‹  Torna alla home", onBack, Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 780.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Txt(if (isNew) "Nuova telecamera" else "Modifica telecamera", 40.sp, weight = FontWeight.Bold)
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassField("Nome", name, { name = it })
                    GlassField("Indirizzo IP (es. 192.168.1.3)", ip, { ip = it }, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
                    GlassButton("Tipo: ${type.label}   ›", { type = type.next() }, Modifier.fillMaxWidth())
                }
            }
            error?.let { Txt(it, 22.sp, color = Sage.Alert, weight = FontWeight.SemiBold) }
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
            GlassButton("Annulla", onCancel, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}
