# How it works

What the reworked launcher does today, and why, in short. It keeps the decisions and the things a maintainer must
not lose: safety and security notes, limits, licence rules and pitfalls. The code says the rest.

Work still open (mocks, fallbacks, ideas, questions) is in [BACKLOG.md](../BACKLOG.md). The decisions log and the
milestones are in [docs/rework.md](rework.md).

## Data and accounts

### Real catalogue
- With `tmdb_api_key` in `launcher/tv.properties`, titles, artwork, cast, episodes, More like this, trailers, people
  and search come from TMDB (`TmdbCatalog`), in English. The household's lists start as stubs and fill in two seconds
  after Home is up, three requests at a time.
- Answers are kept on disk (`DiskCache`) and a stale copy is used when TMDB can't be reached, so what was seen once
  shows offline. Images are fetched at the TMDB size nearest the box and decoded at the size shown.
- A build with `-PmockCatalogue=true` (the emulator, tests) uses the mock catalogue and needs no network. Without a
  token, Movies, TV Shows and Search ask for a key.
- The 43 mock titles map to TMDB by `MockIds` (2026-10-30); "marvel-one-shots" has no TMDB title and drops out. Stored
  ids move to TMDB ids (`movie:329865`) when read. Switching a TV between a TMDB build and a token-less one empties
  its lists until it switches back.
- **Search:** TMDB's `/search/multi` once typing pauses, merged with the fuzzy matching over every title the TV knows
  ("arival" still finds Arrival). Results without artwork or photo are left out. A genre typed in full lists its
  titles, known ones first, then TMDB's most popular, and an exact genre ("drama") puts them before titles merely
  named like it. Past the first 20 results the next pages load as focus reaches the last row, up to five.
- **Ratings:** the US certification, on the scale the filters use. A title without one counts as not rated (NR) in
  Pick for me.
- **Loading, offline and error states** (design 06-states A): skeleton cards while lists first load; an amber
  "Offline · showing your lists as saved today 19:52" pill; a "nothing saved" block when offline with nothing
  saved; error boxes in the preview's tabs. "Offline" means Android reaches no internet; when only TMDB fails, the
  lists show their saved copy without the pill. A title's details, cast, seasons and availability come in one request,
  so they fail together, Overview's box too (the project lead, 2026-10-31).
- **R8 pitfall:** the catalogue holds screens' listeners weakly, and R8 dropped the `onCatalog` fields that kept them,
  so release builds stopped hearing that titles filled in. `proguard-rules.pro` keeps them.
- **TMDB attribution (a licence rule):** TMDB's terms (checked 2026-10-30) ask for the notice "This product uses the
  TMDB API but is not endorsed or certified by TMDB" in About or Credits, and an approved logo, less prominent than
  the app's own. The notice and the "Primary short" logo are in Launcher settings' About (only in builds with a TMDB
  key) and the notice is in the README. Keep both.
- **The token is in the APK:** `BuildConfig.TMDB_TOKEN` can be read out of the APK by anyone who has it. It only
  reads TMDB's public data, but it is the account's: keep a token build to yourself, and revoke the token on
  themoviedb.org if an APK leaks. Public releases carry no keys.
- **Rate limits:** TMDB caps at about 40 requests a second and answers 429 past it. A first start fetches about 45
  details pages. A 429 or a failure is not retried for a minute.
- **Guide line:** "Scores from TMDB · Where to watch from JustWatch" with the real catalogue; the mock one keeps
  "Illustrative services, scores & availability".
- **Genre options** are TMDB's genres in the launcher's words (`GenreMap`: "Sci-fi" for Science Fiction); TV Shows still
  leave out Food, Musical, Western and Horror, as the prototype does.

### Streaming availability and prices
- With a TMDB token, a title's offers come from TMDB's watch providers (JustWatch data) for the region: streaming,
  free, rent, buy. TMDB's add-on channels ("HBO Max Amazon Channel") and Blockbuster (closed) are dropped.
