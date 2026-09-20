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
  through, and the places their books come from. There is no overflow menu on
  it at all any more. A menu hides which setting is in force — the only way to
  find out how the library was sorted was to open it — so the sort order is a
  chip in the list carrying the name of the order it is in, the layout is an
  icon in the bar showing the layout it switches to, and a rescan is the button
  beside them. Everything the screen does is visible on the screen.
- **Continue reading** — a shelf of part-read books along the top of the
  library, newest first, each with its cover, its progress and the page it was
  left on. Finished books drop off it on their own, and so do ones never opened
  past the first page. This used to be a tab called Recent, which put "carry on
  with what I was reading" one tap away from the screen that appears on launch;
  the full history is still there, one press of the shelf's own button away.

  Not everybody wants it open, so the heading is a control rather than a label:
  a tap folds the cards away and leaves a line saying how many books are on the
  shelf, and another tap brings them back. That is all it does. It could once
  be hidden altogether too, from the folded heading, from a dialog behind a hold
  and from Configuració — three places to manage one shelf, one of them on
  another screen, and a state in which it was gone with nothing on the library
  to say it had been there. Folded is as far as it goes now, so the way back is
  always the heading, where the reader is looking. Nothing is forgotten either
  way: the reading history is untouched, and every book keeps the page it was
  left on.
- **Reading progress** — how long a document is, learned the first time it is
  opened, so every cover in the app carries a bar along its foot and every list
  row says how far in you are. A shelf of two hundred PDFs answers "which of
  these have I started" without a single number being read.
- **Adding documents** — one sheet, reached from everywhere. There was a
  version of this app where the welcome screen offered a folder and a
  whole-device sweep, the empty library offered whichever of the two was not
  already on, and a small plus disc at the top of the list offered a third pair
  in a sheet of its own: three surfaces, overlapping, none of them the whole
  answer. Now every one of those buttons opens the same sheet, which lists every
  way in with a line each saying what it does — a folder and everything inside
  it, single PDFs, or the whole phone — in the order they should be preferred,
  and drops the sweep from the list once it is already on. The button that opens
  it is a plain + at the bottom right, where a phone puts its main action — as
  every add button in the app now is: the + is the most recognised mark there
  is, and the words are kept for screen readers rather than printed over the
  list. There is no *manage sources* row on the sheet: somebody who pressed + has
  already said what they want, and the card at the top of the library is where
  the other question is asked.
- **Sources** — where the library's documents come from, as one card at the top
  of the library rather than an item in a menu: a tinted disc, a name and a line
  of detail, with room around it. It was briefly built like the folder rows under
  it, on the grounds that it is a place you go into, and that was the mistake —
  it is not one of the reader's own folders and should not sit among them looking
  like one. It opens every source and every folder inside it as a tree of tick
  boxes, under the same disc and title that were pressed to get there, so the
  sheet reads as the inside of that card rather than as somewhere new. When part
  of a source is switched off the card reads "31 of 48 PDFs shown", in the
  accent, because a library quietly missing a folder otherwise looks exactly like
  a library showing everything.
  Holding a folder in the library offers to stop showing it, which is the same
  decision reached from the folder it is about — and it is a decision the app
  remembers: a hidden folder stays in the list, unticked, and ticking it brings
  its documents straight back. Giving a folder back for good is a separate
  choice in the same dialog, because it is a different thing: the permission is
  released and the folder would have to be found in the system picker again.
  Nothing here deletes anything from the phone.
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
- **What the app keeps for you** — three collections that fill themselves:
  *Starred*, *Read later* and *Recently viewed*, in one card above the ones you
  build. The last two used to live in the library — read-later as a filter chip,
  the history behind the overflow menu — which put "where did I set that aside"
  in two tabs at once. None of them is a real folder and none of them can be: a
  document belongs to at most one folder, so making any of them a folder would
  pull its PDFs out of the collection they were filed in. They are live views of
  the bookmark flag, the read-later flag and the reading history.
