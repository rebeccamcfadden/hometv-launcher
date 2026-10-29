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

The 1920×1080 TV screen is scaled to fit the window. The keyboard stands in for the remote:

| Key | Remote |
|---|---|
| Arrow keys | D-pad |
| Enter | OK |
| Esc, or Backspace when nothing is typed | Back |
| H | Home (except on Search, where letters type) |
| S | The remote's Settings key (except on Search) |
| Letters, digits, space | Type on Search |

The mouse works too: a click is focus + OK. The bar under
the stage shows the screen stack (what Back returns through), a **Jump to** menu, and **Reset state**, which
clears My list, Seen, Favorites and recent searches (kept in `localStorage`).

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
and the Seen icon (an eye, so it no longer matches My list's tick).

## Product decisions

- **V1 is lists-only.** Movies and TV Shows show the household's imported watchlists (My list, Household,
  Friends). The Explore versions (Trending, New releases and so on) are parked, not cut.
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
- **Nav switches on OK.** Moving along Home, Movies, TV Shows, Music and Search only highlights a section; OK
  switches to it. Switching on focus alone was tried and dropped.
- **Top-bar buttons keep the base launcher's behaviour**, except Wi-Fi: Inputs opens the base's Inputs dropdown;
  Settings opens the TV's system settings; the remote's Settings key opens the base's quick-settings panel.
  Wi-Fi gets a new dropdown (the base opens Wi-Fi settings directly).
