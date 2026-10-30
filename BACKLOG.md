# Backlog

Features the new launcher needs but V1 doesn't have yet: where V1 uses mock data or a fallback, what was left
for V2, and what isn't designed. Each entry says what V1 does, what the real version needs, and any findings
so far. A PR that adds a mock adds its entry here; a PR that makes one real removes it. How this fits the
milestones: [docs/rework.md](docs/rework.md).

## Data and accounts

### Real catalogue
- **V1:** the mock catalogue from `design_preview/src/data`, behind an interface (`Catalog`, with `MockCatalog`
  ported title for title). Its artwork is 17 JPEGs bundled in the APK (`res/drawable-nodpi/art_*.jpg`, at most
  960 px wide, about 1.35 MB), decoded at the size shown by `Art` into a memory cache only.
- **Wanted:** TMDB for titles, artwork, cast, episodes, similar titles and search, cached on the TV so offline
  still works.
- **Findings:** cleanyourtv-ui's `Tmdb.java` caches answers on disk for as long as the path deserves: search
  1 hour, trending and discover 3 hours, a title 12 hours, images, people, seasons and the region's providers
  a week. Its `Images.java` keeps up to 24 MB of bitmaps in memory and 160 MB on disk for 7 days. TMDB requires
  the notice "This product uses the TMDB API but is not endorsed or certified by TMDB". The key goes in the
  gitignored build properties file.

### Streaming availability and prices
- **V1:** mock: which service has a title, "Included", and the rent and buy prices.
- **Wanted:** real availability for the region, and real prices.
- **Findings:** cleanyourtv-ui uses TMDB's `/watch/providers` for the region (JustWatch data). That gives which
  services stream, rent or sell a title, but no prices. Its `Providers.java` maps TMDB service names to the TV
  apps that open them, and the person can override the mapping. Prices need a second source, such as Watchmode
  or the Streaming Availability API (RapidAPI). Both need a key and have free tiers with rate limits; check
  their current terms before choosing.
- **Recommendation:** TMDB providers for availability first; prices from a second source only if the "Rent from
  $3.99" lines are worth a key and a quota.

### Two regions
- **V1:** SE only.
- **Wanted:** search SE and US together, since the household also watches through a VPN, and label each result
  with its region.

### TMDB sign-in and watchlist sync
- **V1:** My list, Seen, Favorites, recent searches and recently viewed are stored on the TV only.
- **Wanted:** sign in to TMDB on the TV and keep My list and the rest in step with the account.
- **Findings:** cleanyourtv-ui signs in with TMDB's v3 flow. The TV shows a QR code (`Qr.java`, its own encoder
  after Project Nayuki's, MIT) for a request token that the person approves on their phone. The session id is
  then kept in the app's settings. Without an account, My list and Seen stay on the TV.

### Household and Friends lists
- **V1:** mock placeholders in the list chips and the grid.
- **Wanted:** both come from the project lead's TMDB lists. Which list is which is decided when this is built.

### Household seed
- **V1:** on the first run, My list, Seen, Favorites, recent searches and recently viewed start from the
  prototype's mock seed (`design_preview/src/core/store.ts`): My list holds the catalogue's titles on the mock "My
  list", two titles are seen, one is a favourite, four searches and six titles are recent. After that every change
  is kept on the TV (`Household`, SharedPreferences `household`).
- **Wanted:** start empty, or from the TMDB account once sign-in exists (see TMDB sign-in and watchlist sync).

### Trailers
- **V1:** Trailer opens YouTube's TV app searching for "name year trailer"; without YouTube it says so.
- **Wanted:** open the trailer itself, from TMDB's videos for the title.

### Title deep links
- **V1:** "Watch on …", episode rows and Where to watch open the service's app on its home screen.
- **Wanted:** open the title, or the episode, directly.
- **Findings:** cleanyourtv-ui's `DeepLinks.java` finds the Wikidata item for a TMDB id (`P4947` movies,
  `P4983` shows). From it, it reads the service ids for Netflix (`P1874`), Disney+ (`P7595`/`P7596`), Apple TV
  (`P9586`/`P9751`), Max (`P8298`) and Prime Video (`P8055`), builds each app's link, and keeps the answer for a
  month. No source for episode links was found yet.

### Choosing the household's services
- **V1:** subscribed services come from the installed apps, with an override: `subscribed_services` in
  `config.xml` or a pushed `config.json`, a list of lowercase ids (`["netflix", "prime"]`; an unknown id is
  ignored, so `["none"]` means none). Left empty, every subscription service whose TV app is installed counts,
  and YouTube; the Apple TV app counts as Apple TV+, never as the Apple TV store. Read when Home starts, so an
  app installed later counts after Home restarts.
- **Wanted:** a screen in Launcher settings to tick the services the household pays for.

