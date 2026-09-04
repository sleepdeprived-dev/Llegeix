# Llegeix

An Android PDF reader built for reading Catalan documents while learning the
language. Written in Kotlin with Jetpack Compose.

## Releasing

The source is private and the builds have to be public, because the app's update
check asks GitHub for them with no credentials and a token shipped inside a
sideloaded APK is a token everybody holding the APK has. So the signed APKs and
the release notes go to
[Llegeix-releases](https://github.com/sleepdeprived-dev/Llegeix-releases), which
carries nothing else, while the tag stays here. `tools/release.sh <notes-file>`
does both after bumping the version in `app/build.gradle.kts`.

## Features

- **Library** — discovers PDFs on the device through MediaStore, the Storage
  Access Framework, and direct file picks, with sorting, search, first-page
  cover thumbnails, and a choice of list or grid. It opens on the two things
  somebody launching a reading app actually wants: the books they are part-way
  through, and the places their books come from. Sorting, the layout and a
  rescan live behind the one overflow menu, so the app bar carries the app's
  name and nothing else it does not need.
- **Continue reading** — a shelf of part-read books along the top of the
  library, newest first, each with its cover, its progress and the page it was
  left on. Finished books drop off it on their own, and so do ones never opened
  past the first page. This used to be a tab called Recent, which put "carry on
  with what I was reading" one tap away from the screen that appears on launch;
  the full history is still there, one press of the shelf's own button away.
- **Reading progress** — how long a document is, learned the first time it is
  opened, so every cover in the app carries a bar along its foot and every list
  row says how far in you are. A shelf of two hundred PDFs answers "which of
  these have I started" without a single number being read.
- **Sources** — where the library's documents come from, as a strip of tiles at
  the top of the library rather than an item in a menu. Each tile says what it
  holds and what it is contributing — "31 of 48" when part of it is switched
  off — because a library quietly missing a folder used to look exactly like a
  library showing everything. Pressing one opens it into a tree of tick boxes;
  the last tile adds another. Nothing here deletes anything from the phone.
- **Browsing** — the library is a small file manager over the folders the PDFs
  are already in: folders first with a count of everything below them, then the
  documents that sit in this folder, with a path along the top and the back
  button walking up it. A flat list of everything and the read-later shelf are
  each one chip away, and a search leaves the folder you are in and looks
  through all of them.
- **Reader** — page rendering via [PdfiumAndroid](https://github.com/johngray1965/PdfiumAndroid),
  with a selectable text layer, pinch and double-tap zoom, and find-in-document
  across every page. One slim bar, and a tap on the page takes even that away.
  The page is lifted off its ground by a shadow so it reads as paper rather than
  as a white rectangle, and the page counter and the find counter are worded
  differently on purpose — they used to be the same two numbers in the same
  shape, on screen together, meaning different things.
- **Getting around a document** — the hairline under the bar is a scrubber: drag
  it, or tap anywhere along it, and a bubble names the page as it goes. A
  document that carries its own table of contents gets a button for it, opened
  at the section you are in rather than at chapter one. Before these, reaching
  page 180 of a 300-page book meant 180 swipes.
- **How the page is drawn** — one sheet holds the four settings that used to be
  scattered or missing: the page tinted as paper, warm or inverted; pages turned
  or scrolled as one strip; the blank margins trimmed off so the type comes out
  bigger without zooming; and the zoom itself. Trimming is the biggest
  legibility win on a phone, where a fifth of an A4 page's width is paper with
  nothing on it. The margins are found by looking at the pixels rather than at
  the text layer, so it works on scans too.
- **Find** — a search says how many times the word is in the book and where,
  and then shows them: every occurrence quoted in the line it was found in,
  grouped under its page, with the word itself the only coloured thing on the
  row. Tapping one goes straight to it. A strip under the field carries the
  count and opens and closes the list, the row you are on is marked so stepping
  with the arrows never loses your place, and picking a result hands the whole
  screen back to the page. The library's own rows offer "Search inside…", which
  opens the document with the find bar already carrying whatever was typed.
- **Collections** — the shelves you make, as against the folders the phone
  already has. They shared a word and an icon until they were impossible to tell
  apart on sight; now a collection has its own glyph and its own name. Making
  one walks straight into it with a picker already up: everything you own, in
  one list, with a tick beside each and a note saying which collection a
  document is in already. Filling a collection used to be four taps a book,
  through a menu and a dialog, with the collection never once on screen.
- **Organisation** — collections, tags with a colour you can change at any time
  and sort by, reading history, and bookmarks, persisted in a Room database. A
  collection's colour is a disc behind its icon rather than a tint on the
  outline, and the library row says where a PDF has been filed. Starring a book
  and marking it to read later are a swipe of its row in either direction.
- **Translation** — press and hold a word to translate it with ML Kit's on-device
  models; keep holding and drag to take a whole phrase, which comes back
  translated, broken down word by word, and set in the line it came from. The
  hold is shorter than the platform's and answered with a tick of haptics the
  moment it takes, the highlight follows the finger without the stutter of a
  job started per pointer event, another tick marks each word the selection
  takes in, and the finished highlight is left alone for a beat before the
  sheet rises over it — long enough to see what was picked, short enough that
  nothing feels slow.
- **Dictionary** — one of four tabs, because a word you want to check is not
  always a word in front of you. Type any Catalan word and the bundled
  Viccionari and thesaurus are searched as you go: the headwords you could mean
  appear under the field, shortest first, and what you actually typed is always
  the row at the top, so a plural, a conjugated verb or a name nobody listed is
  still something you can look up. The answer is the reader's own lookup panel —
  the word, its pronunciation, the translation, the definition in Catalan, the
  synonyms and the antonyms — because a word checked here and the same word
  pressed while reading should not look like two different words. The star keeps
  it with everything else you have saved.
- **Privacy** — nothing is collected, and the manifest is made to say so as
  well as the policy: the app's data is excluded from Google's cloud backup, so
  a vocabulary list and the sentences behind it never leave the phone, while
  Android's direct device-to-device transfer still carries it to a new one.

- **Updates** — Llegeix is sideloaded rather than installed from a store, so
  nothing would ever update it. Configuració has a button that asks, once, when
  it is pressed: it reads the newest release from a small public repository that
  carries the builds and nothing else, says what changed, fetches the APK for
  this phone's architecture rather than the universal one, and hands it to
  Android's own installer. No background poll, no check on launch, no
  notification, and nothing installed without the system's own dialog. The
  request carries no identifier — it is the same unauthenticated call anybody
  can make of a public repository.

- **Scanned pages** — a scan is a photograph of writing: there is no text in
  the file, so press-and-hold used to do nothing at all and there was no way to
  tell that from a missed press. Now a page with no text of its own is read with
  on-device recognition the first time somebody presses on it, and the words
  become selectable, translatable and saveable like any others. The model is
  bundled rather than downloaded, so it works offline the first time it is
  asked. Nothing is read until it is needed, and a page is only read once.
- **Pronunciation** — Central Catalan IPA, generated from the spelling rather
  than looked up, so it works for any word including names and inflections,
  backed by a word list for the one thing spelling cannot record. See the note
  below.
- **Saved words** — star a word or phrase and it is kept with its translation,
  its pronunciation, the line it appeared in, and the document, page and line it
  came from. One starred in the Dictionary has no page behind it and says so,
  rather than quoting a line it was never on. Where a saved word turns up on a
  page you are reading it is quietly underlined, so the book itself shows what
  has been worked on.
- **Practice** — the saved words are also a deck. A word saved and never met
  again is a word that was not learned, and a list you scroll past is not
  meeting it, so a short session asks about the ones that are due: the Catalan,
  a pause, then what it meant — including what it meant *in the line it was
  saved from*, which is why the app kept the sentence. Right and it moves up a
  box and comes back later; wrong and it comes back in ten minutes. Everything
  is on the device and the schedule is derived from one stored number, so the
  intervals can be tuned without migrating anything.
- **Search** — one field, in the library, in the dictionary, in the saved words
  and in the reader's find bar, so searching looks and behaves the same wherever
  you do it. Each offers back what was searched for last, under the field while
  it is still empty. Only searches that actually found something are kept, which
  is what keeps half-typed words out of the list; the reader's find, the
  dictionary and the saved words share one pile, because a word chased across a
  page is the one you come back looking for in the dictionary, and then in your
  own list.
- **Settings** — reached from the gear in the Library app bar: light, dark,
  AMOLED black or follow-the-system, an accent colour (Material You from the
  wallpaper, one of six named colours, or one mixed by hand with a hue slider
  and a hex field), and the app's own language. How the *page* is drawn is not
  here — that belongs to the reader, and lives in its display sheet.

## Design

Llegeix is built for reading in a second language with ADHD, so the interface is
held to one rule: never make the reader work out what to look at. A library of
two hundred PDFs in one list is a list with nowhere to start, so the default is
to walk the folders they are already filed in and let each screen ask one
question — what is in here. Screens carry one obvious action at a time, empty
states sit centred in the space they have rather than stacked under the app bar,
and grouping is done with space rather than a rule between every row. Menu items
are marked with a single-colour glyph in the menu's own ink rather than a colour
emoji — a mark you can aim at without reading it, which does not then compete
with the words for attention.

Two rules that came out of using it. **The answer to a question belongs on the
screen the question is asked about.** "Why is that book not in my library" is
asked while looking at the library, so the sources are a strip along the top of
it rather than an item in the overflow menu called *Fonts* — a word that only
means anything once you already know what it does. **A fact worth reading at a
glance should not be a number.** How far through a book you are is a bar on its
cover, not a page count to do arithmetic on; how much of a source is reaching
the library is "31 of 48", not "48".

There were five tabs, three of them re-cuts of the same documents. There are now
four: the books, the language, the shelves you made, and what you saved. Recent
became the shelf of part-read books at the top of the library, which is where
somebody wanting to carry on reading was going to look anyway.

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