- **Collections** — the shelves you make, as against the folders the phone
  already has. They shared a word and an icon until they were impossible to tell
  apart on sight; now a collection has its own glyph and its own name. Making
  one walks straight into it with a picker already up: everything you own, in
  one list, with a tick beside each and a note saying which collection a
  document is in already. Filling a collection used to be four taps a book,
  through a menu and a dialog, with the collection never once on screen. A tick
  only ticks: the list is ordered by what was in the collection when the picker
  opened and does not rearrange itself while you work down it, because ticking a
  book used to sort it to the top and take it out from under the finger that had
  just tapped it, so a mistap could not be undone. A collection has a search
  field of its own once it is built, matched against what you call a document as
  well as against the file's own name — renaming a PDF is most of the point of
  collections, and a search that only knew the file name would be searching a
  list nobody is looking at.
- **Organisation** — collections, tags with a colour and a name you can change
  at any time and sort by, reading history, and bookmarks, persisted in a Room
  database. A collection's colour is a disc behind its icon rather than a tint on
  the outline, and the library row says where a PDF has been filed. Starring a
  book and marking it to read later are a swipe of its row in either direction.
  Holding any row — in the library, in a collection, on a saved word — opens the
  same menu the three dots would, which is the gesture every file list on the
  phone already answers to.
- **Names** — a PDF can be called something else. Course sites and language
  schools hand out files called `PROVA_C1_2019_comprensio_lectora_v2.pdf`, and that
  string is then the title on every shelf, row and app bar in the app. Renaming
  changes what *Llegeix* calls it and nothing else: the file in Downloads keeps
  its own name, because the app holds a read-only grant on it and has no
  business writing to it. The dialog says so, and clearing the field gives the
  file's own name back. The name is one fact, held in one place and asked for by
  every screen that draws a title — including the search, so a book renamed can
  be found by the name it was given.
- **Trimmed margins do not move the words** — with *Crop margins* on, the page
  is re-rendered as the ink alone, and for a long time the text layer went on
  being asked about it as though nothing had moved: pressing a word selected the
  one a line or two below it, because the top margin was exactly the part that
  had been cut. Every conversion between a bitmap pixel and a point in the PDF
  now goes through the same box the page was drawn inside — `PageGeometry`,
  which is pure arithmetic and tested as such.
- **Translation** — press and hold a word to translate it with ML Kit's on-device
  models; keep holding and drag to take a whole phrase, which comes back
  translated, broken down word by word, and set in the line it came from. The
  hold is shorter than the platform's and answered with a tick of haptics the
  moment it takes, the highlight follows the finger without the stutter of a
  job started per pointer event, another tick marks each word the selection
  takes in, and the finished highlight is left alone for a beat before the
  sheet rises over it — long enough to see what was picked, short enough that
  nothing feels slow.
- **Dictionary** — a tab of its own, because a word you want to check is not
  always a word in front of you. Type any Catalan word and the bundled
  Viccionari and thesaurus are searched as you go: the headwords you could mean
  appear under the field, shortest first, and what you actually typed is always
  the row at the top, so a plural, a conjugated verb or a name nobody listed is
  still something you can look up. The answer is the reader's own lookup panel —
  the word, its pronunciation, the translation, the definition in Catalan, the
  synonyms and the antonyms — because a word checked here and the same word
  pressed while reading should not look like two different words. The star keeps
  it with everything else you have saved.

  Sheets do not bounce at the end. Android's stretch overscroll takes a whole
  list into a render effect and squashes it while a finger keeps pulling, which
  inside a bottom sheet is a second thing deforming at the same moment as the
  sheet's own drag handling is deciding whose gesture it is — and the two fight,
  visibly, as judder. Every sheet in the app goes through one `AppBottomSheet`
  that turns it off, so reaching the bottom of a list in a sheet does nothing at
  all. The page itself keeps its stretch, where it is the only thing that says a
  document has ended.

  A conjugated verb is also read back as one — here and, in the same words,
  in the reader's own panel when a word is pressed on a page. Looking up
  *cantéssim* used to answer with a translation and, if the inflected-forms file
  happened to have it, a definition quietly filed under a different word; what
  was missing was the part a learner needs — that it is a verb, that the verb is
  *cantar*, that it is the first conjugation, and that this is the imperfect
  subjunctive, first person plural. That is there now, with the verb's own
  meaning under it, and in the Dictionary the infinitive is a row you can press
  to look it up properly.

  The infinitive is not guessed: it is read from the bundled table of inflected
  forms, which records it outright, irregulars and all. Which *form* it is comes
  from `CatalanVerbs`, which tries the six verbs no rule describes — *ser*,
  *estar*, *haver*, *anar*, *fer*, *tenir* — then the regular paradigm generated
  from the infinitive and compared with the accents off, since Catalan moves its
  stress around a paradigm and changes nothing else, and only then the ending on
  its own. Where none of the three is sure, it names the verb and says it could
  not place the form, because a learner told that *corria* is a conditional has
  been taught something false about their own reading and will not find out.

  None of it needs the network, so it is also the one part of the panel that
  still answers when the translation model has not been downloaded: the grammar
  comes off files already on the phone. It is worked out after the translation
  is on screen, like the rest of the breakdown, so nothing holds up the line the
  sheet was opened for, and it is skipped for a dragged phrase — which part of
  the verb *a boca de canó* is has no answer.
