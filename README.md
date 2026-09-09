# Eyecon Lite — Finding Anyone

Premium caller-identification Android app. Search any number, see the caller's name and photo,
and download a stylish portrait PNG card straight to the Download folder.

## Features

- Splash screen with app logo (2.2s)
- No login — direct premium search screen
- Dark navy + blue premium UI
- Number search with automatic format normalization (`+880...`, `00880...`, `01...`)
- Result card: photo (tap for fullscreen + pinch zoom), name, country, operator, type
- Download button: stylish **1080×1920 portrait PNG** → `Download/EyeconLite_<number>.png`
- Recent search history (last 6)

## Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
Prebuilt APK is attached to the GitHub release.

## Author

Developed by **JUBAIR HOSEN**.

## Project structure

- `app/src/main/java/com/eyeconlite/MainActivity.kt` — splash + search + result UI
- `app/src/main/java/com/eyeconlite/data/EyeconApi.kt` — caller lookup client
- `app/src/main/java/com/eyeconlite/data/CardImageMaker.kt` — portrait PNG card builder + saver
- `app/src/main/res/drawable/app_logo.png` — app logo
