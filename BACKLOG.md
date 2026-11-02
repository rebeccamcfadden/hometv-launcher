# Backlog

Features the new launcher needs but V1 doesn't have yet: where V1 uses mock data or a fallback, what was left
for V2, and what isn't designed. Each entry says what V1 does, what the real version needs, and any findings
so far. A PR that adds a mock adds its entry here; a PR that makes one real removes it. How this fits the
milestones: [docs/rework.md](docs/rework.md).

## Data and accounts

### Real catalogue
- **Now (V2 Phase 1):** with `tmdb_api_key` in `launcher/tv.properties`, titles, artwork, cast and crew (with
  photos), episodes, More like this, trailers, people and search come from TMDB (`TmdbCatalog`), in English
  (en-US). Movies and TV Shows list the household's lists as before; their titles start as stubs (name and year from
  `MockIds`) and fill in from the disk cache, then the network, two seconds after Home is up, three requests at a
  time. Answers are kept in the app's cache folder (`DiskCache`, 16 MB of JSON, 64 MB of images) for as long as
  cleanyourtv-ui keeps them: search 1 hour, discover 3 hours, a title 12 hours, seasons and people a week; a stale
  copy is used when TMDB can't be reached, so what was seen once shows offline. Images come at the TMDB size nearest
  the box (w300 small cards, w780 library cards, w1280 the preview, w185 portraits) and are decoded at the size
  shown into the 24 MB memory cache (`Art`). Without a token the build falls back to the mock catalogue, which needs
  no network; the 17 bundled JPEGs left the APK (1,612,551 B to 275,607 B) and its cards show the plain placeholder.
- **Mock ids:** the 43 mock titles map to TMDB by `MockIds` (looked up once by name and year, 2026-10-30);
  "marvel-one-shots" has no single TMDB title and drops out. The household's stored ids move to TMDB ids
  (`movie:329865`, `tv:95396`) when read. A TMDB build's ids mean nothing to a token-less build, so switching a TV
  between the two empties My list and the rest until it switches back.
- **Search:** TMDB's `/search/multi` once typing pauses (300 ms, from two letters, an hour's cache), merged with the
  fuzzy matching over every title the TV knows, so "arival" still finds Arrival. Titles without artwork and people
  without a photo are left out of TMDB's answer (it lists many namesakes and stubs). A genre typed in full lists its
  titles, the known ones first, then TMDB's most popular (`/discover`, movies and shows taken in turns, 40 at most);
  an exact genre ("drama") puts them before titles merely named like it.
- **Ratings:** the US certification from TMDB's release dates (movies, the theatrical one first) and content ratings
  (shows), on the US scale the filters use. A title without a US rating shows none and counts as not rated (NR) in
  Pick for me. **Open question:** use Sweden's ratings (Btl, 7, 11, 15) where TMDB has them, and how they map onto
  "Up to PG-13" and "Up to R".
- **Loading, offline and error states (V2 design 06-states A):** built from the network (`NavModel.internet`, Android's
  validated network) and the catalogue's own state (`Catalog.pending`, `failed`, `savedAt`; the rules in
  `CatalogState`, `StateWords`, `OfflineSearch`, the pieces in `StateUi`). Movies and TV Shows show eight skeleton
  cards while their lists first load (at most 8 seconds once some titles are in, so a slow network shows what has
  come), the amber "Offline · showing your lists as saved today 19:52" pill (the oldest saved copy's time) with
  "· prices may be out of date", or, offline with no title saved, the "nothing saved" block with Wi-Fi settings and
  Try again. The preview shows its gradient and skeletons while a summary's details load, and the amber error box in
  Overview, Where to watch, Cast & crew and Episodes when they failed with no saved copy; Retry and the network coming
  back load them again. Until the availability is in, Watch shows only its play icon, with no label (OK on it after a
  failure loads again): "Not on your services" only once it is in and there are no offers. Search offline looks at the saved titles only; Home's weather keeps the last forecast with "Offline ·
  updated 19:52". The nav's Wi-Fi icon is crossed out whenever Android reaches no internet. Still as before: More like
  this is an empty row until TMDB answers (it has no error state in the design), and a search card or a title whose
  artwork was never fetched keeps the raised placeholder offline.
  - **One request, one failure:** a title's details, cast, seasons and availability come in one TMDB request, so
    they fail together: the error box shows in every tab that needs them, not in one tab as the prototype's State
    menu shows it, Overview's too ("Couldn’t load details"; project lead, 2026-10-31). The header keeps what the card
    that opened the preview knew.
  - **TMDB down, network up:** offline means Android reaches no internet. When TMDB alone fails, the lists show
    their saved copy without the amber pill, and a preview whose details never loaded shows the error box.
  - **Not built here:** Settings offline (the Account, Sync and Weather panes; with the new Launcher settings page),
    and the Where to watch credit's "Saved today 19:52, prices may be out of date" (with 07-prices A).
  - **Not designed:** Search and Home's weather while loading (the spec's open list).
- **Listeners kept from R8:** the catalogue holds screens' listeners weakly, and R8 dropped the `onCatalog` fields that
  kept them (written, never read), so in release builds pages stopped hearing that titles filled in; Movies stayed on
  its first render until a tab switch. `proguard-rules.pro` now keeps those fields (found building 06-states A).
- **TMDB attribution:** TMDB's terms (developer.themoviedb.org/docs/faq, checked 2026-10-30) ask for the notice "This
  product uses the TMDB API but is not endorsed or certified by TMDB" in the app's About or Credits section, and for
  one of their approved logos (themoviedb.org/about/logos-attribution), less prominent than the app's own mark. For
  now the notice is in Launcher settings' About pane (only in builds with a token) and in the README; the logo is not
  in the launcher yet (the design has a placeholder for it). Wanted: TMDB's logo in About.
- **The token is in the APK:** `BuildConfig.TMDB_TOKEN` can be read out of the APK by anyone who has it. A read
  access token can only read TMDB's public data, but it is the account's: keep a token build to yourself, and
  revoke the token on themoviedb.org if an APK leaks.