- **Privacy** — nothing is collected, and the manifest is made to say so as
  well as the policy: the app's data is excluded from Google's cloud backup, so
  a vocabulary list and the sentences behind it never leave the phone, while
  Android's direct device-to-device transfer still carries it to a new one.

  One feature talks to the internet without a button press, and only while a
  flashcard is open to be written: suggestions. After a pause in the typing,
  the word goes to ARASAAC (in Catalan), for its pictograms and for the
  Romanian and English its pictograms are labelled with, or, on the Photos tab, its
  English meaning goes to Openverse — the word and nothing else, with no
  identifier. Both are free public services that need no account or key. With
  no card open, nothing is sent.

- **Updates** — Llegeix is sideloaded rather than installed from a store, so
  nothing would ever update it. Configuració has a button that asks when it is
  pressed: it reads the newest release from a small public repository that
  carries the builds and nothing else, says what changed, fetches the APK for
  this phone's architecture rather than the universal one, and hands it to
  Android's own installer. Nothing is installed without the system's own dialog,
  and the request carries no identifier — it is the same unauthenticated call
  anybody can make of a public repository.

  The app also asks that question of its own accord, at most once a day, when
  the library comes to the foreground — and the entire result is a red dot on
  the settings gear. There is no background service, no job scheduler and no
  notification; it runs only while somebody is already looking at the app, and a
  phone that is offline simply learns nothing and tries again tomorrow. This is
  the one thing on the network nobody pressed a button for, and it exists
  because a sideloaded app that never mentions a new version is a sideloaded app
  running last March's build. Pressing the button clears the dot as readily as
  it sets it.

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
- **Said out loud** — a speaker beside the word, in the reader's panel, in the
  dictionary and on a practice card. IPA is a notation somebody has to have
  learned; this is the same fact for everybody else. It uses the phone's own
  speech engine, so nothing leaves the device, and where there is no Catalan
  voice at all the button is simply not drawn rather than sitting there dead.
  Where the voice merely has not been downloaded, the button offers to fetch it.
- **Dark pages keep their pictures** — inverting a page is not a colour filter
  any more. A filter is a matrix over every pixel with no exceptions, which is
  why dark mode used to turn every photograph in a book, and every diagram in a
  textbook, into a negative of itself. The page is now inverted as a
  bitmap by `PageInvert`, which finds the pictures by looking at the pixels —
  printed text sits on paper and paper is nearly white, and a photograph is not
  — and leaves them alone. Text goes light-on-dark; the illustrations stay
  illustrations.
- **Saved words** — star a word or phrase and it is kept with its translation,
  its pronunciation, the line it appeared in, and the document, page and line it
  came from. One starred in the Dictionary has no page behind it and says so,
  rather than quoting a line it was never on. Where a saved word turns up on a
  page you are reading it is quietly underlined, so the book itself shows what
  has been worked on.
