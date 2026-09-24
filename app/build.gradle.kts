import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Valores opcionales desde local.properties o variables de entorno
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun prop(nombre: String, defecto: String = ""): String =
    localProps.getProperty(nombre) ?: System.getenv(nombre) ?: defecto

android {
    namespace = "com.episcan.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.episcan.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.0.4"

        buildConfigField("String", "GEMINI_API_KEY", "\"${prop("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${prop("GEMINI_MODEL", "gemini-2.5-flash")}\"")
        buildConfigField("String", "OTA_UPDATE_URL", "\"${prop("OTA_UPDATE_URL")}\"")
        buildConfigField("boolean", "PLAY_IN_APP_UPDATES", prop("PLAY_IN_APP_UPDATES", "false"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Permite volcar el .xlsx de prueba: ./gradlew testDebugUnitTest -Pxlsx.salida=ruta.xlsx
        unitTests.all { t ->
            (project.findProperty("xlsx.salida") as String?)?.let { t.systemProperty("xlsx.salida", it) }
        }
    }
}

dependencies {
    // Compose + Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // CameraX
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Room (SQLite local, offline-first)
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Red: Gemini Vision + comprobación OTA
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Miniaturas
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Google Play In-App Updates (canal oficial, opcional)
    implementation("com.google.android.play:app-update:2.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
