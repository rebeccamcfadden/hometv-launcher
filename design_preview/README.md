# design_preview

A clickable web prototype of the reworked launcher UI. It was built to settle the flow between screens and
buttons before the shell is built for real TVs in `launcher/`. This README is the hand-off: it records what the
design is, the rules it follows, and the decisions taken along the way, so the TV build can follow it without
the conversation that produced it.

- **Flows and behaviour:** this prototype is the source of truth. Where it and Figma disagree on how something
  behaves, follow the prototype.
- **Look:** Figma is the source of truth for visuals; the prototype follows its tokens closely (see
  [Visual system](#visual-system)).
- **Data:** everything in `src/data/` is mock data. Titles, services, prices, scores, cast and networks are
  illustrative only.

## Run it

Node 20 or newer.

```sh
cd design_preview
npm install
npm run dev      # http://localhost:5173
npm run build    # type-checks, then writes dist/ (open it from any static host)
```

Each merge to `main` that touches `design_preview/` also publishes the build to GitHub Pages
(`.github/workflows/pages.yml`), at `https://<owner>.github.io/<repo>/`. It shows mock data only.

The 1920×1080 TV screen is scaled to fit the window. The keyboard stands in for the remote:

| Key | Remote |
|---|---|
| Arrow keys | D-pad |
| Enter | OK |
| Esc or Backspace | Back, on every screen (on Search, Backspace never deletes letters) |
| H | Home (except on Search, where letters type) |
| S | The remote's Settings key (except on Search): on the TV it opens the TV maker's own panel; here the base launcher's stand-in |
| Letters, digits, space | Type on Search, and in Launcher settings › Weather › Change city |

The mouse works too: a click is focus + OK. The bar under
the stage shows the screen stack (what Back returns through), a **State** menu, an **Account** menu, **Theme** and
**Date** menus, a **Jump to** menu, and **Reset state**, which clears My list, Seen, Favorites, recent searches and Launcher settings (kept in
`localStorage`).

The **State** menu simulates the network and the catalogue, so each loading, offline and error state can be
reviewed (see [Loading, offline and errors](#loading-offline-and-errors)): Online; Loading (Movies and TV Shows);
Loading (title preview); Offline, lists saved; Offline, nothing saved; Error in the Where to watch tab; Error in the
Cast & crew tab. Open screens change in place, with focus kept where it can be. Two URL parameters do the same from a
link: `?state=offline` (any id from `src/core/net.ts`) and `?jump=moon` (any Jump to entry's id from `src/main.ts`,
for example `quick`, `settings`, `settings-account`, `settings-services`, `settings-stores`, `settings-weather`,
`settings-apps`, `settings-theme`, `setup`, `arrival`, `interstellar`, `daredevil`).

The **Account** menu (or `?account=in`) steps the mocked TMDB sign-in, so each state of the Account pane can be
reviewed (see [Account](#account)): Signed out, Getting a code, Waiting (QR), Approved on the phone, Signed in, Code
expired, Couldn’t reach TMDB. Without it the pane runs by itself: Sign in fetches a code in about a second, and the
code waits for 15 minutes until the menu approves it; approval imports for a few seconds and signs in.

The **Theme** menu (or `?theme=cinema`, any id from [Theme](#theme)) picks a theme as Launcher settings › Theme does,
and keeps it. The **Date** menu (or `?date=2026-10-31`) puts the preview on a day in each Seasonal period, or back on
today, so every period can be reviewed without waiting for it.

## Figma

File `7gFFvfdGdXJVLZnj18Zah4` (private; ask the owner for access). One page, laid out as a main column on the
left and parked work on the right.

| Section | What it is |
|---|---|
| 01 · Home | Home, resting and scrolled to Inputs / Shortcuts / Weather |
| 02 · V1 — Lists-only Movies & TV Shows | The Movies and TV Shows pages for V1 |
| 03 · Pick for me | Filter setup, then four recommendations |
| 04 · Title preview | The original title card, superseded by 04b |
| 04b · Title preview — Option C (chosen) | The title preview to build: Overview and Where to watch tabs |
| 05 · Search | The full search mockup: empty, typing, results, no matches |
| [Parked] … | Explore versions of Movies and TV Shows, title preview options A and B, earlier search concepts |

The prototype goes further than Figma in places. These exist only here: the filters drawer, the rent/buy dialog,
the Episodes, Cast & crew and More like this tabs, the Inputs, Wi-Fi and quick-settings dropdowns, the person
page stub, the empty and no-match states of Movies and TV Shows, the focusable search bar on the full results,
the Seen icon (an eye, so it no longer matches My list's tick), and Phone notes' picker for a line TMDB didn't match
exactly (TMDB's best three as posters with title and year, Search again to fix the text with Search's keyboard
and look it up again, and None of these; drawn as the confirm dialog, with no design yet; the project lead,
2026-11-01, F9).

### V2 designs

The V2 screens with no Figma frame yet were drawn as HTML mockups (rendered at 1920×1080) and the project lead chose
one option for each (2026-10-30). The prototype builds these:

| Design | What it is | Here |
|---|---|---|
| 06-states A, "inline" | Skeletons while loading, an amber offline pill over cached content, offline with nothing saved, a preview loading, errors with Retry inside the failing tab | [Loading, offline and errors](#loading-offline-and-errors) |
| 07-prices A, "cards" | Where to watch cards with kronor, a line per picture quality and a Cheapest tag; "Rent from 39 kr · 3 stores" on cards; the rent/buy dialog with an HD/4K switch | [Prices](#prices) |
| 05-settings A, "split" | Launcher settings: sections on the left, the focused section's pane on the right | [Launcher settings](#launcher-settings) |
| 01-setup B, "checklist" | First run: one page of setup rows with their guessed values and a help pane | [First run](#first-run) |
| r2-account A, with B's Choose lists | The TMDB sign-in inside the Account pane: QR, steps and countdown; signed in; Choose lists with a Show / Hidden switch per list (B's rows, with a two-way switch instead of the Household / Friends roles) | [Account](#account) |
| r2-services B, "tiles" | Streaming services as a grid of tiles with ticks and Installed / No app / Free | [Streaming services and stores](#streaming-services-and-stores) |
| r2-services A, "rows" (stores) | Stores for rent and buy as checkbox rows, each saying which app it opens | [Streaming services and stores](#streaming-services-and-stores) |
| r2-weather A, "summary" | The place, Change city (keyboard and matches in the pane), Use default, the unit; B's now showing / quick picks as the empty match list | [Weather](#weather) |
| r2-setup-checklist | First run's rows open the same panes; since F28 on the same page, the checklist shrinking to tabs, with Next / Done | [First run](#first-run) |
| final-quick-settings | The nav's Settings icon opens a quick-settings dropdown (5A and 5C combined) | [Top-bar dropdowns](#top-bar-dropdowns) |

## Product decisions

- **V1 is lists-only.** Movies and TV Shows show the household's imported watchlists (My list, then the TMDB
  lists chosen to show; the mock Household and Friends before any sign-in). The Explore versions (Trending, New
  releases and so on) are parked, not cut.
- **The lists are the account's own** (2026-10-31): signed in, the TMDB lists ticked Show in Account → Choose lists
  become chips under their TMDB names, in the account's order, **at most 6** (the project lead, 2026-10-31); a list
  shown past six has no chip, but its titles are still in All lists. Before any sign-in the mock Household and Friends
  stand in; after a sign-out only My list is left. Since F14 (the project lead, 2026-11-01) Movies, TV Shows and
  Search need a sign-in and show "needs a TMDB account" without one, so the page shows these lists only signed in.
- **Title preview is Option C:** a popup with the art across the top, the essentials and actions on the card, and
  tabs for everything else. Options A (cinematic, scroll for the rest) and B (side panel) are parked in Figma.
- **The title preview is a popup, not a page.** The page it was opened from stays visible, dimmed, around its
  edges, nav included.
- **Search is one page that grows.** Results preview beside the keyboard while you type and widen into the full
  results when you press → from the keyboard. There is no separate results page and no "top match" block.
- **Search keyboard is a 6×6 grid** of a–z and 0–9 together, with Space, ⌫ and Clear below. A letters-only
  keyboard with a 123 toggle and a seven-column layout were tried and dropped: the narrower 440 px panel was
  preferred.
- **Search matching is fuzzy:** "arvl" finds Arrival, and small typos are forgiven. Matched letters are
  highlighted in suggestions and results, and the top suggestion is shown as grey ghost text in the bar.
- **Nav switches on OK.** Moving along Home, Movies, TV Shows and Search only highlights a section; OK
  switches to it. Switching on focus alone was tried and dropped.
- **Top-bar buttons:** Inputs opens the base's Inputs dropdown; Wi-Fi gets a new dropdown (the base opens Wi-Fi
  settings directly); **Settings opens a quick-settings dropdown** (decided 2026-10-30, design final-quick-settings,
  5A + 5C combined), with TV settings and Launcher settings on top. This replaces the earlier decision that the
  Settings icon opens the TV's settings with no menu. The remote's Settings key keeps the TV maker's own panel.
- **Launcher settings is a split page** (5A): sections left, the focused section's pane right. The quick-settings
  dropdown reaches it and its busiest panes in one step; First run reuses the same panes.
- **Segmented controls select on OK.** Moving along two or three options (Motion, the unit, a list's role) only
  highlights; OK selects. The same holds for every row of choices in the prototype.
- **Cheapest rule** (confirmed 2026-10-30): rentals with a price first, cheapest first; then purchases with a price;
  stores with no price last. A tie on the cheapest price goes to a store whose app is installed (2026-10-31).
- **Paid titles ask before leaving the launcher.** Renting or buying opens the store's app, after a dialog.
- **Network features are allowed** in this fork (see [AGENTS.md](../AGENTS.md#this-fork)), for catalogue
  search, availability, prices, artwork and cast. Analytics and telemetry are not.
- **Prices are in Swedish kronor** (V2 region SE), per store and per picture quality, as the launcher's real data has
  them. A store that no price source prices still shows, as "Rent · Buy" without an amount; its app shows the price.
- **Service names follow current branding:** "HBO Max"; Apple's subscription is "Apple TV" and its store the
  "Apple TV Store" (which opens in the Apple TV app); Amazon's store is "Amazon Video" (in the Prime Video app) and
  Viaplay's the "Viaplay Store".
- **Nothing waits on the network.** A page shows its shape at once and fills in; problems are said where the missing
  data would be, and focus always lands on something real (design 06-states A).

## Requirements for the TV build

From [AGENTS.md](../AGENTS.md) and the design:

- **Resolution and safe area:** design at 1920×1080. Keep text and focus inside the TV title-safe area, 96 px from
  the sides and 54 px from the top and bottom. Only artwork and scrolling rows may run to the screen edge.
- **Text size:** nothing below 18 px at 1080p; body text is 22–24 px.
- **Focus is always visible:** every focusable item has a focused state (pale-blue fill or a 4 px ring, plus a
  small scale-up), and focus never lands somewhere invisible.
- **D-pad only.** Every action must be reachable with the D-pad, OK and Back. No touch or pointer assumptions.
- **Offline:** cache what the network supplies, never block Home on the network, and keep local content (apps,
  inputs, cached lists) working when the TV is offline.
- **Light enough for 1 GB of RAM:** no background blur, no autoplaying video, no heavy background polling. The
  prototype's animations are short opacity and position changes; keep it that way.
- **Keep the base launcher's TV plumbing:** `minSdk` 26; the Home guard and the safety rules in AGENTS.md are
  unchanged; Settings and Wi-Fi rows open settings through `SystemSettings` (with the vendor fallback); the
  Inputs list follows the rules in `MainActivity.showInputs()` (HDMI ports first, CEC devices take their port's
  row, hidden inputs left out, bogus "all connected" states dropped).
- **Household state is stored on the TV:** My list, Seen, Favorites, recent searches and recently viewed.

## Global behaviour

### Screens and layers

| Layer | Used for | Behaviour |
|---|---|---|
| Page | Home, Movies, TV Shows, Search, Pick for me, person, Launcher settings, First run | Full screen; the top-level pages have the nav at the top |
| Popup | Title preview | Over the page it came from; a 58% dark scrim covers the whole page, nav included |
| Dialog | Filters drawer, rent/buy dialog, dropdown menus (quick settings included) | Over whatever is showing, which stays visible |

### Back

1. Closes the top dialog or popup (dropdown, drawer, title preview).
2. Otherwise returns to the previous page (Pick for me → Movies; person → title preview; Launcher settings → the
   page that opened it, with focus on the button or row that opened it; First run → Home). Inside Launcher settings,
   Back first leaves a pane's sub-state (Choose lists, Change city, an app being moved), then the pane (focus to its
   section), then the page.
3. On a top-level page other than Home it goes Home, with focus on Home in the nav.
4. Search uses Back first to collapse the full results back to the keyboard, with the query kept. With the keyboard
   showing, Back goes Home (rule 3), whatever is typed.
5. A keyboard's Backspace is Back, on every screen, Search included; only Search's on-screen delete key deletes a
   letter. Held down, it goes Back once.

### Focus

- Moving with the D-pad picks the nearest item in that direction; moving up or down keeps left edges aligned so a
  column feels like a column.
- Each row or grid remembers its last focused item, so leaving and coming back returns to the same item.
- Each page keeps its focus and state when you switch tabs and come back, except Search: leaving it for another
  tab, by Back or by the remote's Home clears the query, the filter and the full results, so coming back shows the
  empty page with focus as on the first visit. A title or person opened from Search keeps the search.
- The first focus on a page: Home → the first app; Movies / TV Shows → the first title (the selected list chip while
  loading); Search → the "a" key; title preview → the Watch button; dropdowns → the first row (quick settings: TV settings);
  dialogs → the primary button; Launcher settings → the first section, or, opened from a quick-settings row or an
  empty state's Turn on or Sign in, that section's pane on its first control; First run → the first row; Back from a
  step's panel → its tab, Back on a tab → that row, Done → the next row not done (or Start).

### Nav

- Order: Home, Movies, TV Shows, then a search icon; on the right, Wi-Fi, Inputs and Settings icons. Offline,
  the Wi-Fi icon is crossed out on every page. There is no Music section (F17, the project lead 2026-11-01) and no
  Apps section: Home's All Apps shortcut opens the installed apps.
- ←/→ on the nav stay on it and move to the next item: ← from Wi-Fi is always Search, never something on the page
  below, and past either end nothing happens.
- Landing on a section highlights it; OK switches to it (OK on the current page's section does nothing). Down
  enters the page. Landing back on the nav from a page puts focus on that page's own section, regardless of
  which section or icon was used last or is nearest; Pick for me's section is Movies or TV Shows.
- Switching animates: the new page's content slides 90 px in from the side of the tab you switched towards and fades
  in (360 ms); the old content slides 60 px the other way and fades out (240 ms). The nav stays still. With the
  system's reduced motion, pages switch without animating; Launcher settings › Performance › Motion (Reduced, Off) is described
  under [Launcher settings](#launcher-settings).

### Top-bar dropdowns

All three hang under their button, which stays highlighted while its menu is open, grow inward from the right, and
close on Back, on OK on a row, on a click outside, or when the D-pad moves past the menu's edge. Focus returns to
the button; a move past the left, right or bottom edge carries on one step from the button, so → in the Wi-Fi menu
lands on Inputs. (Closing on the D-pad was decided after the first run on a real TV: closing with Back alone felt
clunky.) Rows are 72 px; the panel is opaque (no blur).

| Opened by | Rows |
|---|---|
| Inputs icon | HDMI 1–3 and named HDMI-CEC devices with a green "Connected" mark, then AV, then Live TV, divided |
| Wi-Fi icon | Header with the network, "Connected · Internet OK · Strong signal" (offline: an amber "Connected · No internet"); saved networks; Other networks; Wi-Fi settings |
| Settings icon | Quick settings (design final-quick-settings), 600 px wide, ten rows in four groups divided: **TV settings** (the TV's settings) and **Launcher settings** › (the split page); **Picture** (the TV's display settings), **Sound, Network & Wi-Fi, Inputs** (the TV's own pages; a toast here); **TMDB account** (status), **Streaming services** ("8 ticked"), **Weather** ("New York · °C"; before a place is chosen, the build's city), each with a chevron, opening that section's pane; **Motion** (Full / Reduced / Off; never Full on a TV whose config sets reduced motion), where OK steps the value in place, the menu stays open, and small dots show the position. No Sleep timer row (the project lead's decision, 2026-10-31) |
| Remote's Settings key | The TV maker's own panel, untouched by the launcher. The preview's S key shows the base launcher's stand-in: TV system settings, Picture, Network, Apps ("Re-enable"), Launcher settings |

### Title menu

Holding OK on a title card, wherever one shows (Movies, TV Shows, Pick for me's results, Search, the person page and
the title preview's More like this), opens a menu in the dropdowns' glass panel, hung from the card: under it, else
over it, else beside it, inside the title-safe area (the project lead, F3, 2026-11-01). As on the TV, OK acts when
it is let go, so a held OK opens the menu instead of the preview; in the preview a right-click stands in for it.
Home has no title cards yet.

| Row | What it does |
|---|---|
| **Mark seen** / **Mark unseen** | Toggles Seen |
| **Favorite** / **Unfavorite** | Toggles Favorites |
| **Add to a list** / **Change lists**, with "On My list", "On Family picks" or "On 2 lists" | Signed in to TMDB: a second menu at the same card with My list, then the shown TMDB lists (past the chip cap too), a tick on each that holds the title. OK puts it on or takes it off and the menu stays open; Back closes it. A TMDB list is ticked at once and unticked again, with a toast, when TMDB doesn't take the change (offline) |
| **Add to My list** / **Remove from My list** | In place of the lists row when My list is the only list the TV can change: signed out (the TMDB lists need the account), or a title TMDB doesn't know |
| **More like this** | The title preview, opened on its More like this tab |

Seen, Favorites and My list are kept on the TV and sent to the TMDB account when signed in, as the preview's buttons
are, so they stay signed out. Each change updates the card's list line in place, with a toast; the menu closes on
OK (except in the lists menu), on Back, or when the D-pad moves past its edge, and focus returns to the card, or to
the one beside it when the change took the card off the page (a title removed from the list a row shows).

## Screens

### Home

- **Resting:** greeting ("Good evening"), date, a large clock with AM/PM, today's and tomorrow's forecast, and a
  row of seven app tiles (Netflix, YouTube, Prime Video, Disney+, HBO Max, Apple TV, Spotify), which Launcher
  settings' Home row apps changes. The forecast and the weather strip are for Launcher settings' place, in its unit.
- **Scrolled:** moving down from the apps slides the page up to Inputs (Live TV, HDMI 1–3: at most four tiles,
  never scrolling; any others are in the nav's Inputs menu), Shortcuts (All Apps, Launcher settings, TV Settings) and an hourly Weather strip, and dims the background (with Motion set to Reduced,
  the page jumps instead of sliding; the dimming still fades). Launcher settings opens the same page as the Settings
  icon's Launcher settings row. (The Figma frame still shows an Explore shortcut here;
  it was replaced because Explore is parked.)
- **Offline:** apps, inputs and the clock work without the internet. The weather keeps the last forecast, with an
  amber "Offline · updated 19:52" in place of the conditions; with no forecast saved, the strip and the line under
  the clock say a quiet "Weather unavailable offline".

### Movies and TV Shows (V1)

- Title, "Your collection · My list, Family picks & Friends’ tips" (the shown lists; past four, "My list, Family
  picks & 4 more"), and a **Pick for me** button.
- Scope line ("3 imported watchlists · All 8 services", counting My list and every shown list, chip or not) and the
  active filters with a **Sort · A–Z** button and a **Filters · n** button.
- List chips with counts: All lists, My list, then the first six shown TMDB lists by name (Household and Friends
  before any sign-in); All lists also holds the titles of shown lists past six. One row: when the chips don't fit it
  scrolls with focus, and back on All lists it returns to its start.
  A list hidden while chosen falls back to All lists. Up from any chip, and Right from the last one, goes to
  **Pick for me**, so it comes before the nav and instead of the grid.
- A four-column grid of title cards: art, name, service and "Included", "Free" or a price ("Rent 39 kr", "Buy from
  99 kr", or just "Rent" for a store with no known price), runtime or seasons · rating · score, and the first list
  in chip order that holds it, and its genre.
- **Filters drawer** (from the right): Availability, Review score, Genre, Length (movies only), Watch state. ←/→
  changes a value, and the grid behind updates live. Clear all and Done. The default is no filters (F2; Figma's
  frame shows "Included only · Review 7.0+").
- **Sort** (F4, no Figma frame): a small button left of Filters; OK opens a dropdown under it with Alphabetical
  (A–Z, the default), Recently added to my lists, Newest release first, Oldest release first and Highest rated, the
  one in force checked and focused. A–Z leaves out a leading "The", "A" or "An", accents and case; the other orders
  fall back to A–Z between equals and put titles without a year, score or known place last. ← from Sort stays; →
  goes to Filters.
- **What a page keeps** (F2): filters, the chosen list and the sort start cleared when the TV is turned on (on the
  TV, a boot or the screen back from standby; here, the preview loading). Each page keeps its own while it is left
  and clears them after more than an hour away.
- **Empty states:** a list with nothing on it ("add titles with + My list…"), and filters that hide everything
  (with Clear filters).
- **Loading, offline and errors:** see [Loading, offline and errors](#loading-offline-and-errors).

### Pick for me

- **Setup:** choose from My watchlists or My services; pick one or more lists (My list and the chip lists, up to six, as on
  the chips; My list and the first of them to start); Movies or TV Shows; six fields that
  OK steps through (length, genre pair, review score, content rating, watch state, availability). A card on the
  right shows how many picks match ("4 picks, just for tonight") and the **Find picks** button.
- **Results:** four random matches with a short blurb each, the filter line, Reroll, and Adjust filters (Back to
  setup). A bar under the tiles shows the focused pick with **Title details** and **Open in <service>**. Up from
  a pick goes to Adjust filters or Reroll, whichever is nearer, before the nav. Reroll keeps focus, so it can be
  pressed again and again.

### Title preview (Option C)

- A 1728×900 popup over the page it came from. A Back button sits top-left on the art.
- **Header:** title (84 px), then year · genres · runtime (or seasons) · rating badge · ★ score.
- **Actions,** on the right of the header:
  - **Watch:** "Watch on Prime Video" when included; for shows, "Watch S1 E3 on …" (the next episode); otherwise
    "Rent on SF Anytime · 39 kr" ("· from 49 kr" when the store's qualities cost different amounts, no amount when
    the store has no known price). The offer used is the included one, else the cheapest rental, else the cheapest
    purchase; a priced offer beats one with no price.
  - **My list, Mark seen, Favorite:** toggles that show an "on" state and a short confirmation. Icon-only until
    focused, then they show their label.
  - **Trailer:** opens the trailer (YouTube) outside the launcher.
- **Tabs:** Overview, Episodes (shows only), Where to watch, Cast & crew, More like this. Landing on a tab shows
  it; the content area is a fixed height so the card never jumps.
  - **Overview:** synopsis; "Included on Prime Video · Rent from 29 kr elsewhere"; starring; director or creator.
  - **Episodes:** seasons stacked on the left (landing on one shows its episodes); episodes in a row with
    runtime, watched ticks and an "Up next" badge.
  - **Where to watch:** one card per service; see [Prices](#prices). OK on an included service opens it; on a
    store, the rent/buy dialog.
  - **Cast & crew:** round portraits (initials for now) with role; OK opens the person.
  - **More like this:** similar titles; OK opens their preview on top.
- **Rent/buy dialog:** see [Prices](#prices).

### Search

- **Bar:** full width under the nav. While typing it shows the query and the ghost completion ("arvl → arrival")
  and a "› Results" hint.
- **Left panel (440 px):** a list above the keyboard, then the 6×6 keyboard. The list is Recent searches (up to
  four) when the bar is empty, and Suggestions (four, mixing titles, people and genres) while typing. Recent
  searches are saved when a result is opened.
- **Empty state:** Recently viewed, From My list and Browse by genre, in rows that run off the right edge.
- **Typing:** the results preview sits right of the panel: "N results", filter chips (All, Movies, TV shows,
  People, Included only) and a grid of cards that runs off the right edge. The preview can't be focused yet.
- **Full results:** → from the keyboard's right edge slides the panel away and widens the same grid to six
  columns, with focus on the first result. Same cards, same size, same header, so it reads as the preview
  expanding. The grid starts at the 96 px safe margin and its six columns end 98 px short of the right edge. Up
  from the chips reaches the search bar; OK on it, Back (or Backspace), or ← from the first column bring the
  keyboard back with the query kept. If the focused card goes (a filter chip, typing), focus moves to the first
  result or the bar, never to the hidden keyboard.
- **No matches:** "No matches for “qzxv”", a tip, and Recently viewed. In this prototype an exact genre search
  ("drama", or Browse by genre → Drama) shows No matches too. The launcher, with the real catalogue, lists the
  genre's titles instead, the ones it knows first and then TMDB's most popular ([BACKLOG.md](../BACKLOG.md), Real
  catalogue).
- **Back and Backspace:** on the full results they collapse to the keyboard; with the keyboard showing they go
  Home. Only the on-screen ⌫ key deletes. Leaving Search for another tab clears it (see Focus).
- **No guide line:** Search has no footer, unlike the other pages; the panel and the results use the height.
- In the prototype, typing on the computer's keyboard also types into the bar; on the TV, a remote or app with a
  keyboard should do the same.
- **Offline** (06-states A): the keyboard, recent searches and the Recently viewed and From My list rows work. The bar
  shows an amber "Offline · saved titles only" pill; results come from the titles saved on the TV (those on the lists
  and those viewed), the count says "18 results from saved titles", and people are left out. No match reads "No
  saved titles match “…”" with a note that the full catalogue needs the internet. With nothing saved: an amber
  "Offline" pill and, beside the keyboard, "You’re offline, and nothing is saved yet" with **Wi-Fi settings**;
  recent searches still work.
- **Card line:** each card's third line says how to watch it: "Prime Video · Included", "YouTube · Free", or the
  cheapest way to pay across the stores, "Rent from 39 kr · 4 stores" (else "Buy from 99 kr · 2 stores"; one store:
  "Apple TV Store · Rent 29 kr", or "Rakuten TV · Rent" when its price isn't known; a show's seasons: "Buy a season
  from 199 kr · 2 stores"). Quality is left to Where to watch. Only the household's services and stores count (F1,
  2026-11-01): a subscription it ticked in Streaming services, a free service (SVT Play, Pluto TV, Cineast, YouTube)
  whose app is installed or that is ticked, and the stores ticked in Stores; when none of them has the title the line
  is blank. The Movies and TV Shows cards' provider and "Included", "Free" or price follow the same rule; the title
  preview (Watch, Where to watch) still shows every offer. The title preview's More like this and the person page use
  the same cards.

### Prices

V2 design 07-prices A. Mock prices in kronor, shaped like the launcher's real data (`PricedOffer`): each store's rent
and buy offers carry a price per picture quality (SD, HD, 4K), or none at all when no price source priced the store.

- **Ticks:** a title is Included only on the streaming services ticked in Launcher settings, or free on a service
  that is ticked or whose app is installed (as the cards count it). Prices come only
  from the ticked stores: an unticked store's offers are left out everywhere (cards, Watch, Where to watch).
- **Where to watch:** one card per service, one D-pad stop each. Included and free services first ("With your
  subscription", "Free with ads", the quality), then the stores, cheapest first: rentals with a price, cheapest
  first, then purchases with a price, then the stores with no price (confirmed 2026-10-30). When two or more stores
  have a price, the first gets a green **Cheapest** tag and its deciding price is green. When stores tie on that
  deciding price (the cheapest rental, else the cheapest purchase), the first place and the tag go to one whose app
  is installed on the TV (decided 2026-10-31, in the launcher); otherwise equal stores keep
  TMDB's order.
- **A line per quality:** "Rent **49 kr** · Buy **149 kr**   HD", and a second line for 4K only when it costs
  something different (a quality that costs the same joins the line: "HD · 4K"). A store that only sells 4K shows
  "Buy 169 kr   4K". A show's season prices add "Per season".
- **No known price:** the card reads "Rent · Buy" and "Price shown in the app"; its dialog has the same buttons
  without amounts ("Price in the app").
- **Credit:** under the row, "Availability: JustWatch · Prices: Apple / Watchmode", naming only the price sources
  that priced this title (mock: Apple for the Apple TV Store, Watchmode for the rest). Offline it adds "Saved today
  19:52, prices may be out of date".
- **Rent/buy dialog:** "Moon on Apple TV Store", an **HD / 4K** switch when the store prices both (OK switches; the
  note says "4K costs 10–30 kr more here"), two big price buttons (Rent · HD 49 kr, "48 hours to finish once
  started"; Buy · HD 149 kr, "Yours to keep in Apple TV"), Cancel, and a line that says payment finishes in the
  store's app and names a cheaper store when there is one ("SF Anytime has it for 39 kr (HD)."). The switch is not
  shown for a store with one quality. First focus: the Rent button. OK on a price opens the store's app (a toast).
- **Names:** stores open in their app: the Apple TV Store in Apple TV, Amazon Video in Prime Video, the Viaplay
  Store in Viaplay. Mock stores: the Apple TV Store (dearer 4K), Amazon Video (4K to buy only), SF Anytime (HD,
  cheapest), the Viaplay Store (HD) and Rakuten TV (no known price). There is no Google TV store: TMDB lists none
  for Sweden.

### Loading, offline and errors

V2 design 06-states A. Reviewed with the dev bar's **State** menu. On the TV these follow the network and the
catalogue's cache.

| State | What shows | First focus |
|---|---|---|
| Loading (Movies, TV Shows) | The page at once: title, scope line, filters and list chips with their counts; eight skeleton cards (not focusable); "Loading artwork and details…" in place of the count | The selected list chip |
| Offline, lists saved | The scope line becomes an amber pill, "Offline · showing your lists as saved today 19:52"; the count says "· prices may be out of date"; everything else works from the saved copy. The nav's Wi-Fi icon is crossed out | The first title |
| Offline, nothing saved | An amber "Offline" pill; the list chips without counts; a crossed-out Wi-Fi circle, "You’re offline, and nothing is saved yet", a note that apps, inputs and the clock on Home still work, and **Wi-Fi settings** and **Try again** (which says "Trying…" for a moment, then "Still offline") | Wi-Fi settings |
| Loading (title preview) | The title and the Watch button (known from the card that opened it) are real; the art is a plain gradient, the details, the other action buttons and every tab's content are skeletons. Tabs still switch. On the TV, a card from TMDB doesn't know where to watch: Watch shows only its play icon, with no label, until the availability is in | Watch |
| Error in a tab | Inside the failing tab only (Overview, Where to watch, Cast & crew or Episodes): an amber box, "Couldn’t load where to watch" (Overview: "Couldn’t load details"), "TMDB didn’t answer. The rest of the page is from the saved copy.", and **Retry**. Retry moves focus to the tab, shows the tab's skeleton, then its content. Until the availability is in, Watch stays neutral, its play icon with no label (OK on it loads again); it says "Not on your services" only once the availability is in and there really are no offers | Watch; Retry when the tab is entered with ↓ |
| Back online | When the network comes back (the State menu from an error or offline to Online), a failed tab, or the tab showing, loads again by itself: its skeleton for a moment, then its content, with no Retry. Focus stays where it was, or moves to the tab if Retry goes | Unchanged |
| Search offline | See [Search](#search) | The "a" key |
| Home's weather offline | See [Home](#home) | Unchanged |
| Settings offline | Account's Sign in, New code and Try again end on "Couldn’t reach TMDB"; Sync now says it syncs when back online; Weather shows the last forecast with "Offline · updated 19:52", and Change city keeps the quick picks but can't search | Unchanged |

- Switching the State menu changes open screens in place; focus stays where it was when that item still exists,
  and otherwise goes to the screen's first focus above.
- Skeletons are static grey blocks, with no shimmer, to stay light on a 1 GB TV.
- Trailer, offline, says it needs the internet.

### Launcher settings

V2 design 05-settings A, with the round-2 panes. Opened from the Settings icon's Launcher settings row (or one of its
TMDB account, Streaming services and Weather rows, straight into that pane), from Home's Shortcuts → Launcher
settings, and from the remote's Settings key stand-in. A full page without the nav: the title, "Back closes · changes
save at once", the section list on the left (470 px, each row with its icon and current value in grey) and the pane
on the right.

- **Sections, in groups** (F23, the project lead's choice, 2026-11-01): the list is split by small grey headings into
  four groups, in this order. **Accounts:** Account (Not signed in, amber "Getting a code…", "Waiting…", "Signing
  in…", "Code expired", or the username), Phone notes (Off, On, or amber "3 not matched"). **What you watch:**
  Streaming services ("8 ticked"), Stores for rent and buy ("5 ticked"), API keys ("3 working", amber "1 refused"),
  Features ("4 of 4 on"). **Home & look:**
  Theme (Midnight, or "Nature · Halloween"), Performance (Default, All on, or Custom), Home row apps (7), Weather (the
  city). **This TV:** Open on start (Home), Default Home (On), Region (Sweden), TV settings, About (2.0.0). Values
  update live. Headings aren't focusable; ↑/↓ step over them. A section's name doesn't change with its group, so a path such as Launcher settings ›
  Phone notes stays as it is. The list stays inside title-safe and scrolls with focus when its sections don't fit,
  keeping a group's heading in view above its first section. A section whose feature is off in Features stays and
  reads Off (Account, Phone notes, Weather, Stores for rent and buy while Price lookup is off): its pane says the
  feature is off, with **Turn on** (first focus), which switches it on and goes straight into its setup. First run
  shows its own steps as tabs (F28).
- **Moving:** landing on a section shows its pane at once, as a preview. → or OK enters the pane, on its first control
  (the selected option in a segmented control), and the pane's border brightens. ← from the pane's left edge
  returns to the section. Back leaves a sub-state first (Choose lists, Change city, an app being moved), then the pane,
  then closes the page. ↑/↓ on the list stay on it.
- **Highlight:** the focused section has the accent fill; while the pane has focus, its section stays lit (white at
  10%, bold).
- **Segmented controls** (Performance's choices, Open on start, the unit, a list's role): moving only highlights an option; OK
  selects it, and it gets the accent ring.

#### Account

r2-account A, with r2-account B's Choose lists. The whole TMDB sign-in happens in the pane; the list stays usable.

| State | Pane | First focus |
|---|---|---|
| Signed out | "Sign in to TMDB", three benefits (lists in step; all your TMDB lists, by name; your password stays with TMDB), **Sign in with your phone**, "Not signed in, My list and Seen are kept on this TV only." | Sign in |
| Getting a code | The QR's 486 px place held by a dashed box with a turning arc, "Getting a code from TMDB…"; the three steps; **Cancel** | Cancel |
| Waiting | The QR (486 px) beside the steps (1 current), a countdown ("14:32 left on this code") with a bar, **Cancel** and **New code**, and "← goes to the sections and keeps this code waiting." The section reads "Waiting…" in amber; leaving for another section keeps the code. Closing Launcher settings stops it | Cancel |
| Approved | A green "Approved on your phone" in the QR's place, "Signing in…", steps 1–2 ticked, "Importing your lists… 3 of 5" with a green bar (My list, Seen, Favorites, then each shown list) | Unchanged (often the list) |
| Signed in | Avatar and username, "Signed in to TMDB", My list · Seen · Favorites counts, Shown as chips (the chip lists' names, or past three their count, plus "+1 in All lists" for those past six; or None), Last synced; **Sync now**, **Choose lists**, **Sign out** (asks first) | Sync now |
| Code expired | The QR dimmed under "Code expired", "That code has expired", **New code**, Cancel | New code |
| Couldn’t reach TMDB | "No code" in amber, "Couldn’t reach TMDB", **Try again**, Wi-Fi settings. Offline, Sign in lands here | Try again |
| Choose lists | A sub-state of Signed in, with a back crumb: one row per TMDB list (name, titles, updated) with a Show / Hidden switch, and "n of 6 chips" beside the title. Any number can show; the first six become chips by their TMDB names, in this order, and a shown list past six says "No chip: in All lists" in amber | The first list's switch |

The QR is a look-alike (finder and alignment patterns over seeded noise); it doesn't scan. The dev bar's Account
menu steps the states.

#### Phone notes

Not designed in Figma yet; drafted from the Account pane's parts (2026-10-31). Each person keeps a watch list in a
note on their phone (Apple Notes or Google Keep; on Android, Keep or Samsung Notes) and shares it to a "Send to TV"
shortcut (the Shortcuts app on an iPhone, HTTP Shortcuts on Android), which posts the note's text and their list's name
to the TV over the home network. The TV looks each new line up on TMDB and adds it to that
person's TMDB list (making a private one with that name if the account has none), so it shows as a chip in Movies and
TV Shows. Adding only: a line deleted on the phone takes nothing off, and a title taken off on the TV isn't put back.
A ticked line goes to Seen. Off by default.

| State | Pane | First focus |
|---|---|---|
| Off | "Send lists from your phones", what it does, an **On / Off** switch, "Off: the TV doesn’t listen for phones." | The switch |
| On | The switch; amber "Sign in to TMDB in Account first…" while signed out; Address (`http://<tv>:8765/notes`) and Pairing code ("K7QM-4PXT", 8 characters without 0/O or 1/I/L) beside a QR that opens `http://<tv>:8765/setup#<code>` on the iPhone, a page with **Get the shortcut** (the household's shared iCloud link) and **Pair this phone**, which hands the Shortcut both to keep, so a new code means pairing again (the code stays in the link's fragment; the project lead, 2026-10-31 and 2026-11-01); a tiny note that it is plain HTTP on the home network and the code only lets a phone add titles; **New code** (asks first: phones pair again) and **How to set up a phone**; Last sends, one row per list (when, lines, added, and an amber "3 not matched" with each line not matched exactly as a button, "Inglorious basterds · Choose", that opens the picker: OK on a poster puts it on the list, or on Seen for a ticked line; None of these stops asking; the lines being focusable, the pane scrolls down to them) | The switch |

The dev bar's Jump to → "Phone notes · a phone sends a note" plays a send; `?notes=on` starts with it on.

#### Import pill

While a note is looked up, a small pill sits at the bottom centre of whatever screen shows, between the guide line's
ends, and on Launcher settings at the top centre on the title's line, as its pane runs to the bottom edge (the project
lead, 2026-11-01): an accent dot, "Importing Rebecca · 6 of 12" and a thin bar (every line of the note counts, so it
reads "0 of 12" from the start). At the end it reads
"Partner · 8 added" with a tick for four seconds, or in amber "Rebecca · 9 added · 3 not matched", or "Rebecca ·
waiting for the internet" when the TV went offline (the lines are tried again). Never focusable; the shape is the
offline pill's (06-states A), amber only for a problem. Motion Off drops its slide.

#### Streaming services and stores

- **Streaming services** (r2-services B, tiles): "What does the household pay for?", "8 of 11 ticked", and a
  four-column grid of wordmark tiles with a corner tick and a caption: the name, then Free / Free tier and Installed
  (green) or No app. Netflix, Prime Video, Disney+, HBO Max, Apple TV, Viaplay, SkyShowtime, SVT Play, TV4 Play,
  Tele2 Play, Telia Play, then **+7 More services**, which opens a second grid (Hayu, MUBI, Crunchyroll, BritBox,
  Pluto TV, Plex, Cineast) and becomes "Fewer services". It stays open while anything in it is ticked. OK ticks or
  unticks any tile, free ones included; installed apps start ticked. Ticks change Included and "All 8 services" at once.
- **Stores for rent and buy** (r2-services A, rows): "Where may we show prices from?", one checkbox row each for the
  Apple TV Store, Amazon Video, Viaplay Store, SF Anytime and Rakuten TV, saying which app renting opens ("Opens in
  the Prime Video app") and Installed or No app. All start ticked. A note says prices are in kronor and Google Play
  isn't sold in Sweden.

#### API keys

F16 (the project lead, 2026-11-01 and -02; no Figma design yet, drafted from the Phone notes pane's parts). The
household's own TMDB, Watchmode and Movie of the Night keys, so nobody shares a free plan's monthly limit and no key is
ever inside an APK. Never typed on the TV.

- **The pane**: "Keys for titles and prices". One row per key, focusable:
  - What the row shows: the name, what the key is for, its free plan, and a status line.
  - The statuses: **Not set** (Movie of the Night: "Not set · optional"), **Checking with TMDB…**, **Works · ends
    Qx7w** (green; only the last four characters ever show), **Works · from this TV's build** (a `tv.properties`
    key), **Kept, not checked yet** (amber: the TV couldn't reach the service, so the key doesn't count as working until
    a check passes; it tries again later, and OK tries now; a key that once worked stays Works), or **refused** (red
    border: "TMDB refused it. Copy the API Read Access Token (the long one), all of it.").
  - OK on a key entered here checks it again; on a build's key it says one entered on the phone or laptop page replaces
    it, and on a missing key where to enter it.
- **Price lookup off**: the TMDB key stays (the catalogue needs it). In place of the two price rows, a dashed box says
  "Price lookup is off, so the TV asks for no prices and needs no price keys", with **Turn on Price lookup**.
- **Beside the rows**: a QR for a phone's camera, and for a laptop "Or on a laptop, open http://<tv>:8766/keys and type
  the code K7M4P2". The page there says how to get each key and sends it to the TV, which checks it with one free
  request (TMDB `/3/authentication`, Watchmode `/v1/regions/`, Movie of the Night `/v4/countries/us`).
- **Buttons**: **Remove the TMDB key** / **Remove a key** (asks which). A removed key leaves none, the build's own
  too: the TV has no key for that service until one is entered here (the project lead, 2026-11-02). **New code** asks
  first: a page opened with the old code can't send keys any more.
- **One note**: the page, the step-by-step help, and that the TV listens only while the pane is open, on the home
  network, with the code.
- The section value reads "3 working", "Not set", "1 of 3 set", "Checking…", "1 not checked" or "1 refused" (both
  amber); with Price lookup
  off, the TMDB key's alone.
- D-pad: ↑/↓ step through the rows, Turn on Price lookup and the buttons' row; → on a row stays; ← at the left edge
  returns to the section.
- **In First run**: the **Price keys** tab (Price lookup) is this pane with the two price keys; the **TMDB key** tab
  (TMDB account, on a TV without a working key) with the TMDB key. Price lookup counts as set up once the Watchmode key
  works (checked, not just kept); Movie of the Night is optional.
- **The phone and laptop page** (`keys.html`; the TV serves it, like Phone notes' setup page):
  - The code comes from the QR link's `#fragment`, never sent over the network; a laptop types it.
  - One card per key: its status, **How to get it** (TMDB open, the others folded), with sign-up links, which fields to
    fill and Copy buttons for TMDB's app name, URL and summary, then a paste box and **Check and save on the TV**.
  - A key leaves the box once sent; the page keeps nothing. In the prototype it updates the TV tab at once (Jump to
    → API keys · open the phone page). A mock check passes a TMDB token shaped like a JWT ("eyJ…", 100+ characters)
    and other keys of 20–64 letters and digits; "offline" in a key stands for an unreachable service. A check's answer
    is kept with the key and shown by whichever tab is open when it's due, so closing the phone page doesn't leave the
    TV on Checking, and a key replaced or removed meanwhile drops it. On the TV, the TV makes the check itself.
- **Account without a TMDB key**: Sign in gives way to "This TV has no TMDB key yet…" and **Add a TMDB key** (in First
  run: add it in the TMDB key step above).

#### Weather

r2-weather A, with r2-weather B's "now showing / quick picks".

- **Summary:** "Weather on Home", a card with the place (English name, then the local name, region and country) and
  the weather there now; **Place**: **Change city** and **Use default (New York)**; **Temperature**: °C / °F /
  TV default (°C), where TV default follows the TV's region (Sweden here). Changes reach Home at once.
- **Change city** (a sub-state, with a back crumb): a search bar with ghost completion, the 6×6 keyboard on the left
  and the matches on the right. With nothing typed the list shows **Now showing** (green) and **Quick picks**
  (New York, the default, and recent places). Typing finds places by their English or local name, accents ignored,
  Sweden first: "gothe" lists three Gothenburgs; "göteb" finds "Göteborg → Gothenburg" with a note that the TV asks in
  English and Swedish. OK on a match sets the place and returns to the summary on Change city; Back returns
  without changing it. Offline, the list keeps the quick picks and says searching needs the internet.

#### Theme

The look of the whole launcher, from the Figma file "CleanTV · Theme explorations" (`lwShqRaciMpDUDW5dYljNw`, page
Settings · Theme picker, frame `18:10`). The default stays Midnight, today's look, so nothing changes until someone
picks another theme.

- **Pane:** "Theme", "Colours, type and the picture behind Home. Rows, apps and data stay as they are.", then two
  groups of 156 × 100 swatches: **Classic** and **Seasonal · follows the date · today: Halloween** (today's period).
  Each swatch is drawn from that theme's own tokens: its sky, the horizon and a shelf of three mini tiles, the first
  focused with the accent ring, and "Aa" in its display face. Under them, a half-width row: **Typeface** (Theme
  font / System font; "System font uses Roboto"), and **Time of day** (Follow the sun / Day / Night, F29; "For Seasonal
  themes", F22). Moving details moved to Performance as Animated details. Moving highlights a swatch with the accent fill; OK selects it, it gets the accent ring, and the whole
  preview wears it at once. OK on a row switches its value. The pane enters on the selected theme.
- **Themes** (ids for `?theme=`): Classic, which keep their look all year: Midnight `midnight`, Daylight `daylight`,
  OLED Black `oled`, High Contrast `high-contrast`, Cinema `cinema`, Landscapes `landscapes`. Seasonal, which follow the
  date: Vector art `seasonal-vector`, 8-bit `seasonal-8bit`. Retro comes in a later PR. Nature was removed
  (2026-11-01): a saved `seasonal-nature` shows Vector art. A Seasonal theme's colours are its period's; its corners,
  line widths and type are its style's. Its section value is the style and the period: "Vector art · Halloween".
- **Time of day (F22):** a Seasonal picture follows the part of the day: dawn from 40 minutes before sunrise to 50
  after, sunset from 50 minutes before sunset to 40 after, day and night in between, from the sun at the mock's place
  (New York; on the TV, the Weather place). The picture changes when the sun moves on; the colours stay the period's.
  Day or Night keeps that picture all day (F29). The dev bar's Time menu, or `?phase=dawn|day|sunset|night`, puts the preview on a
  part of the day. `src/core/daylight.ts` is the launcher's `DayPhase`.
- **Snow and rain (F25):** with Launcher settings › Performance › Animated details on (off by default), snow or rain
  falls over a Seasonal picture while Home is idle: it starts 3 s after the last key and stops on the next one, so
  moving around Home never draws it. It falls by the real weather at the Weather place (the mock place's description;
  snow only in Snow, Thaw, Autumn, Halloween and Christmas) and by the season when offline (Snow and Christmas snow,
  Spring rain rains). It needs Motion Full and Backdrop picture = Photo. Vector art runs at 12 fps; 8-bit at 10 fps,
  one scene pixel per flake. The colour follows the part of the day. **Leaves (F31)** fall in Autumn and Halloween
  while it's dry (rain or snow at the place wins; offline they fall): tumbling ovals in rust, amber, brick and ochre,
  dimmer at dawn, sunset and night; in 8-bit, two scene pixels that turn over. The dev bar's Weather menu, or
  `?fall=snow|rain|leaves|off`, forces it. `src/core/weatherfx.ts`; the launcher's `WeatherLayer` follows the same rules.
- **Periods:** Snow 1 Jan – 28/29 Feb, Thaw March, Spring rain April, Bloom May, Pride June, Midsummer July, Late
  summer August, Autumn 1 Sep – 14 Oct and 3 – 30 Nov, Halloween 15 Oct – 2 Nov, Christmas December. The period is
  looked at again on the hour, so it turns over at midnight.
- **Fonts:** each theme has a display face (the greeting, the clock and page titles) and a UI face, from Google
  Fonts: Midnight Inter, Daylight Figtree, OLED Black Manrope, High Contrast Atkinson Hyperlegible Next, Cinema DM
  Serif Display and DM Sans, Landscapes Outfit, Vector art Baloo 2 and Nunito, 8-bit Press Start 2P and
  Pixelify Sans. Only the active theme's are fetched (and the swatches' display faces while the picker shows); the
  system font stands in until they arrive. **System font** puts every theme in Roboto, the TV's own.
- **Backdrops:** Midnight keeps the bundled evening landscape; OLED Black and High Contrast have none, only their
  background. The others are downloaded once from `launcher/backdrops/` in the repo (through jsDelivr; one constant,
  `BACKDROP_BASE` in `src/core/theme.ts`) and kept: `classic/daylight.webp`, `classic/cinema.webp`,
  `classic/landscapes-1.webp` … `-4.webp` (a new one every hour), `seasonal/vector-6/<period>-<phase>.webp` and
  8-bit's `seasonal/8bit/<period>[-<phase>].png` (no suffix for day), 160 × 90 pixels scaled up without smoothing. While a picture loads, when it can't be fetched, and offline when it never arrived, the theme's sky shows
  instead: a gradient from its sky to its horizon colour. Cinema adds a soft vignette.
- **Not taken from Figma:** shadows under tiles and the focus glow (Midnight has none today), and the photo or
  drawing inside the Seasonal swatches (the swatches show the sky instead).

#### The other panes

- **Home row apps:** not drawn in the design. Home's row as small tiles in order ("On Home · 7 of 7"), then the other
  apps. OK on a tile on Home picks it up, as Home's hold-OK Move does: it lifts with arrows on its sides, ← → move
  it, OK puts it down, ↓ takes it off Home (one must stay) and Back puts it back where it was. OK on another app adds
  it at the end, already picked up to place (the row holds seven). Home's row follows at once.
- **Performance** (the project lead's decision, 2026-11-01): "How much the launcher draws and moves", a **Light mode**
  button (first focus) that sets every choice to its lightest (Motion Off, card shadows Off, glow Off, Plain colour,
  animated details Off; each stays its own choice afterwards; the section's value reads "Light mode"), then a row per
  choice, its name and a line on the left and a small segmented control on the right: **Motion** (Full / Reduced /
  Off; Reduced drops slides, rises and focus scale-ups and keeps the fades, Home's scroll jumps and its dimming fades;
  Off stops every animation), **Card shadows** (On / Focused card only / Off), **Focus glow** (On / Off), **Backdrop
  picture** (Photo / Plain colour: the theme's sky without the picture) and **Animated details** (On / Off, off to start; the old
  Moving details: snow, rain and falling leaves over Seasonal pictures while Home is idle, F25, F31). A low-RAM TV starts at the light
  end (no card shadows, Motion Reduced, no animated details; glow and picture on) until the household chooses, and the
  pane says so; config.xml or config.json `light_defaults` can set or stop that per TV. The prototype starts with
  everything on but animated details; it applies Motion and the backdrop, and saves the card shadows and glow, which it doesn't draw.
- **Features** (F14, the project lead's decision 2026-11-01; no Figma frame): one build, with four features switched
  on or off at run time: TMDB account, Phone notes, Price lookup and Weather (her review of 2026-11-02 took out Pick
  for me and Themes as switches, and renamed Prices). **Every feature starts on** (the project lead, 2026-11-02, in
  place of "all off" the day before); First run sets each up and **switches off, when it ends, any whose setup isn't
  finished** ([First run](#first-run)). "Turn on what this household uses", then a row per feature laid out as
  Performance's, each with an On / Off control; a feature that is off never starts: no page and nothing fetched for
  it. Its Launcher settings section stays, reading Off, and every empty state a switched-off feature leaves has
  **Turn on**, which switches it on there and goes straight into its setup; Back returns to the page. Quick settings
  drops its row. A pushed config.json can set the switches too.
  ?features=on starts the preview with every feature on, ?features=off with none.
  - **TMDB account:** Movies, TV Shows and Search need it: while it is off, **or on but the TV isn't signed in**, they
    are an empty state alone, "Movies needs a TMDB account", with **Turn on** (off: switches it on) or **Sign in**,
    first focus; either opens Account's sign-in, and Back returns to the page. Signing in from the dev bar's Account
    menu shows the page at once.
  - **Phone notes** needs TMDB account (it puts the phone's list on TMDB): while that is off its row says "Off ·
    needs TMDB account" and ↑/↓ pass it by, and its section's pane says to turn on TMDB account first. Off, no import
    pill, and the TV doesn't listen.
  - **Price lookup** (Watchmode and Movie of the Night): off, every offer comes without its prices: cards and the title
    preview still say Rent or Buy and where ("Rent on Apple TV Store"), from TMDB's availability. Stores for rent and
    buy reads Off and its pane is the off state ("Price lookup is off", **Turn on**); in [API keys](#api-keys) the price
    keys wait behind the same off state while the TMDB key stays.
  - **Weather:** off, Home has no weather or forecast.
  - **Pick for me** is no switch: its button is there on Movies and TV Shows when TMDB account is on, the TV is signed
    in and the lists hold something to pick from. Otherwise it is hidden, and the page's own state says why ("needs
    a TMDB account", "Nothing on your lists yet", offline with nothing saved); the chips lead on to the nav.
  - **Themes** are always there: no switch, every theme, its fonts and its pictures.
  The section's value is "n of 4 on". ↓ goes to the next row's selected option and → stays within its control, as in
  Performance.
- **Open on start:** Home / Last app / Live TV, only saved.
- **Region** (F12; Family install step 2, the launcher's `Region`): "Where is this TV?", Sweden / United States as a
  segmented control, and a line on what that means: the country's streaming services and stores ("Hulu, Peacock,
  Fandango at Home, Google TV…" for the US), prices in its currency, and what TMDB says is on there. Sweden unless set,
  so no TV's default changes. Streaming services and Stores list the region's own (`pickServices`, `pickStores`). As in
  the launcher, each region keeps its own store ticks (a region not chosen before starts with every store ticked), and
  the streaming services are the household's in any region. The mock titles keep their Swedish offers and prices.
- **Default Home:** "This launcher is Home", "The remote’s Home button opens it", and **Choose Home app** (Android's
  Home app setting).
- **TV settings:** a note and **Open TV settings**, Picture, Network, Apps.
- **About:** "Clean your TV launcher", version and catalogue cache lines, the TMDB notice ("This product uses the
  TMDB API but is not endorsed or certified by TMDB.") beside a placeholder for TMDB's logo, the Open-Meteo,
  JustWatch and Apple credits, and **Open-source licences** (a stub page) and **Clear cache** (asks first).
- Choices are kept in `localStorage`; Reset state clears them.

### First run

V2 designs 01-setup B and r2-setup-checklist, made one page by F28 (the project lead, 2026-11-02). Shown on demand from
the dev bar (Jump to → First run). A page without the nav: "First run", "Set up your Home screen", "Everything has a
sensible default. Move right to open a row, or start right away.", and four rows, each with its number, title, a line
under it, the current value and a pill: Features ("4 of 4 on", the features on, Suggested), Region ("Services, stores
and prices", Sweden, Suggested; F12, before the two it decides), Streaming services ("8 ticked", "Netflix, Prime Video
+6", Suggested), Stores for rent and buy ("Apple TV Store, Amazon Video +3", Suggested). A value that only repeats its
pill is left out. Below them **Start using Home** and "The same pages are in Launcher
settings."; on the right a help pane for the focused row.

- **One page, the checklist as tabs** (F28): the checklist starts wide and focused. **→ on a row** (or OK) opens that
  step's panel on the same page, focus in it: the checklist shrinks to a column of tabs on the left (the title smaller,
  the lead, values, pills, chevrons and help pane gone) and the panel takes the right, fading in. The panel is the
  same pane as Launcher settings, with a footer: "Step 2 of 4 · changes save at once" and **Next: Streaming services** (the last
  step: **Done**). No new page opens, and nothing needs OK to open.
- **While a panel is open:** ↑ and ↓ on the tabs show each step's panel at once, as Launcher settings' sections do
  (shown is not done); → enters it; ← from the panel's left edge, or Back in it, returns to its tab, which stays lit
  while its panel has focus. **Back on a tab** closes the panel and widens the checklist again, focus on that row.
  **Done** on the last step widens it too, focus on the first row not done, or Start.
- A step is done once its panel was entered (→, OK or Next): a green check in place of its number, no pill. When every
  row is done the page reads "You’re all set" and the help pane for Start says "Your Home screen is set up.", with the
  Settings icon hint.
- **Features** (F14, the project lead 2026-11-02): every feature starts on, and each has its setup as a tab under
  Features (indented, its icon, or a green check once set up, and its value or "Not set up yet"): TMDB account → Sign
  in to TMDB, Phone notes → its pairing, Price lookup → Price keys, Weather → Weather city. Features' panel is
  Launcher settings' Features pane, headed "Everything starts on, and each is set up next. Switch off what this
  household won't use; one not set up when you finish is switched off." **Next** leads from Features through each
  setup tab, then to Streaming services; a setup's footer has **Back to Features** (on that feature's switch) and
  Next. Turning a feature on again opens its tab at once; switching one off removes its tab. **What counts as set up**
  (`featureSetUp`): TMDB account, the TV is signed in; Phone notes, TMDB account set up and the TV listening (Phone
  notes' own switch on: the TV can't tell a phone has paired until it sends a note); Price lookup, the Watchmode and
  Watchmode key working (F16; Movie of the Night is optional); Weather, a place
  set (the TV's default counts). **When First run ends** (Start, Back, Home), every feature on but not set up is
  switched off, and a toast names them ("Not set up, so switched off: …"); Start's help pane lists them beforehand.
  Price keys is F16's price keys pane ([API keys](#api-keys), Watchmode and Movie of the Night). On a TV that had no
  working TMDB key when First run opened, TMDB account gets a **TMDB key** tab before Sign in to TMDB, done once the key
  works. With Price lookup off, the Stores step's row reads "Price lookup off" (a muted pill, no names) and its panel
  is the off state, whose Turn on switches it on and opens Price keys. A setup tab stays while its feature is on, so it
  can be opened again; switched off, it goes. On the TV, TMDB account and Phone notes rebuild the launcher before their
  setup opens. There is no other on/off in First run: Sign in to TMDB and Weather city are no longer rows of their own.
- **Start using Home** goes Home; so does Back on the wide checklist.
- Focus: the first row; ↓ from the last row reaches Start; ← on a row stays, and ← and → on Start stay on it.

### All Apps and TV Utilities (F35)

- Home's All Apps shortcut opens every installed app (the mock TV's: the streaming apps, a few the household
  installed, and what a TCL TV comes with), six to a row, sorted by name, under a count. OK opens an app.
- **TV Utilities**, a folder tile last in the grid (Rebecca, F35 Q2), holds the apps the TV came with that aren't watch
  apps, on every brand (Rebecca 2026-11-02: on for every TV, no switch). The rule (`src/core/tvUtilities.ts`; on the
  TV, `TvUtilities`): an app goes in when it came on the system image, or comes from a TV maker's package (`com.tcl.`,
  `com.sony.`, `org.droidtv.` and so on), unless it is a watch app the launcher knows (the streaming services and
  stores, Play Store, Google's YouTube and media apps). It is only about where an app came from, so an app on Home's
  row stays in it (F35 Q4). The tile is a folder, with a tab on its top edge and a gear in the middle, drawn in code in
  the theme's colours (F41, `src/ui/folderArt.ts`; on the TV, `FolderArt`), and the focus ring follows its outline;
  under it, "TV Utilities · 13". OK opens it as a page like All
  Apps, and Back returns to the tile. It hides when empty.
- Hold OK on an app: Add to Home or Remove from Home, Move to TV Utilities or Move out of TV Utilities, and App info.
  A move is kept on the TV (`utilityMoves`); one that matches the rule is forgotten, so the rule takes over again. An
  app moved out of the folder hands focus to the one that took its place, and the folder page closes when it empties;
  one moved in focuses the folder tile. Eros Now starts in the folder (it came with the TV and the launcher doesn't know
  it) to show the move out.
- Dev bar: Jump to → All Apps, or All Apps › TV Utilities.

### Stubs

Weather, Open-source licences and the person page are placeholders. The person page lists
the titles the person appears in.

## Visual system

From `src/styles/tokens.css`, taken from the Figma frames. This is Midnight, the default theme; `src/styles/themes.css`
overrides the same tokens for the others ([Theme](#theme)), so styles use tokens rather than literal colours, radii
and line widths.

| Token | Value |
|---|---|
| Background | `#090E15`, raised `#0D141D`; pages sit on the evening landscape image under an 86–90% dark shade |
| Surfaces | white at 5%, 9% and 14%; lines white at 9% and 18% |
| Text | `#F4F5F7`, secondary `#ADB5C0`, tertiary `#7D8999`, ghost `#5E6878` |
| Accent / focus | `#C4DDF7`; text on accent `#101820`; selected chips use the accent at 14% with an accent border |
| Active nav item | white at 91% with dark text |
| Included / connected | `#8FD9A8` (badge background at 16%); also the Cheapest tag and its price |
| Score star | `#F2C46D` |
| Offline and errors | the star's amber: pill background at 12%, error box at 8% with a 30% border |
| Skeletons | white at 6–7%, radius 8 (art keeps its card's radius), static |
| Type (Inter) | 84 title, 44 page heading, 30, 26, 24 body, 20 small, 18 minimum; other themes have their own faces ([Theme](#theme)) |
| Radii | 8, 12, 16, 24, pills 100 |
| Focus | accent fill, or a 4 px accent ring on cards and art, with a 3–8% scale-up |
| Motion | 140 ms for focus changes; 320–360 ms for page and popup transitions |

Card sizes: library cards 417 × 172 art; search and row cards 271 × 152 art; Pick for me tiles a quarter of the
width with 243 px art.

## What is mock, and open questions for the build

- **Data source:** where catalogue, availability, prices, artwork and cast come from, and how they are cached. The
  prices and states here are shaped like the launcher's V2 data (TMDB availability for Sweden, prices per quality);
  the values are made up.
- **Deep links:** "Watch on …" and Episodes assume each service can open a title or episode directly.
- **Setup:** the checklist and its panes are built. In the launcher First run shows by itself once, after a fresh
  install (never after an update), and keeps its done rows; a row counts as done once its pane has been opened.
- **Sign-in:** TMDB's approval link is too long to type, so the QR is the only way in; whether to host a short
  redirect is open. The code is polled only while Launcher settings is open.
- **Wi-Fi switching:** joining a saved network from the dropdown may need system permissions a launcher lacks;
  fall back to opening Wi-Fi settings.
- **Phone notes:** the address assumes the TV keeps its IP (a DHCP reservation in the router); what Apple Notes and
  Keep put in the shared text (checklist ticks, the note's title) is to be checked on the phones.
- **API keys:** the checks and the phone page's sending are mocked (`src/core/keys.ts`); the TV makes one real
  request per key. The port (8766, beside Phone notes' 8765) and the QR's address are the TV's own.
- **Not designed yet:** hold-OK menus in Figma (the base uses them for tile Move / Remove / App info and a Settings
  menu; the title menu is built here without a design), the screensaver, removing recent searches, voice search,
  progress on started titles, the Sort chip, a
  person page design, Search and Home's weather while loading, the Home row apps pane (built here without a design),
  and right-to-left layouts (the base supports Hebrew).

## Prototype code

| Path | What it is |
|---|---|
| `src/core/app.ts` | Screen stack, layers, Back, keys, toasts, tab transition, stage scaling |
| `src/core/focus.ts` | Spatial D-pad navigation; `data-group` remembers focus per row or grid |
| `src/core/fuzzy.ts` | Search matching: subsequences with word-start and run bonuses; 1–2 typo tolerance for words of 4+ letters |
| `src/core/store.ts` | Household state: My list, Seen, Favorites, recent searches, recently viewed |
| `src/core/theme.ts` | The themes, the Seasonal periods by date, and what applies one to the page: `data-theme` and `data-period` on `<html>`, its fonts and its backdrop |
| `src/core/account.ts` | The mocked TMDB sign-in and lists, behind the Account pane and the dev bar's Account menu |
| `src/core/lists.ts` | The lists Movies, TV Shows and Pick for me show: My list, then the account's shown TMDB lists (or the mock Household and Friends before a sign-in) |
| `src/core/notes.ts` | Phone notes: on/off, the pairing code, each list's last send, and the simulated import behind the import pill |
| `src/core/keys.ts` | API keys (F16): each key's state and last four characters, the code, the mock check; shared with the phone page through localStorage |
| `keys.html`, `src/keysPage.ts` | The phone and laptop page the API keys QR opens (a second Vite page) |
| `src/core/net.ts` | The simulated network and data state behind the dev bar's State menu |
| `src/data/` | Mock catalogue, services (and the services and stores to tick), weather places, and offer rules in kronor (`bestOffer`, `offersByService`, `cardLine`, `priceLines`, `cheapestStore`, `offersOf`) |
| `src/screens/` | One module per screen; `dropdown.ts` holds the top-bar menus, `quickSettings.ts` the Settings icon's, `rent.ts` the rent/buy dialog, `settings.ts` Launcher settings, `setup.ts` First run |
| `src/screens/panes/` | Launcher settings' panes, shared with First run: account, services and stores, weather, Home row apps, theme, and the rest |
| `src/ui/` | Nav, buttons, chips, segmented controls, cards (and their skeletons), icons, and the look-alike QR |
| `src/styles/` | `tokens.css` (the visual system, Midnight), `themes.css` (the other themes, from the Figma variables), `base.css`, `screens.css` |
| `public/img/` | Artwork exported from the Figma file |

In development builds, `window.app` exposes the screen stack and `window.net` the data state in the browser console
(`net.set('offline')`).
