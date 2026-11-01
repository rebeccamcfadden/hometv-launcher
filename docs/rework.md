# Launcher rework

The playbook for building the new launcher UI on a real TV. Read [AGENTS.md](../AGENTS.md) first, then this,
then the spec, [design_preview/README.md](../design_preview/README.md). Everything mocked or deferred is in
[BACKLOG.md](../BACKLOG.md).

| Question | Source of truth |
|---|---|
| How a screen behaves: flows, Back, focus, what OK does | The prototype in [`design_preview/`](../design_preview/) and its README |
| How it looks: spacing, sizes, colours | Figma (file `7gFFvfdGdXJVLZnj18Zah4`, read-only through the Figma MCP server) |
| What was decided and how we work | This file |
| What is not real yet | [BACKLOG.md](../BACKLOG.md) |

## Roles

- **Project lead** (the repo owner): decides scope and open questions, merges every PR, and says when the real
  TV is free.
- **Lead engineer** (a coding agent): plans each milestone and builds the foundation. Hands work to sub-agents,
  reviews what they produce, and checks it on the emulator before it goes to the project lead.
- **Sub-agents** take a milestone or part of one, with this file and the spec as context. Their work reaches
  the project lead only after the lead engineer has reviewed it.

If something isn't answered here, in the spec or in AGENTS.md, ask the project lead; don't assume. When a
question is settled, add it to [Decisions](#decisions).

## Decisions

Settled with the project lead on 2026-10-28.

| Area | Decision |
|---|---|
| The old launcher | Replaced outright, in M1: the new activity becomes Home, and the old Home UI and its feature code are deleted, along with tests that only covered them. |
| TV plumbing kept | Home and Input key accessibility helpers (`HomeKeyService`, `InputKeyService`), `SystemSettings` with the vendor fallback, the Inputs rules of `MainActivity.showInputs()`, the remote-key handling, the Home guard in the scripts. |
| Carried into V1 | The hold-OK tile menu (Move, Remove, App info), restyled. |
| Left for V2 | Themes and wallpapers, the clock screensaver, module cards and add-ons, Watch Next, festivals, sky and lunar calendars. Listed in [BACKLOG.md](../BACKLOG.md). |
| Toolkit | Plain Java and Android Views, with the existing Gradle build. No Kotlin, no Compose. |
| Typeface | Roboto, the system font; nothing bundled. Sizes and weights follow Figma, which uses Inter. Since M9 a theme may bring its own display and UI faces from Google Fonts, downloaded through Play services' font provider and kept on the TV; Roboto until they arrive and with the System font option. |
| APK size | V1 (M0, 2026-10-29) dropped upstream's 512 KB gate: `ci.yml` only reported the size. Since V2 Phase 1 (2026-10-30) CI fails a release APK over 1,048,576 bytes (1 MB), at the project lead's decision; each PR still reports its size. |
| Languages | English only. Launcher translations, RTL and the translated READMEs are dropped. |
| Catalogue | Mock in V1, ported from `design_preview/src/data`, behind an interface a TMDB source can replace. Real in V2 Phase 1 (`TmdbCatalog`) when the build has a TMDB token; without one, the mock catalogue still builds and works offline. |
| Availability and prices | Mock in V1. Real in V2 Phase 2 with a TMDB token (see the V2 Phase 2 row). |
| Watch on … | Opens the service's app. Opening the title itself is in the backlog. |
| Watchlists | My list, Seen, Favorites, recent searches and recently viewed are stored on the TV. Household and Friends are mock placeholders; they will come from the project lead's TMDB lists (backlog). In V2, see Accounts, 2026-10-30. |
| Services ("Included") | Worked out from the installed apps, with a `config.xml` override. A settings screen is in the backlog. |
| Region | SE. Searching SE and US together, with each result labelled, is in the backlog. |
| Weather | Real, from Open-Meteo (no key), at a latitude and longitude set at build time. |
| Clock and units | Follow the TV's settings: 12 or 24 hour, °C or °F. |
| `addons/household-search/` | Removed; it was an earlier attempt at search, replaced by this rework. |
| Secrets | The TMDB key, latitude and longitude go in a gitignored properties file read at build time, never in git. |
| Home app row | The household's ordered favourites (by default the installed streaming apps), reordered with hold-OK Move. |
| Wi-Fi dropdown | The header (network, internet, signal) is real. Saved and other networks open Wi-Fi settings, as the spec allows. |
| Test devices | An Android TV 9 (API 28) emulator at 1920×1080, then the project lead's TCL BeyondTV (Android TV 9). |
| Toolchain | Installed system-wide: JDK 17 with winget, the Android SDK in `%LOCALAPPDATA%\Android\Sdk`. |
| Branches | One feature branch per milestone, one PR into `main`; the project lead merges. |
| Check-ins | After every milestone: emulator screenshots and a short summary, approved before the next starts. |
| Staging the deletion | Split (decided 2026-10-28). M1 deletes the old Home's code, its translations and RTL layouts, and the tests that covered only those. M1b prunes `Config.java` and `config.xml`, the theme-visual tools and tests, the docs and the README's old launcher sections. Until then `Config` keeps every key, with its pure helpers (`Festival`, `RaceWeekend`, `Sun`, the `Wallpaper*` classes) dormant. |
| `themes/` and `addons/` | Kept in the repo, unused by the new launcher, until V2. The `config.json` push (`tools/theme.sh`, `ThemeProvider`) stays: `examples/*/config.json` carries per-TV plumbing keys through it. |
| Launcher settings | The old `LauncherSettingsActivity` stayed, without its grid, dock and wallpaper rows, until M9 replaced it with the split page of V2 design 05-settings A (`LauncherSettingsPage`), a page in Home's Shell. |
| Pull requests | Every PR asks the Copilot reviewer for a review. |
| Settings key over apps | `QuickSettingsActivity` draws the same dropdown as Home (`MenuPanel`), so the remote's Settings key looks the same everywhere. |
| Dropdowns and the D-pad | Decided after the first run on the TV (2026-10-29): a D-pad move past a dropdown's edge closes it and returns focus to its button; past the left, right or bottom edge it carries on one step, so → in the Wi-Fi menu lands on Inputs. Closing with Back alone felt clunky. The prototype and its README changed with it. |
| All Apps | A plain working grid of the installed apps, styled from the tokens: OK opens an app, hold-OK adds it to or removes it from Home. The spec's stub would leave apps that aren't on the Home row with no way to open, and an app removed with hold-OK with no way back. A proper design is in the backlog. (2026-10-29) |
| Build settings file | `launcher/tv.properties`, gitignored; `launcher/tv.properties.example` is the committed template, and the build reads the example while `tv.properties` is missing. It holds the weather location (New York by default), the temperature unit and, later, the TMDB key. (2026-10-29) Keys since M2: `latitude` and `longitude` in decimal degrees and `temperature_unit` (`C` or `F`), checked at build time and passed to the app as `BuildConfig` fields. Since V2 Phase 1: the optional `tmdb_api_key`, TMDB's v4 read access token, as `BuildConfig.TMDB_TOKEN` (empty without it, which builds the mock catalogue; the example has the key commented out, with no value). Since M9: the optional `theme_assets_url`, https only, where the themes' photos and drawings in `launcher/backdrops/` are downloaded from, as `BuildConfig.THEME_ASSETS_URL` (default: that folder on the fork's `main` through jsDelivr). Since V2 quick settings (2026-10-31): the optional `weather_city`, the default location's name for the quick settings' Weather row and Launcher settings' Weather pane, as `BuildConfig.WEATHER_CITY` (empty without it, and they say Default; the file is read as UTF-8, and a quote, a backslash or a control character fails the build). `temperature_unit` is optional since M9. |
| Temperature unit | °C unless `tv.properties` says `temperature_unit=F`. The TV's language (en-US) would have given °F in New York, and Android 9 has no temperature setting. The clock follows the TV's 12/24-hour setting. (2026-10-29) Since M9: `temperature_unit` wins when it is set; left out, the TV's region decides; Launcher settings › Weather can choose either on the TV (the project lead, 2026-10-31). |
| Order of the PRs | Stacked: each milestone's branch starts from the one before, and the PRs merge in order. M1b, the cleanup, runs after M8, once every screen's config keys are known. Real-TV checks wait for one batch session with the project lead. (2026-10-29) |
| Search, after review (2026-10-29) | Backspace is Back on every screen, Search included: on the full results it brings the keyboard back with the query kept, with the keyboard showing it goes Home, and only the on-screen ⌫ key deletes. Leaving Search for another tab (nav, Back, the remote's Home) clears the query, filter and full results; a title or person opened from it keeps them. Recent searches keep four, not five. Search has no guide line. The full results' six columns stay title-safe (96 px left, 98 px right); the start page's rows may still run off the right edge. The prototype and its README changed with it. |
| M1b scope | Decided 2026-10-29. **Theme docs frozen:** `themes/README.md`, `docs/switches.md` and `docs/sky.md` stay as a V2 reference, marked as not read by the current launcher; only the tests that compare them with `config.xml` go. **Workflows retired:** `docs/workflows/translation.md`, `theme.md`, `launcher-switch.md`, `addon.md` and `docs/modules.md` are deleted and dropped from AGENTS.md, with a note that V2 may take them from upstream ([Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)). **README:** the sections that describe the old launcher are rewritten for the new one. **`docs/card.jpg` and `docs/stats.png`:** redrawn with the tools, after fixing the language count. M1b runs now, before the real-TV run. |
| Open questions, settled (2026-10-29) | `subscribed_services` stays on `tests/test_switches.py`'s "screenshots wanted" list until a TV screenshot exists. `.github/maintainer/MAINTAINER.md` allows the INTERNET permission for the fork's network features and pauses the 512 KB APK gate; `CONTRIBUTING.md` allows limited network access; `PROMPT.md` describes the new UI. The APK stays at about 1.6 MB for now, to be measured again once the mock artwork goes (BACKLOG.md, From M1). `LastOpened` stays, unread, with a BACKLOG entry for a future use. |
| V2 plan | Decided 2026-10-30. Phases in order: 0 V1 wrap-up (finished first), 1 real catalogue (TMDB), 2 real availability, 3 accounts, 4 on-TV settings; work in a phase may start early where it doesn't wait on another. **Prices:** real rent and buy prices are a priority, from a second source alongside TMDB's providers (researched, see [V2](#v2)). **TMDB:** each builder uses their own TMDB API read access token (v4, sent as a `Bearer` header, not a v3 `api_key` query parameter), put in `launcher/tv.properties` as `tmdb_api_key`. That file is gitignored: never paste a token into a commit, issue or PR. Without it the launcher keeps the mock catalogue. **Designs:** screens without a design are drafted in Figma as a few options, the project lead picks, then they go into `design_preview/` and the launcher. |
| Accounts, 2026-10-30 | Signing in to TMDB is optional; signed out, everything stays on the TV. **Sign-in:** TMDB's v4 flow by QR code, turned into a v3 session for the watchlist and favourite writes. **Lists:** My list follows the TMDB watchlist, Favorites the favorites, and Seen a private TMDB list named "Seen", which the TV makes when the account has none (`tmdb_seen_list`, `create_seen_list`); all both ways. **First sign-in:** the TV takes the account's lists and adds nothing of its own, mock leftovers or not; the same account signing in again carries on with its queue; signing out keeps the lists on the TV. **Household and Friends:** chosen on the TV from the account's TMDB lists (each list Household, Friends or Not used), for now on the temporary account page's Choose lists until the new Launcher settings design has the pane; `tmdb_household_list` and `tmdb_friends_list` in `config.xml` override the choice. **Fresh install:** My list, Seen, Favorites, recent searches and recently viewed all start empty; a TV with saved lists keeps them. |
| V2 Phase 2 defaults | Set by the lead engineer on 2026-10-30, until the project lead answers. TMDB's watch providers for SE replace the mock offers of every TMDB title; the mock catalogue keeps its own. Prices: Apple's iTunes (keyless, by Wikidata's iTunes ids) now; Watchmode and Streaming Availability only when their key is in `tv.properties` (superseded by the price policy row). Stores without a known price still show, as "Rent" or "Buy" with no amount, and their dialog opens the app. Names follow current branding: "HBO Max"; Apple's subscription "Apple TV" and its store "Apple TV Store". Where to watch credits its sources in an 18 px caption ("Availability: JustWatch · Prices: Apple"); Launcher settings has the full notices. New services for Sweden get ServiceIds; "Included" follows the installed apps or `subscribed_services` as before. Watch opens the title through a link limited to the service's package, else the app. No lookups while Home is hidden. |
| Price policy | Decided by the project lead on 2026-10-30, with real Watchmode and Movie of the Night keys in `tv.properties` (`watchmode_api_key`, `movie_of_the_night_api_key`). No paid plans, ever. Order: Watchmode (most Swedish stores), Movie of the Night only when Watchmode has no price or its budget is spent, Apple's iTunes when neither priced Apple; with a budget spent, Apple only and Rent or Buy without an amount elsewhere. Prices kept 7 days; the household's lists (My list, Household, Friends) kept fresh in the background once a TMDB account is signed in (not before: a fresh TV spends nothing on the mock lists), other titles priced when their preview opens, grids and search never. Movie of the Night's change feed read at most once a day while Home shows, to mark kept titles stale. Budgets follow the servers' quota headers: Watchmode stops at 2,300 credits, Movie of the Night at 900 requests, spread over the days to the reset. |
| First run | Decided by the project lead on 2026-10-31. The checklist shows by itself once, on the first start after a fresh install, never after an update; config.xml's `first_run_checklist` is an opt-out (on by default). A row is done once its pane was opened, and the done rows are kept across restarts. Streaming services and Stores show the ticked names ("Netflix, Prime Video +6"). No "Set up again" row. Detail in [BACKLOG.md](../BACKLOG.md#first-run). |
| Lists by name, 2026-10-31 | Replaces the Household and Friends roles of Accounts, 2026-10-30 (prototype PR #28). The lists are the account's own: Account → Choose lists has a Show / Hidden switch per TMDB list, any number can show, and each shown list is a chip in Movies, TV Shows and Pick for me after All lists and My list, under its TMDB name, in the account's order; at most six get a chip (the project lead, 2026-10-31), and a shown list past six stays in All lists; the chip row scrolls with focus. Before any sign-in, with nothing set, the mock Household and Friends stay; once the TV has an account, only the shown lists (after a sign-out, those chosen on the TV are hidden until the next sign-in). `tmdb_shown_lists` in `config.xml` (numbers, links or names, in its order) overrides the choice; the older `tmdb_household_list` and `tmdb_friends_list` are the shown lists while it is empty, and a TV that chose Household and Friends starts with those two shown. Detail in [BACKLOG.md](../BACKLOG.md#shown-tmdb-lists). |
| Phone notes, 2026-10-31 | Prototype PR #29. Each person keeps a watch list in a note on their iPhone (Apple Notes or Google Keep) and shares it to a **Send to TV** Shortcut, which posts it over the home Wi-Fi to `http://<tv>:8765/notes` with a **pairing code** shown in Launcher settings → Phone notes (a new section after Account; New code asks first), and a QR beside it that opens a setup page on the iPhone (`GET /setup`; the code stays in the link's fragment) with **Get the shortcut** (the household's shared iCloud link, `note_shortcut_url`) and **Pair this phone**, which hands the shortcut the address and code to keep, so a new code means pairing again rather than editing the shortcut (2026-11-01). **Off by default:** `note_inbox` in `config.xml` (or a pushed `config.json`) is the default and the household's switch wins; while off the TV doesn't listen. The note's new lines are looked up on TMDB and added to the signed-in account's list of the note's name (case and spaces at the ends aside, as `tmdb_shown_lists` matches names), or a **private list made by that name**, which then shows as a chip. **Add-only:** a line deleted on the phone takes nothing off, and a title taken off on the TV isn't put back; a **ticked line goes to Seen** and comes off the list. Signed out or offline, the latest note per list waits on the TV. An **import pill** at the bottom of every launcher screen shows the progress and the result for four seconds; lines that matched nothing are listed in the pane. Plain HTTP on the home network only: the code lets a phone add titles and nothing more. Detail in [BACKLOG.md](../BACKLOG.md#phone-notes) and [phone-notes.md](phone-notes.md). |
| Family install, 2026-11-01 | The project lead's family and friends (Windows laptops, the US, not technical) install the launcher themselves with a script while she helps on a call; no AI agent. **One public APK with no secrets** on GitHub Releases, signed with a release key and uploaded from her PC by a new PowerShell release script in `tools/` (no CI release workflow); each household's settings are pushed separately as `household.json`. **Their own keys:** each household makes its own TMDB, Watchmode and Movie of the Night keys with the installer's help, so no free quota is shared. **Updates in the app** through Android's install prompt. **Debloat:** an exact `examples/` list when one matches the model the installer reads, minus whatever the household ticks in PROMPT.md's checklist question; otherwise only the stock launcher, plus a report she turns into a list with her agent. **Safety as PROMPT.md:** a backup and the restore file before any change; on a CVTE board the stock launcher stays enabled unless that exact board's list disables it, with the new Home set, one reboot, and Home and ADB confirmed before any key helper. **Order:** package rename, settings at run time, US region, signed releases, the installer, in-app updates, debloat from the report. **Samsung (Tizen):** a separate repository, starting with a TizenBrew spike that shares its logic with `design_preview/`. Detail in [BACKLOG.md](../BACKLOG.md#family-install). |

## Open questions

Raise these when they become relevant; don't settle them alone.

- **The remote's Settings key** on the TCL opens TCL's own settings panel, not the launcher's quick-settings dropdown
  (checked on the TV, 2026-10-30). The launcher can take it over with the Home key helper (`HOME_KEY_SERVICE=1` and
  `settings_key_scancodes`, read from `getevent -lt` on the TV), at the cost the PROMPT.md notes describe for key
  helpers. Keep TCL's panel, or map the key?

## Real-TV run

One session with the project lead on the TCL BeyondTV (Android TV 9) once the stack is merged or built from its top.
Nothing here starts before the project lead says the TV is free, and the project lead tries the launcher by hand
before any scripted check.

**Before:**

1. Back up into `backup/<date>-pre-v1/`: the package list, the disabled list, `getprop`, settings and a
   `ROLLBACK.txt` naming the Home before the install (CleanYourTV-UI, `io.cleanyourtv.ui`, stays enabled as the
   fallback).
2. Build from the top of the stack and install with `launcher\build-and-install.ps1 -TvHost <tv ip> -StockLauncher
   none`. Confirm Home with `dumpsys activity activities | grep mFocusedApp`.
3. Push nothing in `config.json` unless the run needs it, and remove it afterwards.

**By hand, with the real remote:**

| Area | Check |
|---|---|
| Home | Clock follows the TV's 12/24-hour setting; weather for the `tv.properties` location in °C; the app row shows installed apps; hold-OK menu moves and removes a tile; Back and Backspace put a moved tile down |
| Home, scrolled | Inputs: Live TV and each HDMI open their source, with CEC devices named; Shortcuts open All Apps, Launcher settings and TV Settings; hourly weather |
| Keys | Home from inside an app returns to Home; Back on Home stays on Home; the Input key, if a helper is on; the Settings key opens the quick-settings dropdown only where it reaches the launcher (`settings_key_panel`). On the TCL it may still go to TCL's handler: note what it does (Open questions) rather than fail it |
| Nav and dropdowns | The Wi-Fi and Inputs dropdowns open on OK and close on a D-pad move past their edge; the Settings icon opens the TV's settings, with no dropdown |
| Movies and TV Shows | Chips, the Filters drawer, both empty states; the grid scrolls smoothly with artwork |
| Title preview | Every tab; Watch opens an installed service (Netflix, Prime Video, YouTube); the rent/buy dialog opens its app; Trailer opens YouTube; My list and Seen update the library |
| Search | On-screen keyboard; typo tolerance; a Bluetooth or remote keyboard types, and Backspace goes back (full results → keyboard → Home), if one is at hand; leaving Search for another tab clears it |
| Pick for me | Setup, Find 4 picks, Reroll, Adjust filters, Title details, Open in |
| Offline | Wi-Fi off: Home, the clock and the cached weather stay; every page still works; the Wi-Fi icon says offline |

**Scripted, after the project lead's go:**

- `tools/bench.py --keys 36 --series 3` with `TV_HOST` set, for key latency, jank and PSS. Its cold start is `am start -W`'s
  WaitTime, while the M1 baseline (2.8 s) and the old launcher's 2.7 s are TotalTime: compare like with like, by also
  running `am start -W` after a force stop and reading TotalTime, or by benchmarking the old build the same way.
  Also PSS after visiting every page, with artwork loaded (M1: 11 MB).
- `tools/tv.py --screenshot shots/<screen>.png` for each screen, a different file each time (for example `shots/home.png`, `shots/movies.png`), to replace the prototype images in README.md. Blur or crop names,
  cities and Wi-Fi names first.
- A photo of the new Home on the TV, to replace `docs/new-home.jpg` (the README card's "after", which still shows
  the old Home). Then redraw the card with `tools/readme_card.py Fredoka.ttf docs/card.jpg`, after adjusting its
  crop and its blur box for the Wi-Fi name to the new photo.

**After:** close ADB as AGENTS.md requires: USB and network debugging off, authorisations revoked, a reboot, then `tools/adb-closed.py <tv ip>` must report the port closed. Record the results and any new decisions here.

### Results (2026-10-30, `main` at 0212b6a4)

The project lead tried every screen by hand on the evening of 2026-10-29 ("it looks good") and merged V1. The
scripted part ran the next night on the same build:

| Measure | V1 | Before |
|---|---|---|
| Cold start, `am start -W` TotalTime after a force stop (3 runs) | 1.9–2.1 s | 2.8 s (M1), 2.7 s (old launcher) |
| Cold start, `tools/bench.py` WaitTime p50 / p95 | 609 / 626 ms | |
| Key response p50 / p95 | 21 / 31 ms | |
| Movement p50 (key to the end of the focus animation) | 197 ms | |
| Janky frames while pressing keys on Home | 47 % (337 of 712) | a V2 follow-up |
| Return to Home p50 / p95 | 71 / 235 ms | |
| Idle CPU, whole TV / launcher | 2.9 % / 0.1 % | |
| PSS after a cold start / after visiting every page | 22 MB / 41 MB | 11 MB (M1, no artwork) |

- **Screenshots:** `shots/v1/` (gitignored); Home, Movies and Search are now in README.md and Home is the README
  card's "after".
- **Settings key:** TCL's own settings panel (Open questions).
- **Not checked on the TV:** Wi-Fi off (it takes ADB with it; checked on the emulator in the integration pass).
- **ADB:** the project lead chose to close it. The TV was off before the port could be checked, so
  `tools/adb-closed.py <tv ip>` still needs a run with the TV on and debugging off.

## V2

The plan from the V2 plan decision above. Each phase follows the same workflow as V1: one PR per topic, stacked,
emulator first, the real TV in a batch with the project lead.

| Phase | Scope | Waits on |
|---|---|---|
| 0 · V1 wrap-up | Real-TV results and docs; follow-ups from the integration pass and the TV (nav focus on the page's own tab, the tile menu over the greeting, screen-reader state, jank on Home) | Nothing |
| 1 · Real catalogue | TMDB titles, artwork, cast, episodes, More like this, trailers and search, cached on disk; the mock art leaves the APK, then the size gate is reconsidered | The TMDB token (there) |
| 2 · Real availability | TMDB watch providers for SE, deep links through Wikidata, the household's services, real rent and buy prices | The price source (researched), the services picker design |
| 3 · Accounts | TMDB sign-in by QR, My list, Seen and Favorites synced, Household and Friends from TMDB lists, start empty instead of the mock seed | The first-run and sign-in designs |
| 4 · On-TV settings | Weather city and unit, the new Launcher settings page, switching between saved Wi-Fi networks | The Launcher settings and picker designs |
| 5 · Family install | Package rename, settings pushed at run time, a US region, signed releases, a Windows installer for non-technical households, in-app updates, debloat from a report ([BACKLOG.md](../BACKLOG.md#family-install)) | Nothing; the rename goes first |
| Later | Two regions (SE and US) searched together, Music, Explore pages, voice search, the screensaver, Retro themes and moving details, add-ons, Watch Next, more languages | A design or a decision |
## Milestones

| | Scope | Done when |
|---|---|---|
| M0 · Tooling | JDK 17, SDK platform 34, build-tools 34.0.0, the emulator and the API 28 TV image; the CI size gate changed to report | The current launcher builds and runs as Home on the emulator, and this file's commands are confirmed |
| M1 · Foundation | New Home activity, made Home; the old Home UI, its feature code and the tests that only covered it deleted; design tokens (colours, type, radii, focus); nav bar; screen stack with page, popup and dialog layers; Back rules; focus memory per row and grid; page transition; the three top-bar dropdowns; Settings button | The new launcher is Home on the emulator; every nav item and dropdown works with the D-pad, Back follows the spec's four rules, and pages are placeholders |
| M1b · V1 cleanup | `Config.java` and `config.xml` cut to the keys the new launcher reads, with their dormant helpers; theme-visual tools and tests; the docs (`docs/switches.md`, `docs/sky.md`, `docs/workflows/translation.md`, the module-card spec) and the README's old launcher sections; redraw `docs/card.jpg` and `docs/stats.png`, which still say 25 languages | Nothing in the repo describes or tests the old Home's UI |
| M2 · Home | Greeting, date, clock, weather; the app row with its hold-OK menu; the scrolled state with Inputs, Shortcuts and hourly weather | Home matches the prototype, with real apps, inputs and weather, and works offline |
| M3 · Data layer | Household store; catalogue interface with the mock source; offer rules; image loading and cache | Unit tests cover the store, offers and filters |
| M4 · Movies and TV Shows | Header, list chips, grid, Filters drawer, empty states | Filters update the grid live; both empty states reachable |
| M5 · Title preview | Popup over the page, actions, Overview, Episodes, Where to watch, Cast & crew, More like this; rent/buy dialog; person stub | Every tab and toggle works; Watch opens the service's app |
| M6 · Search | Bar with ghost text, 6×6 keyboard, recents and suggestions, results preview, full results, no matches, typing from a hardware keyboard | Fuzzy matching ported with its tests; focus and Back match the prototype |
| M7 · Pick for me | Setup and results | Reroll, Adjust filters and both buttons on the results bar work |
| M8 · Finish | Music, Launcher settings, All Apps and Weather stubs; docs; the real TV | A run on the TCL BeyondTV with the project lead |
| M9 · Themes and Launcher settings | The split Launcher settings page with every pane (Account, Streaming services, Stores, Weather, Home row apps, Theme, Motion, Open on start, Default Home, TV settings, About); themes: Classic (Midnight, Daylight, OLED Black, High Contrast, Cinema, Landscapes) and Seasonal (Nature, Vector art, 8-bit) following the date; fonts from Google Fonts; backdrops downloaded from `launcher/backdrops/` | Every theme and period on the emulator, Midnight unchanged; then the TCL |

## Toolchain and emulator

Confirmed on Windows 11 in M0 (2026-10-28).

```powershell
winget install --id Microsoft.OpenJDK.17 --exact   # asks for administrator rights once
# Android command-line tools: commandlinetools-win-*_latest.zip from https://dl.google.com/android/repository/,
# unpacked to %LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest. Its sdkmanager now hands over to the new
# `android` CLI, which names packages with / where sdkmanager used ;
$bin = "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin"
& "$bin\android.exe" sdk install platform-tools emulator platforms/android-34 build-tools/34.0.0 system-images/android-28/android-tv/x86
# The new CLI can't choose a system image for a virtual device; avdmanager still can. --% keeps the ; intact.
& "$bin\avdmanager.bat" --% create avd -n tv28 -k "system-images;android-28;android-tv;x86" -d tv_1080p
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd tv28 -no-snapshot-save -no-boot-anim
```

The emulator runs with WHPX acceleration and boots in about a minute: Android 9 (API 28), 1920×1080. It has
no HDMI or tuner inputs, so the Inputs rows can only be fully checked on the real TV.

Build and test, from `launcher/`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

The emulator image's stock launcher (`com.google.android.tvlauncher`) outranks a sideloaded one, so
`set-home-activity` alone doesn't move Home on Android 9. On the emulator, follow the order the install script
uses: start the new launcher, disable the stock one, then set the preference and check where Home resolves.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\release\app-release.apk
& $adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity
& $adb shell pm disable-user --user 0 com.google.android.tvlauncher
& $adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
& $adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN
```

Take screenshots with `adb shell screencap -p /sdcard/x.png` and then `adb pull`. PowerShell's `>` breaks binary
output, so `adb exec-out screencap -p > x.png` gives an unreadable file.

Testing on the emulator, learned in M1:

- Keys from `adb shell input keyevent` skip the accessibility key filter, so they never reach `HomeKeyService` or
  `InputKeyService` (AGENTS.md: an injected key is not proof). The emulator's console (`adb emu event send`)
  produced no key events either. To see a screen those helpers open, `adb root` (the image allows it) and then
  `adb shell am start -n com.gathin.tvhome/.QuickSettingsActivity`; the helper's own key handling waits for the
  real remote.
- A test `config.json` pushed to `/sdcard/Android/data/com.gathin.tvhome/files/` switches config keys without a
  rebuild, as `tools/theme.sh` does; `am force-stop com.gathin.tvhome` and start Home again to read it. For example
  `{"live_tv_package":"com.lego.android.tvleanback"}` gives the Inputs icon a Live TV row on an emulator with no
  inputs, and `{"settings_key_keycodes":[131],"settings_key_panel":true}` makes F1 the Settings key.
- The Wi-Fi name needs `pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION`, as the install script
  does.

Baseline before any rework code: the current launcher builds in about 90 s with zero lint errors, the release
APK is 509,545 B, and it runs as Home on the emulator. After M1 the release APK is 163,624 B and Home uses about
25 MB (PSS) on the emulator.

The real TV is a TCL BeyondTV on Android TV 9. Read its folders in `examples/` (`tcl-beyondtv` and the ones next
to it) and the TV safety rules in AGENTS.md before touching it. Install only after the project lead says the TV
is free, with `launcher/build-and-install.sh`, which keeps the Home guard.

## Real or mock

Each element in the spec is either wired to a real source or uses mock data, and every mock or fallback has an
entry in [BACKLOG.md](../BACKLOG.md): what V1 does, what the real version needs, and any notes. A PR that adds a
mock adds its entry; a PR that makes one real removes the entry.

## Definition of done

A PR is ready for the project lead when:

- Behaviour matches the prototype (Back, first focus, focus memory, what OK does) and the look matches Figma, with
  any difference listed in the PR and the reason for it.
- Every focusable item has a visible focused state; text and focus stay inside the title-safe area (96 px at the
  sides, 54 px top and bottom); no text below 18 px at 1080p.
- It works from the D-pad alone, from every edge, and Home still works with the network off.
- `./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease` passes with zero lint errors, and the
  APK size is in the PR.
- New logic has unit tests, or the PR says why it needs none.
- BACKLOG.md is up to date.
- No secrets or identifiers in code, commits or screenshots.
- Emulator screenshots or a video are in the PR. A real-TV run happens only when the project lead has said the TV
  is free.

## Reference: cleanyourtv-ui

[matheus-caldeira/cleanyourtv-ui](https://github.com/matheus-caldeira/cleanyourtv-ui) (MIT) already does in
plain Java much of what the backlog needs, with no extra libraries. Its `launcher-ui/` has `Tmdb.java` (TMDB with
a disk cache), `Images.java` (memory and disk image cache), `Providers.java` (TMDB services to TV apps),
`DeepLinks.java` (Wikidata ids to title links), `Qr.java` and `TmdbLoginActivity.java` (TMDB sign-in by QR).
Use it as inspiration, not a guide. Any code adapted from it keeps its notice at the top of the file.
