# Questions people ask

## I don't code and don't have a laptop. Can I still do this?

Yes. [@udhaykumarbala](https://github.com/udhaykumarbala) built a free, open-source
[phone app](https://cleanyour.tv/app) for Android that does the cleanup half over Wi-Fi: it connects to the TV, shows a
plan first, saves a restore point, and only switches apps off, so everything can be put back. It's community-built, and
it doesn't install this launcher.

## Is this safe? Can it brick the TV?

Nothing is flashed, rooted or deleted; packages are disabled in the user profile. The risk is disabling something the TV
leans on, which is why lists are applied in small batches with a restore script ready. The TV's own Settings → Apps page
can re-enable packages even without the tools.

## What about firmware updates?

The updater is left on in most lists, so firmware updates arrive as usual. Some lists disable the TV's own updater, and
on those a firmware update arrives only when you re-enable it. When one does land it installs over the cleaned-up
system, so a package comes back and you re-run the script: see
[After a firmware update](restore.md#after-a-firmware-update).

## Won't the apps break?

Streaming apps still update through the Play Store, which is left alone. The launcher just lists what is installed and
launches it with intents that have been stable for a decade.

## Can I do this for someone in another home?

Yes, over a private [Tailscale](https://tailscale.com) link instead of the same Wi-Fi: either Tailscale on their TV,
shared with you, or a computer or Apple TV at their home routing to it. Never by opening the ADB port on their router.
Step by step in [REMOTE.md](REMOTE.md).

## Isn't ADB access a security hole?

It is one explicit "allow this computer" tap on the TV, the same as developing an Android app. Network ADB is plaintext
on port 5555, and on firmware with `ro.adb.secure=0` any device on the LAN gets a shell, so close it when you are done:

1. On the TV: Developer options → turn **USB debugging** and **Network/ADB debugging** off, and revoke the USB
   debugging authorisations.
2. Reboot the TV, then confirm the port is gone from your computer: `python3 tools/adb-closed.py <tv ip>` must say the
   port is closed. With `nc -vz <tv ip> 5555`, only "Connection refused" counts: on macOS, Local Network privacy can
   block `nc` and make it fail with "No route to host" even while the port is open.

The launcher keeps working without debugging.

## Can it block ads inside apps too?

Partly, with Android's Private DNS (Android 9 and newer). Point it at an ad-blocking resolver such as
[AdGuard DNS](https://adguard-dns.io) and every app's ad and tracker domains stop resolving, system-wide, surviving
reboots. The trade-off: that resolver then sees every DNS query the TV makes.

```bash
tools/tv.py 'settings put global private_dns_specifier dns.adguard-dns.com'
tools/tv.py 'settings put global private_dns_mode hostname'
```

It does not remove YouTube ads, which come from the same servers as the videos. When the TV reports "No internet", the
resolver is down or unreachable and switching back restores everything:
`tools/tv.py 'settings put global private_dns_mode opportunistic'`. A resolver that answers can still break one app on
its own, so if a single app fails while the rest play, see
[one app refuses to play](TROUBLESHOOTING.md#one-app-refuses-to-play-while-the-rest-of-the-tv-is-online) before you
debug the app.

## AirPlay?

Not from this repo. Chromecast built-in stays for Android phones. For iPhones,
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) works; an install script for it is in
[PR #84](https://github.com/Gathin23/cleanyourtv/pull/84).

## Does the launcher go online?

Yes, and only for content. The weather comes from Open-Meteo, with no key and no account. With a TMDB token in the
build, the catalogue, artwork, availability and prices come from TMDB, Watchmode and Movie of the Night, and a themed
backdrop is downloaded once from a CDN. Everything fetched is kept, so Home and what you have already seen still show
while the TV is offline, and without a token the launcher builds with a mock catalogue that needs no network. There are
no analytics and no telemetry. Details: [launcher.md](launcher.md).

## Can I just download the APK?

Build it with the one-line script; it takes a minute and you control what goes on your TV. No prebuilt binaries.
