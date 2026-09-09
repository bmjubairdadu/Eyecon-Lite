# Eyecon Lite — Finding Anyone

Premium caller-identification Android app. Search any number, see the caller's name and photo
(via Eyecon data), and download a stylish portrait PNG card straight to the Download folder.

## Features

- Splash screen with app logo (2.2s)
- No login — direct premium search screen
- Dark navy + blue premium UI
- Number search via Eyecon API (`getnames.jsp` + `pic`)
- Result card: photo, name, number pill, details
- Download button: stylish **1080×1920 portrait PNG** → `Download/EyeconLite_<number>.png`
- Recent search history (last 6)

## API (captured from `eyecon.har`)

- `GET https://api.eyecon-app.com/app/getnames.jsp?cli=<number>&lang=en&is_callerid=true&is_ic=true&cv=...`
  → `[{"name":"...","type":"..."}]`
- `GET https://api.eyecon-app.com/app/pic?cli=<number>&size=big&type=0/1`
  → `image/jpeg`
- Required headers: `e-auth`, `e-auth-c`, `e-auth-k`, `e-auth-v`

> If the API returns 401/403, the captured session expired. Capture a fresh HAR from the
> Eyecon app (filter `api.eyecon-app.com/app/getnames`) and update the values in
> `app/src/main/java/com/eyeconlite/data/EyeconApi.kt` (`E_AUTH`, `E_AUTH_C`, `E_AUTH_K`, `E_AUTH_V`).

## Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
Prebuilt APK is attached to the GitHub release.

## Project structure

- `app/src/main/java/com/eyeconlite/MainActivity.kt` — splash + search + result UI
- `app/src/main/java/com/eyeconlite/data/EyeconApi.kt` — Eyecon API client
- `app/src/main/java/com/eyeconlite/data/CardImageMaker.kt` — portrait PNG card builder + saver
- `app/src/main/res/drawable/app_logo.png` — app logo
