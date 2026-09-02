# Llegeix

An Android PDF reader built for reading Catalan documents while learning the
language. Written in Kotlin with Jetpack Compose.

## Features

- **Library** — discovers PDFs on the device through MediaStore, the Storage
  Access Framework, and direct file picks, with sorting, search, first-page
  cover thumbnails, and a choice of list or grid. Sorting, the layout, a rescan
  and the way into Sources all live behind the one overflow menu, so the app bar
  carries the app's name and nothing else it does not need.
- **Browsing** — the library opens as a small file manager over the folders the
  PDFs are already in: folders first with a count of everything below them, then
  the documents that sit in this folder, with a path along the top and the back
  button walking up it. A flat list of everything and the read-later shelf are
  each one chip away, and a search leaves the folder you are in and looks
  through all of them.
- **Sources** — one screen for where the library's documents come from: the
  whole-device scan, the folders you have granted, and the folders found inside
  them. Each source arrives folded and says what it holds — "48 PDFs · 12
  folders" — and opens into a tree of tick boxes. The source's own box is
  three-state, so one that is only partly in the library says so without being
  opened, and ticking it is the quick way back to all of it or none of it.
  Nothing on this screen deletes anything from the phone.
- **Reader** — page rendering via [PdfiumAndroid](https://github.com/johngray1965/PdfiumAndroid),
  with a selectable text layer, pinch and double-tap zoom, find-in-document
  across every page, and an invert toggle that darkens the page itself without
  touching the app's theme. One slim bar, no bottom chrome.
- **Organisation** — folders, tags with a colour you can change at any time and
  sort by, recently viewed, and bookmarks, persisted in a Room database. A
  folder's colour is a disc behind its icon rather than a tint on the outline,
  the picker that files a PDF shows those colours along with what each folder
  holds and which one the document is in already, and the library row says where
  a PDF has been filed.
- **Translation** — press and hold a word to translate it with ML Kit's on-device
  models; keep holding and drag to take a whole phrase, which comes back
  translated, broken down word by word, and set in the line it came from.
- **Pronunciation** — Central Catalan IPA, generated from the spelling rather
  than looked up, so it works for any word including names and inflections,
  backed by a word list for the one thing spelling cannot record. See the note
  below.
- **Saved words** — star a word or phrase and it is kept with its translation,
  its pronunciation, the line it appeared in, and the document, page and line it
  came from.
- **Settings** — reached from the gear in the Library app bar: light, dark,
  AMOLED black or follow-the-system, an accent colour (Material You from the
  wallpaper, one of six named colours, or one mixed by hand with a hue slider
  and a hex field), and the app's own language.

## Design

Llegeix is built for reading in a second language with ADHD, so the interface is
held to one rule: never make the reader work out what to look at. A library of
two hundred PDFs in one list is a list with nowhere to start, so the default is
to walk the folders they are already filed in and let each screen ask one
question — what is in here. Screens carry one obvious action at a time, empty states sit centred in the space they have
rather than stacked under the app bar, one-off setup lives behind a menu instead
of on the reading surface, and grouping is done with space rather than a rule
between every row. Menu items are marked with a single-colour glyph in the
menu's own ink rather than a colour emoji — a mark you can aim at without
reading it, which does not then compete with the words for attention.

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

## A note on the pronunciation

The IPA is generated by rule from Catalan spelling, targeting Central
(standard) Catalan. It covers what the orthography actually encodes: unstressed
vowel reduction, stress placement, the digraphs, and the positional consonants —
s voicing between vowels, r trilled initially, final devoicing, betacism, silent
final r in polysyllables.

One thing spelling does not encode is whether a stressed **e** or **o** without a
written accent is close or open. *Pedra* is [ˈpeðɾə] but *terra* is [ˈtɛrə], and
nothing in the letters distinguishes them.

`assets/catalan-aperture.tsv` answers that for the words it lists, and the
lookup strips regular inflection so listing a lemma also covers its plural,
feminine and verb forms. Anything the list does not reach still takes the open
vowel and is marked **approx.** in the app, so a guess is never presented as a
fact. The file is a correction list, not a dictionary — it is deliberately short
and holds only entries worth being confident about, because a wrong entry is
worse than an honest marker. Extending it is the intended way to improve the
transcriber.

The transcription is deliberately broad: no spirantisation of b/d/g between
vowels, and no phrase-level assimilation.

## Downloads

The release publishes one APK per architecture plus a universal one. **arm64-v8a**
is the right choice for essentially every phone made since 2016. The universal
APK carries all four architectures and is roughly twice the size; it exists for
anyone who would rather not check.

Per-ABI splits halve the download because Pdfium ships native libraries for every
architecture and a device runs only one of them.
