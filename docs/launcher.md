# The launcher

HomeTV Launcher is a home screen for Android TV, driven entirely by the remote. It is plain Java and Android Views,
built for TVs with 1 GB of RAM: no blur, no video, no analytics, and no polling while Home is hidden. English only.
It goes online for the weather and, with a TMDB token, the catalogue; it keeps what it fetched and works offline.

The TV needs Android 8.0 (API 26) or newer. `minSdk` is a compile-time floor in
[`launcher/app/build.gradle`](../launcher/app/build.gradle), and the install script asks the TV for
`ro.build.version.sdk` before it builds, so an older set is told both numbers instead of failing at `pm install`
(what is left on such a set: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)). The design is
[`design_preview/README.md`](../design_preview/README.md); what is mocked or deferred is in
[BACKLOG.md](../BACKLOG.md).

## What it does

| What | How it works |
|---|---|
| **Home** | Greeting, date, clock and weather for the city chosen in Launcher settings (default: the location in `launcher/tv.properties`), then the app row. The row starts with the installed apps from `dock_apps`; hold OK on a tile to move it, remove it from Home or open App info, and Home keeps that order. Down slides the page up to Inputs, Shortcuts (All Apps, Launcher settings, TV Settings) and an hourly forecast. |
| **Inputs** | The nav's Inputs dropdown and Home's Inputs row: HDMI ports under their names, CEC devices on their port, AV and Live TV (`live_tv_package`). Home's row shows the first four (Live TV first); the dropdown lists them all. Sets that list their inputs another way are handled in [`config.xml`](../launcher/app/src/main/res/values/config.xml). |
| **All Apps** | Every installed app. Hold OK to put one on Home or take it off. A **TV Utilities** folder comes last, with the apps the TV came with that aren't for watching (its settings, inputs, media players, updaters), on any brand; hold OK on an app to move it into the folder or out of it. |
| **Movies, TV Shows, Search, Pick for me** | Titles, artwork, cast, episodes, similar titles and search come from [TMDB](https://www.themoviedb.org) when the build has a TMDB token, and are cached on the TV so what was seen once still shows offline; without a token the launcher uses the prototype's mock catalogue. Where to watch is real for Sweden, or for the US with `region` set to `US`: which services stream, rent or sell a title, from JustWatch through TMDB, with rent and buy prices in kronor (dollars in the US) from Watchmode and Movie of the Night (optional keys in `tv.properties`, free plans only, kept a week) and Apple's iTunes; a store with no known price still shows, without one. My list, Seen, Favorites, recent searches and recently viewed are stored on the TV; a fresh install starts with all of them empty. |
| **TMDB account** | Optional. Launcher settings' Account pane shows a QR code; approve it on your phone and My list, Favorites and Seen follow the account's watchlist, favorites and a private list named "Seen" (made when missing), both ways, with changes made offline sent later. The first sign-in takes the account's lists and adds nothing from the TV. Choose lists shows or hides each of your TMDB lists; shown ones become chips in Movies, TV Shows and Pick for me, by name. `tmdb_shown_lists` in `config.xml` overrides that (list numbers or links, or list names; the older `tmdb_household_list` and `tmdb_friends_list` still work). Syncs only while Home is on screen; signing out keeps the lists on the TV. |
| **Services** | "Included" marks a service the household pays for: worked out from the installed apps, or listed in `subscribed_services`, Sweden's services included (Viaplay, SkyShowtime, SVT Play, TV4 Play and others), and in the US Peacock, Paramount+, Tubi, The Roku Channel and Fandango at Home. Watch opens the title itself in that service's app where Wikidata has its id (Netflix, Disney+, Apple TV, HBO Max, Prime Video, SVT Play), else the app. |
| **Phone notes** | Optional, off unless turned on: a watch-list note shared from a phone is sent to the TV over the home Wi-Fi and its new lines are added to that person's TMDB list. See [phone-notes.md](phone-notes.md). |
| **Themes** | Launcher settings › Theme. Classic: Midnight (the default), Daylight, OLED Black, High Contrast, Cinema and Landscapes. Seasonal, following the date from Snow to Christmas and the sun from dawn to night: Vector art and 8-bit. A theme changes colours, type, corners, shading and the picture behind Home; layout and data stay the same. Screenshots of each: [themes/README.md](../themes/README.md). |
| **Remote keys** | Keeps the Input, YouTube and gear buttons working after the stock launcher is gone ([below](#remote-keys)). |
| **Left for V2** | Wallpapers pushed as `config.json`, the clock screensaver, module cards and add-ons, Watch Next, festivals and the sky: [BACKLOG.md](../BACKLOG.md#left-for-v2). |

## Build it yourself

It needs JDK 17 and Android SDK platform 34. On macOS: `brew install openjdk@17 android-commandlinetools` and
`sdkmanager "platforms;android-34" "build-tools;34.0.0"`. On Linux the install script finds `~/Android/Sdk` and the
`java` on your PATH. Then `launcher/build-and-install.sh` (`build-and-install.ps1` on Windows) builds and installs it.

The weather's default location and temperature unit come from `launcher/tv.properties`, which is gitignored: copy
[`launcher/tv.properties.example`](../launcher/tv.properties.example) (New York, °C) and edit the copy. Both can be
changed on the TV too, in Launcher settings. The optional `weather_city` names the default location on the quick
settings' Weather row ("New York · °C"); without it the row says Default. For the real catalogue, add your TMDB "API
Read Access Token" there as `tmdb_api_key` (free for non-commercial use, from your account's API settings at
themoviedb.org). Without it the launcher builds with the mock catalogue, which needs no network. The token ends up
inside the APK you build, so keep that APK to yourself. Or leave the keys out and enter them on the TV: Launcher
settings › API keys shows a QR that opens a page on a phone (or an address and a code for a laptop). The page says how
to get each key, and the TV checks each one and keeps it in its own storage.

This product uses the TMDB API but is not endorsed or certified by TMDB.

## Configure

[`config.xml`](../launcher/app/src/main/res/values/config.xml) holds what differs from TV to TV: the apps Home's row
starts with (`dock_apps`) and the ones never shown (`hidden_apps`), the Live TV app and the quirks of a set's Inputs,
an optional VPN app for the Wi-Fi dropdown, `reduced_motion` for TVs whose GPU cannot keep up, `light_defaults` for
where Launcher settings › Performance starts, `subscribed_services`, `region` (`SE`, the default, or `US`), and the
remote-key switches. A `config.json` with
the same keys, pushed with `tools/theme.sh`, overrides it without a rebuild; several [`examples/`](../examples/)
folders carry one. The old launcher's look and layout keys are not read any more;
[themes/README.md](../themes/README.md) keeps them for V2.

## Launcher settings

Open it from Home: press Down to the Shortcuts row and choose it. It is also the last row of the remote Settings
key's dropdown (`settings_key_panel`), and `launcher_settings_button` makes the nav's Settings icon open it. It is one
page: the sections on the left with their current values, and the focused section's pane on the right. Right or OK
enters the pane, Left returns to the list, Back closes. Choices save at once; Home is rebuilt with them when the page
closes. The sections, top to bottom:

- **Account**: sign in to TMDB with a QR code on your phone, then see the lists, sync now, choose which TMDB lists show
  as chips, or sign out. On a launcher built without a TMDB key it says to add one in API keys first.
- **Phone notes**, only once the launcher runs with a TMDB key, off unless turned on here (or by `note_inbox`): the address, pairing
  code and a QR for a phone's "Send to TV" shortcut (on an iPhone, scan it to get the household's shared shortcut,
  `note_shortcut_url`, and pair the phone; on Android, to copy the settings into HTTP Shortcuts), and each list's last
  send with the lines that matched nothing. See [phone-notes.md](phone-notes.md).
- **Streaming services**: tick the services the household pays for; titles on them show as Included.
- **Stores for rent and buy**: tick the stores whose rent and buy prices show.
- **API keys**: the TMDB, Watchmode and Movie of the Night keys. A QR opens a page on a phone (or a laptop opens the
  address and types the code) that explains how to get each key and sends it to the TV, which checks it with one free
  request and keeps it, showing only its last four characters. A key entered here replaces the one in `tv.properties`;
  Remove leaves none. The page answers only while this pane shows. With Price lookup off, the price keys wait behind
  Turn on.
- **Home row apps**: pick an app up with OK to move it, take it off Home, or add another app.
- **Features**: TMDB account, Phone notes, Price lookup and Weather, each On or Off (`features` in a pushed
  config.json). Every one starts on, and First run switches off any whose setup isn't finished when it ends. A feature
  that is off never starts; its section stays and reads Off, and its pane, like Movies, TV Shows and Search's "needs a
  TMDB account", has **Turn on**, which switches it on and goes straight into its setup.
- **Theme**: the look of every page. Typeface switches between the theme's own fonts (downloaded from Google Fonts and
  kept on the TV) and Roboto. The theme's pictures, except 8-bit's, are downloaded once from `theme_assets_url`.
- **Weather**: the place Home's weather is for, with the weather there now. Change city searches Open-Meteo's places
  as you type, on the on-screen keyboard or a real one; Use default goes back to the place in `tv.properties`. The
  temperature in °C, °F or the TV default, which follows the TV's region.
- **Performance**: how much the launcher draws and moves. Light mode sets everything below to its lightest. Motion
  (Full, or Reduced and Off, where pages, menus and Home's scroll switch without moving, as with `reduced_motion`),
  card shadows (On, Focused card only, Off), the focus glow, the backdrop picture (Photo or the theme's plain colour)
  and animated details (off to start: snow, rain or autumn leaves over a Seasonal picture, by the weather at the Weather
  place or the season, while Home is idle for 3 s). A low-RAM TV starts with the lighter choices (no card shadows, Motion Reduced), as does any TV
  whose `light_defaults` says `on`. Pushing or resetting a `config.json` clears the choices.
- **Open on start**, only with `open_on_start_choice`: Home, or an app that opens instead of Home the first time Home
  comes up after the TV starts. Once per boot, so Home and Back still reach the launcher.
- **Default Home** says whether this launcher is Home and opens Android's home picker; **TV settings** opens the TV's
  own settings, Picture, Network and Apps.
- **Region**: Sweden or the United States (`region` in config.xml or a pushed config.json; Sweden unless set). It
  decides which streaming services and stores the two sections above list, the currency of prices, and the country
  TMDB's availability is read for. Each region keeps its own store ticks; the streaming services stay ticked. Changing it rebuilds Home and comes back to it.
  First run asks it before Streaming services and Stores.
- **About**: the version, and the TMDB, availability, price and weather notices.

To stop using this launcher as Home, choose the original one in Default Home. The original launcher must be enabled:
if the install script disabled it, run the restore script ([Restoring your TV](restore.md#restoring-your-tv))
first. Some TVs provide no home picker.

## Remote keys

- An optional accessibility service keeps the remote's **Input** button working on Google TV (`INPUT_KEY_SERVICE=1`
  at install).
- It can bring back other buttons only the stock launcher answered, such as the **YouTube** button (`app_keys` and
  `input_key_keycodes`, in config.xml or a pushed config.json; `HOME_KEY_SERVICE=1` reads `app_keys` too).
- A `hold:` prefix on an `app_keys` entry gives that button a second app: a normal press opens the unprefixed app,
  holding for half a second opens the hold one (by keycode or `hold:scan:...`).
- In place of an app, an `app_keys` entry can name an action: `back`, `home`, `recents`, `mute`, `sleep`,
  `screensaver`, `settings` or `wifi`, so a spare colour button can mute the TV or, held, put it in standby.
- `HOME_KEY_SERVICE=1` also fixes sticks whose Home key sends ALL_APPS, and opens Settings from the remote's **gear**
  button, even one Android does not map (scancodes or keycodes in config.xml).
- With `settings_key_panel` the gear opens the launcher's quick settings dropdown over Home or any app: TV settings,
  Picture, Network, Apps and Launcher settings.

## Navigation

The D-pad moves to the nearest item in that direction, and every row and grid remembers where you were, so leaving
and coming back lands on the same item. Back closes a dropdown, dialog or the title preview first, then goes back a
page, then Home. Home's app row catches up with installed and removed apps each time it shows.
