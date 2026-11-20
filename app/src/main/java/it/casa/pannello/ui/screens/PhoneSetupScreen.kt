package it.casa.pannello.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.casa.pannello.data.SecureStore
import it.casa.pannello.server.PhoneSettingsServer
import it.casa.pannello.server.qrBitmap
import it.casa.pannello.ui.theme.GlassButton
import it.casa.pannello.ui.theme.GlassPanel
import it.casa.pannello.ui.theme.Sage
import it.casa.pannello.ui.theme.Txt

/**
 * "Impostazioni dal telefono": mostra un QR e un indirizzo. Dal telefono (stesso Wi-Fi)
 * si apre la pagina, si scrive con la tastiera e si salva. Il mini server si spegne
 * appena salvi o esci da questa schermata.
 */
@Composable
fun PhoneSetupScreen(store: SecureStore, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var url by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val server = remember { PhoneSettingsServer(current = { store.settings.value }, onSaved = { s -> store.update { s }; saved = true }) }

    DisposableEffect(server) {
        url = server.start()
        started = true
        onDispose { server.stop() }   // spegne sempre il server quando esci
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Txt("Impostazioni dal telefono", 44.sp, weight = FontWeight.Bold)
        val address = url
        when {
            saved -> GlassPanel {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt("Salvato ✓", 40.sp, weight = FontWeight.Bold, color = Sage.Deeper)
                    Txt("Le impostazioni sono sulla TV. Il server temporaneo è spento.", 24.sp)
                }
            }
            started && address == null -> GlassPanel {
                Txt("La TV non è collegata alla rete di casa (Wi-Fi/Ethernet).", 26.sp, Modifier.padding(32.dp), color = Sage.Alert)
            }
            address != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                val qr = remember(address) { qrBitmap(address).asImageBitmap() }
                Image(
                    bitmap = qr, contentDescription = "QR code",
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(10.dp),
                )
                Spacer(Modifier.width(32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt("1. Collega il telefono allo STESSO Wi-Fi della TV", 24.sp)
                    Txt("2. Inquadra il QR (oppure scrivi questo indirizzo):", 24.sp)
                    GlassPanel(shape = RoundedCornerShape(20.dp)) {
                        Txt(address, 26.sp, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), weight = FontWeight.Bold, maxLines = 1)
                    }
                    Txt("3. Scrivi utente e password e premi «Salva sulla TV»", 24.sp)
                    Txt("Funziona solo in casa e si spegne da solo dopo il salvataggio.", 20.sp, color = Sage.InkSoft)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GlassButton(if (saved) "‹  Fatto" else "Chiudi e spegni il server", onBack, primary = saved)
    }
}
