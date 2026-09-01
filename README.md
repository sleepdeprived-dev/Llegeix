# Llegeix

An Android PDF reader built for reading Catalan documents while learning the
language. Written in Kotlin with Jetpack Compose.

## Features

- **Library** — discovers PDFs on the device through MediaStore, the Storage
  Access Framework, and direct file picks, with sorting and search.
- **Reader** — page rendering via [PdfiumAndroid](https://github.com/johngray1965/PdfiumAndroid),
  with a selectable text layer over the rendered page.
- **Organisation** — folders, recently viewed, and bookmarks, persisted in a
  Room database.
- **Translation** — tap and hold a word to translate it with ML Kit's on-device
  translation models.
- **Settings** — light/dark/system theme, a choice of accent colour (or Material
  You, taken from the wallpaper), and the app's own language.

## Language

The interface is available in Catalan and English, and defaults to **Catalan**
regardless of the device language — this is a tool for reading Catalan, so it
starts there. The choice lives in Settings and applies to the app alone; it does
not change the device. "Llegeix" is a proper noun and is never translated.

## Requirements

- Android 12 (API 31) or newer
- Android Studio with JDK 17+ (the bundled JBR works)

## Building

```sh
./gradlew assembleDebug
```

### Signed release builds

Release signing credentials are read from `local.properties` (which is
gitignored), falling back to environment variables so CI can supply them
instead:

```properties
llegeix.storeFile=/path/to/llegeix-release.jks
llegeix.storePassword=...
llegeix.keyAlias=llegeix
llegeix.keyPassword=...
```

The environment-variable equivalents are `LLEGEIX_STORE_FILE`,
`LLEGEIX_STORE_PASSWORD`, `LLEGEIX_KEY_ALIAS`, and `LLEGEIX_KEY_PASSWORD`. When
no keystore is configured the release build still succeeds, but produces an
unsigned APK.

```sh
./gradlew assembleRelease
```

## Tests

```sh
./gradlew test              # unit tests
./gradlew connectedAndroidTest   # instrumented tests, including Room migrations
```