- **Prices** follow the project lead's policy (2026-10-30), and never run while Home is hidden:
  - Watchmode first (`watchmode_api_key`), Movie of the Night only when Watchmode had no price or its budget is spent
    (`movie_of_the_night_api_key`), Apple's keyless iTunes lookup when neither priced Apple. Without keys only iTunes
    is asked.
  - Prices are kept 7 days. With a TMDB account, titles on My list and the shown lists are refreshed in the
    background; any other title is priced only when its preview opens. Grids and search never ask. Movie of the
    Night's change feed is read at most once a day while Home shows and marks kept titles stale.
- **Budgets, no paid plans:** the quota headers of every reply set the count, so a reinstall can't overspend.
  Watchmode stops at 2,300 of 2,500 credits (a lookup costs 2), Movie of the Night at 900 of 1,000 requests. What is
  left is spread over the days to the reset. With a budget spent, only Apple is priced and the other stores show "Rent"
  or "Buy" without an amount. Quotas are per key, so each household needs its own keys.
- **Shown:** the cheapest price per store, type and quality. Where to watch (design 07-prices A) has one card per
  service: included and free first, then stores, cheapest first, the first of two or more priced stores tagged a green
  Cheapest (a tie goes to a store whose app is installed, else TMDB's order, 2026-10-31). Where to watch credits its
  sources, and Launcher settings has the full notices. Small cards say "Rent from 39 kr · 4 stores" from prices
  already known and never ask for one. A store whose SD costs the same as HD shows no SD line.
- **Cards name only the household's own** (F1, the project lead, 2026-11-01): a card counts a ticked subscription, a
  free service whose app is installed or ticked, and the ticked stores' rent and buy; the title page still shows every
  offer. "Included only" follows the same rule for free services.

### Two regions
- One region at a time, SE or US (Family install step 2; `Region`): the TV's own choice, else `region` in
  config.xml or a pushed config.json, else SE. It picks TMDB's Discover region and availability, the Streaming
  services and Stores lists (the US adds Peacock, Paramount+, Tubi, The Roku Channel and Fandango at Home), the price
  sources' country and so the currency. Store ticks are kept per region; the services ticked are the household's in
  either. A change shows once Home is rebuilt.

### TMDB sign-in and watchlist sync
- Signing in is optional, from Launcher settings › Account, on a temporary page (`AccountPage`) until the Figma
  design is chosen. TMDB's v4 flow: a QR code for `themoviedb.org/auth/access`, polled every 5 s for at most 15
  minutes, then the access token is turned into a v3 session, because watchlist and favourite writes exist only in v3.
- My list follows the watchlist, Favorites the favourites, and Seen a private TMDB list named "Seen" that the launcher
  makes when the account has none (`docs/rework.md`, Accounts, 2026-10-30). Sync is both ways: in full after signing
  in and when Home shows if the last one is 10 minutes old, TV changes 5 s after the last one, nothing while Home is
  hidden. Offline or signed-out changes wait in a queue (`SyncLedger`); each title's last change wins.
- A sign-in to a new account takes the account's lists and adds nothing from the TV. Signing out keeps the lists on
  the TV.
- **Safety:** tokens are in the private `tmdb_account` preferences, never logged; account calls skip the disk cache.
- The first read after a sign-in shows "Importing your lists… 3 of 5" with a green bar (2026-10-31).

### Shown TMDB lists
- Once signed in, Launcher settings › Account › Choose lists has a Show / Hidden switch per TMDB list (the lists by
  name decision, docs/rework.md, 2026-10-31). The first six shown are chips in Movies, TV Shows and Pick for me after
  All lists and My list; six is the project lead's cap (2026-10-31). A list shown past six stays in All lists, the
  scope line and prices, and is marked "No chip: in All lists".
- `tmdb_shown_lists` in `config.xml` or a pushed `config.json` overrides the choice (numbers, links or names). The older
  `tmdb_household_list` and `tmdb_friends_list` still work when it is empty.
- The TV only reads these lists and keeps the last copy of each for offline. A TV that never had an account and sets no
  list keeps the mock Household and Friends; with an account, only the shown lists.
- A fresh mock still uses TMDB ids (`MockIds`, `MockOverlay`) and the mock's next episode for the "Up next" badge.

### Phone notes
- Each person keeps a watch list in a note on their iPhone (Apple Notes or Google Keep) and shares it to a "Send to
  TV" Shortcut that posts it to `http://<tv>:8765/notes` with a pairing code (docs/rework.md, Phone notes,
  2026-10-31; set-up steps in [docs/phone-notes.md](phone-notes.md)).
