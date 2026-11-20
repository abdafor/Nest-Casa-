plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Numero di versione: in GitHub Actions arriva dal numero della build, così
// ogni APK nuovo si installa SOPRA il vecchio (serve un numero sempre crescente).
val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

// Firma: la chiave arriva da variabili d'ambiente (mai scritta nel repository).
val keystorePath: String? = System.getenv("KEYSTORE_FILE")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val canSign = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() && File(keystorePath).exists()

android {
    namespace = "it.casa.pannello"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.casa.pannello"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.2.$appVersionCode"
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = File(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "pannello"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.tv:tv-material:1.0.0")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Video: ExoPlayer con RTSP
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")

    // Salvataggio cifrato di utente/password
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // QR code per "Impostazioni dal telefono"
    implementation("com.google.zxing:core:3.5.3")
}
