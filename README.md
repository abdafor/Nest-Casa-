# Il mio pannello 🌿

App per **Google TV / Chromecast con Google TV**: telecamere Tapo in diretta, orologio e meteo.
Tutto resta in **rete locale**: niente cloud, niente telemetria, niente registrazioni.

## Installare sulla TV (con l'app "Downloader")

1. Sulla TV installa **Downloader** (AFTVnews) dal Play Store.
2. Impostazioni TV → **App → Sicurezza e restrizioni → Origini sconosciute** → attiva **Downloader**
   (su alcune versioni: *Installa app sconosciute*).
3. Apri Downloader, nel campo indirizzo scrivi:

   **`https://github.com/abdafor/nest-casa-/releases/latest/download/il-mio-pannello.apk`**

4. **Vai** → aspetta il download → **Installa** → **Fine**.
5. Apri **Il mio pannello** dall'elenco delle app.

> Per aggiornare: ripeti i punti 3–4. Si installa sopra la vecchia versione senza perdere le impostazioni.

## Primo avvio

1. **Impostazioni → Impostazioni dal telefono** (scrivere col telecomando è scomodo): inquadra il QR,
   scrivi utente e password dell'*Account telecamera* Tapo, città, camere → **Salva sulla TV**.
2. In alternativa scrivi tutto col telecomando (premi **OK** su un campo per la tastiera).

## Uso

- **Home**: le camere in diretta. **OK** = schermo intero. **Frecce** = muovi la camera. **Indietro** = esci.
- Sulle Tapo serve l'**Account camera** creato nell'app Tapo (Impostazioni avanzate → Account camera) e **ONVIF** attivo per il movimento.

## Privacy

Utente e password sono salvati **cifrati** sul dispositivo e non stanno nel codice. Nei log non compaiono
password né indirizzi completi. Nessuna immagine viene salvata. L'unico traffico verso internet è il meteo
(Open-Meteo, solo nome città).

## Per chi modifica il codice

Kotlin + Jetpack Compose per TV + Media3 (RTSP). Cartelle: `camera/` (video e ONVIF), `data/` (salvataggio),
`ui/` (grafica e schermate), `server/` (impostazioni dal telefono), `weather/`.
La build parte da sola a ogni push (`.github/workflows/build.yml`) e pubblica l'APK nelle *Releases*.
La firma usa i secret `SIGNING_KEYSTORE_B64` e `SIGNING_PASSWORD`.
Predisposto (spento) il modo "launcher": in `AndroidManifest.xml` basta mettere `enabled="true"` su `LauncherAlias`.