- **Today's words** — a card at the top of the saved words, headed *Words I
  have learned today*, holding the words themselves rather than a number. A word
  is on it if it was saved today or practised today, because those are the two
  ways a word gets worked on here and a card that emptied itself on every day
  spent revising would only ever congratulate new reading. Each word is a pill
  showing the Catalan, and pressing it turns over the meaning it was saved with —
  the whole transaction of learning a word, offered where the day's words are
  already gathered, at the cost of a tap and no navigation at all. It is not
  drawn on a day with nothing on it: an empty card headed *today* is a reproach,
  and this is a record of what happened rather than a target that was missed.
- **Practice** — the saved words are also a deck. A word saved and never met
  again is a word that was not learned, and a list you scroll past is not
  meeting it, so a short session asks about the ones that are due: the Catalan,
  a pause, then what it meant — including what it meant *in the line it was
  saved from*, which is why the app kept the sentence. Right and it moves up a
  box and comes back later; wrong and it comes back in ten minutes. Everything
  is on the device and the schedule is derived from one stored number, so the
  intervals can be tuned without migrating anything.
- **Flashcards** — a tab of vocabulary written by hand, in decks you make:
  the Catalan on one side of a card and the Romanian on the other, with a
  picture if one helps. Writing a card fills in what the app can and says that
  it did: the pronunciation is worked out from the spelling as you type, marked
  *approx.* where the spelling cannot settle a vowel, and the translator on the
  phone suggests a meaning, in Romanian and, optionally, in English — each
  follows the word until you type in the field, and is yours from then on.
  A meaning is taken first from ARASAAC, whose pictograms are labelled by people
  in every language they cover — *poma* is *măr* there, where a translator
  handed the one word says "apple" — and from the translator only for words
  ARASAAC has not labelled. The field says which of the two it came from. It
  works the other way round too: a card can be started from its Romanian or its
  English, and the Catalan, its pronunciation and the other meaning are filled
  in from there. A pictogram's white paper is cut away and replaced with a
  colour of the theme — dark at night — while the drawing itself is left
  exactly as it was drawn.
  Pictures are offered as the word is typed, as a grid of two dozen: pictograms
  from ARASAAC and from Global Symbols — an index over three dozen other freely
  licensed symbol sets — both searched in Catalan; or openly licensed
  photographs from Wikimedia Commons and Openverse, searched with the card's
  English meaning, since the lone Catalan word is too thin a thing to translate
  and *pa* on its own comes back as Pennsylvania. Each pair is asked in parallel
  and the answers interleaved, with the same picture never offered twice: the
  shelves overlap, and a grid half made of pairs is half a grid. Photos are held
  to three safety checks: the source's own adult flag or the file's categories,
  a list of words never searched for, and the same list read against every
  photo's title and tags.
  They are offered and never placed — a picture of the wrong sense of *banc* is
  worse than none — and the one chosen keeps its maker's credit on the card.
  Your own photos come through Android's photo picker, so the app is never given
  the gallery. Either way a picture is copied in shrunk and upright, so a card
  keeps it after the original is gone. A deck is searched with its accents
  ignored, because the word you half remember is typed on whichever keyboard is
  up.

  Each deck is a card of its own on the tab, with a picture of its own — chosen
  from the same grid, or its first card's — a bar for how well it is known and
  its own way into practice. Decks can be pinned to the top. The decks are the
  choice, so there is no second list of them to choose from. Which way round is
  two flags, not two language names, and the meaning can be practised in
  Romanian or in English: a second flag switch, because recognising a Catalan
  word is the same skill whichever language the answer comes in, so it is the
  same schedule. Going through cards again when none are due is always one press
  away, as extra practice that does not move the schedule — repetition helps,
  and three right answers in ten minutes are not three days' learning. Saving a
  copy is a button in the corner.

  Decks can be grouped into collections, one level deep: *Vegetables*, *Fruit*
  and *At the market* on a shelf called *Food*, which folds open and shut and
  has a play button of its own. That button is the point of the feature —
  practising *food* is one session over every card on the shelf, dealt and
  scheduled like any other, not three sessions run back to back. A collection
  owns nothing but the grouping and, if it is given one, a picture: deleting one
  leaves every deck that was on it exactly where it was, and a deck belongs to
  at most one. The picture comes from the same grid a deck's does, searched with
  the shelf's name, and a shelf with none borrows the picture of the first deck
  on it — which is usually right and was, until it could be overruled,
  unarguable. Decks that are on no collection are simply the list they always
  were, so a reader who never makes one never sees one. The + makes either, with
  the choice as the first thing in the sheet rather than behind a second
  floating button.

  The way into practice is the same round button on every collection and every
  deck, and it never carries a number. It used to grow a count when something
  was due and shrink back when nothing was, so the same control was three shapes
  down one screen; and a number on a button reads as a promise about how many
  cards pressing it will deal. Pressing it goes through *everything* in that
  deck or on that collection — not the handful a schedule had picked out, which
  was a defensible design and not the one anybody wanted. A deck is a thing
  somebody wrote by hand, and going through it is the whole of what it is for.

  The schedule is still there and still moves with every answer; what it decides
  now is the *order*. The cards in the lowest Leitner box come first, so the
  least known are met while there is most attention left, and cards level with
  each other are shuffled so a deck never becomes a recitation. Those same boxes
  fill the bar on each deck.

  Which languages, and which way round, is one button in the app bar beside the
  one that saves a copy: four rows — Catalan → Romanian, the way back, then the
  same pair in English — each wearing its own flags, with the one in force
  ticked. One card serves all four, because the meaning in each language is a
  field on the card rather than a card of its own, and each direction keeps its
  own half of the schedule. That button is the whole of what used to be a panel
  headed *14 cards to review*, and then a strip of pills, across the top of the
  tab. Below the bar there is one thing left, and it is the only thing there
  that is not about a particular deck: *Practise them all*.

  Practice asks one way round at a time, and each way keeps its own schedule:
  recognising *pa* says nothing about producing it from *pâine*, so a right
  answer in the easy direction never pushes back the hard one. The speaker and
  the pronunciation go with the Catalan side and the picture with the meaning,
  so nothing on the question side gives the answer away. It is the saved words'
  scheduler, not a second one. When nothing is due, the screen says when the
  next card comes back and offers the other direction if anything is waiting
  there.

  The cards are the one thing in this app that cannot be found again: the
  app's data is kept out of Google's cloud on purpose, so an uninstall or a lost
  phone would take them. The foot of the tab says so, and saves a copy — one zip
  file, a plain JSON of every deck with both schedules and the pictures beside
  it — wherever the reader chooses. Bringing one back only ever adds: a deck of
  the same name is filled rather than duplicated, a card already in it is
  skipped, and restoring the same copy twice changes nothing. A copy is a file
  from outside the app, so it is read narrowly: nothing inside it is used as a
  path, every entry has a size limit, and each picture goes through the same
  shrinking as one from the photo picker.