- **Rate limits:** TMDB has no published quota but caps around 40 requests a second and answers 429 past it. A first
  start fetches about 45 details pages (12 hours' cache), a search one page a pause in typing. A 429 or a failure
  is not retried for a minute (`TmdbCatalog`).
- **Search pages:** TMDB answers a search 20 results a page. When focus reaches the last row of Search's full results,
  the next page is asked for and merged in (`SearchPages`, `Catalog.Found.merge`), up to five pages (100 results
  before the art and photo filter), each cached an hour like the first. Checked with unit tests; a run on a signed-in
  TV is wanted (the emulator has no TMDB sign-in).
- **Still to do:** TMDB's language following the TV's; the full
  filmography and a person page design (see From M5).

### Streaming availability and prices
- **V1:** mock: which service has a title, "Included", and the rent and buy prices. Still so without a TMDB token
  (the mock catalogue).
- **V2 Phase 2 (real):** with a TMDB token, a title's offers are where TMDB's watch providers (JustWatch data) say it
  streams, is free, or can be rented or bought in Sweden, asked with the title's details (`append_to_response=
  watch/providers`, kept 12 hours like them). Prices follow the project lead's policy (2026-10-30), never while Home
  is hidden (`PriceBook`, `PriceLookup`, `PriceCachePolicy`):
  - **Sources:** Watchmode first (`watchmode_api_key`; Apple TV, Amazon, Rakuten TV and SF Anytime in kronor, SD to
    4K; Blockbuster, closed, dropped), Movie of the Night only when Watchmode had no price or its budget is spent
    (`movie_of_the_night_api_key`, or `streaming_availability_api_key`; Apple and Amazon only), Apple's keyless
    iTunes lookup (by the iTunes ids Wikidata keeps) when neither priced Apple. Without keys only iTunes is asked.
  - **When:** prices are kept 7 days. Once a TMDB account is signed in, titles on My list and the TMDB lists shown
    as chips are kept fresh in the background (not before, so a fresh TV spends nothing on the mock lists); any
    other title is priced only when its preview opens. Grids and search never ask. Movie of the
    Night's change feed (`ChangeFeed`: rent and buy changes at Apple and Amazon in Sweden), read at most once a day
    while Home shows and at most 3 pages, marks kept titles stale for an early refetch.
  - **Budgets, no paid plans:** every reply's quota headers (Watchmode `X-Account-Quota`/`-Used`, also from its free
    `/v1/status/` once a day; Movie of the Night `X-Quota-Granted`/`-Used`/`-Reset`) set the count, so a reinstall
    can't overspend. Watchmode stops at 2,300 credits of 2,500 (a lookup costs 2; its month runs from first use, as
    its docs don't say when it renews), Movie of the Night at 900 requests of 1,000 (resets when its server says,
    the 1st). What is left is spread over the days to the reset; the background may use half a day's share. With a
    budget spent, only Apple is priced and the other stores show "Rent" or "Buy" without an amount.
  - **Shown:** the cheapest price per store, type and quality (`OfferMerge`): "Rent 39 kr", "Rent from 49 kr". A
    store with no known price still shows, without an amount, and its dialog only opens the app. Where to watch
    credits its sources ("Availability: JustWatch · Prices: Watchmode"); Launcher settings has the full notices.
    Answers are kept on disk and used stale offline. Seen on the emulator with real keys (2026-10-30).
  - **Design 07-prices A (cards):** Where to watch shows one card per service: included and free first, then the
    stores, rentals with a price cheapest first, purchases with a price, stores with no price last; with two or more
    priced stores the first is tagged a green Cheapest with its deciding price green; a tie on that price goes to a
    store whose app is installed, else to TMDB's order (2026-10-31). A line per quality ("Rent 49 kr
    · Buy 149 kr   HD", a second for 4K when it costs more, "HD · 4K" when it doesn't, "Per season" for a show); an
    unpriced store reads "Rent · Buy", "Price shown in the app". Offline the credit adds "Saved today 19:52, prices
    may be out of date", from when the prices were fetched (the disk copy's time). The rent/buy dialog has the HD /
    4K switch (OK selects), the 4K note, two price buttons ("Price in the app" without a price), Cancel and the
    cheaper store. Small cards (Search, More like this, the person page) say "Rent from 39 kr · 4 stores" and the
    like from the prices already known; a card never asks for one. Logic in `PriceCards` (PriceCardsTest).
  - **Store ticks (mock):** the spec leaves an unticked store's offers out everywhere, but Launcher settings can't
    tick stores yet (the stores pane is being built), so `Data.stores()` returns null and every store counts.
    `PriceCards.ticked` already does the leaving out for Where to watch, the dialog, the card lines, the Watch button
    and library cards; once the pane stores the ticks, `Data.stores()` returns them, and the Overview's "Rent from …
    elsewhere" and Pick for me should go through `PriceCards.ticked` too.
  - **SD left unsaid:** a store whose SD costs the same as its HD (Watchmode lists both for most titles) shows no SD
    line or SD choice in the dialog, so a card fits "HD · 4K" beside two prices. A cheaper SD keeps its own line.
  - **Not priced on cards:** titles from recommendations and searches have no availability until their details are
    loaded, so More like this and search hits that were never opened show no third line, as before.
  - **Cards name only the household's own (F1, the project lead, 2026-11-01):** a card's provider and line count
    a ticked subscription, a free service whose app is installed or that is ticked, and the ticked stores' rent and
    buy (`PriceCards.isYours`); with none of those the card shows no availability. The title page (Watch, Where to
    watch) still shows every offer. "Included only" (Movies and TV Shows, Pick for me, Search) follows the same rule
    for free services (`Offers.isOwnIncluded`): a title free only on an app that isn't installed or ticked doesn't
    pass it.
- **Still missing:** Viaplay's store has no price from any source. Shows are rarely priced (Apple's TV search finds
  no Swedish seasons). Prices are a week old at most, not live.
- **Findings (2026-10-30):** in Sweden TMDB lists rent and buy at Apple TV, Viaplay, Rakuten TV, SF Anytime and
  Amazon Video, and still Blockbuster, which closed and is dropped; no Google Play. TMDB's add-on channels ("HBO Max
  Amazon Channel") are dropped. The first start with keys prices the household's lists at once: about 16 Watchmode
  lookups (32 credits) for the emulator's lists, within the background's daily share.
### Two regions
- **V1:** SE only.
- **Wanted:** search SE and US together, since the household also watches through a VPN, and label each result
  with its region.
- **Family install step 2:** one region at a time, SE or US (`Region`, `RegionChoice`): the TV's own choice (saved
  in the launcher's settings; First run's step and its Launcher settings row are F12), else `region` in config.xml
  or a pushed config.json, else SE. It picks TMDB's Discover region and availability, the Streaming services and
  Stores panes' lists (`ServicePicks`; the US adds Peacock, Paramount+, Tubi, The Roku Channel and Fandango at
  Home), the price sources' country (Watchmode `regions`, Movie of the Night and iTunes `country`) and so the
  currency. Store ticks are kept per region; the services ticked are the household's in either. A change shows once
  Home is rebuilt.

### TMDB sign-in and watchlist sync
- **V1:** My list, Seen, Favorites, recent searches and recently viewed are stored on the TV only.
- **V2 Phase 3:** signing in is optional, from Launcher settings' Account section, on a temporary page over
  Home (`AccountPage`, "Temporary design") until the Figma design is chosen. TMDB's v4 flow: a request token shown as a
  QR code for `themoviedb.org/auth/access` (version 14, about 460 px), polled every 5 s while the page is on screen
  and for at most 15 minutes, then the access token turned into a v3 session (`/3/authentication/session/convert/4`),
  because the watchlist and favourite writes exist only in v3 while v4 lists hold movies and shows and can be
  private. My list follows the watchlist, Favorites the favourites and Seen a private TMDB list named "Seen", which
  the launcher makes when the account has none (`tmdb_seen_list`, `create_seen_list`; docs/rework.md, Accounts,
  2026-10-30), all both ways (`Accounts`, `AccountSync`): a full sync after signing in and when Home shows if the
  last one is 10 minutes old, changes on the TV sent 5 s after the last one, nothing while Home is hidden. Changes
  made offline or while signed out wait in a queue (`SyncLedger`, saved with the lists so a lost process keeps them)
  and are retried; each title's last change wins (`ListMerge`). A sign-in to a new account takes the account's lists
  and adds nothing from the TV; the same account again carries on with its queue. Signing out keeps the lists on the
  TV. The tokens are in the private SharedPreferences file `tmdb_account`, never logged; account calls skip the disk
  cache (`TmdbClient.send`).
- **Import bar (2026-10-31):** the first full read after a sign-in shows "Importing your lists… 3 of 5" with a green
  bar in the Account pane (My list, Favorites, Seen, then each shown list; `AccountSync.withProgress`), as in
  r2-account A. Unit-tested; not yet seen on screen, since it needs a real sign-in.
- **Still to come:** the sign-in and first-run designs, and whether a first run asks to sign in.
- **Not seen yet:** a real approval (the project lead approves one on the TV), and so the session, the account's
  name, making the Seen list and the sync against a real account. Checked on the emulator: the code and its QR
  (decoded), TMDB's "not approved yet" answer (422, code 41), polling stopping while the page is hidden, offline,
  expiry and New code.

### Shown TMDB lists
- **V1:** mock Household and Friends placeholders in the list chips and the grid.
- **V2 Phase 1:** still the mock lists, on the TMDB ids of their titles (`MockIds`, `MockOverlay`), with the mock's
  next episode for the "Up next" badge where the real show has that episode.
- **V2 Phase 3, first:** Household and Friends chosen on the TV from the account's TMDB lists (one list per role).
- **V2 Phase 3, by name** (docs/rework.md, Lists by name, 2026-10-31): the lists are the account's own. Once signed
  in, the Account pane's Choose lists has one row per TMDB list with a Show / Hidden switch; any number can show,
  saved on the TV (`ListChoices`), and the first six shown lists are chips in Movies, TV Shows and Pick for me after
  All lists and My list, under their TMDB names and in the account's order (`ShownList`, `ImportedLists`,
  `LibraryLists`; the cap of six is the project lead's, 2026-10-31, `ListMembership.CHIP_CAP`). A list shown past six
  has no chip but stays in All lists, the scope line and prices; Choose lists says "n of 6 chips" and marks it "No
  chip: in All lists", and the Account pane adds "+1 in All lists". The
  chip row scrolls sideways with focus when it doesn't fit, and back on All lists returns to its start; a chosen
  list that is hidden or gone falls back to All lists. The page's context line names the lists ("My list, Family
  picks & Friends' tips"; past four, "My list, Family picks & 4 more"), the scope line counts them, and a card names
  the first list in chip order that holds it. Pick for me offers My list and the chip lists, with My list and the
  first shown list ticked. `tmdb_shown_lists` in `config.xml` (or a pushed `config.json`) overrides the choice: list
  numbers, themoviedb.org/list links or names in the signed-in account, comma- or newline-separated, in its order (a
  public list by number is read without signing in, and its chip takes the list's TMDB name once read). The older
  `tmdb_household_list` and `tmdb_friends_list` still work: with `tmdb_shown_lists` empty, they are the shown lists,
  in that order. A TV that chose Household and Friends starts with exactly those two shown. The TV only reads these
  lists, and keeps the last copy of each (with its name) for offline. A TV that never had an account and sets no
  list keeps the mock Household and Friends; once it has an account, only the shown lists (none: All lists and My
  list); after a sign-out, the lists chosen on the TV are hidden until the next sign-in (`config.xml`'s stay). All
  lists means My list and the shown lists; the mock catalogue's own "My list" no longer counts.
- **Still to come:** whether the Choose lists rows show each list's last update, as the prototype's do.
### Phone notes
- **V2 Phase 3** (docs/rework.md, Phone notes, 2026-10-31; how to set up a phone in
  [docs/phone-notes.md](docs/phone-notes.md)): each person keeps a watch list in a note on their iPhone (Apple Notes
  or Google Keep) and shares it to a "Send to TV" Shortcut, which posts the text and their list's name to
  `http://<tv>:8765/notes` with the pairing code. Launcher settings' **Phone notes** section, after Account, has the On /
  Off switch (F34, 2026-11-02: it is the Phone notes feature's own switch; `feature_notes` in `config.xml` or
  `"features": {"notes": …}` in a pushed `config.json` is the default, off; the household's choice is saved on the TV
  and wins), the TV's Wi-Fi address (Ethernet when there is no Wi-Fi; "Not on Wi-Fi" without either),
  the pairing code (8 characters without 0/O or 1/I/L, made when it is first turned on), a QR beside them (the project
  lead, 2026-10-31) that opens `http://<tv>:8765/setup#<code>` on the iPhone (the code stays in the link's fragment,
  never sent to the TV): a fixed page with **Get the shortcut** (the household's shared iCloud link, `note_shortcut_url`
  in `launcher/tv.properties`, a pushed `config.json` or `config.xml`; only `https://www.icloud.com/shortcuts/` links) and **Pair this phone**
  (`shortcuts://run-shortcut?name=Send%20to%20TV` with the address and `Authorization` value, which the shortcut keeps
  in iCloud Drive › Shortcuts › `Send to TV.txt`, asking for the list name; after New code a phone just pairs again;
  the project lead, 2026-11-01), and the address and value to copy into a simple hand-built one. New code (asks first),
  How to set up a phone, and each list's last send with the lines not matched exactly, each a button that opens a picker
  (`NotePicker`: TMDB's best three answers as posters with title and year, Search again to fix the text with Search's
  keyboard and look it up again (F9, `KeyPad`, shared with Weather), and None of these, which is remembered; no
  Figma design yet; the project lead, 2026-11-01); the row reads Off, On or, in amber,
  "3 not matched". While on, `NoteInbox` listens on port 8765 for `POST /notes` (and `GET /setup`, the QR's page)
  from private addresses only, one
  request at a time, with a five-minute lockout after five wrong codes; it is opened and closed off the main thread by
  one owner for the process (`Notes`), so a rebuilt Home never opens it twice, and a port another app holds is said in
  the pane. A note is answered at once ("Sent: 12 lines, 3 new", or signed out "The TV will add these once it's
  signed in to TMDB."), kept as the latest one of its list until it is on the list (`NoteBook`, saved on the TV), and
  imported on the account's worker so it never races a sync (`Accounts.runNotes`, `NoteImport`): the account's list
  with that name (matched as `ListRef` matches names), else a new private one; each new line looked up once
  (`NoteLines`, `NoteMatch`; its answer kept per list and account) and added; a ticked line goes to Seen through the
  household (so it syncs like a change on the TV) and comes off the list. Adding only: a line deleted on the phone
  takes nothing off, and a title taken off on the TV isn't put back. The list then shows as a chip and is read again.
  Signed out, offline or refused by TMDB, the note waits and is tried after the next send or full sync. The import
  pill (`ImportPill`) sits at the bottom centre of every launcher screen, and on Launcher settings at the top centre
  beside the title (its pane runs to the bottom edge): "Importing Rebecca · 6 of 12" with a thin bar, counting every
  line of the note from the start, then for four seconds "Partner · 8 added" or, in amber, "Rebecca · 9 added · 3 not matched", "… waiting for the
  internet", "… couldn't reach TMDB" or "… waiting for TMDB sign-in"; never focusable, and still with Motion Reduced or
  Off.
- **Checked on the TV (2026-11-01):** a note sent from this PC and from two iPhones' Shortcuts (Apple Notes) made and
  filled the lists on the project lead's account, a ticked line went to Seen and came off the list, and `NoteLines`
  was fitted to the text Apple Notes and Google Keep really hand the share sheet; then the picker and the pane's scroll on
  the TV, and Get the shortcut, Pair this phone and a send on the project lead's iPhone from a fresh start. **Android
  (F7, 2026-11-01):** the setup page shows an Android phone the settings to copy into HTTP Shortcuts (the note as
  plain text, the list in `?list=`, which the inbox already took), and docs/phone-notes.md has the steps. **Not
  verified yet:** a second iPhone's pairing, an Android phone, and the TV's
  address changing (the Shortcut keeps the one it was given: docs/phone-notes.md asks for a router reservation, and the
  pane reads the address afresh each time it opens). Checked on the emulator: the pane, the inbox opening and closing
  with the switch, New code, a wrong code refused, the signed-out answer, a note waiting across a restart, and the
  pill's states (drawn with a temporary local hook that was not kept).
- **Still to come:** a Figma design for the pane; whether a send whose list is hidden in Choose lists should show it
  again (it does now).
### Household seed
- **V1:** on the first run, My list, Seen, Favorites, recent searches and recently viewed started from the
  prototype's mock seed (`design_preview/src/core/store.ts`).
- **V2 Phase 3:** a fresh install starts with all of them empty (`HouseholdState.defaults`; docs/rework.md, Accounts,
  2026-10-30); a TV with saved lists keeps them as they are. Search hides "Recently viewed" and "From My list" while
  they are empty. The prototype's seed (`HouseholdState.seed`) is only for the tests that compare with the prototype.
### Trailers
- **V1:** Trailer opens YouTube's TV app searching for "name year trailer"; without YouTube it says so.
- **V2 Phase 1:** with the real catalogue, Trailer opens TMDB's trailer for the title in YouTube's TV app (an official
  YouTube trailer first, else any), and falls back to the search while the details load or when TMDB has none. The
  emulator has no YouTube, so only the "not installed" toast was seen; check the video opens on the TV.
- **Back from a trailer walks YouTube's pages first (F32, open):** YouTube for Android TV (5.30.340, on the TCL,
  2026-11-02) takes the trailer link in its existing activity ("its current task has been brought to the front",
  despite `NEW_TASK | CLEAR_TASK`) and keeps its page history in that activity's saved state, which outlives its
  process. So Back first steps through what the household browsed in YouTube before. Ending YouTube's background
  process before the trailer (`killBackgroundProcesses`) was tried: Back was unchanged and the start took about 0.5 s
  longer, so it was dropped (and Android 14 ignores that call for another app anyway). A task of its own for the
  trailer (`FLAG_ACTIVITY_MULTIPLE_TASK` with `NEW_TASK | CLEAR_TASK`, on the TCL, 2026-11-02) was tried too: the same
  "brought to the front", still one YouTube task, and one Back stayed in YouTube, as with a `singleTask` or
  `singleInstance` activity. The `vnd.youtube:<id>` link's start was also brought to the front. No flag the launcher
  can set changes it: it stays open as YouTube's own behaviour.

### Title deep links
- **V1:** "Watch on …", episode rows and Where to watch open the service's app on its home screen. Still so with the
  mock catalogue.
- **V2 Phase 2 (real):** with a TMDB token, Watch and Where to watch open the title itself where Wikidata knows the
  service's id for it: `WikidataIds` asks query.wikidata.org once per title (SPARQL by the TMDB id, `P4947` movies,
  `P4983` shows, kept a month) for Netflix (`P1874`), Disney+ (`P13902`, `P7595`/`P7596`), Apple TV
  (`P9586`/`P9751`), HBO Max (`P8298`), Prime Video (`P14462`, `P14440`, `P8055`) and SVT Play (`P6817`), and
  `DeepLinks` builds the links (formats after cleanyourtv-ui's `DeepLinks.java`). The link goes as an ACTION_VIEW
  intent limited to the service's package (Netflix with `source=30`), in a cleared task of its own
  (`Services.LINK_FLAGS`: without it an app that is already running comes to the front and drops the link); when no
  installed app takes it, or the app refuses it, the app opens on its home screen as before.
- **On the TV (TCL, 2026-11-02):** Apple TV, Netflix and Prime Video (after its profile picker) open the title.
  Disney+ was signed out on that TV. HBO Max's app answers Wikidata's old `series/urn:hbo` ids with "Content Not
  Available", so only its current `show|movie|mini-series/<uuid>` ids are used (about 110 of 670 on Wikidata,
  2026-11-02); with only an old id the app opens on its home screen. SVT Play's app takes no `svtplay.se` web link, so
  it opens on its home screen; the recheck reads its declared link filters. Recheck pending for the warm case
  (the app already running) and HBO Max's current ids.
- **Mock or missing:** episodes still open the show: Wikidata has no service id for any episode (0 of 210 across five
  big shows, 2026-11-01); Netflix's `/watch/<show id>` (which may resume the next episode) is in the recheck. TV4 Play, Viaplay, SkyShowtime and the
  Swedish stores have no Wikidata property, so they open on their home screen.
- **US (Family install step 2):** Netflix, Disney+, Apple TV, HBO Max and Prime Video open the title in the US too
  (the same Wikidata ids). Hulu (`P6466`/`P6467`; on Wikidata items with a TMDB id, 2026-11-02: about 1,640 movies
  and 1,560 series),
  Peacock (`P11815`, about 40), Paramount+ (`P13147`, about 130, nearly all movies), Tubi (`P7760`/`P7761`, about
  3,100 and 380) and Fandango at Home (`P7334`, about 4,500) are in `WikidataIds` and `DeepLinks` now, as each
  service's web address for the title (Wikidata's formatter URL; Paramount+'s movies under `/movies/video/`; Peacock's
  values with or without a leading `stream-`). Checked on paper only: peacocktv.com and athome.fandango.com verify
  their TV apps for those links (`assetlinks.json`); hulu.com and tubitv.com publish none, and paramountplus.com
  wouldn't serve it. **Wanted:** a TV with each app checks that it opens the title; an app that refuses the link opens
  on its home screen, as before. The Roku Channel has only a channel id, so it opens on its home screen.
