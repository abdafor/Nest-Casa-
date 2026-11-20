#!/usr/bin/env bash
# Prova su emulatore Android TV: installa la vecchia versione, poi aggiorna con quella nuova,
# poi avvia l'app e raccoglie gli errori. Scrive tutto in prova.txt.
APK="$1"
OUT=prova.txt
: > "$OUT"
say() { echo "$*" | tee -a "$OUT"; }

say "== dispositivo =="
say "Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
say "leanback: $(adb shell pm has-feature android.software.leanback 2>&1 | tr -d '\r')"

say "== 1) installo la 1.0.6 (quella che funzionava) =="
curl -sL -o old.apk https://github.com/abdafor/nest-casa-/releases/download/v1.0.6/il-mio-pannello.apk
adb install -r old.apk 2>&1 | tr -d '\r' | tee -a "$OUT"

say "== 2) aggiorno con la nuova: $APK =="
adb install -r "$APK" 2>&1 | tr -d '\r' | tee -a "$OUT"
adb shell dumpsys package it.casa.pannello 2>&1 | grep -E "versionName|versionCode" | head -3 | tee -a "$OUT"

say "== 3) avvio l'app =="
adb shell am start -W -n it.casa.pannello/.MainActivity 2>&1 | tr -d '\r' | grep -E "Status|Error|Exception|Complete" | tee -a "$OUT"
sleep 15
say "processo attivo: $(adb shell pidof it.casa.pannello | tr -d '\r')"

say "== 4) errori nel log =="
adb logcat -d -b crash 2>&1 | tail -40 | tee -a "$OUT"
adb logcat -d 2>&1 | grep -E "PackageManager.*pannello|PackageParser|AndroidRuntime|FATAL EXCEPTION" | tail -30 | tee -a "$OUT"
exit 0
