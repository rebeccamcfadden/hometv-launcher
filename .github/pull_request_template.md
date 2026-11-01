<!--
Launcher rework PR. Read docs/rework.md first; one milestone, or one topic, per PR.
Fill in every section. Write "n/a" where a section doesn't apply, and say why.
-->

## What and why

<!-- One or two sentences. -->

**Milestone:** <!-- e.g. M2 · Home (docs/rework.md#milestones) -->

**Spec:** <!-- the prototype screen(s) and Figma frame(s) this implements -->

**Differences from the prototype or Figma, and why:** <!-- or "none" -->

## Real or mock

- **Wired to a real source:** <!-- e.g. installed apps, TvInputManager, Open-Meteo -->
- **Mock or fallback:** <!-- each one has an entry in BACKLOG.md -->
- **BACKLOG.md entries added or removed:** <!-- or "none" -->

## Tests

| | Tests |
|---|---|
| Added | <!-- test class or file, and what it covers --> |
| Changed | <!-- and why --> |
| Not needed | <!-- what has no test, and why --> |

**Local CI:** <!-- `tools/ci-local.ps1 -Pr <n>` at the head you are asking to merge: python, tables, launcher. GitHub's CI runs only after the merge. -->

**Commands run, with results:**
<!-- Each exact command and whether it passed, e.g.
`./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease` (in launcher/): passed, 0 lint errors
`npm run build` (in design_preview/): passed
`python -m unittest tests.test_agents_md`: passed -->

## Where it was checked

- [ ] Emulator: Android TV 9 (API 28), 1920×1080
- [ ] Real TV, after the project lead said it was free: <!-- model, Android version, firmware build; or "not yet" -->
- [ ] D-pad from every edge (Up, Down, Left, Right); OK and Back follow the spec's Back rules
- [ ] Focus is always visible, and text and focus stay inside the title-safe area
- [ ] With the network off, Home and local content still work

## Screenshots or video

<!-- Every visual change: a capture from the emulator or the TV, next to the prototype screen it implements.
A short video for flows (focus movement, transitions, Back). No Wi-Fi names, account details or IPs. -->

## Size, dependencies and permissions

- **APK size:** <!-- before → after, in bytes -->
- **New dependencies:** <!-- name, what it is for, size cost; or "none" -->
- **New permissions:** <!-- name and why; or "none" -->

## Safety

- [ ] The TV safety rules, the Home guard, the protected packages and the safety checks in `tools/` are untouched,
  or the project lead agreed to the change
- [ ] No secrets or identifiers (TMDB key, coordinates, IPs, Wi-Fi names, serials) in code, commits or screenshots
- [ ] No analytics or telemetry