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
| Typeface | Roboto, the system font; nothing bundled. Sizes and weights follow Figma, which uses Inter. |
| APK size | The 512 KB gate is not enforced in this fork. Each PR reports the size; M0 changes `ci.yml` to report it instead of failing. |
| Languages | English only. Launcher translations, RTL and the translated READMEs are dropped. |
| Catalogue | Mock in V1, ported from `design_preview/src/data`, behind an interface a TMDB source can replace. |
| Availability and prices | Mock in V1. |
| Watch on … | Opens the service's app. Opening the title itself is in the backlog. |
| Watchlists | My list, Seen, Favorites, recent searches and recently viewed are stored on the TV. Household and Friends are mock placeholders; they will come from the project lead's TMDB lists (backlog). |
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
| Launcher settings | The old `LauncherSettingsActivity` stays, without its grid, dock and wallpaper rows, until the new page is designed. |
| Pull requests | Every PR asks the Copilot reviewer for a review. |
| Settings key over apps | `QuickSettingsActivity` draws the same dropdown as Home (`MenuPanel`), so the remote's Settings key looks the same everywhere. |
| Dropdowns and the D-pad | Decided after the first run on the TV (2026-10-29): a D-pad move past a dropdown's edge closes it and returns focus to its button; past the left, right or bottom edge it carries on one step, so → in the Wi-Fi menu lands on Inputs. Closing with Back alone felt clunky. The prototype and its README changed with it. |
| All Apps | A plain working grid of the installed apps, styled from the tokens: OK opens an app, hold-OK adds it to or removes it from Home. The spec's stub would leave apps that aren't on the Home row with no way to open, and an app removed with hold-OK with no way back. A proper design is in the backlog. (2026-10-29) |
| Build settings file | `launcher/tv.properties`, gitignored; `launcher/tv.properties.example` is the committed template, and the build reads the example while `tv.properties` is missing. It holds the weather location (New York by default), the temperature unit and, later, the TMDB key. (2026-10-29) Keys since M2: `latitude` and `longitude` in decimal degrees and `temperature_unit` (`C` or `F`), checked at build time and passed to the app as `BuildConfig` fields. |
| Temperature unit | °C unless `tv.properties` says `temperature_unit=F`. The TV's language (en-US) would have given °F in New York, and Android 9 has no temperature setting. The clock follows the TV's 12/24-hour setting. (2026-10-29) |
| Order of the PRs | Stacked: each milestone's branch starts from the one before, and the PRs merge in order. M1b, the cleanup, runs after M8, once every screen's config keys are known. Real-TV checks wait for one batch session with the project lead. (2026-10-29) |
| Search, after review (2026-10-29) | Backspace is Back on every screen, Search included: on the full results it brings the keyboard back with the query kept, with the keyboard showing it goes Home, and only the on-screen ⌫ key deletes. Leaving Search for another tab (nav, Back, the remote's Home) clears the query, filter and full results; a title or person opened from it keeps them. Recent searches keep four, not five. Search has no guide line. The full results' six columns stay title-safe (96 px left, 98 px right); the start page's rows may still run off the right edge. The prototype and its README changed with it. |

## Open questions

Raise these when they become relevant; don't settle them alone.

- **`subscribed_services` in `tests/test_switches.py`:** M3 added the key to the "screenshots wanted" list, which is
  meant to shrink. Keep it there until a TV screenshot exists, or document it another way?
- **`.github/maintainer/MAINTAINER.md`** still says "no INTERNET permission" and enforces the 512 KB APK gate. This
  fork allows both (AGENTS.md, This fork); the policy file is a maintainer file, so it waits for the project lead.
- **APK size:** about 1.6 MB from M4 on, almost all of it the bundled mock artwork (1.35 MB). The gate is
  report-only in this fork; it drops back once artwork comes from the network (BACKLOG.md, Real catalogue).
- **The remote's Settings key** on the TCL still goes to TCL's own handler, not the launcher's Settings dropdown.
  Check it in the real-TV run, then decide whether a key helper is worth it.

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

**After:** close ADB as AGENTS.md requires: USB and network debugging off, authorisations revoked, a reboot, then `tools/adb-closed.py <tv ip>` must report the port closed. Record the results and any new decisions here.

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
