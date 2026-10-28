# Backlog

Features the new launcher needs but V1 doesn't have yet: where V1 uses mock data or a fallback, what was left
for V2, and what isn't designed. Each entry says what V1 does, what the real version needs, and any findings
so far. A PR that adds a mock adds its entry here; a PR that makes one real removes it. How this fits the
milestones: [docs/rework.md](docs/rework.md).

## Data and accounts

### Real catalogue
- **V1:** the mock catalogue from `design_preview/src/data`, behind an interface.
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

### Title deep links
- **V1:** "Watch on …", episode rows and Where to watch open the service's app on its home screen.
- **Wanted:** open the title, or the episode, directly.
- **Findings:** cleanyourtv-ui's `DeepLinks.java` finds the Wikidata item for a TMDB id (`P4947` movies,
  `P4983` shows). From it, it reads the service ids for Netflix (`P1874`), Disney+ (`P7595`/`P7596`), Apple TV
  (`P9586`/`P9751`), Max (`P8298`) and Prime Video (`P8055`), builds each app's link, and keeps the answer for a
  month. No source for episode links was found yet.

### Choosing the household's services
- **V1:** subscribed services come from the installed apps, with a `config.xml` override.
- **Wanted:** a screen in Launcher settings to tick the services the household pays for.

### Weather location on the TV
- **V1:** latitude and longitude come from the gitignored build properties file.
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
- The clock screensaver.
- Module cards and the add-ons in `addons/`.
- The Watch Next row.
- Festivals, the sky and the lunar calendars.

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
