"""tools/family-install.ps1, the family installer, against fake TVs (BACKLOG.md, Family install step 4).

The installer is a third copy of launcher/build-and-install.sh's Home guard, run by people who cannot rescue a TV
over ADB, so the order that guard depends on is tested here the way tests/test_home_guard.py tests the disable
scripts: the real script runs, and only adb is simulated. The fake adb is a PowerShell script reading and writing a
JSON state file, so the TV commands reach it exactly as written. The APK is generated with a real APK Signature Scheme
v2 block, so the installer's signer check reads the same structure a release APK has.

The scenario tests need `pwsh` on PATH and skip without it (CI has no PowerShell); the static ones always run.
Run from the repository root: python3 -m unittest discover -s tests -v
"""
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import socket
import struct
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
INSTALLER = ROOT / "tools" / "family-install.ps1"
RELEASE = ROOT / "tools" / "release.ps1"
BUILD_AND_INSTALL = ROOT / "launcher" / "build-and-install.sh"
PWSH = shutil.which("pwsh")

OURS = "com.hometv.launcher/.MainActivity"
TVLAUNCHER = "com.google.android.tvlauncher"
LAUNCHERX = "com.google.android.apps.tv.launcherx"
SETUPWRAITH = "com.google.android.tungsten.setupwraith"
CVTE_LAUNCHER = "com.cvte.tv.launcher.atv"

FAKE_ADB = r'''
$path = $env:FAKE_ADB_STATE
$s = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
$a = @($args | ForEach-Object { "$_" })
$s.log = @($s.log) + ($a -join ' ')
function Pkg([string]$c) { return ($c -split '/')[0] }
function Enabled([string]$p) { return (@($s.packages) -contains $p) -and -not (@($s.disabled) -contains $p) }
function HomeNow {
    if ($s.hijacker -and (Enabled (Pkg $s.hijacker)) -and (@($s.disabled) -contains $s.hijack_when_disabled)) { return $s.hijacker }
    $live = @(@($s.claims) | Where-Object { Enabled (Pkg $_) })
    if ($s.preferred -and ($live -contains $s.preferred)) { return $s.preferred }
    if ($live.Count -eq 1) { return $live[0] }
    if ($live.Count -eq 0) { return 'No activity found' }
    return 'android/com.android.internal.app.ResolverActivity'
}
function Claims([string]$p) { return @(@($s.claims) | Where-Object { (Pkg $_) -eq $p }).Count -gt 0 }
function Shell([string]$c) {
    $s.shell = @($s.shell) + $c
    if ($c -match '^getprop (\S+)$') { $v = $s.props.($Matches[1]); if ($null -eq $v) { return '' }; return "$v" }
    if ($c -eq 'getprop') { return (($s.props.PSObject.Properties | ForEach-Object { "[$($_.Name)]: [$($_.Value)]" }) -join "`n") }
    if ($c -eq 'pm list packages' -or $c -eq 'pm list packages -f') { return ((@($s.packages) | ForEach-Object { "package:$_" }) -join "`n") }
    if ($c -eq 'pm list packages -d') { return ((@($s.disabled) | ForEach-Object { "package:$_" }) -join "`n") }
    if ($c -like 'settings list *') { return 'name=value' }
    if ($c -like 'cmd package query-activities*') { return ((@($s.claims) | Where-Object { Enabled (Pkg $_) }) -join "`n") }
    if ($c -match '^cmd package resolve-activity .*category\.HOME (\S+)$') {
        $p = $Matches[1]; return "priority=0`n" + (@($s.claims) | Where-Object { (Pkg $_) -eq $p } | Select-Object -First 1)
    }
    if ($c -like 'cmd package resolve-activity*') { return "priority=0 preferredOrder=0`n" + (HomeNow) }
    if ($c -like 'pm install*') {
        if ($s.install_result -like 'Success*') {
            if (-not (@($s.packages) -contains 'com.hometv.launcher')) { $s.packages = @($s.packages) + 'com.hometv.launcher' }
            if (-not (@($s.claims) -contains 'com.hometv.launcher/.MainActivity')) {
                $s.claims = @($s.claims) + 'com.hometv.launcher/.MainActivity'; $s.preferred = $null
            }
        }
        return $s.install_result
    }
    if ($c -like 'am start -W*') { return $s.start_result }
    if ($c -match '^pm disable-user --user 0 (\S+)$') {
        $p = $Matches[1]
        if (@($s.refuse_disable) -contains $p) { return "Error: java.lang.SecurityException: Shell cannot change component state for $p" }
        $s.disabled = @($s.disabled) + $p; if (Claims $p) { $s.preferred = $null }
        return "Package $p new state: disabled-user"
    }
    if ($c -match '^pm enable --user 0 (\S+)$') {
        $p = $Matches[1]
        if ($s.enable_fails) { return "Error: java.lang.SecurityException: Shell cannot change component state for $p" }
        $s.disabled = @(@($s.disabled) | Where-Object { $_ -ne $p }); if (Claims $p) { $s.preferred = $null }
        return "Package $p new state: enabled"
    }
    if ($c -match '^cmd package set-home-activity --user 0 (\S+)$') { if ($s.set_home_works) { $s.preferred = $Matches[1] }; return 'Success' }
    if ($c -like 'dumpsys activity activities*') {
        $front = if ($s.front) { $s.front } else { HomeNow }
        return "  mResumedActivity: ActivityRecord{1 u0 $front t1}"
    }
    if ($c -eq 'dumpsys wifi') { return $s.wifi }
    return ''
}
if ($a.Count -ge 2 -and $a[0] -eq '-s') { $a = @($a[2..($a.Count - 1)]) }
$out = ''
switch ($a[0]) {
    'connect' {
        if ($s.connect_out) { $out = $s.connect_out -replace 'SERIAL', $a[1] }
        elseif ($s.connect_ok) { $out = "connected to $($a[1])" } else { $out = "failed to connect to $($a[1])" }
    }
    'disconnect' { $out = "disconnected $($a[1])" }
    'get-state' {
        $seq = @($s.auth_sequence)
        if ($seq.Count -gt 0 -and $null -ne $seq[0]) { $out = $seq[0]; $s.auth_sequence = @($seq | Select-Object -Skip 1) } else { $out = $s.auth }
    }
    'push' { $s.pushed = $true; $out = '1 file pushed' }
    'reboot' { $s.reboots = [int]$s.reboots + 1 }
    'pair' { $out = 'Successfully paired' }
    'shell' { $out = Shell $a[1] }
}
$s | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $path
if ($out) { Write-Output $out }
exit 0
'''


