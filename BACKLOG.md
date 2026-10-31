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
- **Still to do:** paging past TMDB's first 20 search results; TMDB's language following the TV's; the full
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
  - **When:** prices are kept 7 days. Once a TMDB account is signed in, titles on My list and the Household and
    Friends lists are kept fresh in the background (not before, so a fresh TV spends nothing on the mock lists); any
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
- **Still to come:** the sign-in and first-run designs, and whether a first run asks to sign in.
- **Not seen yet:** a real approval (the project lead approves one on the TV), and so the session, the account's
  name, making the Seen list and the sync against a real account. Checked on the emulator: the code and its QR
  (decoded), TMDB's "not approved yet" answer (422, code 41), polling stopping while the page is hidden, offline,
  expiry and New code.

### Household and Friends lists
- **V1:** mock placeholders in the list chips and the grid.
- **V2 Phase 1:** still the mock lists, on the TMDB ids of their titles (`MockIds`, `MockOverlay`), with the mock's
  next episode for the "Up next" badge where the real show has that episode.
- **V2 Phase 3:** chosen on the TV (docs/rework.md, Accounts, 2026-10-30): once signed in, the account page's
  "Choose lists" lists the account's TMDB lists, each Household, Friends or Not used (OK moves it on; one list per
  role), saved on the TV (`ListChoices`). `tmdb_household_list` and `tmdb_friends_list` in `config.xml` (or a pushed
  `config.json`) override the choice: a list number or themoviedb.org/list link (a public list is read without
  signing in), or a list name in the signed-in account (`ListRef`). The TV only reads them, and keeps the last copy
  for offline. A TV that never signed in and sets neither keeps the mock lists; once it has an account, a role with
  no list is empty. All lists means My list, Household and Friends; the mock catalogue's own "My list" no longer
  counts.
- **Still to come:** the proper pane for Choose lists, with the new Launcher settings design.

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

### Title deep links
- **V1:** "Watch on …", episode rows and Where to watch open the service's app on its home screen. Still so with the
  mock catalogue.
