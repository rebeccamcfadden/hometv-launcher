# Capacity Stats

Optional standalone Android TV app with a module card, maintained by [lionlev21](https://github.com/lionlev21).
Build with JDK 17 and Android SDK 34: `./gradlew testReleaseUnitTest lintRelease assembleRelease`.
The small gradlew scripts reuse the repository’s pinned Gradle wrapper with this folder as the project;
no wrapper JAR is copied into the add-on and no launcher source is included. Keep this folder inside
the repository when building. Install the APK you build with ADB or your preferred installer.
No binaries or prebuilt APKs are committed.

With HomeTV Launcher it is an ordinary app: open it from its tile in All Apps, or add it to Home's row. It also
declares a [module card](../../docs/specs/card.md), under this launcher's names (upstream's launcher reads its own, so it won't show this build's card);
this launcher doesn't show module cards yet ([BACKLOG.md](../../BACKLOG.md#add-on-cards-on-home)).
Module spec 1 has static captions, so a card can't show live counters on Home either way; the app shows live
readings. English and Hebrew UI, standard locale direction for other languages.
Reads refresh every ten seconds only while this screen is resumed; no background service survives leaving it.

## Permissions

No permissions requested. No network, telemetry, ads or background service. Reads local
/proc/meminfo and the data filesystem's capacity. Available/total and used/total RAM are labeled
separately; “used” is total minus available, not process RSS. On old kernels, available is estimated
as MemFree + Buffers + Cached, bounded by MemTotal. Missing/invalid values are omitted.

## Validation

Tested on a **Sony KD-50X85J running Android TV 12**.

6 JVM tests, lintRelease and release build passed. On real hardware verified both RAM ratios, storage, Hebrew direction, module-card recognition and stopping the worker on Back.

With HomeTV Launcher, on the Android TV 9 emulator (API 28), 2026-11-02: builds with its tests, installs, opens from
its launcher entry and shows RAM and storage.
