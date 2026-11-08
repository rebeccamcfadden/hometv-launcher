# Backlog

Open work for the new launcher: where it still uses mock data or a fallback, what is left to do, open questions,
ideas not built yet, and what isn't designed. A PR that adds a mock adds its entry here; a PR that makes one real
removes it. Built features, with the decisions behind them, are in [docs/how-it-works.md](docs/how-it-works.md). How
this fits the milestones: [docs/rework.md](docs/rework.md).

## Data and accounts

### Real catalogue
Built: [docs/how-it-works.md](docs/how-it-works.md#real-catalogue).
- **Open question:** use Sweden's ratings (Btl, 7, 11, 15) where TMDB has them, and how they map onto "Up to PG-13"
  and "Up to R". Today it is the US scale only.
- **Still to do:** TMDB's language following the TV's (English only now).
- **Not built here:** Settings offline (the Account, Sync and Weather panes), and the Where to watch credit's "Saved
  today 19:52, prices may be out of date".
- **Not designed:** Search and Home's weather while loading. More like this has no error state, and an artwork never
  fetched keeps the raised placeholder offline.
- **Check on a signed-in TV:** Search's extra pages (unit-tested only; the emulator has no TMDB sign-in).

### Streaming availability and prices
Built: [docs/how-it-works.md](docs/how-it-works.md#streaming-availability-and-prices).
- **Mock:** the mock catalogue (`-PmockCatalogue=true`) still has made-up services, rent and buy prices.
- **Store ticks (mock):** Launcher settings can't tick stores yet for the price logic, so `Data.stores()` returns null
  and every store counts. When the pane stores the ticks, `Data.stores()` returns them, and the Overview's "Rent from …
  elsewhere" and Pick for me should go through `PriceCards.ticked` too.
- **Still missing:** Viaplay's store has no price from any source. Shows are rarely priced (Apple's TV search finds no
  Swedish seasons). Prices are a week old at most, not live. Titles from recommendations and searches show no
  availability on their cards until their details load.

### Two regions
Built: [docs/how-it-works.md](docs/how-it-works.md#two-regions) (one region at a time).
- **Wanted:** search SE and US together, since the household also watches through a VPN, and label each result with its
  region.

### TMDB sign-in and watchlist sync
Built: [docs/how-it-works.md](docs/how-it-works.md#tmdb-sign-in-and-watchlist-sync).
- **Still to come:** the sign-in and first-run designs (the Account page is temporary), and whether a first run asks to
  sign in.
- **Not seen yet:** a real approval (the project lead approves one on the TV), so the session, the account's name,
  making the Seen list, the import bar and the sync against a real account are unchecked. The emulator checked the
  code and QR, TMDB's "not approved yet" answer, polling stopping while hidden, offline, expiry and New code.

### Shown TMDB lists
Built: [docs/how-it-works.md](docs/how-it-works.md#shown-tmdb-lists).
- **Mock:** the mock Household and Friends lists (on TMDB ids) for a TV that never had an account and sets no list.
- **Still to come:** whether the Choose lists rows show each list's last update, as the prototype's do.

### Phone notes
Built: [docs/how-it-works.md](docs/how-it-works.md#phone-notes); set up a phone with [docs/phone-notes.md](docs/phone-notes.md).
- **Still to come:** a Figma design for the pane and the picker (none yet); whether a send whose list is hidden in
  Choose lists should show it again (it does now).
- **Not verified yet:** a second iPhone's pairing, an Android phone on a real device, and the TV's address changing
  (the Shortcut keeps the one it was given).

### Phone remote without the warning
- **Planned, not built** (F44; the project lead, 2026-11-03: plan it, build later). Each phone sees the browser's
  "connection not private" warning once, because the TV makes its own certificate
  ([docs/phone-remote.md](docs/phone-remote.md)). The plan, the Plex way: a project domain, a name per TV pointing at
  its home address, and a real certificate per TV from a small service the project runs, key kept on the TV, served on
  a second port. Off by default, with the plain address always offered and the old certificate as the fallback. About
  110 to 160 USD a year and 8 to 10 builder-days; the service would learn each TV's id and house address. Details and
  decisions to take first: [docs/specs/phone-remote-certificates.md](docs/specs/phone-remote-certificates.md).

### Phone remote, V2
- **Back burner** (the project lead, 2026-11-04 19:19: "good enough for V1"). The TV is slow for a moment after Enter on
  the phone's keyboard while Search shows its full results. The phone sends Enter right after the last text, so the time
  is the TV's. Measure first (`tools/trace.py` around `/api/enter`) before changing anything.

### Trailers
Built: [docs/how-it-works.md](docs/how-it-works.md#trailers).
- **Check on a TV:** the trailer video opens (the emulator has no YouTube, so only the "not installed" toast was seen).
- **Open (F32):** Back from a trailer walks YouTube's own pages first. No flag the launcher can set changes it.

### Title deep links
Built: [docs/how-it-works.md](docs/how-it-works.md#title-deep-links).
- **Mock or missing:** episodes open the show (Wikidata has no episode ids; Netflix's `/watch/<show id>` is a recheck).
  TV4 Play, Viaplay, SkyShowtime and the Swedish stores have no Wikidata property, and The Roku Channel only a channel
  id, so they open on their home screen. With only an old HBO Max id the app opens on its home screen.
- **Recheck pending:** the warm case (the app already running) and HBO Max's current ids; SVT Play's declared link
  filters; Disney+ (signed out on the TCL).
- **Wanted (US):** a TV with each of Hulu, Peacock, Paramount+, Tubi and Fandango at Home checks that the link opens
  the title (checked on paper only; an app that refuses the link opens its home screen).

### Wi-Fi networks
Built: [docs/how-it-works.md](docs/how-it-works.md#wi-fi-networks).
- **Waits for design:** joining a new network (a scan and a password) from the dropdown; Other networks opens Wi-Fi
  settings for that. A failed switch isn't reported back: the header shows the network Android fell back to the next
  time the dropdown opens.

## Left for V2

From the old launcher; not in V1 by decision.

- Themes and wallpapers pushed as `config.json`. (M9 brought themes back as an on-TV choice in Launcher settings › Theme;
  see [Themes](#themes).)
- Module cards: the add-ons in `addons/` run as their own apps; see [Add-on cards on Home](#add-on-cards-on-home).
- The Watch Next row.
- Festivals, the sky and the lunar calendars.
- The old Home's on-screen extras: the idle clock (`home_idle_clock_minutes`), the stats line (`show_stats`), the
  VPN badge on the network pill (`vpn_app` now shows its app and state in the Wi-Fi dropdown instead), the app
  library dialog, reorder mode and dock editing. Their `config.xml` keys went in M1b; the theme keys are described,
  frozen, in `themes/README.md`.

(F36, 2026-11-02: the options that came back, `consume_volume_keys`, `gamepad_passthrough` and `screensaver`, are in
[docs/how-it-works.md](docs/how-it-works.md#launcher-settings-page).)

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
Built: [docs/how-it-works.md](docs/how-it-works.md#themes).
- **Retro themes** (Retro CRT, Synthwave '84, Groovy '70s, 8-bit Arcade): designed in Figma, with their tokens and the
  scanline overlay ready; not in the picker yet.
- **Not shipped:** the Vector scenes alternative to Vector art, and the old Seasonal & festive themes (Figma pages kept).
- **Southern hemisphere:** the Seasonal periods follow the northern calendar everywhere.
- **Open, daylight cycle:** a TV bench of a phase change. Stars or clouds that move belong to Animated details, later.
- **Open, Seasonal picture:** decoding it after the first frame would win back about 90 ms of Home's first draw, with
  the picture arriving about 0.15 s later; kept as is on purpose (the project lead).
- **Open, Animated details:** a re-bench at 12 fps (`tools/bench.py --idle-animation 30`); petals and bats, wind or
  lightning; the backdrop window's `buffer_age` on the TCL.
- **Open, Performance:** the low-RAM start's cold start was about 100 ms slower in the TCL runs, not explained (worth a
  trace).

## From M1
- **Not built:** Launcher settings › About's Open-source licences and Clear cache.
- **Network icon:** the old pill could tint by state (`wifi_status_color`, `wifi_status_warnings`); not designed for the
  new nav.
- **Last opened:** `LastOpened` recording is off (F15). Wanted: a use for it, for example ordering All Apps or the Home
  row by recent use, once designed; then call `LastOpened.record` where they open an app.

## From M2
- **All Apps:** wanted a proper design, which may add search, hiding apps and sorting.
- **Weather page:** OK on the weather strip opens a "Not designed yet" stub. Wanted: the hourly and 7-day forecast.
- **Weather icons:** only sun, moon and cloud, so rain, snow, fog and storms all show the cloud. Wanted: icons for
  those, in Figma first.
- **Empty app row:** the single "Add apps" tile is not designed.
- **Inputs on Home:** the tiles don't show "Connected" or "No device" (no room in the design). On the emulator only a
  Live TV row was checked; HDMI and AV rows wait for the TV.
- **Tile menu:** Move, Remove from Home and App info are not designed (the spec lists hold-OK menus as open).

## From M4
- **Empty list note:** says to "import a watchlist in Settings", which doesn't exist yet (see First run and setup under
  Not designed yet).
- **Sort's "Recently added to my lists":** no list says when a title was added. Wanted: a date added per item, if the
  TMDB API gives one.
- **Genre options:** with the mock catalogue they are its genres, as in V1; TV Shows leave out Food, Musical, Western
  and Horror, as the prototype does.
- **Long lists:** the grid builds a card for every title at once, and again each time a title fills in. A list of
  hundreds needs a recycled grid or paging, with artwork decoded as cards come into view.
- **Filters' state:** not stored across a process the TV stops; it starts again cleared, as a boot does.

## From M5
- **Episodes** open the show's best offer, not that episode (see Title deep links).
- **Rent/buy dialog:** it doesn't know whether the rental happened.
- **Person page:** not yet what they did on each title shown on its card (the roles are parsed), and TV episode credits.
  The mock catalogue has no biographies.
- **Watched episodes:** worked out from the mock next episode; nothing records what was watched. Mark seen marks the
  whole title only.

## From M6
- **Genre suggestions:** there is no genre page or genre filter chip.
- **Typing:** accented letters, the system's on-screen keyboard (IME) and voice input are not used.
- **Results preview:** about 15 ms a key press, 30–55 ms to widen "a" (139 results) on the emulator. Check it on the TV.
- **No-matches screenshot:** that state was checked against `search.ts`, not a picture.

## From M7
- **Pick for me's fields:** the six fields and options are the prototype's fixed lists, not built from the catalogue's
  genres or the household's habits. Wanted: options that follow the real catalogue, and a design for picking any genre.
- **Random, not ranked:** nothing weighs score, recency or what the household watches, and the choices aren't
  remembered between visits.

## From V1 follow-ups
- **Back from an app (F26):** not restored: a person page, Pick for me, and the preview's tab and season.
- **Jank:** ideas that showed no gain within noise (fewer full-screen fills, reading apps and inputs after the first
  frame; return to Home p50 79 against 97 ms needs more runs) wait for a `tools/trace.py` run showing the TV is
  fill-rate bound.
- **Accessibility:** the prototype has no ARIA roles or states to match the launcher's reports.
- **Quick settings, check:** which activity is the project lead's TCL's own Inputs page, to add as a vendor target.

## Family install

Built: [docs/how-it-works.md](docs/how-it-works.md#family-install) (planned with the project lead, 2026-11-01;
[docs/rework.md](docs/rework.md), Family install).
- **Step 4, the installer:** a run of `tools/family-install.ps1` on a real TV is still to come.
- **Step 5, in-app updates:** the TV check, once main's versionCode moves on for real (the project lead, 2026-11-02:
  nothing irreversible on her TV before then). A Home app replacing itself must come back cleanly (the #1101-style
  restart); on some OEM builds the "install unknown apps" grant can fail or be undone.
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

## Ideas from AeroTizen

F48 (the project lead, 2026-11-04): three ideas from [AeroTizen](https://github.com/helalaou/AeroTizen), a home
screen for Samsung TVs (MIT), for later; no timing. Only the ideas come from it, not its code. Each needs a design
first and is off by default. Costs are rough: **S** about a day or less, **M** a few days.

### Cards per row, square or wide

- **Idea:** 3, 4 or 5 apps a row, and square or wide app cards.
- **Now:** Home's app row has 7 tiles (`HomePage.APP_COLUMNS`) and All Apps 6 a row (`AllAppsPage.COLUMNS`), both
  fixed in code; `config.xml`'s old `columns` is no longer read. Cards are always wide banners.
- **Wanted:** a setting under Theme or Home row apps for the number a row and the card shape. Square cards need
  the app's icon when it has no square art.
- **Cost:** S.

### Rename inputs, hide empty ports

- **Idea:** each input can get the household's own name, and HDMI ports with nothing plugged in can be hidden.
- **Now:** the Inputs dropdown and Home's Inputs row list the HDMI ports under the TV's names, with CEC devices
  named on their port ([docs/launcher.md](docs/launcher.md)). A name given to a port or device in the TV's own
  Settings › Inputs is already shown (`TvInputs`, `loadCustomLabel`), and inputs hidden there are left out.
- **Wanted:** rename an input from the launcher's own menu, and a switch to hide ports where nothing is connected.
  Off until set. Its design decides which name wins when the TV's Settings and the launcher both have one.
- **Cost:** S-M. `TvInputs` already reads whether a port is connected (`getInputState`), but not every set reports
  it truthfully: check on the real TV first.

### Fireplace tile

- **Idea:** a tile that plays a fireplace video, with sound and its own volume.
- **Wanted:** a tile in All Apps (or Home's row if the household adds it) that opens a full-screen fireplace;
  Back stops it. Nothing plays until it is opened, so it keeps the rule against autoplaying video on 1 GB TVs.
- **Cost:** S-M. Needs a video with a licence that allows it, small enough for the APK limit, or fetched once and
  cached.

## Not designed yet

From the spec's open questions ([design_preview/README.md](design_preview/README.md)). Each needs a design before
it is built.

- Search and Home's weather while loading (06-states A designs offline and errors, not these).
- Home row apps (with reordering) has no design; the prototype's is a stand-in.
- Removing a recent search.
- Launcher settings › Features (F14, 2026-11-01): built without a Figma frame, for the project lead's approval.
- Voice search.
- Progress on started titles.
- The Sort chip on Movies and TV Shows (F4): built as a small button like Filters, with a dropdown; no Figma frame.
- The Explore versions of Movies and TV Shows (parked in Figma).

The Music page, a stub in V1, left the nav with F17 (the project lead, 2026-11-01); it is not planned.

## Languages

- **V1:** English only.
- **Wanted:** the launcher's translations and right-to-left layout (the old launcher had 25 languages, with
  Hebrew mirrored).