### Weather location on the TV
- **V1:** `latitude` and `longitude` come from the gitignored build properties file, `launcher/tv.properties`
  (template: `launcher/tv.properties.example`, New York).
- **Wanted:** pick the city on the TV. The weather add-on in `addons/weather/` already does this with
  Open-Meteo's geocoding.

### Wi-Fi networks
- **V1:** the dropdown's header is real; saved networks and other networks open Wi-Fi settings.
- **Wanted:** list and join networks from the dropdown. Android 10 and later stop a normal app from listing
  saved networks or switching between them. Android 9, which the test TV runs, is looser, but check which
  permissions it asks for before building this.

## Left for V2

From the old launcher; not in V1 by decision.

- Themes and wallpapers pushed as `config.json`.
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

## From M1

- **Launcher settings page:** V1 opens the old `LauncherSettingsActivity`, cut to Animations, Open on start,
  Follow theme, Default Home and TV settings. Wanted: the new page in the new UI, once it is designed.
- **Network icon state:** the nav shows Wi-Fi, Ethernet or Wi-Fi crossed out when offline. The old pill could also
  tint by state (`wifi_status_color`, `wifi_status_warnings`); not designed for the new nav.
- **README card:** since M1b `tools/gen_tables.py` no longer counts languages, and `docs/card.jpg` and
  `docs/stats.png` show TV models, brands and the launcher's size (1.6 MB, mostly mock artwork). Its "after",
  `docs/new-home.jpg`, is a screenshot of the new Home from the TV (2026-10-30). Redraw the stat strip when the APK
  size changes (Phase 1 removes the mock art).
- **APK size:** about 1.6 MB, almost all of it the bundled mock artwork (1.35 MB); the 512 KB gate is paused in this
  fork. When the real catalogue replaces the bundled art, measure the APK again and decide whether the gate comes
  back, and at what size (project lead, 2026-10-29).
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
- **Genre options** are the mock catalogue's genres; TV Shows leave out Food, Musical, Western and Horror, as the
  prototype does. Wanted: TMDB's movie and TV genre lists.
- **Guide line:** "Illustrative services, scores & availability" stays while the data is mock.
- **Long lists:** the grid builds a card for every title at once (16 at most in the mock). A real list of hundreds
  needs a recycled grid or paging, with the artwork decoded as cards come into view.

## From M5

- **Watch and episodes open the service's app, not the title:** Watch, an included Where to watch card, an episode
  and Rent or Buy in the dialog all open the service's app on its home screen (`Router.watch`); see Title deep
  links. An episode card opens the show's best offer, not that episode.
- **Rent/buy dialog:** Rent and Buy open the store's app; nothing is paid or recorded in the launcher, and the
  dialog doesn't know whether the rental happened.
- **Person page:** the prototype's stub (portrait with initials, "Person · not designed yet", the titles they are
  in from the mock catalogue). Wanted: a design, photos and a filmography from the real catalogue.
- **Cast portraits:** initials, as in the prototype; photos wait for the real catalogue.
- **Watched episodes:** worked out from the mock next episode (`Title.nextSeason`/`nextEpisode`); nothing records
  what was watched. Mark seen marks the whole title only.
- **More like this:** the prototype's rule (shared genres, same kind, score) over the mock catalogue. Wanted:
  recommendations from the real catalogue.

## From M6

- **Search source:** V1 searches the mock catalogue on the TV (`Fuzzy` and `SearchIndex`, ported from the
  prototype and tested against its results). Wanted: TMDB search behind the same
  results once the real catalogue lands (Real catalogue), with the local matching kept for what is cached offline.
- **Genre suggestions:** OK searches for the genre's name, as the prototype does, and a genre that matches well
  adds its titles to the results. There is no genre page or genre filter chip.
- **Exact genre search:** searching a genre's exact name ("drama", or Browse by genre → Drama) shows No matches, as
  in the prototype: a genre on its own counts as no match, so the results can't open. Deferred until real search
  data lands ([Real catalogue](#real-catalogue)), which decides how genres are searched.
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
  show). Every mock show is under 90 minutes, so it changes nothing yet; a real catalogue may need a per-show
  runtime instead of per-episode lists.
- **Random, not ranked:** picks are four random matches, as in the prototype; nothing weighs score, recency or what
  the household watches. The choices are not remembered between visits either: each Pick for me starts from the
  prototype's defaults.
- **Open in the service:** the results bar's main button opens an included pick's app on its home screen (see
  Title deep links). A rental or purchase opens the title preview instead, so its rent and buy dialog asks before
  leaving the launcher; the prototype's button opened the preview for every pick.

## Not designed yet

From the spec's open questions ([design_preview/README.md](design_preview/README.md)). Each needs a design before
it is built.

- Offline, loading and error states (V1 still works offline: it just has no designed state for it).
- First run and setup: importing watchlists, choosing services.
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
