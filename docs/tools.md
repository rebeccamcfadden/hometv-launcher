# Doing it by hand

Everything the assistant in [PROMPT.md](../PROMPT.md) does, you can run yourself. The tools need Python 3 and nothing
else.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP address

tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one shell command on the TV
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a debloat list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, stock launcher back
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # what a firmware update undid
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # build the launcher and make it Home
tools/theme.sh examples/<your tv>                            # push that TV's config.json; --reset removes it
```

## Tools

| Tool | What it does |
|---|---|
| [`tools/tv.py`](../tools/tv.py) | Pure-Python ADB client: shell, batch, push, pull, reboot, and screenshots, optionally pressing remote keys first. Works where the stock `adb` binary is blocked. |
| [`tools/b64push.py`](../tools/b64push.py) | Uploads large files such as APKs in base64 chunks and verifies the checksum. |
| [`tools/relay.py`](../tools/relay.py) | Forwards a local port to the TV's ADB port, for assistants whose sandbox can't reach your network. Listens on this computer only. [Guide](../tools/relay.md). |
| [`tools/ci-local.ps1`](../tools/ci-local.ps1) | Checks a pull request on your machine and posts `local-ci/*` statuses, which `main` requires: GitHub's CI runs only after a merge, to keep Actions usage low. Windows. |
| [`tools/disable.sh`](../tools/disable.sh), [`restore.sh`](../tools/restore.sh) | Apply or undo a debloat list, the undo from the state recorded before the change. |
| [`tools/recovery.py`](../tools/recovery.py) | Writes the record `disable.sh` needs to undo itself, and prints the restore batch. |
| [`tools/check.py`](../tools/check.py) | Compares the TV with the list that was applied, for what a firmware update undid; `--fix` puts it back. |
| [`tools/adb-closed.py`](../tools/adb-closed.py) | Checks that the TV's network ADB port is closed once debugging is off: exits 0 when nothing answers, 1 while it is still open, 2 when the check itself could not run. No `nc` needed. |
| [`tools/privacy.py`](../tools/privacy.py) | Audits the TV for voice, ACR and telemetry candidates and prints the evidence for each; `--write` turns the findings into a list `disable.sh` and `restore.sh` already understand. See [microphone.md](microphone.md). |
| [`tools/theme.sh`](../tools/theme.sh) | Push a folder's `config.json` to the launcher and restart Home: the per-TV keys an [`examples/`](../examples/) folder carries. A theme folder from [`themes/`](../themes/) pushes too, but the launcher reads only those per-TV keys from it, not its look. |
| [`tools/manage.py`](../tools/manage.py) | List every add-on, theme and switch with its screenshot, see what is on the TV, and install, update or remove one by running its creator's own script. Themes and module cards are for upstream's launcher until V2. |
| [`tools/bench.py`](../tools/bench.py) | Measures the launcher that answers HOME: key latency, how far the focus moved, janky frames, cold start, return to Home, and the idle CPU and PSS it costs. Changes nothing on the TV and writes the raw per-key numbers to JSON; no extra install. |
| [`tools/trace.py`](../tools/trace.py), [`trace_report.py`](../tools/trace_report.py) | Records Perfetto traces of the launcher (return to Home, cold start, D-pad) and reports where its time goes, per run and before against after. Optional: needs [`requirements-perf.txt`](../tools/requirements-perf.txt). [Guide](../tools/trace.md). |
| [`tools/flamechart.py`](../tools/flamechart.py) | Draws a before/after flame chart from those traces, for a pull request. Optional, like the trace tools. [Guide](../tools/flamechart.md). |
| [`tools/screendiff.py`](../tools/screendiff.py) | Compares a before and an after screenshot pixel by pixel, leaving out the clock or anything you mask, and writes the pair with your Wi-Fi name blurred. Optional, like the trace tools. [Guide](../tools/screendiff.md). |
| [`launcher/build-and-install.sh`](../launcher/build-and-install.sh) | Builds a shrunk release (under 0.5 MB), installs it, switches Home, and rolls back if Home does not land on it. |

The scripts never leave the TV without a Home app: they only disable a stock launcher they recognise, and skip the Home
app in a list until another launcher answers HOME. Details below.

<details>
<summary><b>Windows (PowerShell)</b></summary>

Every script has a PowerShell twin next to it. On Windows with Android Studio installed nothing else is needed:

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
```

</details>

<details>
<summary><b>How the scripts protect Home</b></summary>

The install scripts ask the TV which launcher owns Home and only disable one they recognise, the four in
`KNOWN_STOCK_LAUNCHERS`: `com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx`,
`com.google.android.leanbacklauncher` and Xiaomi's `com.mitv.tvhome` (PatchWall). Pass `STOCK_LAUNCHER=<package>`
(`-StockLauncher`) to override, or `none` to leave it alone.

`tools/disable.sh` (`disable.ps1`) applies the same rule before it disables anything: if the list contains the app that
owns Home and no other app on the TV answers HOME, it skips that entry, says why, and disables the rest of the list.
The setup wizard, the settings apps, a vendor `FallbackHome` and factory-test apps (Xiaomi's
`com.duokan.factorytest`) answer HOME too and don't count. So a list applied before the launcher handover cannot leave
the TV with nothing to start. Run the same list again once `launcher/build-and-install.sh` has made the new launcher
Home and the entry is applied; `FORCE_HOME=1` (`$env:FORCE_HOME`) disables it anyway.

</details>

<details>
<summary><b>Where your lists can live</b></summary>

Debloat list paths passed to `disable.sh` and `restore.sh` are relative to the directory you run from, so your own
lists can live outside the repository. Absolute paths also work.

</details>
