# Restoring, firmware updates and when something goes wrong

## Restoring your TV

Keep the list you applied. One line puts the TV back the way it was:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

| What | How |
|---|---|
| **A record comes first** | Before `tools/disable.sh` changes anything it writes down what the TV looked like, in `~/.cleanyourtv/`. |
| **Restore replays it** | A package you had disabled yourself stays disabled, and Home goes back to the component that owned it. |
| **The launcher stays installed** | Remove it from the TV's Settings → Apps if you want it gone. |
| **No computer?** | If the TV is unreachable, its own Settings → Apps page can re-enable packages too. |

<details>
<summary><b>What the record holds, and what happens without one</b></summary>

Before `tools/disable.sh` changes anything it writes down what the TV looked like: for every package in the list
whether it was enabled, already disabled or not installed, which component owned Home, and the three animation scales.
That record lives in `~/.cleanyourtv/`, one file per TV and list, and a second run of the same list keeps the first
record instead of saving the cleaned-up state over it. If you added packages to the list since, that run adds them to
the record before disabling them.

The `stock launcher` argument is only a fallback: with a record, the launcher that actually owned Home is the one that
gets it back, and the argument is used only when nothing answered HOME at the time or this project's launcher owned it.
The stock launcher is enabled again either way, unless it was already disabled when the record was taken. Without a
record (an older run, or a record you deleted) the script says so and falls back to enabling the whole list and
resetting the scales to `1.0`.

It leaves the new launcher installed and does not know about packages you disabled outside the list, or any other
change you made by hand: undo those yourself. To switch back later, run the install script again; it works out which
launcher to disable even when Home has fallen back to Android's chooser.

</details>

## After a firmware update

An update installs over the cleaned-up system: the listed packages come back, Home goes to the stock launcher, and new
preloads appear that no list mentions. Nothing else here notices, so compare the TV with the list you applied:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

It prints what drifted and exits 1 when anything did, so a weekly job on a home server or a Pi is enough to hear about
it:

```bash
0 9 * * 1 cd ~/hometv-launcher && TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

`--fix` reapplies the list to what drifted: it switches Home to the launcher the list does not disable (pass a package
as the second argument if you run a fork), then disables the listed packages that came back, and reads the TV back to
confirm both. It writes no recovery record, because the record already holds the state from before the first run:
`tools/restore.sh` still returns the TV to stock afterwards. Home is fixed first; if no launcher is left to take it,
`--fix` changes nothing and says so.

It runs the same safety checks as `tools/disable.sh` before it disables anything, since an update is the moment they
matter again: it leaves setupwraith on while the saved Wi-Fi network belongs to it (`FORCE_SETUPWRAITH=1` on Ethernet),
leaves the packages under the HDMI/AV inputs on (`TVINPUT_ALLOW=1` if you really want them off), leaves on the apps the
TV opens its inputs and Settings with (`HANDLERS_ALLOW=1`), and never takes Home to a vendor `FallbackHome` or a
factory-test app. Each package it leaves on comes with the reason and exits 1, because the TV is not back to the list
until you deal with that one by hand.

Lists that also disable the updater get no updates at all. To install one, re-enable the updater on the TV, let it
update, run `check.py --fix`, and disable the updater again.

On Windows the same command is `tools\venv\Scripts\python.exe -I tools\check.py <list>`.

## When something goes wrong

The full list of things that bit us is in [TROUBLESHOOTING.md](TROUBLESHOOTING.md). The three that matter most:

- 🍎 **macOS blocks `adb connect`** ("No route to host") under Local Network privacy. `tools/tv.py` avoids the binary.
- ⚠️ **Disable the stock launcher only after the new one is installed and running**, or Home lands on a factory test app. The install script orders this for you.
- 🔁 **On Google TV the setup wizard** (`com.google.android.tungsten.setupwraith`) can take Home and crash-loop the system after the stock launcher is disabled. Disable it too, and list it in your `debloat.txt` so restore brings it back. But check `dumpsys wifi | grep -o 'cname=[^ ]*'` first: if the Wi-Fi network was saved during setup it belongs to setupwraith, and disabling it deletes the network (re-join it in Settings first; `tools/disable.sh` skips setupwraith until you do). On Google's own Chromecast and on the Google TV Streamer it also costs phone casting, see [Troubleshooting](TROUBLESHOOTING.md). Stuck already? `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.