- Launcher settings › **Phone notes** has its own On / Off switch (F34, 2026-11-02; off by default, via `feature_notes`
  or `"features": {"notes": …}`), the TV's address, the pairing code (8 characters, no 0/O or 1/I/L), and a QR (the
  project lead, 2026-10-31) for `http://<tv>:8765/setup#<code>`. The code stays in the link's fragment and is never
  sent to the TV. The page gives **Get the shortcut** (the household's `note_shortcut_url`, only
  `https://www.icloud.com/shortcuts/` links) and **Pair this phone**. After New code a phone just pairs again.
- **Safety:** `NoteInbox` listens only while the feature is on, from private addresses only, one request at a time,
  with a five-minute lockout after five wrong codes. One owner (`Notes`) opens and closes it, so a rebuilt Home never
  opens it twice.
- Notes are answered at once, kept until on the list, and imported on the account's worker so they never race a sync.
  Each new line is looked up once and added to the account's list of that name (else a new private one); a ticked line
  goes to Seen. **Adding only:** a line deleted on the phone takes nothing off, and a title taken off on the TV isn't
  put back. Lines not matched exactly get a picker (`NotePicker`: TMDB's best three, Search again, None of these,
  which is remembered; the project lead, 2026-11-01). An import pill shows progress on every screen.
- Checked on the TV (2026-11-01) from a PC and two iPhones. **Android (F7, F45, 2026-11-01/02):** the setup page shows
  the settings to copy into HTTP Shortcuts, or one tap imports the TV's `/android-shortcut.json` (no address or code in
  it) and Pair this phone runs the hidden pairing shortcut. Checked on the emulator and in a browser only.
- The Shortcut keeps the address it was given; docs/phone-notes.md asks for a router reservation.

### Phone remote
- Today each phone sees the browser's "connection not private" warning once, because the TV makes its own
  certificate ([docs/phone-remote.md](phone-remote.md)). The plan for removing it is in BACKLOG.md.

### Household seed
- A fresh install starts with My list, Seen, Favorites, recent searches and recently viewed empty
  (`HouseholdState.defaults`; docs/rework.md, Accounts, 2026-10-30); a TV with saved lists keeps them. Search hides
  "Recently viewed" and "From My list" while empty. The prototype's seed is only for tests that compare with it.

### Trailers
- Trailer opens TMDB's trailer for the title in YouTube's TV app (an official YouTube trailer first), falling back to a
  YouTube search while details load or when TMDB has none; without YouTube it says so.
- Back from a trailer may walk YouTube's own pages first (F32): YouTube takes the link in its existing task and keeps
  its page history. Killing its background process, `FLAG_ACTIVITY_MULTIPLE_TASK` and `singleTask` tricks were tried
  on the TCL (2026-11-02) and changed nothing. Leave it as YouTube's own behaviour.

### Title deep links
- With a TMDB token, Watch and Where to watch open the title itself where Wikidata knows the service's id for it
  (`WikidataIds`, one query per title, kept a month): Netflix, Disney+, Apple TV, HBO Max, Prime Video and SVT Play,
  and in the US also Hulu, Peacock, Paramount+, Tubi and Fandango at Home. `DeepLinks` builds the links.