- **Search** — one field, in the library, in the dictionary, in the saved words
  and in the reader's find bar, so searching looks and behaves the same wherever
  you do it. Each offers back what was searched for last, under the field while
  it is still empty. Only searches that actually found something are kept, which
  is what keeps half-typed words out of the list; the reader's find, the
  dictionary and the saved words share one pile, because a word chased across a
  page is the one you come back looking for in the dictionary, and then in your
  own list. Each row carries its own cross as well as the clear-the-lot above
  them: "all or nothing" is the wrong question to ask about five words when what
  is wanted is to be rid of one.
- **Settings** — reached from the gear in the Library app bar: light, dark,
  AMOLED black or follow-the-system, an accent colour (Material You from the
  wallpaper, one of six named colours, or one mixed by hand with a hue slider
  and a hex field), and the app's own language. How the *page* is drawn is not
  here — that belongs to the reader, and lives in its display sheet. Neither is
  the translation language: the reader's lookup panel carries that choice as a
  flag beside the word, where the question is actually asked, and a second
  control for it in Configuració only meant one of the two was always the wrong
  place to look.
- **Titles that carry their own mark** — the bottom bar draws an icon against
  every destination and the app then used to drop it the moment you arrived: the
  Dictionary was a book at the bottom of the screen and a bare word at the top of
  it. The icon now sits beside the title too, in the bar's own ink, so arriving
  somewhere confirms the press rather than merely following it. The library has
  always done this with the flag; the other two tabs had nothing.
