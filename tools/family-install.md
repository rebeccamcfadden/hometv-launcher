# Family installer

`tools/family-install.ps1` puts the HomeTV Launcher on the Android TV of family or friends from their Windows laptop,
with no Python, JDK or Android SDK, while the project lead helps on a call (BACKLOG.md, Family install step 4). It
uses Google's own `adb`, so it can pair with Wireless debugging, which `tools/tv.py` cannot.

It works on Android TV and Google TV sets and boxes running Android 8 or newer. It cannot work on Samsung (Tizen), LG
(webOS) or Fire TV, and stops on a Fire TV.

## Getting it

Always take it from a published release, never from `main`. `tools/release.ps1` stamps the release's copy with its
tag, the APK's name and SHA-256 and the release certificate's SHA-256 ([release.md](release.md)), so that copy
installs exactly that release's APK and nothing else. The copy in the repository is not stamped and refuses to
install without `-ApkPath` and `-CertSha256` (for testing).

1. From the release page, download `family-install.ps1` and `SHA256SUMS`.
2. Check the file: `Get-FileHash .\family-install.ps1` must print the SHA-256 that `SHA256SUMS` lists for it.
3. Run it:

   ```powershell
   powershell -ExecutionPolicy Bypass -File family-install.ps1
   ```

When SmartScreen or a browser blocks the download, the last resort, only with the project lead on the call, is to
fetch the same file from the tagged release in PowerShell and run it the same way:

```powershell
irm https://github.com/rebeccamcfadden/hometv-launcher/releases/download/<tag>/family-install.ps1 -OutFile family-install.ps1
```

Don't pipe it into `iex`: when the script ends it would close the window, and the last message with it.

## What it does

In this order, and it stops at the first step that fails, saying what was and wasn't changed:

1. **Downloads and checks.** Google's platform-tools at a pinned version, checked against the SHA-256 written in the
   script, and the release's APK, checked against the stamped SHA-256. It then reads the APK's signing block and stops
   unless it has a v2 block, and every scheme block present (v2, which Android 8 reads; v3 and v3.1, which newer Android
   reads) names exactly one signer whose certificate is the release key's. The stamped SHA-256 is the trust anchor;
   this check catches an APK from anywhere else.
2. **The TV.** It first asks what is plugged into the TV's USB ports and whether the network comes through a USB
   adapter (some boxes switch that adapter off as soon as USB debugging is on, so then it has the TV join Wi-Fi
   first). Then it walks the person through Developer options and USB debugging, and connects on port 5555, or pairs
   with Wireless debugging's code and connects to its port.
3. **Checks.** It reads the maker, model and Android version, and stops below Android 8 (`minSdk` 26) and on a Fire TV.
4. **Backup, before any change.** The package list, the disabled list, `getprop`, the three settings tables and
   the Home candidates go to `%USERPROFILE%\HomeTV\backups\<model>-<time>\`, with `undo.txt`. Each change gets its
   undo line in `undo.txt` before it is made. It also keeps a copy of itself in `%USERPROFILE%\HomeTV`.
5. **Which home screen hands over.** It applies the rules of `launcher/build-and-install.sh`: a stock launcher it
   knows (a test keeps its list equal to that script's), Android's chooser with one known stock launcher among the
   candidates, or Home already on HomeTV (then it only updates). Anything else stops it, with nothing changed.
   - **Google TV:** Home needs `launcherx` and the setup app (`setupwraith`) switched off. Before anything changes, it
     reads who owns the saved Wi-Fi networks (`dumpsys wifi`, `cname=`). If setupwraith owns one, or the owners
     can't be read, it stops: switching setupwraith off would delete that network and take the TV off Wi-Fi. There is
     no override. It also warns that phone casting usually stops (SHOWCASE.md, Cast Fix), and stops if they cast and
     don't want to lose it.
   - **CVTE boards** (any `com.cvte.*` package): the stock launcher stays enabled. HomeTV is set as Home, the TV
     restarts once, and Home and ADB must come back (#741). No key helper goes on in that pass, not even one the
     force stop below switched off.
6. **The Home guard.** The order of `build-and-install.sh`: install on its own (`pm install -r`, after a push), so a
   failed install never reaches the Home steps. Then stop the launcher and put a key helper back, which the force
   stop switched off (#1101; not on CVTE). Then `am start -W` must answer `Status: ok` (#239). Only then does it
   disable the stock launcher (one package per shell call, each checked in `pm list packages -d`) and set Home. A
   refused disable puts everything back and stops. It then checks that Home resolves to HomeTV and that a Home
   press really shows it (`dumpsys activity activities`). Otherwise it re-enables what it disabled, gives Home back
   to the stock launcher and says so. If even that fails, it points at `-Restore`.
7. **First run**, after a fresh install only: the last command it sends the TV opens First run
   (`com.hometv.launcher.action.FIRST_RUN`), as `build-and-install` does, and nothing presses Home after it. First
   run shows by itself on the first Home start after an install, and the guard's checks would end it. The person goes
   through it before debugging goes off.
8. **Debugging off.** It walks them through revoking the authorisations, switching debugging off and restarting the
   TV. Only a refused connection on the debugging port counts as off: no answer at all (a TV that is off, or out of
   reach) is "not confirmed". Wireless debugging gets a new port after a restart, so for that the person reads it on
   the TV and says it is Off.

It runs its own adb server (port 5038), so another `adb` on the laptop is left alone, and stops it however the run
ends.

It never uninstalls anything, never disables anything but the stock launcher (and setupwraith on Google TV), asks for
no API keys (they go in on the TV: Launcher settings > API keys, or First run), starts no transcript and sends nothing
anywhere but the TV.

Not in it yet: the remote-key helpers by brand, the in-app updates grant (Family install step 5) and the debloat
(step 6).

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Installed and Home, debugging off; or the person chose to stop before any change |
| 1 | Stopped; the message says what was changed, usually nothing |
| 2 | Installed and Home, but debugging is not confirmed off: the port still answers, or the laptop could not reach it, or Wireless debugging was not confirmed Off on the TV |

## Undoing it

With debugging on again, from the backup folder it wrote:

```powershell
powershell -ExecutionPolicy Bypass -File family-install.ps1 -Restore "$env:USERPROFILE\HomeTV\backups\<model>-<time>"
```

It runs `undo.txt`'s lines from the bottom up: the stock launcher is enabled again and given Home back. The launcher
itself stays installed. Then it checks that Home is on the stock launcher again; if an undo line failed or Home is
elsewhere, it lists what is left and exits 1, and debugging stays on until that is sorted. A TV on Wireless debugging
gets a new port after a restart, so pass the current one with `-TvHost` and `-AdbPort`.

## For the helper

| Parameter | What for |
|---|---|
| `-TvHost`, `-AdbPort` | The TV's address and network ADB port (5555 unless that firmware uses another) |
| `-WorkDir` | Where downloads, backups and undo files go (`%USERPROFILE%\HomeTV`) |
| `-AdbPath` | An `adb` already on the laptop, instead of the pinned download |
| `-DownloadFrom` | A folder (a USB stick) with the release's APK, instead of GitHub; the APK is still checked |
| `-Restore` | Undo an install from its backup folder |
| `-NoPrompt` | Every question takes its default, and the pauses are skipped |
| `-ApkPath`, `-CertSha256` | Testing, in the repository's copy only: a local APK and the certificate it must carry |

`tests/test_family_install.py` runs the script against fake TVs (a fake `adb` reading a JSON state) for each of the
paths above. It needs `pwsh` and skips without it.
