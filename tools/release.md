# Releases for family installs

`tools/release.ps1` builds the launcher as a public APK, signs it with the release key and puts it on a draft GitHub
Release, with a `version.json` and a `SHA256SUMS` file. Family and friends install from there (BACKLOG.md, Family
install), and later their launcher updates itself from there. It runs on the project lead's Windows PC, never in CI.

A public build has no keys in it: `-PpublicRelease=true` builds from `launcher/tv.properties.example` alone, and Gradle
stops if that file holds a key or a shortcut link. Each household puts its own keys in on its TV. A public build
also leaves the temperature unit to the TV's region and the weather place to First run.

Only this script signs with the release key. Every other build, `launcher/build-and-install.ps1` included, keeps the
debug key of the machine it was built on. So the project lead's own TV stays on her debug builds. An APK signed with
one key cannot install over an app signed with the other.

## Once: the release key

```powershell
tools\release.ps1 -NewKey
```

It asks for a password (at least 12 characters) twice. Then it makes `%USERPROFILE%\.hometv\hometv-release.jks`
and `release-signing.properties` beside it, outside the repo, and writes the key's certificate SHA-256 to
`tools/release-cert.txt`. Then:

1. **Back up the folder** `%USERPROFILE%\.hometv` in two places, for example a USB stick and a password manager's file
   attachment. Keep the password in the password manager.
2. **Commit `tools/release-cert.txt`** through a pull request. Releases are checked against it, and the family
   installer will pin it.

The key is never replaced: `-NewKey` refuses when one is there. **Losing the key or its password means no family TV
can take an update again.** Each one would have to remove the launcher, losing its lists, ticks and sign-in, and
install it fresh.

## Each release

1. In a pull request, raise `versionCode` (by one) and `versionName` in `launcher/app/build.gradle`. Android installs
   an update only when its `versionCode` is higher than the installed one's.
2. Merge it, then on `main`, up to date with `origin/main` and with no changes:

   ```powershell
   tools\release.ps1 -DryRun   # build, sign and check; nothing is uploaded
   tools\release.ps1           # the same, then a draft release
   ```

   `gh` must be signed in with the account that owns the repo (or `$env:GH_TOKEN` set for it).
3. Open the draft on GitHub, check its files, and publish it. Publishing makes the tag (`v<versionName>`).

## What it checks

Before building:

- The checkout is clean, and HEAD is `origin/main`. A dry run skips the `origin/main` check.
- `v<versionName>` has no release yet.
- `versionCode` is above the last release's.
- The key in `%USERPROFILE%\.hometv` is the one `tools/release-cert.txt` names.

The build runs `testReleaseUnitTest lintRelease assembleRelease` from clean. After it:

- `apksigner` must find exactly one signer, with the pinned certificate.
- None of this machine's `tv.properties` keys or its shortcut link may appear anywhere in the APK. Only the
  setting's name is printed, never its value.

The password reaches Gradle and `keytool` only through this PowerShell process's environment, and is cleared when it
ends.

## What a release holds

| File | What it is |
|---|---|
| `hometv-launcher-v<versionName>.apk` | The public APK, signed with the release key |
| `version.json` | `versionCode`, `versionName`, the APK's name and SHA-256, the certificate's SHA-256, `minSdk`, the commit |
| `SHA256SUMS` | Each file's SHA-256, in `sha256sum` format |

The family installer (Family install step 4) will be added to these files when it is written.

The files are also kept in `%USERPROFILE%\.hometv\releases\v<versionName>\`.
