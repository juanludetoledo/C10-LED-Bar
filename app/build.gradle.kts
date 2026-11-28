plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Nombre del fichero APK de salida (en vez del genérico "app-release.apk")
base {
    archivesName.set("C10-LED-Control-V18")
}

android {
    namespace = "com.juanlu.c10led"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.juanlu.c10led"
        minSdk = 26
        targetSdk = 34
        versionCode = 62
        versionName = "3.2.3-V19"
    }

    // La clave de firma NO esta en este repositorio. La APK oficial se firma aparte y se publica en Releases.
    // Para firmar en local: deja c10led.jks en la raiz y define SIGNING_STORE_PASSWORD y SIGNING_KEY_PASSWORD.
    val firmaFichero = rootProject.file("c10led.jks")
    val hayFirma = firmaFichero.exists() && System.getenv("SIGNING_STORE_PASSWORD") != null

    signingConfigs {
        if (hayFirma) {
            create("release") {
                storeFile = firmaFichero
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "c10led"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Debug y release firmados con la MISMA clave: cualquier APK se actualiza encima de la anterior.
        debug {
            if (hayFirma) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            if (hayFirma) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
}