### Choosing the household's services
- **V1:** subscribed services come from the installed apps, with an override: `subscribed_services` in
  `config.xml` or a pushed `config.json`, a list of lowercase ids (`["netflix", "prime"]`; an unknown id is
  ignored, so `["none"]` means none). Left empty, every subscription service whose TV app is installed counts,
  and YouTube; the Apple TV app counts as Apple TV+, never as the Apple TV store. Read when Home starts, so an
  app installed later counts after Home restarts.
- **V2 Phase 2:** the same rule over Sweden's services too (`WatchProviders`: Viaplay, SkyShowtime, SVT Play, TV4
  Play, Tele2 Play, Telia Play, Hayu, MUBI and others, with their TV apps); a service that also sells (Viaplay)
  counts as a subscription, a store alone (Amazon Video, SF Anytime, Rakuten TV) never. TV4 Play counts as included
  when installed, even on its free tier. With the real catalogue the names follow current branding: "HBO Max", and
  "Apple TV" for the subscription with the "Apple TV Store" beside it.
- **Wanted:** a screen in Launcher settings to tick the services the household pays for.
- **M9 (track D2):** the Streaming services pane of the new Launcher settings page (`ServicesSection`) ticks them, free
  services included; the ticks are saved on the TV (`ServiceChoices`, `ticked_services` in `launcher_settings`), win
  over `subscribed_services` and the installed apps, and change Included at once for screens built after. The Stores
  pane (`StoresSection`, `ticked_stores`) leaves the unticked stores' rent and buy offers out of the real catalogue
  (`TmdbCatalog.setPriceStores`); the mock catalogue keeps all of them. Neither is cleared by Follow theme or a theme
  push.