- The link goes as an ACTION_VIEW intent limited to the service's package, in a cleared task of its own
  (`Services.LINK_FLAGS`; without it a running app comes to the front and drops the link). If no app takes it, the app
  opens on its home screen as before.
- Seen on the TCL (2026-11-02): Apple TV, Netflix and Prime Video open the title. HBO Max's app rejects Wikidata's old
  `series/urn:hbo` ids ("Content Not Available"), so only its current `show|movie|mini-series/<uuid>` ids are used.
  SVT Play's app takes no web link and opens on its home screen.
- Episodes open the show: Wikidata has no service id for any episode (0 of 210, 2026-11-01).

### Choosing the household's services
- By default every subscription service whose TV app is installed counts, and YouTube; Apple TV app counts as Apple
  TV+, never the store. `subscribed_services` in `config.xml` or a pushed `config.json` overrides (lowercase ids;
  `["none"]` means none). A service that also sells (Viaplay) counts as a subscription; a store alone never. TV4
  Play counts as included when installed, even on its free tier. With the real catalogue the names follow current
  branding: "HBO Max", and "Apple TV" for the subscription beside the "Apple TV Store".
- Launcher settings › Streaming services (`ServicesSection`) ticks them, free ones included; the ticks win over
  `subscribed_services` and the installed apps. The Stores pane leaves unticked stores' rent and buy offers out of the
  real catalogue. Neither is cleared by Follow theme or a theme push.

### Weather location on the TV
- `latitude` and `longitude` in `launcher/tv.properties` are the default place. Launcher settings › Weather lets the
  household **Change city** (Open-Meteo geocoding as typing pauses, no key, only the typed name is sent; English and
  Swedish, so "Göteborg" works on an English TV) and pick °C, °F or the TV's default. The place is kept on the TV;
  "Use default" goes back to `tv.properties`.
- Pitfall: Open-Meteo matches names in the language it is asked in; in English the city is "Gothenburg".

### Wi-Fi networks
- The nav's Wi-Fi dropdown lists the TV's other saved networks, and OK switches to one. On Android 10 and later, or on
  Ethernet, there is no list. A refused switch opens Wi-Fi settings. New permission: `CHANGE_WIFI_STATE` (normal).
- Findings (Android 9, targetSdk 34): `getConfiguredNetworks()` works with `ACCESS_WIFI_STATE`; without location access
  the connected name is hidden but its id is not; scans need location. From Android 10 apps targeting it are refused.

## Themes and settings

### Themes
- Launcher settings › Theme (M9, 2026-10-30; Figma file "CleanTV · Theme explorations"): Midnight (default), Daylight,
  OLED Black, High Contrast, Cinema, Landscapes, and Seasonal Vector art and 8-bit in ten periods each. A theme changes
  look only; layout and data stay.
- **Daylight cycle** (F22, 2026-11-01): a Seasonal picture follows dawn, day, sunset and night from sunrise and sunset
  at the Weather place, offline. It swaps once at each edge, so it costs no frames. Theme › Time of day: Follow the
  sun, Day or Night (F29). On the TCL the picture makes Home draw about 100 ms later (F33, 2026-11-02); kept on
  purpose, since Home shows the cached picture at once (the project lead).
- **Seasonal · Nature removed** (the project lead, 2026-11-01); a TV that had it shows Vector art.
- **Animated details** (Launcher settings › Performance; F25, 2026-11-01): off by default. Snow, rain or leaves fall
  over a Seasonal picture by the real weather, only while Home is idle, and never with Motion Reduced or Off. Its cost
  follows the frame rate (about 0.7 % of a core per fps on the TCL), so it ships at 12 fps. Forcing it for tests:
  `adb shell am start -a com.hometv.launcher.action.FALL_PREVIEW --es fall snow -n com.hometv.launcher/.MainActivity`
  (ignored in public builds).