- **Paid titles ask before leaving the launcher.** Renting or buying opens the store's app, after a dialog.
- **Network features are allowed** in this fork (see [AGENTS.md](../AGENTS.md#this-fork)), for catalogue
  search, availability, prices, artwork and cast. Analytics and telemetry are not.

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
| Page | Home, Movies, TV Shows, Music, Search, Pick for me, person | Full screen with the nav at the top |
| Popup | Title preview | Over the page it came from; a 58% dark scrim covers the whole page, nav included |
| Dialog | Filters drawer, rent/buy dialog, dropdown menus | Over whatever is showing, which stays visible |

### Back

1. Closes the top dialog or popup (dropdown, drawer, title preview).
2. Otherwise returns to the previous page (Pick for me → Movies; person → title preview).
3. On a top-level page other than Home it goes Home, with focus on Home in the nav.
4. Search uses Back first to collapse the full results back to the keyboard.

### Focus

- Moving with the D-pad picks the nearest item in that direction; moving up or down keeps left edges aligned so a
  column feels like a column.
- Each row or grid remembers its last focused item, so leaving and coming back returns to the same item.
- Each page keeps its focus and state when you switch tabs and come back.
- The first focus on a page: Home → the first app; Movies / TV Shows → the first title; Search → the "a" key;
  title preview → the Watch button; dropdowns → the first row; dialogs → the primary button.

### Nav

- Order: Home, Movies, TV Shows, Music, then a search icon; on the right, Wi-Fi, Inputs and Settings icons.
- ←/→ on the nav stay on it and move to the next item: ← from Wi-Fi is always Search, never something on the page
  below, and past either end nothing happens.
- Landing on a section highlights it; OK switches to it (OK on the current page's section does nothing). Down
  enters the page. Landing back on the nav from a page puts focus on that page's own section.
- Switching animates: the new page's content slides 90 px in from the side of the tab you switched towards and fades
  in (360 ms); the old content slides 60 px the other way and fades out (240 ms). The nav stays still. With
  reduced motion, pages switch without animating.

### Top-bar dropdowns

All three hang under their button, grow inward from the right, and close on Back, on OK on a row, on a click
outside, or when the D-pad moves past the menu's edge. Focus returns to the button; a move past the left, right or
bottom edge carries on one step from the button, so → in the Wi-Fi menu lands on Inputs. (Closing on the D-pad was
decided after the first run on a real TV: closing with Back alone felt clunky.)

| Opened by | Rows |
|---|---|
| Inputs icon | HDMI 1–3 and named HDMI-CEC devices with a green "Connected" mark, then AV, then Live TV, divided |
| Wi-Fi icon | Header with the network, "Connected · Internet OK · Strong signal"; saved networks; Other networks; Wi-Fi settings |
| Remote Settings key | TV system settings, Picture, Network, Apps ("Re-enable"), Launcher settings |
| Settings icon | No menu: opens the TV's system settings |

## Screens

### Home

- **Resting:** greeting ("Good evening"), date, a large clock with AM/PM, today's and tomorrow's forecast, and a
  row of seven app tiles (Netflix, YouTube, Prime Video, Disney+, Max, Apple TV+, Spotify).
- **Scrolled:** moving down from the apps slides the page up to Inputs (Live TV, HDMI 1–3), Shortcuts (All Apps,
  Launcher settings, TV Settings) and an hourly Weather strip, and dims the background. Launcher settings opens the
  same page as the last row of the quick-settings dropdown. (The Figma frame still shows an Explore shortcut here;
  it was replaced because Explore is parked.)

### Movies and TV Shows (V1)

- Title, "Your collection · My list, Household & Friends", and a **Pick for me** button.
- Scope line ("3 imported watchlists · All 7 services") and the active filters with a **Filters · n** button.
- List chips with counts: All lists, My list, Household, Friends.
- A four-column grid of title cards: art, name, service and "Included" or a price, runtime or seasons · rating ·
  score, and which list and genre it comes from.
- **Filters drawer** (from the right): Availability, Review score, Genre, Length (movies only), Watch state. ←/→
  changes a value, and the grid behind updates live. Clear all and Done. The default is "Included only · Review
  7.0+".
- **Empty states:** a list with nothing on it ("add titles with + My list…"), and filters that hide everything
  (with Clear filters).

### Pick for me

- **Setup:** choose from My watchlists or My services; pick one or more lists; Movies or TV Shows; six fields that
  OK steps through (length, genre pair, review score, content rating, watch state, availability). A card on the
  right shows how many picks match ("4 picks, just for tonight") and the **Find picks** button.
- **Results:** four random matches with a short blurb each, the filter line, Reroll, and Adjust filters (Back to
  setup). A bar under the tiles shows the focused pick with **Title details** and **Open in <service>**.

### Title preview (Option C)

- A 1728×900 popup over the page it came from. A Back button sits top-left on the art.
- **Header:** title (84 px), then year · genres · runtime (or seasons) · rating badge · ★ score.
- **Actions,** on the right of the header:
  - **Watch:** "Watch on Prime Video" when included; for shows, "Watch S1 E3 on …" (the next episode); otherwise
    "Rent on Apple TV · $3.99". The offer used is the included one, else the cheapest rental, else the cheapest
    purchase.
  - **My list, Mark seen, Favorite:** toggles that show an "on" state and a short confirmation. Icon-only until
    focused, then they show their label.
  - **Trailer:** opens the trailer (YouTube) outside the launcher.
- **Tabs:** Overview, Episodes (shows only), Where to watch, Cast & crew, More like this. Landing on a tab shows
  it; the content area is a fixed height so the card never jumps.
  - **Overview:** synopsis; "Included on Prime Video · Rent from $3.99 elsewhere"; starring; director or creator.
  - **Episodes:** seasons stacked on the left (landing on one shows its episodes); episodes in a row with
    runtime, watched ticks and an "Up next" badge.
  - **Where to watch:** one card per service, included ones first, with "Included" or the rent and buy prices
    and the quality. OK on an included service opens it; on a paid one, the rent/buy dialog.
  - **Cast & crew:** round portraits (initials for now) with role; OK opens the person.
  - **More like this:** similar titles; OK opens their preview on top.
- **Rent/buy dialog:** "Arrival on Apple TV", the quality, a Rent and a Buy button with prices, Cancel, and a note
  that payment finishes in that app.

### Search

- **Bar:** full width under the nav. While typing it shows the query and the ghost completion ("arvl → arrival")
  and a "› Results" hint.
- **Left panel (440 px):** a list above the keyboard, then the 6×6 keyboard. The list is Recent searches (up to
  five) when the bar is empty, and Suggestions (four, mixing titles, people and genres) while typing. Recent
  searches are saved when a result is opened.
- **Empty state:** Recently viewed, From My list and Browse by genre, in rows that run off the right edge.
- **Typing:** the results preview sits right of the panel: "N results", filter chips (All, Movies, TV shows,
  People, Included only) and a grid of cards that runs off the right edge. The preview can't be focused yet.
- **Full results:** → from the keyboard's right edge slides the panel away and widens the same grid to six
  columns, with focus on the first result. Same cards, same size, same header, so it reads as the preview
  expanding. Up from the chips reaches the search bar; OK on it, Back, or ← from the first column bring the
  keyboard back with the query kept.
- **No matches:** "No matches for “qzxv”", a tip, and Recently viewed.
- In the prototype, typing on the computer's keyboard also types into the bar; on the TV, a remote or app with a
  keyboard should do the same.

### Stubs

Music, Launcher settings, All Apps, Weather and the person page are placeholders. The person page lists
the titles the person appears in.

## Visual system

From `src/styles/tokens.css`, taken from the Figma frames.

| Token | Value |
|---|---|
| Background | `#090E15`, raised `#0D141D`; pages sit on the evening landscape image under an 86–90% dark shade |
| Surfaces | white at 5%, 9% and 14%; lines white at 9% and 18% |
| Text | `#F4F5F7`, secondary `#ADB5C0`, tertiary `#7D8999`, ghost `#5E6878` |
| Accent / focus | `#C4DDF7`; text on accent `#101820`; selected chips use the accent at 14% with an accent border |
| Active nav item | white at 91% with dark text |
| Included / connected | `#8FD9A8` (badge background at 16%) |
| Score star | `#F2C46D` |
| Type (Inter) | 84 title, 44 page heading, 30, 26, 24 body, 20 small, 18 minimum |
| Radii | 8, 12, 16, 24, pills 100 |
| Focus | accent fill, or a 4 px accent ring on cards and art, with a 3–8% scale-up |
| Motion | 140 ms for focus changes; 320–360 ms for page and popup transitions |

Card sizes: library cards 417 × 172 art; search and row cards 271 × 152 art; Pick for me tiles a quarter of the
width with 243 px art.

## What is mock, and open questions for the build

- **Data source:** where catalogue, availability, prices, artwork and cast come from, and how they are cached.
- **Deep links:** "Watch on …" and Episodes assume each service can open a title or episode directly.
- **Setup:** importing watchlists and choosing the household's services is not designed, yet every list and
  "Included" label depends on it.
- **Wi-Fi switching:** joining a saved network from the dropdown may need system permissions a launcher lacks;
  fall back to opening Wi-Fi settings.
- **Not designed yet:** hold-OK menus (the base uses them for tile Move / Remove / App info and a Settings
  menu), offline / loading / error states, first run, the screensaver, removing recent searches, voice search,
  progress on started titles, sorting lists, a person page design, and right-to-left layouts (the base supports
  Hebrew).

## Prototype code

| Path | What it is |
|---|---|
| `src/core/app.ts` | Screen stack, layers, Back, keys, toasts, tab transition, stage scaling |
| `src/core/focus.ts` | Spatial D-pad navigation; `data-group` remembers focus per row or grid |
| `src/core/fuzzy.ts` | Search matching: subsequences with word-start and run bonuses; 1–2 typo tolerance for words of 4+ letters |
| `src/core/store.ts` | Household state: My list, Seen, Favorites, recent searches, recently viewed |
| `src/data/` | Mock catalogue, services, and offer rules (`bestOffer`, `offersByService`) |
| `src/screens/` | One module per screen; `dropdown.ts` holds the top-bar menus |
| `src/ui/` | Nav, buttons, chips, cards and icons |
| `src/styles/` | `tokens.css` (the visual system), `base.css`, `screens.css` |
| `public/img/` | Artwork exported from the Figma file |

In development builds, `window.app` exposes the screen stack in the browser console.