def lp(data):
    return struct.pack("<I", len(data)) + data


def write_signed_apk(path, cert, schemes=None):
    """A zip with an APK Signing Block: one signer per scheme, v2 with `cert` unless `schemes` names
    {block id: certificate}."""
    schemes = schemes or {0x7109871A: cert}
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("AndroidManifest.xml", b"\0" * 64)
        z.writestr("classes.dex", b"dex\n035\0")
    data = buf.getvalue()
    eocd = data.rfind(b"PK\x05\x06")
    cd_offset = struct.unpack("<I", data[eocd + 16:eocd + 20])[0]
    pairs = b""
    for block_id, block_cert in schemes.items():
        signed_data = lp(b"") + lp(lp(block_cert)) + lp(b"")    # digests, certificates, additional attributes
        signer = lp(signed_data) + lp(b"") + lp(b"")            # signed data, signatures, public key
        value = lp(lp(signer))                                  # the signers: one length-prefixed signer
        pairs += struct.pack("<QI", len(value) + 4, block_id) + value
    size = len(pairs) + 8 + 16
    block = struct.pack("<Q", size) + pairs + struct.pack("<Q", size) + b"APK Sig Block 42"
    eocd_record = data[eocd:eocd + 16] + struct.pack("<I", cd_offset + len(block)) + data[eocd + 20:]
    Path(path).write_bytes(data[:cd_offset] + block + data[cd_offset:eocd] + eocd_record)


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def placeholders(text):
    return sorted(set(re.findall(r"'@RELEASE_[A-Z0-9_]+@'", text)))