- **V2 Phase 2 (real):** with a TMDB token, Watch and Where to watch open the title itself where Wikidata knows the
  service's id for it: `WikidataIds` asks query.wikidata.org once per title (SPARQL by the TMDB id, `P4947` movies,
  `P4983` shows, kept a month) for Netflix (`P1874`), Disney+ (`P13902`, `P7595`/`P7596`), Apple TV
  (`P9586`/`P9751`), HBO Max (`P8298`), Prime Video (`P14462`, `P14440`, `P8055`) and SVT Play (`P6817`), and
  `DeepLinks` builds the links (formats after cleanyourtv-ui's `DeepLinks.java`). The link goes as an ACTION_VIEW
  intent limited to the service's package (Netflix with `source=30`); when no installed app takes it, the app opens
  on its home screen as before.
- **Mock or missing:** episodes still open the show. TV4 Play, Viaplay, SkyShowtime and the Swedish stores have no
  Wikidata property, so they open on their home screen. The emulator has none of these apps: whether each TV app
  takes its link is still to be checked on the TV.
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
- Module cards and the add-ons in `addons/`.
- The Watch Next row.
- Festivals, the sky and the lunar calendars.
- The old Home's on-screen extras: the idle clock (`home_idle_clock_minutes`), the stats line (`show_stats`), the
  VPN badge on the network pill (`vpn_app` now shows its app and state in the Wi-Fi dropdown instead), the app
  library dialog, reorder mode and dock editing. Their `config.xml` keys went in M1b; the theme keys are described,
  frozen, in `themes/README.md`.

## Themes

M9 (2026-10-30): Launcher settings › Theme, from the Figma file "CleanTV · Theme explorations"
(`lwShqRaciMpDUDW5dYljNw`). Classic: Midnight (the default, today's look), Daylight, OLED Black, High Contrast, Cinema
and Landscapes. Seasonal, following the date: Nature, Vector art and 8-bit, each in ten periods (`SeasonCalendar`).
A theme changes colours, type, corners, shading, overlays and the backdrop; layout and data stay the same.

- **Retro themes** (Retro CRT, Synthwave '84, Groovy '70s, 8-bit Arcade): designed in Figma, with their tokens and the
  scanline overlay ready; not in the picker yet.
- **Moving details:** the switch is saved, but nothing moves yet (petals, snow, leaves, bats).
- **Southern hemisphere:** the Seasonal periods follow the northern calendar everywhere.
- **Fonts:** downloaded through Google Play services' font provider and kept on the TV; a family the provider can't
  serve comes straight from Google Fonts' CSS API over HTTPS (the TrueType file, at most 2 MB; still none in the APK).
  Roboto until they arrive. On the API 28 emulator's Play services, Outfit, Figtree, Manrope, Atkinson Hyperlegible
  Next, Baloo 2 and Pixelify Sans come that second way. On the TCL (main 24e8f119, 2026-10-31) the provider served
  Cinema's DM Serif Display and Daylight's Figtree.
- **Backdrops:** the photos and Vector art live in `launcher/backdrops/` and are downloaded once from
  `theme_assets_url` (jsDelivr on this repository's `main`). The 8-bit scenes are in the APK. On the TCL, Cinema's,
  Landscapes' and Vector art's downloaded from `main`. Vector art's sky shows faint gradient banding at full size.
- **Not shipped:** the Vector scenes alternative to Vector art, and the old Seasonal & festive themes (Figma pages kept).
- **Card shadows:** every theme but Midnight draws a resting shadow under each artwork card. Measured on the TCL with
  `tools/bench.py` (main 24e8f119, 36 keys × 3, 2026-10-31):

  | Theme | Response p50 | Movement p50 | Jank | Cold start p50 | PSS |
  |---|---|---|---|---|---|
  | Midnight | 19 ms | 195 ms | 46.9 % | 681 ms | 27 MB |
  | Cinema | 23 ms | 236 ms | 84.8 % | 726 ms | 31 MB |

  Cinema nearly doubles the jank. It also has a photo backdrop and a vignette, so the run doesn't separate the
  shadows' cost from the backdrop's. What to do is open with the project lead; the default stays as it is until then.
- **Focus glow:** checked on the TCL: a theme's glow shows in full on Home's Inputs and Shortcuts tiles, edges
  included.

## From M1

- **Launcher settings page:** M9 replaced the old `LauncherSettingsActivity` with the split page of V2 design
  05-settings A (`LauncherSettingsPage`, its sections in `SettingsSections`, shared controls in `SettingsKit`).
  Every section has its pane: Account, Streaming services, Stores for rent and buy, Weather, Home row apps, Theme,
  Motion (Full, Reduced or Off, on `MotionChoice`), Open on start, Default Home, TV settings and About. Not built:
  About's Open-source licences and Clear cache. Follow theme is a button in About: it drops Motion, Open on start
  and the old layout choices so `config.xml` or a pushed `config.json` decides again; the Theme, weather, account and
  the service and store ticks stay (the project lead, 2026-10-31). A `config.json` push still clears the same.
  Home's row holds seven apps, as the spec: Home row apps and All Apps' Add to Home say so when it is full.
- **Network icon state:** the nav shows the prototype's Wi-Fi arcs, on Ethernet too (the project lead, 2026-10-31), or the arcs crossed out when offline, which since 06-states A
  includes a network with no internet behind it. The old pill could also
  tint by state (`wifi_status_color`, `wifi_status_warnings`); not designed for the new nav.
- **README card:** since M1b `tools/gen_tables.py` no longer counts languages, and `docs/card.jpg` and
  `docs/stats.png` show TV models, brands and the launcher's size. Its "after", `docs/new-home.jpg`, is a screenshot
  of the new Home from the TV (2026-10-30). The stat strip still says 1.6 MB: redraw it once the APK size settles
  (V2 Phase 1 brought it to about 0.27 MB).
- **Last opened:** `LastOpened` notes when Home or All Apps last opened each app (on the TV only, no permission).
  Nothing reads it since the old Home's "Last opened" line went in M1. Wanted: a use for it, for example ordering
  All Apps or the Home row by recent use, once that is designed.
- **Cold start and jank:** V1 measured 1.9–2.1 s (TotalTime) against M1's 2.8 s and the old launcher's 2.7 s, but 47 %
  of frames were janky while pressing keys on Home ([docs/rework.md](docs/rework.md#results-2026-10-30-main-at-0212b6a4)).
  Phase 0 looks for the cause; measure again on the TV with `tools/bench.py` ([perf-ab](docs/workflows/perf-ab.md)).

## From M2

- **All Apps:** V1 is a plain working grid styled from the tokens (Back, title and count, six columns of banner
  tiles sorted by name; OK opens, hold-OK adds to or removes from Home, App info). Wanted: a proper design, which
  may add search, hiding apps and sorting (the prototype's stub says "with hide and reorder").
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

- **Scope line:** "3 imported watchlists" counts the three mock lists, and "All N services" the household's
  subscribed services (`subscribed_services` or the installed apps). Wanted: the real number of imported lists once
  they come from TMDB (see Household and Friends lists).
- **Empty list note:** says to "import a watchlist in Settings", which doesn't exist yet (see First run and setup
  under Not designed yet).
- **Filters and the chosen list** are kept per page while the launcher runs, and go back to the default
  ("Included only · Review 7.0+", All lists) when Home restarts. Not designed: whether they should be stored.
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

- **Jank on Home's key moves:** the real TV measured 47% janky frames (337 of 712), movement p50 197 ms, response p50
  21 ms. The emulator can't show where that goes: every frame there spends 80–100 ms issuing draw commands,
  about 50 ms even with the backdrop, shades, hero, nav, lower rows, banners and labels all switched off, and
  turning any single one off moved it by less than the noise. The UI thread is not the cost (animation 1 ms, layout
  and recording under 0.2 ms per frame, no layout pass on a focus change). Wanted: a `tools/trace.py` run on the real
  TV (hwui and gfx) to see whether the GPU's fill (backdrop, shade and, when scrolled, the dim layer, each full
  screen) or the number of frames per move (140 ms scale and state fades, the 240 ms row slide) is the cost, then
  the fix.
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

The checklist (design 01-setup B; `FirstRunPage`) and its cut-down split (r2-setup-checklist; Launcher settings'
own page through `LauncherSettingsPage.Setup`, filled by `FirstRunSplit`). The project lead decided its open
questions on 2026-10-31.

- **When it shows by itself:** once, on the first start of Home after a fresh install, never after an update
  (`PackageInfo.firstInstallTime` equal to `lastUpdateTime`), so a TV that had the launcher before, this code
  included, doesn't see it. Remembered as `shown` in the `first_run` preferences (kept apart from Launcher settings'
  choices, which a theme push clears); opening it through the intent counts too. config.xml's `first_run_checklist`
  is an opt-out: on by default, `false` (or `"first_run_checklist": false` in a pushed config.json) never shows it by
  itself. The intent action `com.gathin.tvhome.action.FIRST_RUN` on `MainActivity` opens it at any time, for adb:
  `adb shell am start -a com.gathin.tvhome.action.FIRST_RUN -n com.gathin.tvhome/.MainActivity`. There is no
  quick-settings or Launcher settings row for it, and no "Set up again" row. The rule is `FirstRunRules.showOnStart`.
- **When a row counts as done:** as in the prototype, once its pane was opened (OK on the row, or Next to it),
  whatever was chosen there (`FirstRunRules.done`). The done steps are kept as `done` in the `first_run` preferences,
  so the green checks stay after the launcher restarts, and over a rebuild.
- **Row values:** Streaming services and Stores for rent and buy show the ticked names as the prototype does, the
  first two then "+N" ("Netflix, Prime Video +6"; one name alone; nothing when none is ticked), in the panes' order and
  with their names (`FirstRunRules.names`). The line under Streaming services is the count ("8 ticked"), as in the
  prototype. Weather's value is left out while it is "Default" beside the Default pill, and the signed-in account
  shows the username without the avatar.
- **A step that recreates the launcher:** a rebuild (Theme or Typeface, for example) reopens First run with its done
  steps, not the split it was in. None of the four steps rebuilds mid-split today; a new step that does needs the
  restore path to know it came from First run.

## Not designed yet

From the spec's open questions ([design_preview/README.md](design_preview/README.md)). Each needs a design before
it is built.

- Search and Home's weather while loading (06-states A designs offline and errors, not these).
- First run and setup: the checklist (01-setup B) and its panes (r2-setup-checklist) are built in the launcher
  (`FirstRunPage`, `FirstRunSplit`); see [First run](#first-run) for how it behaves.
- Launcher settings in the new UI: the split (05-settings A) and every pane are built in the launcher (M9, see
  [From M1](#from-m1)). Home row apps (with reordering) has no design; the prototype's is a stand-in.
- Removing a recent search.
- Voice search.
- Progress on started titles.
- Sorting lists.
- The person page beyond its stub.
- The Explore versions of Movies and TV Shows (parked in Figma).
- The Music page (a stub in V1).

## Languages

- **V1:** English only.
- **Wanted:** the launcher's translations and right-to-left layout (the old launcher had 25 languages, with
  Hebrew mirrored).