- **Fonts** come through Google Play services' provider, else Google Fonts over HTTPS (one TrueType file, at most 2 MB),
  and are kept on the TV. Roboto until they arrive.
- **Backdrops:** photos and Vector art are downloaded once from `theme_assets_url` (jsDelivr on `main`); 8-bit scenes
  are in the APK. The launcher decodes backdrops as RGB_565, so the pictures are dithered to that grid (banding fix,
  2026-11-01).
- **Card shadows cost jank:** on the TCL (Mali-470, no `buffer_age`, so every frame redraws the screen) resting
  shadows cost about 19 points of jank and 15 ms of movement; a pre-drawn nine-patch benched the same, so it is the
  pixels. Decided (the project lead, 2026-11-01): Launcher settings › Performance with card shadows On, Focused card
  only or Off, the focus glow, the backdrop picture and Animated details, beside Motion, and a Light mode button.
- **Low-RAM start:** a low-RAM TV (`isLowRamDevice()`, which the TCL reports) or `light_defaults` starts at the light
  end: no card shadows, Motion Off (Reduced's fades cost frames there; Rebecca, 2026-11-04), no animated details; focus
  glow and picture stay. On the TCL it brought jank from about 71 % to 1.4 %. The household's choices win; Follow theme
  and a theme push drop them.

### Quick settings
- The nav Settings icon's dropdown (`QuickSettings`). The remote's Settings key still opens the TV maker's panel, or
  `settings_key_panel`'s old panel.
- The sleep timer row was dropped (the project lead, 2026-10-31). Picture opens `ACTION_DISPLAY_SETTINGS` (her
  decision, 2026-10-31); Sound and Network use the matching actions; each falls back to the TV's settings, and every
  row starts the settings app afresh.
- **Motion:** Full, Reduced or Off (`MotionChoice`), kept in step with the old Animations switch. A TV whose config sets
  `reduced_motion` is never Full (the project lead, 2026-10-31). Reduced keeps fades and drops slides, rises and
  scales; Off moves and fades nothing, as does a TV whose system animations are off.
- The Settings icon isn't kept highlighted while its menu is open, as with the Wi-Fi and Inputs dropdowns.

### First run
- `FirstRunPage` (F28, the project lead, 2026-11-02): the checklist (design 01-setup B) starts wide, and → on a row
  opens that step's Launcher settings pane beside it. Steps: Features, Region (F12), Streaming services, Stores.
- **When it shows:** once, on the first start after a fresh install, never after an update (decided 2026-10-31);
  `first_run_checklist` in config.xml or config.json is an opt-out. The intent action
  `com.hometv.launcher.action.FIRST_RUN` opens it any time for adb. No Settings row and no "Set up again".
- A row is done once its panel was entered (as in the prototype); done steps are kept across restarts.
- **Features step** (F14, 2026-11-01/02; F34): every feature but Phone notes starts on (it opens a port), each with its
  setup as a tab. When First run ends, every feature whose setup isn't finished is switched off and a toast names
  them. "Set up" means: TMDB signed in; Phone notes signed in and listening; Price lookup's Watchmode key works;
  Weather always. Every off state has **Turn on** (2026-11-02), which switches the feature on and enters its setup.
- TMDB account and Phone notes rebuild the launcher before their setup, as does a theme change; the rebuild reopens
  First run on the same panel.

## Family install

Getting the launcher onto the TVs of family and friends without an AI agent: a script on a Windows laptop while the
project lead helps on a call (planned 2026-11-01, [docs/rework.md](rework.md), Family install). One PR each, numbered as the code comments cite them:

0. **Package rename** to `com.hometv.launcher` (F8, F11b), before anyone else installs, since a rename later is a new
   app and their lists and sign-in stay behind.
1. **Settings at run time.** No `household.json` (the project lead, 2026-11-01, Q2). Keys go in on the TV itself,
   in Launcher settings › API keys and First run's key tabs (F16): a phone scans a QR, or a laptop opens the address
   and types the code, to a page the TV serves on port 8766 only while that pane shows (`KeyInbox`). Each key is checked
   with one free request, kept in private storage (`ApiKeys`) and shown only by its last four characters. A key entered
   there replaces the build's. Sharing one key's quota between TVs was dropped (2026-11-02).
2. **US region:** see Two regions.
3. **Signed releases** (`tools/release.ps1`, [tools/release.md](../tools/release.md)). The release key is made once with
   `-NewKey` in `%USERPROFILE%\.hometv`, never replaced, and its certificate's SHA-256 is committed
   (`tools/release-cert.txt`). Each release is a public build (`-PpublicRelease=true`: example settings, no keys),
   with `version.json` and `SHA256SUMS`. Every other build keeps the machine's debug key, so the project lead's TV
   stays on her debug builds (her choice, 2026-11-01). No CI release workflow; no APK in the tree.
4. **Family installer** (`tools/family-install.ps1`, [tools/family-install.md](../tools/family-install.md)). Started
   from one tagged release, never `main`: the downloaded script checked against `SHA256SUMS`, or, as a last resort with
   the project lead on the call, the one-liner. It downloads only what it pins (platform-tools with its SHA-256, the
   APK against `SHA256SUMS`), compares the APK's signer with the release key's digest and stops at any mismatch.
   - It reads the model and SDK itself and stops below `minSdk` 26; backs up the package list, disabled list,
     `getprop` and settings and writes `undo.txt` (undone with `-Restore`) before changing anything.
   - **Home guard, ported from `build-and-install`:** on Google TV it checks that setupwraith doesn't own the Wi-Fi
     network (no override) and warns about casting. On a CVTE board (any `com.cvte.*` package) it keeps the stock
     launcher enabled unless the `examples/` list for that exact board disables it, sets Home with `set-home-activity`,
     reboots once and confirms Home and ADB came back first (#741). A remote-key helper is turned on only for a button
     that fails a press on the real remote, after saying what it can cost (Rebecca, 2026-11-03), and on a CVTE board
     only in a later pass.
   - First run opens last; at the end debugging is switched off and the port checked closed.
5. **In-app updates** (`Updates`, `UpdateRules`; Launcher settings › About). The launcher reads the latest GitHub
   Release once a day while Home shows; About and Home say a version is *available*, and nothing downloads until
   Install update (the project lead, 2026-11-02). The APK comes from the release's own tag and goes to Android's
   PackageInstaller only when its SHA-256 matches `version.json` and its one signer is both the release certificate
   and the installed launcher's. Needs `REQUEST_INSTALL_PACKAGES`; the installer grants "install unknown apps" with
   `appops` and reads it back. If missing, About says so and opens Android's screen for it. A debug build can be
   pointed at a test feed with `-PupdateFeed` (https, or http to 127.0.0.1 or 10.0.2.2; refused with a release key).
6. **Debloat from a report.** A matching model doesn't make another household's choices theirs, so the installer asks
   PROMPT.md's checklist question and leaves out what they tick. It only suggests lists whose header names the model,
   from the release's own `debloat-lists.zip` (pinned by SHA-256; the project lead, 2026-11-02), applies them in batches
   of five with Home checked after each, with `tools/disable.sh`'s guards and no overrides, and leaves the firmware
   updater on. Without a list it disables only the stock launcher (not on CVTE) and writes a report (no IP, serial or
   Wi-Fi name) for the project lead, who makes the list under PROMPT.md's rules.
- **Quotas are per key:** Watchmode's free plan is 2,500 credits a month, Movie of the Night's 1,000, and each TV
  budgets the whole quota, hence a key per household. TMDB has no quota but its key form asks for an app name, URL
  and summary. Keys never appear in full on screen, in a report or in a log.

## Screens

### Launcher settings page
- M9 replaced the old settings activity with the split page of design 05-settings A (`LauncherSettingsPage`): Account,
  Streaming services, Stores, Weather, Home row apps, Theme, Performance, Open on start, Default Home, TV settings and
  About. **Follow theme** (in About) drops Performance's choices, Open on start and the old layout choices so config
  decides again; Theme, weather, account and the ticks stay (the project lead, 2026-10-31). Home's row holds seven apps.
- Options brought back (F36, 2026-11-02): `consume_volume_keys` and `gamepad_passthrough` (config only; see
  [docs/launcher.md › Remote keys](launcher.md#remote-keys)), and Screensaver (F36, 2026-11-04): Launcher settings ›
  Screensaver, off by default, the TV's own "HomeTV Clock" (ClockDream). Upstream's `home_idle_clock_minutes`,
  `clock_glow` and `clock_wallpaper` stay unread.
- The nav's Wi-Fi icon shows arcs (on Ethernet too, 2026-10-31), crossed out when Android reaches no internet.
- `LastOpened` recording is off since F15 (the project lead, 2026-11-01): nothing read it.

### All Apps and Home
- All Apps is a grid of banner tiles by name (hold OK adds or removes from Home, moves in or out of TV Utilities, App
  info), TV Utilities last (F35). With no favourites the row shows one "Add apps" tile. Inputs tiles show the
  input's name only.

### Library, filters and sort
- Filters, the chosen list and the sort (F2, F4, the project lead, 2026-11-01) start cleared when the launcher process
  starts or the screen comes back from standby; each page keeps its own while it is left and clears after an hour
  (`LibraryRecall`).
- "Recently added to my lists" has no real dates: My list keeps its add order (a sync keeps TMDB's, oldest first) and a
  TMDB list TMDB's, so a list's last title counts as newest; All lists interleaves by chip order.
- The scope line counts My list and the shown TMDB lists and "All N services" the household's.

### Search
- A hardware keyboard types a–z, 0–9, space and `- : ' ’ & . ! ?` on the Search page only. Backspace is Back on every
  screen (decided 2026-10-29): on full results it brings the keyboard back with the query kept; only the on-screen ⌫
  deletes. Genre suggestions search the genre's name; no genre page.
- While typing, the preview builds only the first three rows. The prototype's fuzzy matching is kept for the mock
  catalogue (`Fuzzy`, `SearchIndex`).

### Title preview, person page and Pick for me
- More like this is TMDB's recommendations, else similar titles, eight at most.
- Person page (Rebecca's pick B, 2026-11-04): photo, job, birth, biography, and every credit, most voted first, without
  talk shows, news, reality TV and appearances as themselves.
- Rent and Buy open the store's app; nothing is paid or recorded in the launcher.
- Pick for me draws four random matches from the prototype's fixed fields. For TV Shows the length field reads the
  episodes' average runtime. A rental or purchase opens the preview first so its dialog asks before leaving.

### Back from an app
- The launcher stays as it was while its process lives (F26). When Android ends the process while another app plays
  (usual on a 1 GB TV), the activity restores the nav page, focus, Search's query and results, a library page's
  filters, chip and sort, and open previews. The state lasts an hour and standby clears it. A Home press starts Home
  again. The place also goes to disk (`SavedPlace`), is dropped once the launcher is in front, and is read only in the
  same boot, so after a force stop or crash Home starts on Home.

### Performance notes
- V1 Home measured 47 % janky frames; V2's on the TCL (2026-11-02) is 1 to 4 %, response p50 20 ms, cold start about
  900 ms. Measure with `tools/bench.py` ([perf-ab](workflows/perf-ab.md)). One PSS reading moves up to 15 MB with
  garbage collection, so `bench.py` reports the median of three (#96).
- Tried with no gain within noise (kept in case a `tools/trace.py` run shows the TV is fill-rate bound): fewer
  full-screen fills, and reading the apps and inputs after the first frame.