class StaticTest(unittest.TestCase):
    def setUp(self):
        self.text = INSTALLER.read_text(encoding="utf-8")

    def test_known_stock_launchers_match_build_and_install(self):
        sh = re.search(r'^KNOWN_STOCK_LAUNCHERS="([^"]+)"', BUILD_AND_INSTALL.read_text(encoding="utf-8"), re.M)
        ps = re.search(r"\$KnownStockLaunchers = @\(([^)]*)\)", self.text, re.S)
        self.assertIsNotNone(sh)
        self.assertIsNotNone(ps)
        self.assertEqual(sh.group(1).split(), re.findall(r"'([^']+)'", ps.group(1)))

    def test_release_stamps_every_placeholder(self):
        found = placeholders(self.text)
        self.assertEqual(len(found), 4, found)
        release = RELEASE.read_text(encoding="utf-8")
        for token in found:
            self.assertIn(f'"{token}"', release, f"tools/release.ps1 does not stamp {token}")

    def test_ascii_only_for_windows_powershell(self):
        # Windows PowerShell 5.1 reads a file without a BOM as the ANSI code page.
        self.assertTrue(self.text.isascii())

    def test_platform_tools_are_pinned(self):
        version = re.search(r"\$PlatformToolsVersion = '([0-9.]+)'", self.text).group(1)
        url = re.search(r"\$PlatformToolsUrl = '([^']+)'", self.text).group(1)
        sha = re.search(r"\$PlatformToolsSha256 = '([^']+)'", self.text).group(1)
        self.assertEqual(url, f"https://dl.google.com/android/repository/platform-tools_r{version}-win.zip")
        self.assertRegex(sha, r"^[0-9a-f]{64}$")

    def test_never_uninstalls(self):
        self.assertNotRegex(self.text, r"pm uninstall|'uninstall'|\buninstall-")