### Weather location on the TV
- **V2 (Phase 4):** `latitude` and `longitude` still come from the gitignored build properties file,
  `launcher/tv.properties` (template: `launcher/tv.properties.example`, New York), as the default place. Launcher
  settings' Weather pane (M9) shows the place with the weather there now, or the last answer and when it came while
  offline. **Change city** searches Open-Meteo's geocoding as the typing pauses (no key; only the typed name is
  sent), in the TV's language and in English and Swedish, so "Göteborg" finds the city on an English TV too; the
  answers are merged (one row per place, names typed exactly first, accents ignored) and countries are named in the
  TV's language. It works on the pane's own 6 × 6 keyboard or a real one, and keeps the chosen place's name,
  region, country and position on the TV; with nothing typed it lists the place now showing, the default and up to
  three places picked before, which work offline. "Use default" goes back to `tv.properties`, whose optional
  `weather_city` names that place on the pane and its row ("Default" without it). **Temperature** is °C, °F or the
  TV default: `temperature_unit` in `tv.properties` when it is set, else the TV's region (Android 14's regional
  preference first, then the locale's country). Home fetches the weather for the new place or
  unit when it is rebuilt; the cached answer is kept per request, so the old place's weather never shows for the
  new one. The logic is in `WeatherPlace`, `WeatherSettings` and `WeatherPrefs`, unit-tested.
- **Findings:** Open-Meteo matches names in the language it is asked in. In English "Göteborg" finds only
  Göteborgnuten (Svalbard) and a park; the city is "Gothenburg". In Swedish both names find it.

### Wi-Fi networks
- **V2 (Phase 4):** the dropdown lists the TV's other saved networks under the header (a lock for a secured one,
  "Saved" on the right), and OK switches to one with a "Switching to …" toast, as the prototype's `wifiMenu` does.
  Other networks and Wi-Fi settings stay the last rows. On Android 10 and later, with Wi-Fi off or on Ethernet,
  there is no list and the dropdown is as before; a switch the TV refuses opens Wi-Fi settings.
  New permission: `CHANGE_WIFI_STATE` (normal, granted at install).
- **Findings (Android 9 emulator, targetSdk 34):** `getConfiguredNetworks()` lists the networks saved in the TV's
  settings with `ACCESS_WIFI_STATE` alone; without location access the connected network's name is hidden
  (`<unknown ssid>`) but its id is not, so the header names it from the saved list. `enableNetwork(id, true)` needs
  `CHANGE_WIFI_STATE` (without it: `SecurityException`); with it, Android logs "does not have permission to update
  configuration" and then "Allowing uid … with insufficient permissions to connect", and connects. The saved
  network itself is not changed. Scan results need location access and location on (`getScanResults()` empty and
  `startScan()` false without it). From Android 10 both calls are refused for apps targeting it.
- **Waits for design:** joining a new network (choosing from a scan and typing a password) from the dropdown;
  Other networks opens Wi-Fi settings for that. A failed switch is not reported back: Android falls back to a
  network it can reach, and the header shows it the next time the dropdown opens.

## Left for V2

From the old launcher; not in V1 by decision.

