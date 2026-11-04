# packages/core

HomeTV's logic that doesn't depend on a screen: shared by [design_preview/](../../design_preview) and by HomeTV for
Samsung TVs (a separate repository, which takes this folder at a pinned commit). The Android launcher's Java stays
the reference for behaviour.

| File | What it is |
|---|---|
| `src/types.ts` | Titles, offers, services: the catalogue's shapes |
| `src/services.ts` | The streaming services and stores, which to tick per region, the Home row's default apps |
| `src/fuzzy.ts` | Search matching (subsequences, word starts, one or two typos) and `createSearch(titles, genres)` |
| `src/keys.ts` | The API keys: what each is for, the words for its state, the section's value, the pairing code's letters |
| `src/tokens.css` | The design tokens (Midnight); `design_preview/src/styles/themes.css` overrides them per theme |

## Rules

- **No DOM, no storage, no network, no dependencies.** Callers pass what the logic needs (the catalogue to search,
  the keys' states). Storage, mocks and screens stay with each caller.
- **Runs on 2018 Samsungs (Chromium 56):** no built-ins newer than that (`matchAll`, `padStart`,
  `Object.fromEntries`, `flat` and the like). Syntax is fine: each caller's bundler lowers it.
  `test/chromium56.test.ts` checks the source.
- **Imports inside the package end in `.ts`,** so Node runs the tests without a build.

## Use

- design_preview imports it as `@hometv/core/<file>` (an alias in its `tsconfig.json` and `vite.config.ts`).
  `design_preview/src/types.ts` and `src/data/services.ts` re-export it, so older imports keep working.
- Tests: `cd packages/core; npm test` (Node 22.18 or newer, which runs TypeScript directly).