@unittest.skipUnless(PWSH, "needs pwsh")
class FamilyInstallTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.adb = self.base / "adb.ps1"
        self.adb.write_text(FAKE_ADB, encoding="utf-8")
        self.state_path = self.base / "tv.json"
        self.work = self.base / "work"
        self.apk = self.base / "hometv.apk"
        self.cert = b"release certificate DER"
        write_signed_apk(self.apk, self.cert)
        self.cert_sha = hashlib.sha256(self.cert).hexdigest()
        self.port = free_port()

    def tv(self, **overrides):
        state = {
            "props": {"ro.build.version.sdk": "28", "ro.product.model": "Test TV", "ro.product.manufacturer": "TestCo",
                      "ro.build.version.release": "9", "sys.boot_completed": "1"},
            "packages": [TVLAUNCHER, "com.android.settings", "com.android.tv.settings", "com.android.vending",
                         "com.google.android.gms", "com.example.tv"],
            "disabled": [],
            "claims": [f"{TVLAUNCHER}/.MainActivity"],
            "preferred": f"{TVLAUNCHER}/.MainActivity",
            "hijacker": None, "hijack_when_disabled": None, "front": None,
            "connect_ok": True, "connect_out": None, "auth": "device", "auth_sequence": [],
            "install_result": "Success",
            "start_result": "Starting: Intent { cmp=com.hometv.launcher/.MainActivity }\nStatus: ok\nActivity: x\nComplete",
            "set_home_works": True,
            "wifi": "", "log": [], "shell": [], "reboots": 0, "pushed": False,
            "refuse_disable": [], "enable_fails": False,
        }
        state.update(overrides)
        return state

    def run_installer(self, state, *extra, apk=True, cert=None, host="127.0.0.1"):
        self.state_path.write_text(json.dumps(state), encoding="utf-8")
        args = [PWSH, "-NoProfile", "-NonInteractive", "-File", str(INSTALLER), "-TvHost", host,
                "-AdbPort", str(self.port), "-WorkDir", str(self.work), "-AdbPath", str(self.adb), "-NoPrompt"]
        if apk:
            args += ["-ApkPath", str(self.apk), "-CertSha256", cert or self.cert_sha]
        args += list(extra)
        env = {k: v for k, v in os.environ.items() if k != "ANDROID_ADB_SERVER_PORT"}
        env["FAKE_ADB_STATE"] = str(self.state_path)
        proc = subprocess.run(args, capture_output=True, text=True, env=env, timeout=300)
        return proc, json.loads(self.state_path.read_text(encoding="utf-8-sig"))

    def run_script(self, script, state, *extra):
        self.state_path.write_text(json.dumps(state), encoding="utf-8")
        args = [PWSH, "-NoProfile", "-NonInteractive", "-File", str(script), "-TvHost", "127.0.0.1",
                "-AdbPort", str(self.port), "-WorkDir", str(self.work), "-AdbPath", str(self.adb), "-NoPrompt", *extra]
        env = {k: v for k, v in os.environ.items() if k != "ANDROID_ADB_SERVER_PORT"}
        env["FAKE_ADB_STATE"] = str(self.state_path)
        proc = subprocess.run(args, capture_output=True, text=True, env=env, timeout=300)
        return proc, json.loads(self.state_path.read_text(encoding="utf-8-sig"))

    def backup_dir(self):
        found = list((self.work / "backups").glob("*"))
        self.assertEqual(len(found), 1, found)
        return found[0]

    def index(self, shell, prefix):
        return next(i for i, c in enumerate(shell) if c.startswith(prefix))

    def assert_tv_untouched(self, end):
        self.assertFalse([e for e in end["log"] if e.split(' ')[0] in ('connect', '-s', 'start-server')], end["log"])

    def assert_nothing_disabled(self, end):
        self.assertFalse([c for c in end["shell"] if c.startswith("pm disable-user")])

    def test_installs_and_hands_over_home(self):
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["disabled"], [TVLAUNCHER])
        self.assertEqual(end["preferred"], OURS)
        shell = end["shell"]
        # The guard's order: install, then start ours, then disable the stock launcher, then set Home.
        self.assertLess(self.index(shell, "pm install"), self.index(shell, "am start -W"))
        self.assertLess(self.index(shell, "am start -W"), self.index(shell, "pm disable-user"))
        self.assertLess(self.index(shell, "pm disable-user"), self.index(shell, "cmd package set-home-activity"))
        backup = self.backup_dir()
        for name in ("packages.txt", "packages-disabled.txt", "getprop.txt", "settings-secure.txt", "home.txt"):
            self.assertTrue((backup / name).is_file(), name)
        undo = (backup / "undo.txt").read_text().splitlines()
        self.assertIn(f"serial 127.0.0.1:{self.port}", undo)
        self.assertIn(f"undo cmd package set-home-activity --user 0 {TVLAUNCHER}/.MainActivity", undo)
        self.assertIn(f"undo pm enable --user 0 {TVLAUNCHER}", undo)
        self.assertTrue(any(line.startswith("disconnect") for line in end["log"]))

    def test_restore_undoes_the_install(self):
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["disabled"], [])
        self.assertEqual(end["preferred"], f"{TVLAUNCHER}/.MainActivity")
        # Re-enabled before Home is set back: the undo lines run from the bottom up.
        restore = end["shell"][self.index(end["shell"], f"pm enable --user 0 {TVLAUNCHER}"):]
        self.assertTrue(any(c.startswith("cmd package set-home-activity") for c in restore))

    def test_failed_install_leaves_home_alone(self):
        proc, end = self.run_installer(self.tv(install_result="Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures]"))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("another key", proc.stdout)
        self.assertFalse([c for c in end["shell"] if c.startswith("am start")])
        self.assert_nothing_disabled(end)

    def test_launcher_that_does_not_start_leaves_home_alone(self):
        proc, end = self.run_installer(self.tv(start_result="Error: Activity not started, unable to resolve Intent"))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assert_nothing_disabled(end)
        self.assertEqual(end["preferred"], None)  # installing ours dropped the preference; nothing set it

    def test_home_taken_by_another_app_rolls_back(self):
        # setupwraith's RecoveryActivity takes Home once the stock launcher is gone (PROMPT.md, Launcher).
        proc, end = self.run_installer(self.tv(packages=self.tv()["packages"] + ["com.example.setup"],
                                               hijacker="com.example.setup/.RecoveryActivity",
                                               hijack_when_disabled=TVLAUNCHER))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertEqual(end["disabled"], [])
        self.assertEqual(end["preferred"], f"{TVLAUNCHER}/.MainActivity")
        self.assertIn("own home screen is back", proc.stdout)

    def test_home_resolving_to_ours_but_showing_another_rolls_back(self):
        proc, end = self.run_installer(self.tv(front=f"{SETUPWRAITH}/.RecoveryActivity"))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertEqual(end["disabled"], [])
        self.assertEqual(end["preferred"], f"{TVLAUNCHER}/.MainActivity")

    def test_below_min_sdk_changes_nothing(self):
        state = self.tv()
        state["props"]["ro.build.version.sdk"] = "25"
        proc, end = self.run_installer(state)
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertFalse(end["pushed"])
        self.assertFalse((self.work / "backups").exists())

    def test_fire_tv_stops(self):
        state = self.tv()
        state["props"]["ro.product.manufacturer"] = "Amazon"
        proc, end = self.run_installer(state)
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertFalse(end["pushed"])

    def google_tv(self, wifi):
        return self.tv(packages=[LAUNCHERX, SETUPWRAITH, "com.android.settings", "com.android.tv.settings",
                                 "com.android.vending", "com.google.android.gms"],
                       claims=[f"{LAUNCHERX}/.MainActivity"], preferred=f"{LAUNCHERX}/.MainActivity", wifi=wifi)

    def test_google_tv_whose_wifi_belongs_to_setup_changes_nothing(self):
        proc, end = self.run_installer(self.google_tv(f"Saved network cname={SETUPWRAITH} x"))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("Forget network", proc.stdout)
        self.assertFalse(end["pushed"])
        self.assert_nothing_disabled(end)

    def test_google_tv_with_unreadable_wifi_owners_changes_nothing(self):
        proc, end = self.run_installer(self.google_tv(""))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertFalse(end["pushed"])

    def test_google_tv_disables_launcherx_and_setup_one_per_call(self):
        proc, end = self.run_installer(self.google_tv("Saved network cname=android.uid.system x"))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(sorted(end["disabled"]), sorted([LAUNCHERX, SETUPWRAITH]))
        disables = [c for c in end["shell"] if c.startswith("pm disable-user")]
        self.assertEqual(disables, [f"pm disable-user --user 0 {LAUNCHERX}", f"pm disable-user --user 0 {SETUPWRAITH}"])
        undo = (self.backup_dir() / "undo.txt").read_text()
        self.assertIn(f"undo pm enable --user 0 {SETUPWRAITH}", undo)

    def test_refused_disable_rolls_back_and_stops(self):
        proc, end = self.run_installer(self.tv(refuse_disable=[TVLAUNCHER]))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("refused to switch off", proc.stdout)
        self.assertEqual(end["disabled"], [])
        self.assertEqual(end["preferred"], f"{TVLAUNCHER}/.MainActivity")
        self.assertFalse([c for c in end["shell"] if f"set-home-activity --user 0 {OURS}" in c])

    def test_google_tv_that_refuses_the_setup_app_gets_launcherx_back(self):
        state = self.google_tv("Saved network cname=android.uid.system x")
        state["refuse_disable"] = [SETUPWRAITH]
        proc, end = self.run_installer(state)
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertEqual(end["disabled"], [])
        self.assertEqual(end["preferred"], f"{LAUNCHERX}/.MainActivity")

    def test_unreachable_port_is_not_reported_closed(self):
        # 192.0.2.1 is TEST-NET-1: nothing answers, which proves nothing about the TV.
        proc, end = self.run_installer(self.tv(), host="192.0.2.1")
        self.assertEqual(proc.returncode, 2, proc.stdout)
        self.assertIn("could not confirm", proc.stdout)
        self.assertNotIn("Debugging is off", proc.stdout)

    def test_incomplete_restore_exits_nonzero(self):
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        end["enable_fails"] = True
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("not complete", proc.stdout)
        self.assertIn("keep debugging on", proc.stdout)
    def test_cvte_keeps_stock_launcher_and_reboots_once(self):
        state = self.tv(packages=[CVTE_LAUNCHER, "com.cvte.tv.setting", "com.android.settings", "com.android.vending",
                                  "com.google.android.gms"],
                        claims=[f"{CVTE_LAUNCHER}/.HomeActivity"], preferred=f"{CVTE_LAUNCHER}/.HomeActivity")
        proc, end = self.run_installer(state)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assert_nothing_disabled(end)
        self.assertEqual(end["reboots"], 1)
        # No key helper in the pass that moves Home on a CVTE board (#741): a plain force stop only.
        self.assertIn("am force-stop com.hometv.launcher", end["shell"])
        self.assertFalse([c for c in end["shell"] if "enabled_accessibility_services" in c])
        self.assertEqual(end["preferred"], OURS)
        self.assertIn(f"undo cmd package set-home-activity --user 0 {CVTE_LAUNCHER}/.HomeActivity",
                      (self.backup_dir() / "undo.txt").read_text())

    def test_already_home_only_updates(self):
        state = self.tv(packages=self.tv()["packages"] + ["com.hometv.launcher"], disabled=[TVLAUNCHER],
                        claims=[f"{TVLAUNCHER}/.MainActivity", OURS], preferred=OURS)
        proc, end = self.run_installer(state)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertTrue(any(c.startswith("pm install") for c in end["shell"]))
        self.assert_nothing_disabled(end)
        self.assertFalse([c for c in end["shell"] if c.startswith("cmd package set-home-activity")])

    def test_unknown_home_changes_nothing(self):
        proc, end = self.run_installer(self.tv(claims=["com.other.home/.Main"], preferred="com.other.home/.Main",
                                               packages=self.tv()["packages"] + ["com.other.home"]))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertFalse(end["pushed"])

    def test_wrong_signer_stops_before_the_tv(self):
        proc, end = self.run_installer(self.tv(), cert="00" * 32)
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("release key", proc.stdout)
        self.assert_tv_untouched(end)

    def test_unsigned_apk_stops(self):
        with zipfile.ZipFile(self.apk, "w") as z:
            z.writestr("AndroidManifest.xml", b"x")
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assert_tv_untouched(end)

    def test_v3_signer_must_match_v2_signer(self):
        # Android 8 reads only v2: a v3 block naming the release key over a v2 block signed by another must not pass.
        write_signed_apk(self.apk, None, {0x7109871A: b"someone else", 0xF05368C0: self.cert})
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assert_tv_untouched(end)

    def test_v2_and_v3_with_the_release_key_pass(self):
        write_signed_apk(self.apk, None, {0x7109871A: self.cert, 0xF05368C0: self.cert})
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)

    def test_v3_alone_is_refused(self):
        write_signed_apk(self.apk, None, {0xF05368C0: self.cert})
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 1, proc.stdout)

    def test_tv_waiting_on_its_allow_box_connects(self):
        # A TV that has not allowed this laptop yet answers adb connect with "failed to authenticate".
        proc, end = self.run_installer(self.tv(connect_out="failed to authenticate to SERIAL",
                                               auth_sequence=["unauthorized", "unauthorized", "device"]))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("Always allow from this computer", proc.stdout)

    def test_fresh_install_opens_first_run_last(self):
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        opened = [i for i, c in enumerate(end["shell"]) if "action.FIRST_RUN" in c]
        self.assertEqual(len(opened), 1)
        self.assertEqual(opened[0], len(end["shell"]) - 1, "nothing on the TV after First run, no Home press")

    def test_update_does_not_open_first_run(self):
        state = self.tv(packages=self.tv()["packages"] + ["com.hometv.launcher"], disabled=[TVLAUNCHER],
                        claims=[f"{TVLAUNCHER}/.MainActivity", OURS], preferred=OURS)
        proc, end = self.run_installer(state)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertFalse([c for c in end["shell"] if "action.FIRST_RUN" in c])

    def test_adb_server_is_stopped_after_a_stop(self):
        proc, end = self.run_installer(self.tv(install_result="Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]"))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertEqual(end["log"][-1], "kill-server")

    def test_repository_copy_needs_an_apk(self):
        proc, end = self.run_installer(self.tv(), apk=False)
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("not from a release", proc.stdout)
        self.assert_tv_untouched(end)

    def test_debugging_still_on_exits_2(self):
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", self.port))
            listener.listen(5)
            proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 2, proc.stdout)
        self.assertIn("debugging is still on", proc.stdout)
        self.assertEqual(end["preferred"], OURS)

    def stamped_copy(self, apk_sha):
        text = INSTALLER.read_text(encoding="utf-8")
        for token, value in {"'@RELEASE_TAG@'": "'v9.9'", "'@RELEASE_APK@'": "'hometv-launcher-v9.9.apk'",
                             "'@RELEASE_APK_SHA256@'": f"'{apk_sha}'",
                             "'@RELEASE_CERT_SHA256@'": f"'{self.cert_sha}'"}.items():
            text = text.replace(token, value)
        self.assertNotIn("@RELEASE_", text)
        script = self.base / "stamped" / "family-install.ps1"
        script.parent.mkdir()
        script.write_text(text, encoding="utf-8")
        release = self.base / "release"
        release.mkdir()
        shutil.copy2(self.apk, release / "hometv-launcher-v9.9.apk")
        return script, release

    def test_stamped_copy_installs_its_release(self):
        script, release = self.stamped_copy(hashlib.sha256(self.apk.read_bytes()).hexdigest())
        proc, end = self.run_script(script, self.tv(), "-DownloadFrom", str(release))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["preferred"], OURS)

    def test_stamped_copy_refuses_another_apk(self):
        script, release = self.stamped_copy("11" * 32)
        proc, end = self.run_script(script, self.tv(), "-DownloadFrom", str(release))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assert_tv_untouched(end)
        proc, end = self.run_script(script, self.tv(), "-ApkPath", str(self.apk), "-CertSha256", self.cert_sha)
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("installs release v9.9 only", proc.stdout)


if __name__ == "__main__":
    unittest.main()
