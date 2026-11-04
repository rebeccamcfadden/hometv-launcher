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
$script:code = 0
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
    # The installer's StrictMode reaches this script: a missing property must not throw.
    if ($c -match '^getprop (\S+)$') { $v = $s.props.PSObject.Properties[$Matches[1]]; if ($null -eq $v) { return '' }; return "$($v.Value)" }
    if ($c -eq 'getprop') { return (($s.props.PSObject.Properties | ForEach-Object { "[$($_.Name)]: [$($_.Value)]" }) -join "`n") }
    if ($c -eq 'pm list packages' -or $c -eq 'pm list packages -f') { return ((@($s.packages) | ForEach-Object { "package:$_" }) -join "`n") }
    if ($c -eq 'pm list packages -d') { return ((@($s.disabled) | ForEach-Object { "package:$_" }) -join "`n") }
    if ($c -like 'settings list *') { return 'name=value' }
    if ($c -match '^settings get secure ' -and $s.secure_read_fails) { $script:code = 1; return 'Error: java.lang.SecurityException: settings' }
    if ($c -eq 'am force-stop com.hometv.launcher') {
        # Android takes a force-stopped app's accessibility services off the enabled list.
        $v = $s.secure.PSObject.Properties['enabled_accessibility_services']
        if ($null -ne $v -and $v.Value) { $v.Value = ((@($v.Value -split ':') | Where-Object { $_ -and $_ -notlike 'com.hometv.launcher/*' }) -join ':') }
        return ''
    }
    if ($c -match '^settings get secure (\S+)$') { $v = $s.secure.PSObject.Properties[$Matches[1]]; if ($null -eq $v -or $null -eq $v.Value) { return 'null' }; return "$($v.Value)" }
    if ($c -match '^settings put secure (\S+) (.*)$') { $val = $Matches[2]; if ($val -eq "''") { $val = '' }; if (-not $s.secure_refused) { $s.secure | Add-Member -Force NoteProperty $Matches[1] $val }; return '' }
    if ($c -match '^settings delete secure (\S+)$') { if (-not $s.secure_refused) { $s.secure.PSObject.Properties.Remove($Matches[1]) }; return 'Deleted 1 rows' }
    if ($c -match '^appops get com\.hometv\.launcher (AUTO_START|APP_AUTO_START)$') {
        $v = $s.tcl_ops.PSObject.Properties[$Matches[1]]
        if ($null -eq $v) { $script:code = 255; return "Error: Unknown operation string: $($Matches[1])" }
        if ($v.Value -eq 'default') { return 'No operations.' }
        # Never set, on a build that names the op's own default mode.
        if ($v.Value -like 'unset-*') { return "No operations.`nDefault mode: $($v.Value.Substring(6))" }
        return "$($Matches[1]): $($v.Value); time=+1s ago"
    }
    if ($c -match '^appops set com\.hometv\.launcher (AUTO_START|APP_AUTO_START) (\w+)$') {
        if ($null -ne $s.tcl_ops.PSObject.Properties[$Matches[1]]) { $s.tcl_ops.($Matches[1]) = $Matches[2] }; return ''
    }
    if ($c -eq 'pm grant com.hometv.launcher android.permission.WRITE_SECURE_SETTINGS') { $s.wss_granted = $true; return '' }
    if ($c -eq 'pm revoke com.hometv.launcher android.permission.WRITE_SECURE_SETTINGS') { $s.wss_granted = $false; return '' }
    if ($c -eq 'ls /sdcard/Android/data/com.hometv.launcher/files/config.json') {
        if ($s.config_on_tv) { return '/sdcard/Android/data/com.hometv.launcher/files/config.json' }
        $script:code = 1; return 'ls: /sdcard/Android/data/com.hometv.launcher/files/config.json: No such file or directory'
    }
    if ($c -eq 'content read --uri content://com.hometv.launcher.theme/config.json' -or $c -eq 'cat /sdcard/Android/data/com.hometv.launcher/files/config.json') {
        if ($s.config_on_tv) { return $s.config_on_tv }
        return 'Error while accessing provider:com.hometv.launcher.theme java.io.FileNotFoundException'
    }
    if ($c -like 'mkdir -p /sdcard/Android/data/com.hometv.launcher/files*content write*') { $s.config_on_tv = $s.staged_config; return '' }
    if ($c -like 'rm -f /sdcard/Android/data/com.hometv.launcher/files/config.json; content delete*') { if (-not $s.config_delete_fails) { $s.config_on_tv = $null }; return '' }
    if ($c -match '^dumpsys package (\S+)$') {
        $p = $Matches[1]
        # A failing dump answers with an error and exit 0, as a busy package service does.
        if ($s.hw_fails) { return "Error: Can't find service: package" }
        $perm = if (@($s.tv_hw) -contains $p) { "      android.permission.TV_INPUT_HARDWARE`n" } else { '' }
        if ($p -eq 'com.hometv.launcher' -and $s.wss_granted) { $perm += "      android.permission.WRITE_SECURE_SETTINGS: granted=true`n" }
        return "Packages:`n  Package [$p] (1a2b3c):`n    requested permissions:`n      android.permission.INTERNET`n$perm    install permissions:`n"
    }
    if ($c -like 'cmd package query-activities --brief -a android.intent.action.VIEW*') {
        if ($s.handlers_fail) { return 'Exception occurred while executing: java.lang.SecurityException' }
        return ((@($s.input_apps) | ForEach-Object { "  $_/.Main" }) -join "`n")
    }
    if ($c -like 'cmd package query-activities --brief -a android.settings.SETTINGS*') {
        return ((@($s.settings_apps) | ForEach-Object { "  $_/.Settings" }) -join "`n")
    }
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
    if ($c -match '^cmd package set-home-activity --user 0 (\S+)$') {
        if ($s.set_home_works -and -not ($s.set_home_once -and $s.set_home_used)) { $s.preferred = $Matches[1]; $s.set_home_used = $true }
        return 'Success'
    }
    if ($c -like 'dumpsys activity activities*') {
        $front = if ($s.front) { $s.front } elseif ($s.front_when_disabled -and (@($s.disabled) -contains $s.front_when_disabled)) { "com.example.setup/.RecoveryActivity" } else { HomeNow }
        return "  mResumedActivity: ActivityRecord{1 u0 $front t1}"
    }
    if ($c -eq 'dumpsys wifi') { return $s.wifi }
    if ($c -match '^appops set com\.hometv\.launcher REQUEST_INSTALL_PACKAGES (\w+)$') { if (-not $s.appops_refused) { $s.install_grant = $Matches[1] }; return '' }
    if ($c -eq 'appops get com.hometv.launcher REQUEST_INSTALL_PACKAGES') {
        if ($s.appops_get -eq 'fail') { $script:code = 1; return 'Error: java.lang.SecurityException: appops' }
        if ($s.appops_get -eq 'garbled') { return 'Operation REQUEST_INSTALL_PACKAGES not known here' }
        # Android names no mode for an app-op never set.
        if ($s.install_grant -eq 'default') { if ($s.appops_get -eq 'two_lines') { return "No operations.`nDefault mode: default" }; return 'No operations.' }
        return "REQUEST_INSTALL_PACKAGES: $($s.install_grant); time=+1m2s ago"
    }
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
    'push' { $s.pushed = $true; if ($a[1] -like '*.json') { $s.staged_config = [IO.File]::ReadAllText($a[1]) }; $out = '1 file pushed' }
    'reboot' { $s.reboots = [int]$s.reboots + 1 }
    'pair' { $out = 'Successfully paired' }
    'shell' { $out = Shell $a[1] }
}
$s | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $path
if ($out) { Write-Output $out }
exit $script:code
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
        self.assertEqual(len(found), 5, found)
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

    def test_core_packages_match_disable_ps1(self):
        disable = (ROOT / "tools" / "disable.ps1").read_text(encoding="utf-8")
        for name in ("coreRe", "coreOk"):
            theirs = re.search(r"\$" + name + r" = '([^']+)'", disable).group(1)
            ours = re.search(r"\$" + name[0].upper() + name[1:] + r" = '([^']+)'", self.text).group(1)
            self.assertEqual(theirs, ours, name)

    def test_checklist_covers_every_shipped_household_app(self):
        # Every package a shipped list files under streaming, music, voice, casting, mirroring or updates (its section
        # heading or its own comment) must be one the checklist asks about, so a new list can't slip one past it.
        names = re.search(r"\$Checklist = \[ordered\]@\{(.*?)\n\}", self.text, re.S).group(1)
        notes = re.search(r"\$ChecklistNotes = \[ordered\]@\{(.*?)\n\}", self.text, re.S).group(1)
        patterns = [re.compile(p, re.I) for p in re.findall(r"= '([^']+)'", names)]
        note_patterns = [re.compile(p.replace("(?i)", ""), re.I) for p in re.findall(r"= '([^']+)'", notes)]
        topic = re.compile(r"stream|music|voice|assistant|\bcast|chromecast|mirror|miracast|firmware|\bota\b|updater", re.I)
        missed = {}
        for path in sorted((ROOT / "examples").glob("*/debloat.txt")):
            section = ""
            for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
                line = raw.strip()
                if not line:
                    continue
                code, _, comment = line.partition("#")
                code = code.strip()
                if not code:
                    section = comment
                    continue
                if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+", code) and topic.search(section + " " + comment):
                    note = section + " " + comment
                    if not any(p.search(code) for p in patterns) and not any(p.search(note) for p in note_patterns):
                        missed.setdefault(code, path.parent.name)
        self.assertEqual(missed, {}, "household apps the checklist would not ask about")

    def test_release_bundles_each_lists_config(self):
        text = RELEASE.read_text(encoding="utf-8")
        self.assertIn('"$($list.Name)/config.json"', text)

    def test_a_reinstall_asks_to_update_not_to_make_home(self):
        # Questions print only without -NoPrompt, so the wording is checked here.
        self.assertRegex(self.text, r"\$mode -eq 'reinstall'\) \{ 'Update the HomeTV Launcher on this TV now\?' \}")

    def test_questions_drop_keys_typed_ahead(self):
        for fn in ("Ask-YesNo", "Ask-Text", "Wait-Done"):
            body = self.text.split(f"function {fn}(", 1)[1].split("\n}", 1)[0]
            self.assertIn("Clear-TypedAhead", body, fn)

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
            "tv_hw": [], "input_apps": [], "settings_apps": [], "hw_fails": False, "handlers_fail": False,
            "front_when_disabled": None,
            "secure": {}, "secure_refused": False, "secure_read_fails": False, "config_delete_fails": False, "tcl_ops": {}, "wss_granted": False, "config_on_tv": None,
            "staged_config": None,
            "refuse_disable": [], "enable_fails": False, "install_grant": "default", "appops_refused": False, "appops_get": None, "set_home_once": False, "set_home_used": False,
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
    def test_updates_are_allowed_with_an_undo(self):
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["install_grant"], "allow")
        undo = (self.backup_dir() / "undo.txt").read_text()
        self.assertIn("undo appops set com.hometv.launcher REQUEST_INSTALL_PACKAGES default", undo)
        # Granted after the handover, before First run opens.
        shell = end["shell"]
        self.assertLess(self.index(shell, "cmd package set-home-activity"), self.index(shell, "appops set com.hometv.launcher"))
        self.assertIn("action.FIRST_RUN", shell[-1])
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["install_grant"], "default")

    def test_a_grant_already_given_is_left_alone(self):
        proc, end = self.run_installer(self.tv(install_grant="allow"))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertFalse([c for c in end["shell"] if c.startswith("appops set")])
        self.assertNotIn("appops", (self.backup_dir() / "undo.txt").read_text())

    def test_restore_puts_back_the_mode_it_found(self):
        proc, end = self.run_installer(self.tv(install_grant="deny"))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["install_grant"], "allow")
        self.assertIn("undo appops set com.hometv.launcher REQUEST_INSTALL_PACKAGES deny", (self.backup_dir() / "undo.txt").read_text())
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["install_grant"], "deny")

    def test_an_unset_grant_with_its_default_mode_line_is_default(self):
        # TCL BeyondTV, Android 9 (2026-11-04): "No operations." then "Default mode: default".
        proc, end = self.run_installer(self.tv(appops_get="two_lines"))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["install_grant"], "allow")
        self.assertIn("undo appops set com.hometv.launcher REQUEST_INSTALL_PACKAGES default", (self.backup_dir() / "undo.txt").read_text())
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["install_grant"], "default")

    def assert_unreadable_grant_left_alone(self, how):
        proc, end = self.run_installer(self.tv(install_grant="deny", appops_get=how))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("Could not read whether the launcher may install its own updates", proc.stdout)
        self.assertFalse([c for c in end["shell"] if c.startswith("appops set")])
        self.assertNotIn("appops", (self.backup_dir() / "undo.txt").read_text())
        self.assertEqual(end["install_grant"], "deny")

    def test_a_failed_grant_read_changes_nothing(self):
        self.assert_unreadable_grant_left_alone("fail")

    def test_an_unknown_grant_answer_changes_nothing(self):
        self.assert_unreadable_grant_left_alone("garbled")

    def test_restore_reports_a_grant_it_could_not_put_back(self):
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["install_grant"], "allow")
        end["appops_refused"] = True
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("not complete", proc.stdout)
        self.assertIn("  appops set com.hometv.launcher REQUEST_INSTALL_PACKAGES default", proc.stdout)

    def test_restore_reports_a_grant_it_could_not_read_back(self):
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        end["appops_get"] = "fail"
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("not complete", proc.stdout)
        self.assertIn("  appops set com.hometv.launcher REQUEST_INSTALL_PACKAGES default", proc.stdout)
    def test_a_refused_grant_only_says_so(self):
        proc, end = self.run_installer(self.tv(appops_refused=True))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("did not let the launcher install its own updates", proc.stdout)
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
        self.assertIn("this run updates it and leaves Home as it is", proc.stdout)

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

    # ---- Debloat from a list (step 6) ----

    BLOAT = ["com.example.bloat1", "com.example.bloat2", "com.example.bloat3", "com.example.bloat4",
             "com.example.bloat5", "com.example.bloat6"]

    def debloat_tv(self, **overrides):
        return self.tv(packages=self.tv()["packages"] + self.BLOAT + ["com.netflix.ninja", "com.example.hdmi",
                                                                      "com.example.inputs", "com.example.setup"],
                       **overrides)

    def write_list(self, names):
        path = self.base / "debloat.txt"
        path.write_text("# Test TV\n" + "".join(f"{n}  # why\n" for n in names), encoding="utf-8")
        return path

    def disables(self, end):
        return [c.split()[-1] for c in end["shell"] if c.startswith("pm disable-user")]

    def test_list_is_applied_after_the_handover_in_batches(self):
        names = self.BLOAT + ["com.example.not.installed"]
        proc, end = self.run_installer(self.debloat_tv(), "-List", str(self.write_list(names)), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(self.disables(end), [TVLAUNCHER] + self.BLOAT)
        self.assertEqual(end["preferred"], OURS)
        undo = (self.backup_dir() / "undo.txt").read_text()
        for name in self.BLOAT:
            self.assertIn(f"undo pm enable --user 0 {name}", undo)
        self.assertIn("switch an app or two back on at every restart", proc.stdout)
        # First run still opens last, after the debloat.
        self.assertIn("action.FIRST_RUN", end["shell"][-1])

    def test_a_list_with_a_core_package_changes_nothing(self):
        proc, end = self.run_installer(self.debloat_tv(), "-List", str(self.write_list(["com.android.shell"])), "-SwitchOffList")
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("core packages", proc.stdout)
        self.assert_tv_untouched(end)

    def test_a_list_with_a_bad_line_changes_nothing(self):
        proc, end = self.run_installer(self.debloat_tv(), "-List", str(self.write_list(["pm uninstall x"])), "-SwitchOffList")
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assert_tv_untouched(end)

    def test_what_the_family_may_use_stays_by_default(self):
        proc, end = self.run_installer(self.debloat_tv(), "-List", str(self.write_list(["com.netflix.ninja", "com.example.bloat1"])), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertNotIn("com.netflix.ninja", self.disables(end))
        self.assertIn("com.example.bloat1", self.disables(end))

    def test_input_and_settings_apps_stay(self):
        state = self.debloat_tv(tv_hw=["com.example.hdmi"], input_apps=["com.example.inputs"], settings_apps=["com.example.setup"])
        names = ["com.example.hdmi", "com.example.inputs", "com.example.setup", "com.example.bloat1"]
        proc, end = self.run_installer(state, "-List", str(self.write_list(names)), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(self.disables(end), [TVLAUNCHER, "com.example.bloat1"])

    def test_a_guard_that_cannot_run_switches_nothing_off(self):
        proc, end = self.run_installer(self.debloat_tv(hw_fails=True), "-List", str(self.write_list(self.BLOAT)), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(self.disables(end), [TVLAUNCHER])

    def test_a_handler_query_that_fails_switches_nothing_off(self):
        proc, end = self.run_installer(self.debloat_tv(handlers_fail=True), "-List", str(self.write_list(self.BLOAT)), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(self.disables(end), [TVLAUNCHER])
        self.assertIn("Could not check", proc.stdout)

    def test_a_home_press_that_shows_another_app_counts_as_home_moved(self):
        # Resolution stays on ours; only a Home press shows the recovery screen once bloat3 is off.
        state = self.debloat_tv(front_when_disabled="com.example.bloat3")
        proc, end = self.run_installer(state, "-List", str(self.write_list(self.BLOAT)), "-SwitchOffList")
        self.assertNotIn("com.example.bloat3", end["disabled"])
        self.assertNotIn("com.example.bloat6", self.disables(end))
        self.assertIn("Home moved away", proc.stdout)

    def test_paramount_and_mediatek_mirroring_stay_by_default(self):
        state = self.debloat_tv()
        state["packages"] = state["packages"] + ["com.cbs.ott", "com.mediatek.androidbox"]
        proc, end = self.run_installer(state, "-List", str(self.write_list(["com.cbs.ott", "com.mediatek.androidbox", "com.example.bloat1"])), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(self.disables(end), [TVLAUNCHER, "com.example.bloat1"])

    def test_a_reinstall_with_a_list_records_home_for_restore(self):
        state = self.debloat_tv(disabled=[TVLAUNCHER], preferred=OURS)
        state["packages"] = state["packages"] + ["com.hometv.launcher"]
        state["claims"] = [f"{TVLAUNCHER}/.MainActivity", OURS]
        proc, end = self.run_installer(state, "-List", str(self.write_list(["com.example.bloat1"])), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        undo = (self.backup_dir() / "undo.txt").read_text().splitlines()
        home = undo.index(f"undo cmd package set-home-activity --user 0 {OURS}")
        self.assertLess(home, undo.index("undo pm enable --user 0 com.example.bloat1"), "Home runs last on -Restore")
    def test_without_a_yes_nothing_from_the_list_is_switched_off(self):
        proc, end = self.run_installer(self.debloat_tv(), "-List", str(self.write_list(self.BLOAT)))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(self.disables(end), [TVLAUNCHER])
        self.assertIn("Nothing from the list was switched off", proc.stdout)
        for name in self.BLOAT:
            self.assertIn(f"  {name}", proc.stdout, "the remaining apps are listed by name before the question")

    def test_a_cvte_launcher_on_a_list_stays_on(self):
        state = self.tv(packages=[CVTE_LAUNCHER, "com.cvte.tv.setting", "com.android.settings", "com.android.vending",
                                  "com.google.android.gms", "com.example.bloat1"],
                        claims=[f"{CVTE_LAUNCHER}/.HomeActivity"], preferred=f"{CVTE_LAUNCHER}/.HomeActivity")
        proc, end = self.run_installer(state, "-List", str(self.write_list([CVTE_LAUNCHER, "com.example.bloat1"])), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertNotIn(CVTE_LAUNCHER, end["disabled"])
        self.assertIn("com.example.bloat1", end["disabled"])
        self.assertIn("CVTE board its own launcher stays on", proc.stdout)
    def test_setup_that_owns_the_wifi_stays(self):
        state = self.debloat_tv(wifi=f"cname={SETUPWRAITH}")
        state["packages"] = state["packages"] + [SETUPWRAITH]
        proc, end = self.run_installer(state, "-List", str(self.write_list([SETUPWRAITH, "com.example.bloat1"])), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertNotIn(SETUPWRAITH, self.disables(end))

    def test_a_batch_that_moves_home_is_switched_back_on(self):
        state = self.debloat_tv(hijacker="com.example.setup/.Recovery", hijack_when_disabled="com.example.bloat2")
        proc, end = self.run_installer(state, "-List", str(self.write_list(self.BLOAT)), "-SwitchOffList")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["disabled"], [TVLAUNCHER])
        self.assertNotIn("com.example.bloat6", self.disables(end), "the batch after is not started")
        self.assertIn("Home moved away", proc.stdout)
        self.assertEqual(end["preferred"], OURS, "Home is set back to ours after the batch is undone")

    def test_a_batch_whose_undo_leaves_home_elsewhere_stops(self):
        # Disabling a Home app drops the saved default and so does enabling it again (Android 9 and older); here
        # set-home-activity works only for the handover, so Home stays on the chooser after the batch is undone.
        state = self.debloat_tv(set_home_once=True)
        state["claims"] = state["claims"] + ["com.example.alt1/.Main", "com.example.alt2/.Main"]
        state["packages"] = state["packages"] + ["com.example.alt1", "com.example.alt2"]
        proc, end = self.run_installer(state, "-List", str(self.write_list(["com.example.alt1"])), "-SwitchOffList")
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertNotIn("com.example.alt1", end["disabled"])
        self.assertIn("-Restore", proc.stdout)
        self.assertNotIn("== Done", proc.stdout)

    def test_a_batch_that_cannot_be_undone_stops_with_restore(self):
        state = self.debloat_tv(hijacker="com.example.setup/.Recovery", hijack_when_disabled="com.example.bloat2",
                                enable_fails=True)
        proc, end = self.run_installer(state, "-List", str(self.write_list(self.BLOAT)), "-SwitchOffList")
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("Keep debugging on", proc.stdout)
        self.assertIn("-Restore", proc.stdout)
        self.assertNotIn("== Done", proc.stdout)

    def test_the_report_has_no_serial(self):
        state = self.tv()
        state["props"]["ro.serialno"] = "SERIAL12345"
        proc, end = self.run_installer(state)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        report = (self.backup_dir() / "report.txt").read_text()
        self.assertIn("ro.product.model=Test TV", report)
        self.assertIn(TVLAUNCHER, report)
        self.assertNotIn("SERIAL12345", report)

    # ---- The remote's buttons (step 4b) ----

    def helpers(self, end):
        return end["secure"].get("enabled_accessibility_services")

    def undo(self):
        return (self.backup_dir() / "undo.txt").read_text()

    def test_buttons_that_work_need_no_helper(self):
        proc, end = self.run_installer(self.tv())
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("Every button works", proc.stdout)
        self.assertIsNone(self.helpers(end))
        self.assertFalse(end["wss_granted"])
        self.assertIsNone(end["config_on_tv"])

    def test_a_broken_input_button_gets_its_helper_with_an_undo(self):
        proc, end = self.run_installer(self.tv(), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(self.helpers(end), "com.hometv.launcher/.InputKeyService")
        self.assertEqual(end["secure"]["accessibility_enabled"], "1")
        self.assertTrue(end["wss_granted"])
        self.assertEqual(json.loads(end["config_on_tv"]), {"restore_key_services": True})
        self.assertIn("one step per press", proc.stdout, "what a helper can cost is said before it goes on")
        undo = self.undo()
        for line in ("undo settings delete secure enabled_accessibility_services",
                     "undo settings delete secure accessibility_enabled",
                     "undo pm revoke com.hometv.launcher android.permission.WRITE_SECURE_SETTINGS"):
            self.assertIn(line, undo)
        shell = end["shell"]
        helper_on = self.index(shell, "settings put secure enabled_accessibility_services")
        # After the handover, after the config.json it reads, and before First run, which stays last.
        self.assertLess(self.index(shell, "cmd package set-home-activity"), helper_on)
        self.assertLess(self.index(shell, "mkdir -p /sdcard/Android/data/com.hometv.launcher/files"), helper_on)
        self.assertIn("action.FIRST_RUN", shell[-1])

    def test_home_and_settings_share_one_helper(self):
        proc, end = self.run_installer(self.tv(), "-BrokenButtons", "Home,Settings")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(self.helpers(end), "com.hometv.launcher/.HomeKeyService")

    def test_other_helpers_stay_listed_and_restore_puts_the_list_back(self):
        state = self.tv(secure={"enabled_accessibility_services": "com.other/.Reader", "accessibility_enabled": "1"})
        proc, end = self.run_installer(state, "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(self.helpers(end), "com.other/.Reader:com.hometv.launcher/.InputKeyService")
        self.assertIn("undo settings put secure enabled_accessibility_services com.other/.Reader", self.undo())
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["secure"], {"enabled_accessibility_services": "com.other/.Reader", "accessibility_enabled": "1"})
        self.assertFalse(end["wss_granted"])
        self.assertIsNone(end["config_on_tv"])
        self.assertEqual(end["disabled"], [])

    def test_restore_reports_a_helper_list_it_could_not_put_back(self):
        proc, end = self.run_installer(self.tv(), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        end["secure_refused"] = True
        end["secure"]["enabled_accessibility_services"] = "x/.Y"
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("settings delete secure accessibility_enabled", proc.stdout.split("not complete")[-1])

    def test_tcl_auto_start_is_allowed_and_written_with_an_undo(self):
        proc, end = self.run_installer(self.tv(tcl_ops={"AUTO_START": "default"}), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["tcl_ops"], {"AUTO_START": "allow"})
        self.assertIn("appops write-settings", end["shell"])
        self.assertLess(self.index(end["shell"], "appops write-settings"),
                        self.index(end["shell"], "settings put secure enabled_accessibility_services"))
        undo = self.undo().splitlines()
        self.assertIn("undo appops set com.hometv.launcher AUTO_START default", undo)
        self.assertNotIn("APP_AUTO_START", "\n".join(undo))
        # -Restore runs from the bottom up: the modes go back first, then they are written to disk (or a restart undoes them).
        self.assertLess(undo.index("undo appops write-settings"), undo.index("undo appops set com.hometv.launcher AUTO_START default"))

    def test_restore_writes_the_auto_start_it_put_back_to_disk(self):
        proc, end = self.run_installer(self.tv(tcl_ops={"AUTO_START": "default"}), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["tcl_ops"], {"AUTO_START": "default"})
        restore = end["shell"][end["shell"].index("appops set com.hometv.launcher AUTO_START default"):]
        self.assertIn("appops write-settings", restore)

    def test_an_unset_auto_start_whose_default_is_allow_is_left_alone(self):
        proc, end = self.run_installer(self.tv(tcl_ops={"AUTO_START": "unset-allow"}), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertFalse([c for c in end["shell"] if "AUTO_START allow" in c or c == "appops write-settings"])
        self.assertNotIn("AUTO_START", (self.backup_dir() / "undo.txt").read_text())

    def test_a_tv_without_auto_start_gets_no_app_op(self):
        proc, end = self.run_installer(self.tv(), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertFalse([c for c in end["shell"] if "AUTO_START allow" in c or c == "appops write-settings"])

    def test_cvte_turns_on_no_helper_in_the_pass_that_moves_home(self):
        state = self.tv(packages=[CVTE_LAUNCHER, "com.cvte.tv.setting", "com.android.settings", "com.android.vending",
                                  "com.google.android.gms"],
                        claims=[f"{CVTE_LAUNCHER}/.HomeActivity"], preferred=f"{CVTE_LAUNCHER}/.HomeActivity")
        proc, end = self.run_installer(state, "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["secure"], {})
        self.assertIn("run this installer again", proc.stdout)

    def test_a_lists_button_keys_come_along_and_nothing_else(self):
        folder = self.base / "test-tv"
        folder.mkdir()
        (folder / "debloat.txt").write_text("# Test TV\ncom.example.bloat1  # why\n", encoding="utf-8")
        (folder / "config.json").write_text(json.dumps({"dock_apps": ["com.netflix.ninja"], "settings_key_keycodes": [83],
                                                        "app_keys": ["4062:com.netflix.ninja"], "wallpaper": "x.jpg"}), encoding="utf-8")
        proc, end = self.run_installer(self.debloat_tv(), "-List", str(folder / "debloat.txt"), "-BrokenButtons", "Settings")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(json.loads(end["config_on_tv"]), {"app_keys": ["4062:com.netflix.ninja"], "settings_key_keycodes": [83],
                                                           "restore_key_services": True})

    def test_a_lists_button_keys_come_along_without_a_helper(self):
        folder = self.base / "test-tv"
        folder.mkdir()
        (folder / "debloat.txt").write_text("# Test TV\ncom.example.bloat1\n", encoding="utf-8")
        (folder / "config.json").write_text(json.dumps({"input_key_keycodes": [170]}), encoding="utf-8")
        proc, end = self.run_installer(self.debloat_tv(), "-List", str(folder / "debloat.txt"))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(json.loads(end["config_on_tv"]), {"input_key_keycodes": [170]})
        self.assertIsNone(self.helpers(end))

    def test_restore_reports_a_config_it_could_not_remove(self):
        proc, end = self.run_installer(self.tv(), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        end["config_delete_fails"] = True
        proc, end = self.run_script(INSTALLER, end, "-Restore", str(self.backup_dir()))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("config.json", proc.stdout.split("not complete")[-1])

    def test_unreadable_accessibility_settings_change_nothing(self):
        proc, end = self.run_installer(self.tv(secure_read_fails=True), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("Could not read the TV's accessibility settings", proc.stdout)
        self.assertFalse([c for c in end["shell"] if c.startswith("settings put") or c.startswith("settings delete")])
        self.assertFalse(end["wss_granted"])
        self.assertIsNone(end["config_on_tv"])
        self.assertNotIn("secure", self.undo())

    def test_a_helper_list_that_looks_wrong_changes_nothing(self):
        state = self.tv(secure={"enabled_accessibility_services": "Error: null object", "accessibility_enabled": "1"})
        proc, end = self.run_installer(state, "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["secure"]["enabled_accessibility_services"], "Error: null object")
        self.assertIn("Could not read the TV's accessibility settings", proc.stdout)

    def test_handle_view_inputs_comes_along_and_home_restarts_to_read_it(self):
        folder = self.base / "hisense"
        folder.mkdir()
        (folder / "debloat.txt").write_text("# Test TV\ncom.example.bloat1\n", encoding="utf-8")
        (folder / "config.json").write_text(json.dumps({"hidden_apps": ["x.y"], "handle_view_inputs": True}), encoding="utf-8")
        proc, end = self.run_installer(self.debloat_tv(), "-List", str(folder / "debloat.txt"), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(json.loads(end["config_on_tv"]), {"handle_view_inputs": True, "restore_key_services": True})
        shell = end["shell"]
        restart = self.index(shell, "am force-stop com.hometv.launcher")
        self.assertLess(self.index(shell, "mkdir -p /sdcard/Android/data/com.hometv.launcher/files"), restart)
        self.assertLess(restart, self.index(shell, "settings put secure enabled_accessibility_services com.hometv"))
        self.assertEqual(self.helpers(end), "com.hometv.launcher/.InputKeyService")

    def test_a_restart_for_new_keys_puts_our_helper_back(self):
        folder = self.base / "keys"
        folder.mkdir()
        (folder / "debloat.txt").write_text("# Test TV\ncom.example.bloat1\n", encoding="utf-8")
        (folder / "config.json").write_text(json.dumps({"input_key_keycodes": [170]}), encoding="utf-8")
        ours = "com.other/.Reader:com.hometv.launcher/.InputKeyService"
        state = self.debloat_tv(secure={"enabled_accessibility_services": ours, "accessibility_enabled": "1"})
        proc, end = self.run_installer(state, "-List", str(folder / "debloat.txt"))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("am force-stop com.hometv.launcher", end["shell"])
        self.assertEqual(self.helpers(end), ours)
        self.assertIn(f"undo settings put secure enabled_accessibility_services {ours}", self.undo())

    def test_each_button_is_tested_from_its_own_screen(self):
        proc, end = self.run_installer(self.tv(), "-BrokenButtons", "Settings")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        shell = end["shell"]
        step = shell[self.index(shell, "am start -a android.settings.SETTINGS"):]
        home = "am start -a android.intent.action.MAIN -c android.intent.category.HOME"
        # Home from Settings once; Input and Settings from Home; the restart for the new config; Settings again from Home.
        self.assertEqual([c for c in step if c in (home, "am start -a android.settings.SETTINGS")],
                         ["am start -a android.settings.SETTINGS", home, home, home, home])

    def test_an_unknown_broken_button_changes_nothing(self):
        proc, end = self.run_installer(self.tv(), "-BrokenButtons", "Home,Mute")
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("not: Mute", proc.stdout)
        self.assert_tv_untouched(end)

    def test_a_config_already_on_the_tv_is_left_alone(self):
        proc, end = self.run_installer(self.tv(config_on_tv='{"dock_apps": []}'), "-BrokenButtons", "Input")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["config_on_tv"], '{"dock_apps": []}')
        self.assertIn("already has a config.json", proc.stdout)
        self.assertEqual(self.helpers(end), "com.hometv.launcher/.InputKeyService")

    def lists_zip(self, folder, lists):
        path = folder / "debloat-lists.zip"
        with zipfile.ZipFile(path, "w") as z:
            for name, text in lists.items():
                z.writestr(f"{name}/debloat.txt", text)
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def stamped_copy(self, apk_sha, lists=None, lists_sha=None):
        """A release copy of the installer and its release folder; `lists` puts a debloat-lists.zip there, and the
        stamped SHA-256 is its own unless `lists_sha` names another."""
        release = self.base / "release"
        release.mkdir()
        shutil.copy2(self.apk, release / "hometv-launcher-v9.9.apk")
        real = self.lists_zip(release, lists) if lists is not None else "00" * 32
        text = INSTALLER.read_text(encoding="utf-8")
        for token, value in {"'@RELEASE_TAG@'": "'v9.9'", "'@RELEASE_APK@'": "'hometv-launcher-v9.9.apk'",
                             "'@RELEASE_APK_SHA256@'": f"'{apk_sha}'",
                             "'@RELEASE_CERT_SHA256@'": f"'{self.cert_sha}'",
                             "'@RELEASE_LISTS_SHA256@'": f"'{lists_sha or real}'"}.items():
            text = text.replace(token, value)
        self.assertNotIn("@RELEASE_", text)
        script = self.base / "stamped" / "family-install.ps1"
        script.parent.mkdir()
        script.write_text(text, encoding="utf-8")
        return script, release

    def test_stamped_copy_installs_its_release(self):
        script, release = self.stamped_copy(hashlib.sha256(self.apk.read_bytes()).hexdigest())
        proc, end = self.run_script(script, self.tv(), "-DownloadFrom", str(release))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(end["preferred"], OURS)

    def test_stamped_copy_suggests_the_lists_naming_this_model(self):
        lists = {"test-tv-eu": "# Test TV (ro.product.model Test TV), Android 9\ncom.example.bloat1\n",
                 "test-tv2": "# Test TV2, Android 11\ncom.example.bloat1\n",
                 "other": "# Other TV\ncom.example.bloat1\n"}
        script, release = self.stamped_copy(hashlib.sha256(self.apk.read_bytes()).hexdigest(), lists)
        proc, end = self.run_script(script, self.tv(packages=self.tv()["packages"] + ["com.example.bloat1"]),
                                    "-DownloadFrom", str(release))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("  test-tv-eu", proc.stdout)
        self.assertNotIn("  test-tv2", proc.stdout, "Test TV2 is another model")
        self.assertNotIn("  other", proc.stdout)
        # No one picked a list (-NoPrompt): nothing but the home screen is switched off.
        self.assertEqual([c for c in end["shell"] if c.startswith("pm disable-user")], [f"pm disable-user --user 0 {TVLAUNCHER}"])

    def test_stamped_copy_refuses_lists_it_did_not_publish(self):
        script, release = self.stamped_copy(hashlib.sha256(self.apk.read_bytes()).hexdigest(), {"x": "# x\n"}, "22" * 32)
        proc, end = self.run_script(script, self.tv(), "-DownloadFrom", str(release))
        self.assertEqual(proc.returncode, 1, proc.stdout)
        self.assertIn("debloat lists download", proc.stdout)
        self.assert_tv_untouched(end)

    def test_repository_copy_suggests_from_lists_from(self):
        lists = self.base / "examples" / "test-tv-x"
        lists.mkdir(parents=True)
        (lists / "debloat.txt").write_text("# TestCo Test TV (ro.product.model Test TV)\ncom.example.x\n", encoding="utf-8")
        proc, end = self.run_installer(self.tv(), "-ListsFrom", str(self.base / "examples"))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("  test-tv-x", proc.stdout)

    def test_at_most_three_lists_best_match_first(self):
        examples = self.base / "examples"
        headers = {
            # Its model is only mentioned, with no build of this TV: not offered.
            "a-mention": "# Like the Test TV, but another set\n",
            "b-declared": "# Some TV (ro.product.model Test TV), Android 9\n",
            "c-same-software": "# Test TV (ro.product.model Test TV), Android 9, software TV05\n",
            "d-declared": "# Other (ro.product.model Test TV)\n",
            "e-declared": "# Other (ro.product.model Test TV)\n",
            "f-build-only": "# Test TV on PPR1.180610.011\n",
        }
        for name, header in headers.items():
            (examples / name).mkdir(parents=True)
            (examples / name / "debloat.txt").write_text(header + "com.example.x\n", encoding="utf-8")
        state = self.tv()
        state["props"].update({"ro.build.display.id": "PPR1.180610.011 release-keys", "ro.build.version.incremental": "TV05"})
        proc, end = self.run_installer(state, "-ListsFrom", str(examples))
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        offered = [line.strip() for line in proc.stdout.split("best first:")[1].splitlines()[1:4]]
        self.assertEqual(offered, ["c-same-software", "b-declared", "d-declared"])
        self.assertNotIn("  a-mention", proc.stdout)

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