- **The flag** — the mark beside the app's name is the Senyera, drawn rather
  than shipped as an asset so it stays crisp at any size. Pressing it offers the
  Estelada instead. It is not in Configuració on purpose: the question only
  makes sense while looking at the flag.

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
asked while looking at the library, so the sources are along the top of it
rather than an item in the overflow menu called *Fonts* — a word that only means
anything once you already know what it does. **A fact worth reading at a glance
should not be a number.** How far through a book you are is a bar on its cover,
not a page count to do arithmetic on; how much of a source is reaching the
library is "31 of 48", not "48".

A third, learned the expensive way: **a control that has earned its place still
has to earn its height.** The sources strip was right about *where* and wrong
about *how much* — three lines of permanent furniture for a fact that matters on
one visit in fifty. It is now a line with a button on it, and the fact is
printed on that line only when it is not the obvious one. The same reasoning
retired the read-later chip, which had been sitting among the two chips that
decide how the library is arranged while being a shelf rather than an
arrangement.

And a fourth, which arrived with the essay pages: **the further a control is
from the thing it changes, the worse it is, and a modal is infinitely far.**
Typing an answer used to mean a dialog over the page: you typed blind, pressed
a button, and found out afterwards whether the words fitted the gap. It is now
a box on the page with a cursor in it. The same reasoning moved rescanning out
of the overflow menu and onto the app bar, put the choice of translation
language next to the word being translated rather than in Configuració, and put
the two flags behind the flag.

There were five tabs, three of them re-cuts of the same documents. There are now
three: the books, the language, and what you set aside. Recent became the shelf
of part-read books at the top of the library, which is where somebody wanting to
carry on reading was going to look anyway, and its full history is now one of the
three collections the app keeps for itself — which is where a reader looking for
something they set aside goes.

Collections and Saved were the next pair to go. They were two names for setting
something aside, sitting next to each other in the bar, and between them they
drew the starred PDFs twice — once as an automatic collection and once as a whole
tab, from the same query on the same object. Joining them let one of those two
lists go, which is the better half of the trade.

Exam mode was the fourth tab, and in v4.1 it is gone — the feature, its five
tables, and the copies of the papers it kept on disk. Doing a practice paper on
a phone turned out to be worse than doing it on paper, which is what it was
imitating. Nothing was promoted into the space it left: the bar is not a shelf
with four slots to keep filled, it is the list of things this app is for.

Flashcards is the fourth tab now, and it earns the place rather than filling
it. It is not another view of the documents or of what was set aside while
reading: it is vocabulary written by hand, in decks you make, and one of the
main reasons to open the app. It sits last, after the three a reader meets
first. Four is the ceiling: anything wanting a tab after this has to merge into
one that exists.

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

## Credits

The pictograms offered for flashcards come from two places. Those from
[ARASAAC](https://arasaac.org) are the property of the Government of Aragón and
were created by Sergio Palao, under the Creative Commons BY-NC-SA licence; the
rest come through [Global Symbols](https://globalsymbols.com), which indexes
some three dozen freely licensed symbol sets — Mulberry, Sclera, Blissymbols and
others — each under its own licence, which the app reads from the index and
writes onto the card. Photographs come from
[Wikimedia Commons](https://commons.wikimedia.org) and
[Openverse](https://openverse.org), each under its own open licence. The credit
for any picture, from any of the four, is kept on the card it was put on.

## Downloads

The release publishes one APK per architecture plus a universal one. **arm64-v8a**
is the right choice for essentially every phone made since 2016. The universal
APK carries all four architectures and is roughly twice the size; it exists for
anyone who would rather not check.

Per-ABI splits halve the download because Pdfium ships native libraries for every
architecture and a device runs only one of them.
