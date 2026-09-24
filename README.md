# EPI Scan

App nativa Android (Kotlin + Jetpack Compose) para catalogar EPIs: foto → análisis con Gemini Vision → validación por el técnico → catálogo local (Room) → exportación a Excel. Con actualizaciones OTA.

## Compilar

```bash
./gradlew :app:assembleDebug          # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # tests del generador de Excel
./gradlew :app:assembleRelease        # APK sin firmar (R8 activado); hay que firmarlo para distribuirlo
```

Requiere JDK 17 y Android SDK 34 (`sdk.dir` en `local.properties`).

## Configuración

Desde **Ajustes** dentro de la app (o como valor por defecto en `local.properties`, ojo: queda dentro del APK):

| Ajuste | Para qué |
|---|---|
| `GEMINI_API_KEY` | Clave de Google AI Studio. Sin ella se puede rellenar la ficha a mano. |
| `GEMINI_MODEL` | Por defecto `gemini-2.5-flash`. |
| `OTA_UPDATE_URL` | URL **https** del JSON de versiones. |
| `PLAY_IN_APP_UPDATES` | `true` solo si se distribuye por Google Play. |

## Actualizaciones OTA

Publica un JSON en una URL https (GitHub Releases, S3, servidor propio):

```json
{
  "latestVersionCode": 102,
  "latestVersionName": "1.0.2",
  "apkUrl": "https://servidor-empresa.com/downloads/epi-scan-v1.0.2.apk",
  "releaseNotes": "Mejoras en detección de marcado EN 388 y nuevo exportador Excel.",
  "mandatory": false,
  "sha256": "opcional: huella del APK; si está, se verifica antes de instalar"
}
```

Se compara `latestVersionCode` con el `versionCode` instalado (`app/build.gradle.kts`). El APK nuevo **debe estar firmado con la misma clave** que el instalado, o Android rechazará la actualización.

## Estructura

- `data/` Room (`EpiEntity`, `EpiDao`), repositorio, ajustes y cliente Gemini (`remote/EpiAnalyzer.kt` contiene el prompt normativo).
- `export/XlsxWriter.kt` genera el `.xlsx` a mano (ZIP + OOXML), sin Apache POI.
- `ota/OtaUpdateManager.kt` comprobación, descarga con `DownloadManager` e instalación con `FileProvider`.
- `ui/` pantallas Compose: catálogo, captura CameraX, Bottom Sheet de edición, ajustes, diálogo OTA.