- Themes and wallpapers pushed as `config.json`. (M9 brought themes back as an on-TV choice in Launcher settings › Theme;
  see [Themes](#themes).)
- The clock screensaver. Until it returns, a remote key mapped to `screensaver` (`app_keys`) starts the TV's own
  screensaver only where the firmware has the system's "start now" shortcut; the launcher's clock fallback went
  with the old Home in M1.
- Module cards: the add-ons in `addons/` run as their own apps; see [Add-on cards on Home](#add-on-cards-on-home).
- The Watch Next row.
- Festivals, the sky and the lunar calendars.
- The old Home's on-screen extras: the idle clock (`home_idle_clock_minutes`), the stats line (`show_stats`), the
  VPN badge on the network pill (`vpn_app` now shows its app and state in the Wi-Fi dropdown instead), the app
  library dialog, reorder mode and dock editing. Their `config.xml` keys went in M1b; the theme keys are described,
  frozen, in `themes/README.md`.

### Options to bring back

F36 (the project lead, 2026-11-02, later). R1 removed `docs/switches.md`, the page of screenshots for each key a theme
or `config.json` can set; upstream keeps it, with the screenshots, at
[Gathin23/cleanyourtv › docs/switches.md](https://github.com/Gathin23/cleanyourtv/blob/eb0131a3/docs/switches.md). Three
options from it to bring back properly:

- **`consume_volume_keys`** and **`gamepad_passthrough`**: still in the launcher (`config.xml`, and a pushed
  `config.json`; both need `key_filter` and are off by default), described in `themes/README.md`'s Keys table, but
  with no switch in Launcher settings and no screenshot here any more.
- **`screensaver`**: the clock screensaver went with the old Home in M1 (above). As an `app_keys` action,
  `screensaver` starts the TV's own screensaver only where the firmware has the system's "start now" shortcut.

### Add-on cards on Home

The project lead, 2026-11-02, later. The 19 add-ons in `addons/` work with this launcher as ordinary apps (each has
its own tile; all build and pass their tests), and their manifests already declare the module card under this
launcher's names (`com.hometv.launcher.card`, `com.hometv.launcher.category.DOCK_CARD`; spec in
[docs/specs/card.md](docs/specs/card.md)). What's missing is a host that shows the cards. A minimal one, about 3 to 4
builder-days:

- **Design first** (design_preview, then the project lead): a card replaces its app's tile in Home's app row (upstream's way,
  no new row), or a Cards row in Home's lower area; the consent prompt (a new card stays hidden until the owner says
  Show); a Launcher settings list to show or hide each card.
- **Launcher:** upstream's `Cards.java` (discovery, XML parsing, validation, signer, decisions) and `CardFeature.java`,
  with their tests, carry over almost as they are (at the fork point `eb0131a3`); new glue for Home, the prompt and
  the list; a Features switch, off by default; a `<queries>` entry for the card category.
- **TV check:** the cost of the card lookup each time Home comes to the front (bench), D-pad, the prompt.

Test it with capacity stats, headphones, AirPlay status and smart home first.

### Headphones and Bluetooth pill

The project lead, 2026-11-02, later; design first. A new pill in Home's top bar that opens a headphones and
Bluetooth menu, built into the launcher: where the sound goes now, the paired headphones with their battery, pairing,
and the quiet guard. The `headphones` add-on does this today as its own app, and its code is the starting point.

## Themes

M9 (2026-10-30): Launcher settings › Theme, from the Figma file "CleanTV · Theme explorations"
(`lwShqRaciMpDUDW5dYljNw`). Classic: Midnight (the default, today's look), Daylight, OLED Black, High Contrast, Cinema
and Landscapes. Seasonal, following the date: Vector art and 8-bit, each in ten periods (`SeasonCalendar`).
A theme changes colours, type, corners, shading, overlays and the backdrop; layout and data stay the same.

- **Daylight cycle (F22, 2026-11-01):** a Seasonal picture follows the part of the day: dawn, day, sunset and night,
  from sunrise and sunset at the Weather place (`DayPhase`, offline). Each period has four drawn pictures
  (`seasonal/vector-6/<period>-<phase>.webp`, 3.9 MB for all 40 in the repo, about 0.5 MB per period downloaded;
  8-bit's 40 scenes are in the APK, 68 KB), with seasonal sunrise and sunset colours and the sun or moon in the same
  place for each phase. The picture swaps once at each edge, crossfading by Motion; nothing is drawn in between, so it
  costs no frames. The colours stay the period's, so Home doesn't rebuild. Theme › Time of day: Follow the sun (the
  default for a Seasonal theme), Day or Night (F29). The 40 pictures are levelled to one brightness curve, so dawn, day
  and sunset blend and the seasons step evenly (F24); 8-bit's generator applies the same curve to its scenes. Halloween's jack-o'-lanterns and its house glow less since F30 (2026-11-02, `vector-6`): the pumpkins' brightest pixels match the windows'. Plan and decisions: Figma page "Calendar · Time of day". Open: a TV
  bench of a phase change; stars or clouds that move belong to Animated details, later.
- **Seasonal picture's cost on the TCL (F33, 2026-11-02):** same build, three cold starts each, Vector art against
  Midnight: Home is drawn about 100 ms later (978 against 876 ms) and the native heap is about 3 MB larger. That is
  the picture itself: one 1920×1080 RGB_565 decode (105–129 ms, once) that overlaps `onCreate`. With the picture
  skipped, Vector art starts like Midnight. F22's phase maths (3–9 ms) and next edge (3–4 ms) run off the main thread,
  and there is no second bitmap and no decode ahead of time. Kept on purpose: Home shows the cached picture straight
  away (the project lead's decision). Decoding it after the first frame would win back about 90 ms, with the picture
  then arriving about 0.15 s later; decoding at background priority won back only about 20 ms.
- **Seasonal · Nature removed (2026-11-01):** the project lead's decision. A TV that had it shows Vector art; its photos
  stay in `seasonal/nature/` for TVs on an older build.

- **Retro themes** (Retro CRT, Synthwave '84, Groovy '70s, 8-bit Arcade): designed in Figma, with their tokens and the
  scanline overlay ready; not in the picker yet.
- **Animated details** (Launcher settings › Performance, formerly the Theme pane's Moving details; F25, the project
  lead's decisions of 2026-11-01): off by default on every TV. When on, snow or rain falls over a Seasonal picture by
  the real weather at the Weather place, or by the season offline (snow in Snow and Christmas, rain in Spring rain),
  only while Home is idle: it starts 3 s after the last key and stops on the next. Leaves fall in Autumn and Halloween
  while it is dry: rain or snow at the place wins, and offline they fall (F31). Vector art at 12 fps, 8-bit at
  10 fps on the scene's pixel grid. Off with Motion Reduced or Off, or a plain backdrop. For tests and the TV bench,
  `adb shell am start -a com.hometv.launcher.action.FALL_PREVIEW --es fall snow -n com.hometv.launcher/.MainActivity` forces snow,
  rain or leaves (`off` ends it) until the launcher restarts; public release builds ignore it (`PUBLIC_RELEASE`). TCL bench
  (2026-11-02, snow on Vector art, Motion Full, 30 s): frame time p50 and p90 7 ms with no janky frames, 16.8 fps
  before the vsync-anchored timing, the launcher at 11.8 % of one core while it falls against 0.1 % still; that cost
  follows the frame rate (the whole window redraws on that GPU), not the flake count: about 0.7 % per fps. At the
  12 fps it ships with, that predicts about 8.5 %. Open: a re-bench at 12 fps
  (`tools/bench.py --idle-animation 30`); petals and bats, and wind or lightning; the backdrop window's
  `buffer_age` on the TCL.
- **Southern hemisphere:** the Seasonal periods follow the northern calendar everywhere.
- **Fonts:** downloaded through Google Play services' font provider and kept on the TV; a family the provider can't
  serve comes straight from Google Fonts' CSS API over HTTPS (the TrueType file, at most 2 MB; still none in the APK).
  Roboto until they arrive. On the API 28 emulator's Play services, Outfit, Figtree, Manrope, Atkinson Hyperlegible
  Next, Baloo 2 and Pixelify Sans come that second way. On the TCL (main 24e8f119, 2026-10-31) the provider served
  Cinema's DM Serif Display and Daylight's Figtree.
- **Backdrops:** the photos and Vector art live in `launcher/backdrops/` and are downloaded once from
  `theme_assets_url` (jsDelivr on this repository's `main`). The 8-bit scenes are in the APK. On the TCL, Cinema's,
  Landscapes' and Vector art's downloaded from `main`. Vector art's sky showed gradient banding there (the launcher
  decodes backdrops as RGB_565); since 2026-11-01 the pictures are dithered to that grid (`seasonal/vector-2/`, and
  the daylight cycle's `seasonal/vector-5/` and `vector-6/`).
- **Not shipped:** the Vector scenes alternative to Vector art, and the old Seasonal & festive themes (Figma pages kept).
- **Card shadows:** every theme but Midnight draws a resting shadow under each artwork card. Measured on the TCL with
  `tools/bench.py` (main 24e8f119, 36 keys × 3, 2026-10-31):

  | Theme | Response p50 | Movement p50 | Jank | Cold start p50 | PSS |
  |---|---|---|---|---|---|
  | Midnight | 19 ms | 195 ms | 46.9 % | 681 ms | 27 MB |
  | Daylight | 24 ms | 212 ms | 71.2 % | 701 ms | 51 MB |
  | Cinema | 23 ms | 236 ms | 84.8 % | 726 ms | 31 MB |

  All three have a photo backdrop. Daylight adds the resting shadows, and Cinema the shadows plus a vignette and a
  glow. Each row is one run, in which one of the three series was lost the same way (72 of 108 keys measured). Every
  theme, Classic and Seasonal, renders on the TCL.

  **Shadows alone** (the project lead's option (a), 2026-11-01): Daylight with the resting shadows on and off, the same
  build otherwise (a bench-only switch, not in the launcher), same 36 keys × 3:

  | Daylight | Runs | Jank | Movement p50 | Cold start p50 | PSS |
  |---|---|---|---|---|---|
  | Shadows on | 2 | 71.2 %, 70.6 % | 212, 213 ms | 701, 709 ms | 51, 45 MB |
  | Shadows off | 2 | 53.1 %, 51.6 % | 196, 198 ms | 682, 732 ms | 29, 51 MB |

  So the resting shadows cost about 19 points of jank and 15 ms of movement on the TCL; PSS and cold start move
  between runs either way. A third shadows-on run measured 102 keys instead of 72 (48.7 %), a different set of frames,
  and is left out. The TCL's Mali-470 has no `buffer_age`, so every frame redraws the whole screen, and each resting
  shadow is fill on every frame. A pre-drawn nine-patch per shape, the same look in one draw (#39, closed), benched the
  same (70.0 %, 71.3 %): the cost is the shadow's pixels, not how they are drawn.

  **Decided** (the project lead, 2026-11-01): a Launcher settings › Performance page. Card shadows On, Focused card
  only or Off, the focus glow, the backdrop picture (Photo or the theme's plain sky) and Animated details, beside Motion.
  A Light mode button at its top sets every choice to its lightest (Motion Off included).

  **With the low-RAM start**, on the TCL (#41's build, `tools/bench.py` 36 keys × 3, 2026-11-01):

  | | Jank | Movement p50 | Response p50 | Cold start p50 | PSS |
  |---|---|---|---|---|---|
  | Midnight, low-RAM start | 1.3 % | 19 ms | 19 ms | 798 ms | 30 MB |
  | Daylight, low-RAM start | 1.4 % | 19 ms | 19 ms | 803 ms | 37 MB |
  | Daylight, everything on (main, the runs above) | 71.2 %, 70.6 % | 212, 213 ms | 24 ms | 701, 709 ms | 51, 45 MB |

  Reduced motion makes moves instant, so nearly no frame is late; the focus glow and the photo, which the low-RAM
  start keeps, cost nothing visible next to that. Cold start was about 100 ms slower in these runs, not yet explained
  (Reduced motion shouldn't add to it; worth a trace).
  A low-RAM TV (`ActivityManager.isLowRamDevice()`, which the TCL reports) or one whose config.xml or config.json
  sets `light_defaults` to on starts at the light end: no card shadows, Motion Reduced, no animated details; the focus
  glow (one tile) and the picture stay. The household's choices win; Follow theme and a theme push drop them.
- **Focus glow:** checked on the TCL: a theme's glow shows in full on Home's Inputs and Shortcuts tiles, edges
  included.

## From M1

- **Launcher settings page:** M9 replaced the old `LauncherSettingsActivity` with the split page of V2 design
  05-settings A (`LauncherSettingsPage`, its sections in `SettingsSections`, shared controls in `SettingsKit`).
  Every section has its pane: Account, Streaming services, Stores for rent and buy, Weather, Home row apps, Theme,
  Performance (Motion: Full, Reduced or Off on `MotionChoice`; card shadows, focus glow, backdrop picture, animated
  details), Open on start, Default Home, TV settings and About. Not built:
  About's Open-source licences and Clear cache. Follow theme is a button in About: it drops Performance's choices, Open on start
  and the old layout choices so `config.xml` or a pushed `config.json` decides again; the Theme, weather, account and
  the service and store ticks stay (the project lead, 2026-10-31). A `config.json` push still clears the same.
  Home's row holds seven apps, as the spec: Home row apps and All Apps' Add to Home say so when it is full.
- **Network icon state:** the nav shows the prototype's Wi-Fi arcs, on Ethernet too (the project lead, 2026-10-31), or the arcs crossed out when offline, which since 06-states A
  includes a network with no internet behind it. The old pill could also
  tint by state (`wifi_status_color`, `wifi_status_warnings`); not designed for the new nav.
- **Last opened:** `LastOpened` could note when Home or All Apps last opened each app (on the TV only, no permission).
  Recording is off since F15 (the project lead, 2026-11-01): nothing read it since the old Home's "Last opened" line
  went in M1, and it wrote a file each time an app started. Wanted: a use for it, for example ordering All Apps or the
  Home row by recent use, once that is designed; then call `LastOpened.record` where Home and All Apps open an app.
- **Cold start and jank:** V1 measured 1.9–2.1 s (TotalTime) against M1's 2.8 s and the old launcher's 2.7 s, but 47 %
  of frames were janky while pressing keys on Home ([docs/rework.md](docs/rework.md#results-2026-10-30-main-at-0212b6a4)).
  Phase 0 looks for the cause; measure again on the TV with `tools/bench.py` ([perf-ab](docs/workflows/perf-ab.md)).

## From M2

- **All Apps:** V1 is a plain working grid styled from the tokens (Back, title and count, six columns of banner
  tiles sorted by name; OK opens, hold-OK adds to or removes from Home, moves into or out of TV Utilities, App info),
  with the TV Utilities folder last (F35). Wanted: a proper design, which may add search, hiding apps and sorting
  (the prototype's stub said "with hide and reorder").
- **Weather page:** OK on the weather strip opens a "Not designed yet" stub. Wanted: the hourly and 7-day forecast.
- **Weather icons:** the prototype has three (sun, moon, cloud), so rain, snow, fog and storms all show the cloud;
  the words under the temperature say which. Wanted: icons for those, in Figma first.
- **Empty app row:** with no favourites installed (or all removed) the row shows one "Add apps" tile that opens All
  Apps. Not designed.
- **Inputs on Home:** the tiles show the input's name only; the dropdown's "Connected" and "No device" states are
  not on them (the design has no room for them). On the emulator only a Live TV row could be checked (a pushed
  `live_tv_package`); the HDMI and AV rows wait for the TV.
- **Tile menu:** Move, Remove from Home and App info, in the dropdowns' panel with no icons, under the tile or over
  it where there is no room below. Not designed (the spec lists hold-OK menus as open).

## From M4

- **Scope line:** "3 imported watchlists" counted the three mock lists, and "All N services" the household's
  subscribed services (`subscribed_services` or the installed apps). Done in V2: it counts My list and the shown
  TMDB lists (see Shown TMDB lists).
- **Empty list note:** says to "import a watchlist in Settings", which doesn't exist yet (see First run and setup
  under Not designed yet).
- **Filters, the chosen list and the sort** (F2, F4, the project lead 2026-11-01) start cleared (no filters, All
  lists, A–Z) when the TV is turned on: the launcher's process starting, which a boot does, or the screen coming back
  from standby, which on most TVs keeps the process. Each page keeps its own while it is left (another tab, an app)
  and clears them after more than an hour away (`LibraryRecall`). Figma's frame shows "Included only · Review 7.0+",
  the default before F2. Not stored: a launcher process the TV stops starts again cleared, as a boot does.
- **Sort's "Recently added to my lists"**: no list says when a title was added to it. My list keeps the order titles
  were added (a sync keeps the TMDB watchlist's, oldest first) and a TMDB list TMDB's own order, so a list's last
  title counts as its newest; across lists (All lists) every list's last title comes first, in chip order, then
  every list's second-to-last, and so on. The mock Household and Friends lists have no order (the catalogue's).
  Wanted: a date added per item, if the TMDB API gives one.
- **Genre options** are TMDB's movie or TV genres in the launcher's words (`GenreMap`: "Sci-fi" for Science
  Fiction, "Sci-Fi & Fantasy" as both); TV Shows still leave out Food, Musical, Western and Horror, as the
  prototype does. With the mock catalogue they are its genres, as in V1.
- **Guide line:** with the real catalogue it reads "Scores from TMDB · Where to watch from JustWatch" (V2 Phase 2;
  Pick for me's results likewise); the mock catalogue keeps "Illustrative services, scores & availability".
- **Long lists:** the grid builds a card for every title at once (16 at most in the lists today). A real list of
  hundreds needs a recycled grid or paging, with the artwork decoded as cards come into view. With the real
  catalogue the grid is built again each time a title in it fills in, a few times while the lists load.

## From M5

- **Watch and episodes open the service's app, not the title:** Watch, an included Where to watch card, an episode
  and Rent or Buy in the dialog all open the service's app on its home screen (`Router.watch`); see Title deep
  links. An episode card opens the show's best offer, not that episode.
- **Rent/buy dialog:** Rent and Buy open the store's app; nothing is paid or recorded in the launcher, and the
  dialog doesn't know whether the rental happened.
- **Person page:** the prototype's stub ("Person · not designed yet"), now with the TMDB photo over the initials and
  up to 30 titles they are best known for (TMDB's combined credits, most voted first, without talk shows, news,
  reality TV and appearances as themselves). Wanted: a design, and the full filmography.
- **Cast portraits:** TMDB's photos over the initials (V2 Phase 1); the prototype shows initials only.
- **Watched episodes:** worked out from the mock next episode (`Title.nextSeason`/`nextEpisode`); nothing records
  what was watched. Mark seen marks the whole title only.
- **More like this:** TMDB's recommendations for the title, else its similar titles, eight at most (V2 Phase 1); the
  prototype's rule (shared genres, kind, score) only with the mock catalogue.

## From M6

- **Search source:** TMDB's search merged with the local matching since V2 Phase 1 (see Real catalogue); with the mock
  catalogue, the local matching alone (`Fuzzy` and `SearchIndex`, ported from the prototype and tested against its
  results).
- **Genre suggestions:** OK searches for the genre's name, as the prototype does, and a genre that matches well
  adds its titles to the results. There is no genre page or genre filter chip.
- **Typing from a keyboard:** a hardware keyboard or a remote with keys types a–z, 0–9, space and `- : ' ’ & . ! ?`
  (the prototype's set) into the bar, on the Search page only. Backspace is Back on every screen, Search included
  (decided 2026-10-29): on the full results it brings the keyboard back with the query kept, with the keyboard
  showing it goes Home, and only the on-screen ⌫ key deletes. Accented letters, the system's on-screen keyboard
  (IME) and voice input are not used.
- **Results preview:** while typing, the preview builds only the first three rows of cards (all it can show); the
  full results build every card. Measured on the emulator: about 15 ms a key press, 30–55 ms to widen "a" (139
  results). Check it on the TV.
- **No-matches screenshot:** `work/proto-shots/search-04-no-matches.png` shows Home, so that state was checked
  against `search.ts` rather than a picture.

## From M7

- **Pick for me's fields:** the six fields and their options are the prototype's fixed lists (three lengths, four
  genre pairs, three scores, three ratings), not built from the catalogue's genres or the household's habits.
  Wanted: options that follow the real catalogue once it lands, and a design for picking any genre.
- **Episode length:** for TV Shows the length field reads the episodes' average runtime (the prototype passes every
  show), and with the real catalogue TMDB's typical episode length while the episodes aren't loaded (they load a
  season at a time, in the title preview). A title whose details haven't arrived yet is not picked.
- **Random, not ranked:** picks are four random matches, as in the prototype; nothing weighs score, recency or what
  the household watches. The choices are not remembered between visits either: each Pick for me starts from the
  prototype's defaults.
- **Open in the service:** the results bar's main button opens an included pick's app on its home screen (see
  Title deep links). A rental or purchase opens the title preview instead, so its rent and buy dialog asks before
  leaving the launcher; the prototype's button opened the preview for every pick.

## From V1 follow-ups

- **Back from an app (F26):** the launcher stays as it was while its process lives. When Android ends the process
  while the other app plays (usual on a 1 GB TV), the activity comes back from its saved state with the nav's
  page, its focused item, Search's query, filter and full results, a library page's filters, list chip and sort,
  and the title previews over it. The saved state lasts an hour (the hour a library page keeps its choices) and
  standby clears it. A Home press still starts Home again. Not restored: a person page, Pick for me, and the preview's
  tab and season (it reopens on Overview, or on More like this if it opened there). The place also goes to disk
  (`SavedPlace`), because Home keeps `stateNotNeeded` and Android may bring it back without its saved state; it is
  dropped once the launcher is in front again and only read in the same boot, install and Home task, so after a force
  stop or a crash Home starts on Home. The trailer and title links start in a cleared task of their own; for YouTube that
  is not enough (see Trailers: Back walks YouTube's own pages first). Seen on the emulator with Settings as the app and
  `am kill` as Android.

- **Jank on Home's key moves:** V1 on the real TV measured 47% janky frames (337 of 712), movement p50 197 ms. V2's
  Home on the TCL (2026-11-02, `tools/bench.py --keys 36 --series 3`) is at 1 to 4% (1 to 3 janky frames of about
  75), response p50 20 ms, cold start about 900 ms, return to Home p50 86 to 97 ms (these benches ran before the key
  sequence was corrected, so their Left and Right moves were partly on the row below the apps). Ideas tried on it that
  showed no gain within the noise of one run, kept here in case a `tools/trace.py` run (hwui GPU time) ever shows the
  TV is fill-rate bound (the emulator can't show it: every frame there spends 80–100 ms issuing draw commands, about
  50 ms even with every layer switched off, while the UI thread's animation, layout and recording stay under 1 ms):
  - **Fewer full-screen fills:** no root fill under a settled opaque backdrop, and Home's dim fading without an
    offscreen layer; a card's art painted without the fill under it unless the art has transparent parts. Jank 5 to
    7% against main's 1.4%, response 21 to 23 ms against 20: noise. A 960x540 pre-baked backdrop cost 17 MB for no
    gain.
  - **Home's refresh after the first frame:** reading the apps and inputs back (PackageManager and TvInputManager
    binder calls) after the first frame on return to Home, not before it. Return to Home p50 79 ms against 97 ms, but
    main alone read 86 to 97 ms across runs: needs two runs or more of each before it counts.
- **Memory in benches:** one PSS reading moves by up to 15 MB with garbage collection (25 to 40 MB on one build,
  settling at 23 MB on the emulator), so the single readings of the 2026-11-02 benches above can't be compared.
  `bench.py` now reports the median of three readings, 5 s apart (#96).
- **Accessibility in the prototype:** the launcher now reports the current preview tab and nav section as selected
  and reads Pick for me's fields with their value; the prototype has no ARIA roles or states to match.

## Quick settings

The nav Settings icon's dropdown (design final-quick-settings; `QuickSettings`, `QuickPanel`). The remote's Settings
key still opens the TV maker's panel, or `settings_key_panel`'s old quick-settings panel; nothing about it changed.

- **Sleep timer:** the row was dropped by the project lead's decision, 2026-10-31.
- **Picture and Inputs:** Picture opens `ACTION_DISPLAY_SETTINGS`, as the TCL's own panel and the remote's old
  panel (`Menus.quickSettings`) do (the project lead's decision, 2026-10-31). Inputs has no public settings action and
  opens the TV's settings. Sound and Network & Wi-Fi use `ACTION_SOUND_SETTINGS` and `ACTION_WIFI_SETTINGS`. Each
  falls back to the TV's settings when its page doesn't resolve (through `SystemSettings`, the vendor's settings app
  where it has one). Every row starts the settings app afresh, so it opens on the page asked for and not on the page
  it was left on. Wanted: a check on the project lead's TCL of which activity is its own Inputs page, to add as a
  vendor target.
- **Launcher settings rows:** Launcher settings, TMDB account, Streaming services and Weather open the old
  `LauncherSettingsActivity`, the last three with a `section` extra (`account`, `services`, `weather`) that it
  ignores. `QuickSettings.openLauncherSettings` is the one place to point at the new split page and its panes once
  they land, and the rows' values go through `QuickSettings.Value`, the shape of `SettingsSection.value`, so the
  panes can supply them. The TMDB account row is left out of builds without a TMDB token, as in Launcher settings.
- **Motion:** Full, Reduced and Off, saved as `motion` (`full`, `reduced`, `off`; `MotionChoice`), with the old
  Animations switch kept in step: Off is Animations off, and turning the switch on or off in Launcher settings drops
  the saved Motion. A TV whose config.xml or config.json sets `reduced_motion` is never Full (the project lead's
  decision, 2026-10-31): a saved Full is Reduced there, a saved Off stays Off, the row shows the Motion in effect and
  OK steps Reduced and Off only. With nothing saved, the switch and `reduced_motion` decide as before. A step applies at once and moves the settings change count, so Home
  rebuilds with it the next time it resumes. The launcher has one reduced-motion mode (no animations), so Reduced
  and Off look the same for now. Wanted: Reduced as the spec has it, keeping the fades and dropping slides, rises
  and focus scale-ups.
- **The open menu's button:** as with the Wi-Fi and Inputs dropdowns, the Settings icon is not kept highlighted while
  its menu is open (the prototype's `is-open`).

## First run

`FirstRunPage`, one page since F28 (the project lead, 2026-11-02): the checklist (design 01-setup B) starts wide, and
→ on a row opens its step's panel on the same page, the checklist shrinking to tabs on the left. The panel is the
step's Launcher settings pane (`SettingsSection.pane`, with `FirstRunPage` as its `Host`) and a footer. The project
lead decided its open questions on 2026-10-31.

- **When it shows by itself:** once, on the first start of Home after a fresh install, never after an update
  (`PackageInfo.firstInstallTime` equal to `lastUpdateTime`), so a TV that had the launcher before, this code
  included, doesn't see it. Remembered as `shown` in the `first_run` preferences (kept apart from Launcher settings'
  choices, which a theme push clears); opening it through the intent counts too. config.xml's `first_run_checklist`
  is an opt-out: on by default, `false` (or `"first_run_checklist": false` in a pushed config.json) never shows it by
  itself. The intent action `com.hometv.launcher.action.FIRST_RUN` on `MainActivity` opens it at any time, for adb:
  `adb shell am start -a com.hometv.launcher.action.FIRST_RUN -n com.hometv.launcher/.MainActivity`. There is no
  quick-settings or Launcher settings row for it, and no "Set up again" row. The rule is `FirstRunRules.showOnStart`.
- **Tabs and panel (F28):** with a panel open, ↑/↓ on the tabs show each panel at once (shown is not done), → enters
  it, ← from its left edge or Back in it returns to its tab, and Back on a tab widens the checklist again. The footer
  reads "Step N of 3 · changes save at once" with Next, or Done on the last step, which widens the checklist with focus
  on the first row not done, or Start.
- **When a row counts as done:** as in the prototype, once its panel was entered (→ or OK on the row, or Next to
  it), whatever was chosen there (`FirstRunRules.done`). The done steps are kept as `done` in the `first_run`
  preferences, so the green checks stay after the launcher restarts, and over a rebuild.
- **Row values:** Streaming services and Stores for rent and buy show the ticked names as the prototype does, the
  first two then "+N" ("Netflix, Prime Video +6"; one name alone; nothing when none is ticked), in the panes' order and
  with their names (`FirstRunRules.names`). The line under Streaming services is the count ("8 ticked"), as in the
  prototype. Features shows the features on, the same way, or "All off", which is left out while it repeats the All
  off pill; its line is the count ("2 of 4 on").
- **Features step (F14, the project lead 2026-11-01, 2026-11-02; F34, Rebecca 2026-11-02):** the steps are Features,
  Region (F12, before the two it decides), Streaming services and Stores. Every feature but Phone notes starts on
  (Phone notes opens a port), and each has its setup as a tab under Features, on or off. Features itself is an
  overview with no switches; each setup's pane has its feature's On/Off on top, and off, says so above that switch
  (`FeatureOffSection`). The tabs (`FirstRunRules.setupOf`, `setups`:
  TMDB account → Sign in to TMDB, Phone notes → its pane, Weather → Weather city); Next leads from Features through
  them, then on (`FirstRunRules.panels`). A setup tab reads "Not set up yet" until it is. **When First run ends**
  (Start, Back, the remote's Home; not a rebuild) every feature on whose setup isn't finished is switched off and a
  toast names them (`FirstRunPage.switchOffUnfinished`); Start's help pane lists them first. What counts as set up
  (`FirstRunRules.setUp`): TMDB account, signed in; Phone notes, signed in and the TV listening; Price lookup, the
  Watchmode key works (`PriceKeys.ready`; Movie of the Night is optional); Weather, always (the TV's default place
  counts). Price lookup's setup is the Price keys tab (F16), and TMDB account's starts with a TMDB key tab when the TV
  had no working TMDB key as First run opened (`FirstRunRules.setups`); Sign in after it rebuilds the launcher with the
  key first (`FirstRunRules.signInNeedsRebuild`). With Price lookup off the Stores row's pill reads "Price lookup off"
  and its panel is the off state, with Turn on.
- **Turn on (2026-11-02):** every off state has Turn on, which switches the feature on (and the one it needs) and goes
  straight into its setup (`SettingsSection.Host#turnOn`, `FeaturePrefs.turnOn`): a Launcher settings section, First
  run's Stores, and Movies, TV Shows and Search's "needs a TMDB account" (`MainActivity#signInFromPage`, where Back
  from the sign-in returns to the page, `backCloses`).
- **A step that recreates the launcher:** TMDB account and Phone notes rebuild the launcher before their setup (it
  makes the TMDB client and the notes inbox only with them on; `FirstRunRules.setupNeedsRebuild`), and a theme change
  in Launcher settings rebuilds it too. The rebuild reopens First run with its done steps and the panel it had open
  (MainActivity keeps `first_run_setup`).

## Family install

Getting the launcher onto the TVs of family and friends who aren't technical, with no AI agent: they run a script
on a Windows laptop while the project lead helps on a call. Planned with the project lead on 2026-11-01
([docs/rework.md](docs/rework.md), Family install). Today `launcher/build-and-install.sh` / `.ps1` needs Python, JDK 17
and the Android SDK on the laptop, bakes the keys and weather place into the APK (`tv.properties`), signs with the
build machine's debug key, and speaks ADB on port 5555 with no pairing (`tools/tv.py`).

In order, one PR each:

0. **Package rename** to `com.rmcfad.tv` (F8; the project lead's feedback list), then `com.hometv.launcher` (F11b). Before anyone else installs: a rename
   later is a new app, and their lists, ticks and sign-in stay behind in the old one.
1. **Settings at run time.** A `household.json` pushed over ADB into the launcher's files folder, as `tools/theme.sh`
   pushes `config.json`: the TMDB token, the Watchmode and Movie of the Night keys, the region, this TV's share of
   each key's quota (for two TVs on one key), the shown lists, the default weather place and the Features switches
   (F14; today config.xml's `feature_<id>` and a pushed config.json's `features`). The launcher moves it
   into private storage and deletes the file. The `tv.properties` keys stay as the fallback for our own builds. This
   lets one public APK carry no secrets.
   **Changed:** no `household.json` (the project lead, 2026-11-01, Q2). **Built (F16):** the keys go in on the TV
   itself, in Launcher settings › API keys and First run's TMDB key and Price keys tabs. A phone scans the QR, or a
   laptop opens the address and types the code, to a page the TV serves on port 8766 only while that pane shows
   (`KeyInbox`, `res/raw/keys_setup.html`). Each key is checked with one free request, kept in the launcher's private
   storage (`ApiKeys`) and shown again only by its last four characters. A key entered there replaces the build's;
   Remove leaves none, the build's included. The region is a Launcher settings choice (F12). Sharing one key's quota
   between TVs was dropped (2026-11-02).
2. **US region.** The region as a setting, SE by default, US from `household.json`: TMDB's region and watch
   providers, the US services with their TV apps and ServiceIds, prices in dollars, Watchmode `regions=US`, Movie of
   the Night `country=us`, iTunes `us`. Today SE is a constant (`TmdbApi.REGION`, `WatchProviders.REGION`), with
   kronor. A first step towards [Two regions](#two-regions).
3. **Signed releases.** A release keystore kept outside git and backed up; a new PowerShell release script in
   `tools/` builds, signs and uploads to a GitHub Release (`gh release create`) from the project lead's PC, with no CI
   release workflow: the APK, the installer script and a `SHA256SUMS` file for both. AGENTS.md's "This fork" gets a
   line allowing signed APKs on GitHub Releases, never in the tree and never with keys.
   **Built:** `tools/release.ps1` and [tools/release.md](tools/release.md). `-NewKey` makes the key once, in
   `%USERPROFILE%\.hometv`, and never replaces it. Its certificate's SHA-256 is committed as
   `tools/release-cert.txt`. Each release is a public build (`-PpublicRelease=true`: the committed example settings,
   no keys, `BuildConfig.PUBLIC_RELEASE`), signed with that key, checked, and drafted with `version.json` beside
   `SHA256SUMS`. Gradle signs only that build with the release key. Every other build keeps the machine's debug key,
   so the project lead's TV stays on her debug builds (her choice, 2026-11-01).
4. **Family installer.** A PowerShell script. Two ways to start it, both from one tagged release, never from `main`:
   the release page's script, downloaded and checked against `SHA256SUMS` first (the first choice), or a one-liner
   pasted during the call (`irm <tagged url> | iex`), for when SmartScreen blocks the download; the one-liner is the
   last resort, used only with the project lead on the call. It:
   - downloads only what it pins, and checks each file before use: Google's platform-tools at a fixed version with its
     SHA-256 written in the script (its `adb` can pair; `tools/tv.py` can't), and the APK from the same release
     against `SHA256SUMS`. It also compares the APK's signing certificate with the release key's digest, also written
     in the script, and stops at any mismatch;
   - asks what is plugged into the TV's USB ports and whether its network comes through a USB Ethernet adapter, then
     walks them through Developer options and debugging in plain words, and connects, or pairs with Wireless
     debugging's code (usual on Google TV);
   - reads the model, Android version and brand itself, and stops below `minSdk` 26;
   - before changing anything, backs up the package list, the disabled list, `getprop` and settings to the laptop,
     and writes the restore file that undoes every step it is about to take;
   - on Google TV, warns that Home needs `setupwraith` gone and that it can cost casting (Cast Fix, in upstream's SHOWCASE.md);
   - runs the Home guard, ported from `build-and-install`. On a CVTE board (any `com.cvte.*` package) it keeps the
     stock launcher (`com.cvte.tv.launcher.*`) enabled unless the `examples/` list for that exact board disables
     it: it sets the new Home with `set-home-activity`, reboots once, and confirms Home and ADB came back before
     anything else (#741);
   - picks the key helpers by brand, and on a CVTE board only in a later pass, after that reboot;
   - helps them make their own TMDB, Watchmode and Movie of the Night keys: opens each sign-up page, gives the text for
     TMDB's app form, and checks each key with a test call. For re-runs it keeps them in one file in their Windows
     profile, encrypted with DPAPI (current user only), and never in plain text. A key never appears in full on
     screen, in the report or in a log (only its last four characters), and the script starts no transcript;
   - pushes `household.json` and grants in-app updates, adding each step to the restore file;
   - ends with them switching debugging off, and checks the port is closed.

   Tested against fake TVs, as the shell scripts are.
   **Built (first part):** `tools/family-install.ps1` and [tools/family-install.md](tools/family-install.md), with
   `tools/release.ps1` stamping each release's copy with its tag, APK name, APK SHA-256 and certificate SHA-256. In
   it: the pinned downloads and the signer check, the walk-through, connect or pair, the model and SDK checks, the
   backup and `undo.txt` (undone with `-Restore`, so no second script), the Google TV Wi-Fi owner check (no override)
   and Cast warning, the CVTE path, the Home guard with a Home-press check, First run opened last after a fresh install,
   and debugging off with the port check.
   Changed: the last-resort command fetches the file with `irm -OutFile` and runs it, since `| iex` closes the window
   at the end; keys go in on the TV (F16), so the installer keeps none. Still to come: key helpers by brand, the
   updates grant (step 5), the debloat (step 6).
5. **In-app updates.** The launcher reads the latest GitHub Release once a day while Home shows, downloads the APK,
   and Android asks to install it; shown in Launcher settings › About. Needs `REQUEST_INSTALL_PACKAGES` (justified in
   the PR) and "install unknown apps" for the launcher, which the installer grants with `appops` while ADB is on and
   then reads back. The grant can fail or be undone, on some OEM builds and on restricted profiles. When it isn't
   there, About says so and its Update button opens Android's "Install unknown apps" screen for the launcher
   (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`), with a line saying what to switch on. On a TV without that screen, updates
   go back to re-running the installer, and About says that too. To check on a TV: a Home app replacing itself comes
   back cleanly (the #1101-style restart).
6. **Debloat from a report.** A matching model doesn't make another household's choices theirs: a list can disable
   apps its owner didn't use (YouTube Music and the Assistant in `examples/jvc-android-tv-menlo`). So before
   applying a list for the model it read, the installer asks PROMPT.md's checklist question (streaming apps, the
   voice assistant, Chromecast, Miracast, the firmware updater) and leaves out everything they tick. It then applies
   the rest after the backup, with the protected-package check, in small batches, with the firmware updater left on.
   Without a list it disables only the stock launcher (and not even that on a CVTE board, as in step 4), and writes a
   report (model, Android version, package list, their ticks; no IP, serial or Wi-Fi name) for the project lead. She
   makes the list with her agent under PROMPT.md's rules, and the next run applies it.

- **Quotas are per key:** Watchmode's free plan is 2,500 credits a month, Movie of the Night's 1,000, and each TV
  budgets the whole quota for itself; hence a key per household. TMDB has no quota, but its key form asks for an app
  name, URL and summary.
- **Later, maybe:** a phone settings page on the TV's `/setup` server, to change keys or lists without a laptop.

### Tizen port (separate repo)
- **Finding:** a Samsung TV runs Tizen, so nothing here installs on it, and no third-party app can replace Smart Hub
  or start at power-on. The best case is an app they open (some models reopen the last app used).
- **Planned (2026-11-01):** a spike in its own repository, as a
  [TizenBrew](https://github.com/reisxd/TizenBrew) application module: a web app served from npm, so it updates
  whenever it's published, and TizenBrew is installed once from a PC in Developer Mode. Its logic (TMDB catalogue,
  prices, deep links) is shared with `design_preview/` as a TypeScript package rather than copied; the Android
  launcher's Java is the reference.
- **The spike answers:** whether a module can open Netflix and the rest (`tizen.application`), and how the keys get on
  without ADB (a QR to a page on the phone).
- **Ruled out:** an own `.wgt` through Tizen Studio (a certificate per TV's DUID, which expires) and Samsung's store.
  Fallback: a Google TV Streamer on the Samsung's HDMI, running this launcher.

## Not designed yet

From the spec's open questions ([design_preview/README.md](design_preview/README.md)). Each needs a design before
it is built.

- Search and Home's weather while loading (06-states A designs offline and errors, not these).
- First run and setup: the checklist (01-setup B) and its panes (r2-setup-checklist) are built in the launcher
  (`FirstRunPage`); see [First run](#first-run) for how it behaves.
- Launcher settings in the new UI: the split (05-settings A) and every pane are built in the launcher (M9, see
  [From M1](#from-m1)). Home row apps (with reordering) has no design; the prototype's is a stand-in.
- Removing a recent search.
- Launcher settings › Features (F14, 2026-11-01): built in the prototype without a Figma frame, for the project lead's
  approval. The launcher's Features page and its `features` keys in household.json and a pushed config.json come after
  the package rename (F8); a feature switched off must not start (no receivers, jobs or network calls).
- Voice search.
- Progress on started titles.
- The Sort chip on Movies and TV Shows (F4): built as a small button like Filters, with a dropdown; no Figma frame.
- The person page beyond its stub.
- The Explore versions of Movies and TV Shows (parked in Figma).

The Music page, a stub in V1, left the nav with F17 (the project lead, 2026-11-01); it is not planned.

## Languages

- **V1:** English only.
- **Wanted:** the launcher's translations and right-to-left layout (the old launcher had 25 languages, with
  Hebrew mirrored).
