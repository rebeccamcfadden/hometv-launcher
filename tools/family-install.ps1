<#
Installs the HomeTV Launcher on an Android TV from a Windows laptop, for family and friends (BACKLOG.md, Family
install step 4). Read tools/family-install.md first.

  powershell -ExecutionPolicy Bypass -File family-install.ps1                   # install, from a release's copy
  powershell -ExecutionPolicy Bypass -File family-install.ps1 -Restore <folder> # undo an install, from its backup folder

Use the copy attached to a GitHub Release: release.ps1 stamps it with that release's APK name, SHA-256 and signing
certificate, so it installs exactly that APK. The copy in the repository is not stamped and installs only with
-ApkPath and -CertSha256 (testing).

What it does, in order: downloads Google's adb (pinned version and SHA-256) and the release's APK, and checks both;
walks the person through Developer options; connects to the TV (or pairs with Wireless debugging); reads the model and
Android version and stops below Android 8; backs the TV up to this laptop and writes the undo file before it changes
anything; installs the launcher and makes it Home with the Home guard of launcher/build-and-install.sh; and ends with
debugging switched off and the port checked. Everything it writes on this laptop goes under -WorkDir. It starts no
transcript and sends nothing anywhere but the TV.
#>
param(
    # The TV's IP address; asked for when missing.
    [string]$TvHost,
    # The TV's network ADB port: 5555 on most TVs, another fixed port on a few.
    [int]$AdbPort = 5555,
    # Where the downloads, backups and undo files go.
    [string]$WorkDir = (Join-Path $env:USERPROFILE 'HomeTV'),
    # An adb already on this laptop, instead of Google's platform-tools at the pinned version.
    [string]$AdbPath,
    # A folder holding the release's APK, instead of downloading it from GitHub. The APK is still checked.
    [string]$DownloadFrom,
    # Testing, and only in an unstamped copy: a local APK and the signing certificate's SHA-256 it must carry.
    [string]$ApkPath,
    [string]$CertSha256,
    # Undo an install: the backup folder it wrote (it holds undo.txt).
    [string]$Restore,
    # A debloat list (an examples/ debloat.txt, or one made from this installer's report) to apply after the handover.
    [string]$List,
    # Testing, in an unstamped copy: a folder of <name>\debloat.txt lists (the repository's examples\) to suggest from.
    [string]$ListsFrom,
    # Take every question's default answer and skip the pauses (tests, or the project lead on a call).
    [switch]$NoPrompt,
    # Answer yes to the debloat's one "Switch these off?", whose default is no (tests, or the helper who has read the list).
    [switch]$SwitchOffList
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
# Windows PowerShell 5.1: GitHub and dl.google.com need TLS 1.2, and the progress bar makes downloads crawl.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
$ProgressPreference = 'SilentlyContinue'
# Its own adb server, so a developer's adb on this laptop (another version) is neither killed nor used.
$script:OwnServer = $false
if (-not $env:ANDROID_ADB_SERVER_PORT) { $env:ANDROID_ADB_SERVER_PORT = '5038'; $script:OwnServer = $true }

# ---- Pinned: the release this copy installs (stamped by tools/release.ps1) and Google's platform-tools ------------
$ReleaseRepo = 'rebeccamcfadden/hometv-launcher'
$ReleaseTag = '@RELEASE_TAG@'
$ReleaseApk = '@RELEASE_APK@'
$ReleaseApkSha256 = '@RELEASE_APK_SHA256@'
$ReleaseCertSha256 = '@RELEASE_CERT_SHA256@'
$ReleaseListsSha256 = '@RELEASE_LISTS_SHA256@'
$PlatformToolsVersion = '37.0.1'
$PlatformToolsUrl = 'https://dl.google.com/android/repository/platform-tools_r37.0.1-win.zip'
$PlatformToolsSha256 = '45f4d63113e895ebde0c90f194099a4676b6ac653bd28d54314a9e022bbc1a99'

$Launcher = 'com.hometv.launcher'
$LauncherHome = 'com.hometv.launcher/.MainActivity'
$MinSdk = 26
$HomeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
$HomeClaimsQuery = 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
# The stock launchers launcher/build-and-install.sh knows, in the same order (a test keeps the two lists equal).
$KnownStockLaunchers = @('com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx',
    'com.google.android.leanbacklauncher', 'com.mitv.tvhome', 'com.gathin.tvhome', 'com.rmcfad.tv')
$GoogleTvLauncher = 'com.google.android.apps.tv.launcherx'
$SetupWraith = 'com.google.android.tungsten.setupwraith'
# The core packages tools/disable.ps1 refuses, in the same words (a test keeps them equal): without them Android may
# not boot again (#884).
$CoreRe = '^(android|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*)$'
$CoreOk = '^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$'
$PkgRe = '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$'
# PROMPT.md's checklist question: what a family may use, kept unless they say it can go.
$Checklist = [ordered]@{
    'streaming apps' = '(netflix|amazonvideo|primevideo|aiv|avod|imdb|youtube|disney|hbo|wbd\.stream|hulu|peacock|paramount|cbs|tubi|roku|spotify|deezer|tidal|music|plex|twitch|svt|viaplay|appletv|atve|crunchyroll|pluto|vudu|fandango|crackle|sling|fubo|philo|mubi|britbox|showtime|starz|discovery|dazn|espn|rakuten|itv|bbc|iplayer|all4|channel4|my5|stv|freeview|uktv|zee5|hotstar|jio|sonyliv|nrk|tv2|ruutu|yle|joyn|zattoo|waipu|canal|molotov|nowtv|stan|kayo|binge)'
    'the voice assistant' = '(katniss|assistant|alexa|voice|googlequicksearchbox)'
    'casting from a phone (Chromecast)' = '(mediashell|\.cast)'
    'screen mirroring (Miracast)' = '(miracast|wfd|multiscreen|screenshare|screencast|mirror|androidbox|eshare|airplay|dlna|dmr|bigscreen|cast\.sink)'
    'firmware updates' = '(ota|update|fota|upgrade)'
}
# The same questions, from what a list's own comments say a package is (its heading and its line).
$ChecklistNotes = [ordered]@{
    'streaming apps' = '(?i)(stream|music|video app|tv app|apps? not used|by choice|personal choice|owner|preloaded app|not watched|catch-?up|vod)'
    'the voice assistant' = '(?i)(voice|assistant|alexa|gemini|hotword|ok google|mic)'
    'casting from a phone (Chromecast)' = '(?i)(\bcast|chromecast|dial)'
    'screen mirroring (Miracast)' = '(?i)(mirror|miracast|screen ?shar|multi-?screen|magiconnect)'
    'firmware updates' = '(?i)(firmware|\bota\b|updater|system update)'
}
$script:ListNames = @()
$script:ListNotes = @{}
$script:BackupDir = $null
# The folder the release's debloat lists were unpacked to (or -ListsFrom), when the person may pick one.
$script:ListsDir = $null

$script:Adb = $null
$script:Serial = $null
$script:UndoFile = $null

class InstallStop : System.Exception {
    [int]$Code
    InstallStop([string]$Message, [int]$Code) : base($Message) { $this.Code = $Code }
}

# ---- Talking to the person -----------------------------------------------------------------------------------------
function Say([string]$Text) { Write-Host $Text }
function Step([string]$Text) { Write-Host ''; Write-Host "== $Text" -ForegroundColor Cyan }
function Stop-Install([string]$Text, [int]$Code = 1) { throw [InstallStop]::new($Text, $Code) }

function Ask-YesNo([string]$Question, [bool]$Default) {
    if ($NoPrompt) { return $Default }
    $hint = if ($Default) { '[Y/n]' } else { '[y/N]' }
    while ($true) {
        $answer = "$(Read-Host "$Question $hint")".Trim().ToLowerInvariant()
        if ($answer -eq '') { return $Default }
        if ($answer -eq 'y' -or $answer -eq 'yes') { return $true }
        if ($answer -eq 'n' -or $answer -eq 'no') { return $false }
        Say 'Please type y or n.'
    }
}

function Ask-Text([string]$Question, [string]$Default = '') {
    if ($NoPrompt) { return $Default }
    return "$(Read-Host $Question)".Trim()
}

function Wait-Done([string]$Text) {
    if ($NoPrompt) { return }
    [void](Read-Host "$Text Press Enter when that is done")
}

# ---- adb ----------------------------------------------------------------------------------------------------------
# Windows PowerShell 5.1 turns a native command's stderr into an error under 'Stop'; adb writes progress there.
function Invoke-Adb([string[]]$Arguments) {
    $saved = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $script:Adb @Arguments 2>&1 | ForEach-Object { "$_" }
        $script:AdbExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $saved }
    return ((@($out) -join "`n") -replace "`r", '').Trim()
}

# One command per call, as PROMPT.md asks. No double quotes: Windows PowerShell 5.1 mangles them on the way to adb.
function Invoke-TvShell([string]$Command) {
    if ($Command.Contains('"')) { throw "Internal error: a TV command holds a double quote: $Command" }
    return Invoke-Adb @('-s', $script:Serial, 'shell', $Command)
}

function Get-Prop([string]$Name) { return (Invoke-TvShell "getprop $Name").Trim() }

function Get-HomeNow { return ((Invoke-TvShell $HomeQuery) -split "`n" | Select-Object -Last 1).Trim() }

function Get-PackageOf([string]$Component) { return ($Component -split '/')[0] }

# An app-op's mode for a package, or $null when the read failed or its answer is not one we know. Android answers
# 'No operations.' for an app-op never set, which is 'default'; nothing else is taken to mean that.
function Get-AppOpMode([string]$Package, [string]$Op) {
    $out = Invoke-TvShell "appops get $Package $Op"
    if ($script:AdbExit -ne 0) { return $null }
    $m = [regex]::Match($out, "\b${Op}: (allow|ignore|deny|default|foreground)\b")
    if ($m.Success) { return $m.Groups[1].Value }
    if ($out.Trim() -eq 'No operations.') { return 'default' }
    return $null
}

# Ours in front: the activity Android resumed after a Home press. resolve-activity alone can name ours while Home still
# opens setupwraith's RecoveryActivity on some Google TV builds (PROMPT.md, Launcher).
function Test-HomeShowsOurs {
    [void](Invoke-TvShell 'input keyevent KEYCODE_HOME')
    Start-Sleep -Seconds 3
    $front = Invoke-TvShell 'dumpsys activity activities | grep -E ''mResumedActivity|mFocusedApp|topResumedActivity'''
    return $front.Contains("$Launcher/")
}

function Get-FileSha256([string]$Path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    $stream = [IO.File]::OpenRead($Path)
    try { return (($sha.ComputeHash($stream) | ForEach-Object { $_.ToString('x2') }) -join '') }
    finally { $stream.Dispose(); $sha.Dispose() }
}

function Get-PinnedAdb {
    if ($AdbPath) {
        if (-not (Test-Path -LiteralPath $AdbPath)) { Stop-Install "There is no adb at $AdbPath." }
        return $AdbPath
    }
    $dir = Join-Path $WorkDir "platform-tools-$PlatformToolsVersion"
    $adb = Join-Path $dir 'platform-tools\adb.exe'
    $zip = Join-Path $WorkDir "platform-tools_r$PlatformToolsVersion-win.zip"
    if (-not (Test-Path -LiteralPath $zip) -or (Get-FileSha256 $zip) -ne $PlatformToolsSha256) {
        Say "Downloading Google's Android platform-tools $PlatformToolsVersion (8 MB) from dl.google.com..."
        Invoke-WebRequest -UseBasicParsing -Uri $PlatformToolsUrl -OutFile $zip
    }
    if ((Get-FileSha256 $zip) -ne $PlatformToolsSha256) {
        Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
        Stop-Install "Google's platform-tools download is not the file this installer expects (its SHA-256 differs). Nothing was changed. Try again later, and tell the person helping you."
    }
    # Unpacked from the checked zip each run, so a changed adb.exe on disk is never used, not even to stop a server: a
    # fresh copy from the checked zip stops the one an earlier run left (it holds adb.exe open), then replaces the old.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $stage = "$dir.new"
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
    [IO.Compression.ZipFile]::ExtractToDirectory($zip, $stage)
    if (-not (Test-Path -LiteralPath (Join-Path $stage 'platform-tools\adb.exe'))) { Stop-Install 'The platform-tools zip has no adb.exe.' }
    if (Test-Path -LiteralPath $dir) {
        if ($script:OwnServer) {
            $saved = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
            try { & (Join-Path $stage 'platform-tools\adb.exe') kill-server 2>&1 | Out-Null } finally { $ErrorActionPreference = $saved }
            Start-Sleep -Seconds 1
        }
        try { Remove-Item -LiteralPath $dir -Recurse -Force }
        catch { Stop-Install "Could not replace the old copy of adb in $dir. Close any other window running this installer, or restart the laptop, then run it again. Nothing was changed on the TV." }
    }
    Move-Item -LiteralPath $stage -Destination $dir
    return $adb
}

# ---- The APK and its signer ----------------------------------------------------------------------------------------
# The SHA-256 of the signer's certificate in each APK Signature Scheme block present: v2, which Android 8 reads, and v3
# and v3.1, which newer Android reads instead (source.android.com/docs/security/features/apksigning). Empty when there
# is no v2 block, a block names more or fewer than one signer, or the APK is malformed. Signatures are not verified:
# the release APK's SHA-256 is pinned, and this check only catches an APK from anywhere else.
function Get-ApkSignerDigests([string]$Path) {
    $b = [IO.File]::ReadAllBytes($Path)
    try {
        $n = [long]$b.Length
        $eocd = -1L
        for ($i = $n - 22; $i -ge [Math]::Max(0, $n - 22 - 65535); $i--) {
            if ($b[$i] -eq 0x50 -and $b[$i + 1] -eq 0x4b -and $b[$i + 2] -eq 0x05 -and $b[$i + 3] -eq 0x06) { $eocd = $i; break }
        }
        if ($eocd -lt 0) { return @() }
        $cd = [long][BitConverter]::ToUInt32($b, $eocd + 16)
        if ($cd -lt 32 -or $cd -gt $eocd) { return @() }
        if ([Text.Encoding]::ASCII.GetString($b, $cd - 16, 16) -ne 'APK Sig Block 42') { return @() }
        $size = [long][BitConverter]::ToUInt64($b, $cd - 24)
        $start = $cd - $size - 8
        if ($start -lt 0 -or [long][BitConverter]::ToUInt64($b, $start) -ne $size) { return @() }
        $blocks = @{}
        $pos = $start + 8
        $end = $cd - 24
        while ($pos + 12 -le $end) {
            $len = [long][BitConverter]::ToUInt64($b, $pos)
            $id = [long][BitConverter]::ToUInt32($b, $pos + 8)
            $valueEnd = $pos + 8 + $len
            if ($len -lt 4 -or $valueEnd -gt $end) { return @() }
            $blocks[$id] = @(($pos + 12), $valueEnd)
            $pos = $valueEnd
        }
        $v2 = [long][Convert]::ToUInt32('7109871a', 16)
        $ids = @($v2, [long][Convert]::ToUInt32('f05368c0', 16), [long][Convert]::ToUInt32('1b93ad61', 16))
        # A uint32 length, then that many bytes: returns the slice and moves the cursor past it.
        $readLp = {
            param([long]$At, [long]$Limit)
            if ($At + 4 -gt $Limit) { throw 'short' }
            $s = $At + 4
            $e = $s + [long][BitConverter]::ToUInt32($b, $At)
            if ($e -gt $Limit) { throw 'long' }
            return , @($s, $e)
        }
        $sha = [Security.Cryptography.SHA256]::Create()
        $digests = @()
        if (-not $blocks.ContainsKey($v2)) { return @() }
        foreach ($id in $ids) {
            if (-not $blocks.ContainsKey($id)) { continue }
            $signers = & $readLp $blocks[$id][0] $blocks[$id][1]
            $at = $signers[0]
            $found = 0
            while ($at -lt $signers[1]) {
                $signer = & $readLp $at $signers[1]
                $at = $signer[1]
                $signedData = & $readLp $signer[0] $signer[1]
                $digestSeq = & $readLp $signedData[0] $signedData[1]
                $certSeq = & $readLp $digestSeq[1] $signedData[1]
                $cert = & $readLp $certSeq[0] $certSeq[1]
                $hash = $sha.ComputeHash($b, [int]$cert[0], [int]($cert[1] - $cert[0]))
                $digests += (($hash | ForEach-Object { $_.ToString('x2') }) -join '')
                $found++
            }
            # Each scheme present must name exactly one signer: Android 8 reads only v2, newer Android v3.
            if ($found -ne 1) { return @() }
        }
        return $digests
    } catch {
        return @()
    }
}

function Get-CheckedApk {
    $stamped = -not $ReleaseTag.StartsWith('@')
    if ($stamped) {
        $dir = Join-Path $WorkDir "release-$ReleaseTag"
        New-Item -ItemType Directory -Force $dir | Out-Null
        $apk = Join-Path $dir $ReleaseApk
        if (-not (Test-Path -LiteralPath $apk) -or (Get-FileSha256 $apk) -ne $ReleaseApkSha256) {
            if ($DownloadFrom) {
                $source = Join-Path $DownloadFrom $ReleaseApk
                if (-not (Test-Path -LiteralPath $source)) { Stop-Install "There is no $ReleaseApk in $DownloadFrom." }
                Copy-Item -LiteralPath $source $apk -Force
            } else {
                Say "Downloading HomeTV Launcher $ReleaseTag from GitHub..."
                Invoke-WebRequest -UseBasicParsing -OutFile $apk `
                    -Uri "https://github.com/$ReleaseRepo/releases/download/$ReleaseTag/$ReleaseApk"
            }
        }
        if ((Get-FileSha256 $apk) -ne $ReleaseApkSha256) {
            Remove-Item -LiteralPath $apk -Force -ErrorAction SilentlyContinue
            Stop-Install "The launcher download is not the file release $ReleaseTag published (its SHA-256 differs). Nothing was changed on the TV."
        }
        $cert = $ReleaseCertSha256
    } else {
        $apk = $ApkPath
        $cert = ($CertSha256 -replace '[^0-9A-Fa-f]', '').ToLowerInvariant()
        if (-not (Test-Path -LiteralPath $apk)) { Stop-Install "There is no APK at $apk." }
    }
    $signers = @(Get-ApkSignerDigests $apk)
    if ($signers.Count -eq 0 -or @($signers | Where-Object { $_ -ne $cert }).Count -gt 0) {
        Stop-Install 'The launcher APK is not signed with the HomeTV release key. Nothing was changed on the TV; tell the person helping you.'
    }
    return $apk
}

# ---- The undo file -------------------------------------------------------------------------------------------------
# One line per change, written before the change is made, so an install cut off half-way can still be undone.
function Add-Undo([string]$Command) {
    if ($Command.Contains('"')) { throw "Internal error: an undo command holds a double quote: $Command" }
    Add-Content -LiteralPath $script:UndoFile -Value "undo $Command" -Encoding ascii
}

# ---- Connecting ----------------------------------------------------------------------------------------------------
function Wait-Authorized([string]$Serial) {
    $told = $false
    for ($i = 0; $i -lt 60; $i++) {
        $state = Invoke-Adb @('-s', $Serial, 'get-state')
        if ($state -eq 'device') { return $true }
        if ($state -match 'unauthorized' -and -not $told) {
            Say 'Look at the TV: it asks "Allow USB debugging?". Tick "Always allow from this computer", then choose OK.'
            $told = $true
        }
        if ($state -match 'offline') { [void](Invoke-Adb @('connect', $Serial)) }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Connect-Tv {
    [void](Invoke-Adb @('start-server'))
    $tvHost = $TvHost
    while (-not $tvHost) {
        if ($NoPrompt) { Stop-Install 'No TV address: pass -TvHost.' }
        Say "The TV's address: on the TV, Settings > Network & Internet > your network shows its IP address, like 192.168.1.50."
        $tvHost = Ask-Text "Type the TV's IP address"
    }
    Say 'Watch the TV while the laptop connects: it asks "Allow USB debugging?". Tick "Always allow from this computer", then choose OK.'
    for ($try = 1; $try -le 3; $try++) {
        $serial = "${tvHost}:$AdbPort"
        $out = Invoke-Adb @('connect', $serial)
        # A TV that has not allowed this laptop yet answers "failed to authenticate": it is there, waiting on its box.
        if ($out -match '(already )?connected to|failed to authenticate') {
            if (Wait-Authorized $serial) { return @($tvHost, $serial, $false) }
            Say 'The TV did not allow this laptop. On the TV, accept the "Allow USB debugging?" box.'
        } else {
            Say "The TV did not answer at $serial."
        }
        if (Ask-YesNo 'Does Developer options on the TV have "Wireless debugging"?' $false) {
            Say 'On the TV: Developer options > Wireless debugging > turn it on, then choose "Pair device with pairing code".'
            $pairAt = Ask-Text 'Type the IP address and port it shows under the code (like 192.168.1.50:37123)'
            $code = Ask-Text 'Type the 6-digit pairing code'
            $paired = Invoke-Adb @('pair', $pairAt, $code)
            if ($paired -notmatch 'Successfully paired') { Say "Pairing did not work: $paired"; continue }
            Say 'Paired. Now the Wireless debugging screen itself shows "IP address & Port" at the top (not the pairing one).'
            $port = Ask-Text 'Type just the port number from there'
            $serial = "${tvHost}:$port"
            $out = Invoke-Adb @('connect', $serial)
            if ($out -match 'connected to|failed to authenticate' -and (Wait-Authorized $serial)) { return @($tvHost, $serial, $true) }
            Say "Could not connect to $serial."
        } elseif (Ask-YesNo 'Check the address and that USB debugging (and Network debugging, if shown) is on. Try again?' $true) {
            $typed = Ask-Text "Type the TV's IP address again (Enter keeps $tvHost)"
            if ($typed) { $tvHost = $typed }
        } else {
            break
        }
    }
    Stop-Install 'Could not connect to the TV. Nothing was changed. Check the steps in tools/family-install.md with the person helping you.'
}

function Wait-TvBack([string]$TvHostName, [bool]$Wireless) {
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 5
        if (-not $Wireless) { [void](Invoke-Adb @('connect', $script:Serial)) }
        if ((Invoke-Adb @('-s', $script:Serial, 'get-state')) -eq 'device' -and (Get-Prop 'sys.boot_completed') -eq '1') { return $true }
        if ($Wireless -and $i -eq 18) {
            Say 'Wireless debugging gets a new port after a restart. On the TV: Developer options > Wireless debugging, turn it on.'
            $port = Ask-Text 'Type the port shown under "IP address & Port"'
            if ($port) { $script:Serial = "${TvHostName}:$port"; [void](Invoke-Adb @('connect', $script:Serial)) }
        }
    }
    return $false
}

# 'open', 'closed' (the TV refused the connection: nothing listens) or 'unknown' (a timeout, no route, no network):
# only a refusal proves debugging is off, since a TV that is off or unreachable doesn't answer either.
function Get-PortState([string]$HostName, [int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        if (-not $task.Wait(3000)) { return 'unknown' }
        if ($client.Connected) { return 'open' }
        return 'unknown'
    } catch {
        $e = $_.Exception
        while ($e) {
            if ($e -is [Net.Sockets.SocketException] -and $e.SocketErrorCode -eq [Net.Sockets.SocketError]::ConnectionRefused) { return 'closed' }
            $e = $e.InnerException
        }
        return 'unknown'
    } finally { $client.Dispose() }
}

# ---- Restore -------------------------------------------------------------------------------------------------------
function Invoke-Restore {
    $file = Join-Path $Restore 'undo.txt'
    if (-not (Test-Path -LiteralPath $file)) { Stop-Install "There is no undo.txt in $Restore." }
    $lines = @(Get-Content -LiteralPath $file)
    $serialLine = $lines | Where-Object { $_.StartsWith('serial ') } | Select-Object -First 1
    $script:Serial = if ($TvHost) { "${TvHost}:$AdbPort" } elseif ($serialLine) { $serialLine.Substring(7).Trim() } else { $null }
    if (-not $script:Serial) { Stop-Install 'undo.txt names no TV: pass -TvHost.' }
    $undo = @($lines | Where-Object { $_.StartsWith('undo ') } | ForEach-Object { $_.Substring(5) })
    # Where Home has to end up: the stock home screen the install recorded first (so it runs last).
    $homeLine = @($undo | Where-Object { $_ -match '^cmd package set-home-activity --user 0 (\S+)$' } | Select-Object -First 1)
    $target = if ($homeLine.Count -gt 0) { Get-PackageOf (($homeLine[0] -split ' ')[-1]) } else { $null }
    [array]::Reverse($undo)
    Step "Undoing the install on $($script:Serial)"
    [void](Invoke-Adb @('start-server'))
    [void](Invoke-Adb @('connect', $script:Serial))
    if (-not (Wait-Authorized $script:Serial)) {
        Stop-Install "The TV does not answer at $($script:Serial). Switch debugging on again (tools/family-install.md), or pass -TvHost and -AdbPort."
    }
    $failed = @()
    foreach ($command in $undo) {
        Say "TV: $command"
        $out = Invoke-TvShell $command
        Say $out
        $ok = $script:AdbExit -eq 0 -and $out -notmatch '(?i)error|exception|unknown package'
        if ($ok -and $command -match '^pm enable --user 0 (\S+)$') { $ok = $out -match 'new state: enabled' }
        # appops set prints nothing either way: read the mode back.
        if ($ok -and $command -match '^appops set (\S+) (\S+) (\S+)$') { $ok = (Get-AppOpMode $Matches[1] $Matches[2]) -eq $Matches[3] }
        if (-not $ok) { $failed += $command }
    }
    $homeNow = Get-HomeNow
    Say "Home is now: $homeNow"
    if ($target -and -not $homeNow.StartsWith("$target/")) { $failed += "Home is $homeNow, not $target" }
    if ($failed.Count -gt 0) {
        Stop-Install ("The undo is not complete, so keep debugging on and send this to the person helping you:`n  " + ($failed -join "`n  "))
    }
    Say 'Done. Switch debugging off on the TV again (Developer options).'
}

# ---- Debloat from a list (Family install step 6) -------------------------------------------------------------------
# The release's debloat lists (debloat-lists.zip, its SHA-256 stamped), unpacked under -WorkDir; null without one.
function Get-CheckedLists {
    if ($ReleaseTag.StartsWith('@')) { return $null }
    $dir = Join-Path $WorkDir "release-$ReleaseTag"
    $zip = Join-Path $dir 'debloat-lists.zip'
    if (-not (Test-Path -LiteralPath $zip) -or (Get-FileSha256 $zip) -ne $ReleaseListsSha256) {
        if ($DownloadFrom) {
            $source = Join-Path $DownloadFrom 'debloat-lists.zip'
            if (-not (Test-Path -LiteralPath $source)) { Say 'No debloat lists next to the APK: nothing beyond the home screen will be switched off.'; return $null }
            Copy-Item -LiteralPath $source $zip -Force
        } else {
            Invoke-WebRequest -UseBasicParsing -OutFile $zip -Uri "https://github.com/$ReleaseRepo/releases/download/$ReleaseTag/debloat-lists.zip"
        }
    }
    if ((Get-FileSha256 $zip) -ne $ReleaseListsSha256) {
        Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
        Stop-Install "The debloat lists download is not the file release $ReleaseTag published (its SHA-256 differs). Nothing was changed on the TV."
    }
    $out = Join-Path $dir 'lists'
    if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Recurse -Force }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($zip, $out)
    return $out
}

# The lists whose header (the comment lines at the top) names this model as a word: a suggestion only, since one
# model often has lists from different countries and firmware, and another household's choices aren't theirs.
function Find-ListsForModel([string]$Dir, [string]$Model) {
    if (-not $Dir -or -not $Model -or -not (Test-Path -LiteralPath $Dir)) { return @() }
    $word = '(?<![A-Za-z0-9])' + [regex]::Escape($Model) + '(?![A-Za-z0-9])'
    $found = @()
    foreach ($folder in Get-ChildItem -LiteralPath $Dir -Directory | Sort-Object Name) {
        $file = Join-Path $folder.FullName 'debloat.txt'
        if (-not (Test-Path -LiteralPath $file)) { continue }
        $header = @(Get-Content -LiteralPath $file -TotalCount 40 | ForEach-Object { $_ } | Where-Object { $_ -match '^\s*#' })
        if (($header -join "`n") -match $word) { $found += $folder.Name }
    }
    return $found
}

# Step 6, option (a) (the project lead, 2026-11-02): suggests the lists that name this model, and the person helping
# picks one by name on the call, or none. Without a pick nothing beyond the home screen is switched off; the report is
# for making a list.
function Select-DebloatList([string]$Model) {
    if (-not $script:ListsDir) { return }
    $suggested = @(Find-ListsForModel $script:ListsDir $Model)
    Step 'Debloat lists'
    if ($suggested.Count -eq 0) {
        Say "No list in this release names the model $Model. Nothing beyond the home screen will be switched off; report.txt in the backup folder is for making one."
    } else {
        Say "Lists in this release that name the model ${Model}:"
        foreach ($s in $suggested) { Say "  $s" }
        Say 'Each is another household''s choices: the person helping you picks one with you, or none.'
    }
    if ($NoPrompt) { return }
    while ($true) {
        $name = Ask-Text 'Type the name of the list to use (the person helping you knows it), or press Enter for none'
        if (-not $name) { Say 'No list: nothing beyond the home screen will be switched off.'; return }
        $file = Join-Path (Join-Path $script:ListsDir $name) 'debloat.txt'
        if ($name -match '^[A-Za-z0-9._-]+$' -and (Test-Path -LiteralPath $file)) {
            $script:ListNames = @(Read-DebloatList $file)
            Say "Using $name ($($script:ListNames.Count) apps on it; the ones you use stay on)."
            return
        }
        Say "There is no list called '$name' in this release."
    }
}
# Read and checked before anything reaches the TV: a list that names something that isn't a package, or one of
# Android's core packages, stops the run with nothing changed, as tools/disable.ps1 refuses it.
function Read-DebloatList([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { Stop-Install "There is no list at $Path. Nothing was changed." }
    # What the list says about each package: the comment heading above it and its own comment. The checklist reads them,
    # so a household app the list itself files under streaming, voice, casting and the like is asked about by name.
    $script:ListNotes = @{}
    $section = ''
    $names = @()
    foreach ($raw in Get-Content -LiteralPath $Path) {
        $line = $raw.Trim()
        if (-not $line) { continue }
        $hash = $line.IndexOf('#')
        $code = if ($hash -ge 0) { $line.Substring(0, $hash).Trim() } else { $line }
        $comment = if ($hash -ge 0) { $line.Substring($hash + 1) } else { '' }
        if (-not $code) { $section = $comment; continue }
        if ($names -notcontains $code) { $names += $code; $script:ListNotes[$code] = "$section $comment" }
    }
    $bad = @($names | Where-Object { $_ -cnotmatch $PkgRe })
    if ($bad.Count -gt 0) { Stop-Install "The list $Path has lines that are not package names: $($bad -join ', '). Nothing was changed." }
    $core = @($names | Where-Object { $_ -cmatch $CoreRe -and $_ -cnotmatch $CoreOk })
    if ($core.Count -gt 0) {
        Stop-Install "The list $Path would switch off Android's own core packages, and without them the TV may not start again (#884): $($core -join ', '). Nothing was changed."
    }
    return $names
}

# The packages a TV answer names at the start of a line (pkg/activity), in order.
function Get-LinePackages([string[]]$Lines) {
    return @($Lines | ForEach-Object { if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+)/') { $Matches[1] } } | Select-Object -Unique)
}

# Applies the list after the handover, with tools/disable.sh's guards and no overrides: a check that cannot run skips
# the debloat (the person can't test what it protects). Returns the packages it switched off.
function Invoke-Debloat([string[]]$Names) {
    Step 'Switching off what this TV does not need'
    $installedNow = @((Invoke-TvShell 'pm list packages') -split "`n" | ForEach-Object { $_.Trim() -replace '^package:', '' } | Where-Object { $_ })
    $offNow = @((Invoke-TvShell 'pm list packages -d') -split "`n" | ForEach-Object { $_.Trim() -replace '^package:', '' } | Where-Object { $_ })
    $todo = @($Names | Where-Object { $installedNow -contains $_ -and $offNow -notcontains $_ -and $_ -ne $Launcher })
    $skip = { param($pkg, $why) Say "Leaving $pkg on: $why"; $script:todoNow = @($script:todoNow | Where-Object { $_ -ne $pkg }) }
    $script:todoNow = $todo

    # Home guard: never the app that owns Home; and if Home isn't this launcher, no known launcher either.
    $homePkg = Get-PackageOf (Get-HomeNow)
    if ($script:todoNow -contains $homePkg) { & $skip $homePkg 'it is the home screen.' }
    if ($homePkg -ne $Launcher) {
        foreach ($k in $KnownStockLaunchers) { if ($script:todoNow -contains $k) { & $skip $k 'Home is not on the HomeTV Launcher, so the old home screen stays.' } }
    }
    # A CVTE board keeps its own launcher enabled (AGENTS.md, #741): only that exact board's examples/ list may switch it
    # off, and a list suggested by a model word can be another CVTE board's. So never from here.
    if (@($installedNow | Where-Object { $_ -like 'com.cvte.*' }).Count -gt 0) {
        foreach ($p in @($script:todoNow | Where-Object { $_ -like 'com.cvte.tv.launcher*' })) {
            & $skip $p 'on a CVTE board its own launcher stays on (only that exact board''s list may switch it off, by hand).'
        }
    }
    # PROMPT.md's checklist: what the family uses stays on.
    foreach ($what in $Checklist.Keys) {
        $hits = @($script:todoNow | Where-Object { $_ -match $Checklist[$what] -or ($script:ListNotes.ContainsKey($_) -and $script:ListNotes[$_] -match $ChecklistNotes[$what]) })
        if ($hits.Count -eq 0) { continue }
        if (Ask-YesNo "Does anyone use $what on this TV? ($($hits -join ', '))" $true) {
            foreach ($h in $hits) { & $skip $h "you use $what." }
        }
    }
    # setupwraith deletes the Wi-Fi network it saved when it is switched off (tools/disable.sh).
    if ($script:todoNow -contains $SetupWraith) {
        $owners = @([regex]::Matches((Invoke-TvShell 'dumpsys wifi'), 'cname=[^ ]*') | ForEach-Object { $_.Value })
        if ($owners.Count -eq 0 -or @($owners | Where-Object { $_ -match 'setupwraith' }).Count -gt 0) {
            & $skip $SetupWraith 'it may own the saved Wi-Fi network, and switching it off would take the TV off Wi-Fi.'
        }
    }
    if ($script:todoNow.Count -eq 0) { Say 'Nothing on the list is left to switch off.'; return @() }

    # The software under HDMI, AV and the tuner (#321), and the apps that open the inputs and Settings. Each probe is
    # judged on its own answer: a package's dump must name the package, and a query must answer without an error, or
    # nothing more is switched off (the person can't test what these protect).
    $probeFailed = $false
    $hwPkgs = @()
    foreach ($p in @($script:todoNow)) {
        $dump = Invoke-TvShell "dumpsys package $p"
        if ($script:AdbExit -ne 0 -or -not $dump.Contains("Package [$p]")) { $probeFailed = $true; break }
        $m = [regex]::Match($dump, '(?s)requested permissions:(.*?)((install|runtime) permissions:|User [0-9]+:|$)')
        if ($m.Success -and $m.Groups[1].Value -match 'android\.permission\.TV_INPUT_HARDWARE') { $hwPkgs += $p }
    }
    $inputs = Invoke-TvShell 'cmd package query-activities --brief -a android.intent.action.VIEW -t vnd.android.cursor.item/channel'
    if ($script:AdbExit -ne 0 -or $inputs -match '(?i)exception|error|unknown') { $probeFailed = $true }
    $settingsApps = Invoke-TvShell 'cmd package query-activities --brief -a android.settings.SETTINGS'
    if ($script:AdbExit -ne 0 -or $settingsApps -match '(?i)exception|error|unknown') { $probeFailed = $true }
    if ($probeFailed) {
        Say 'Could not check which apps run the TV''s inputs and Settings, so nothing more is switched off. Send the report to the person helping you.'
        return @()
    }
    foreach ($p in $hwPkgs) { & $skip $p 'it runs the HDMI, AV or tuner inputs.' }
    foreach ($p in (Get-LinePackages ($inputs -split "`n"))) { if ($script:todoNow -contains $p) { & $skip $p 'the TV opens its inputs with it.' } }
    foreach ($p in (Get-LinePackages ($settingsApps -split "`n"))) { if ($script:todoNow -contains $p) { & $skip $p 'the TV opens Settings with it.' } }
    if ($script:todoNow.Count -eq 0) { Say 'Nothing on the list is left to switch off.'; return @() }

    # Everything left is listed once, and switched off only on a yes: a list is another household's choices.
    Say 'From the list, these would be switched off now (each can be switched back on with -Restore):'
    foreach ($p in $script:todoNow) { Say "  $p" }
    # No unless someone says yes: Enter, or -NoPrompt alone, switches nothing off.
    $yes = if ($SwitchOffList) { $true } else { Ask-YesNo 'Switch these off?' $false }
    if (-not $yes) { Say 'Nothing from the list was switched off.'; return @() }

    # A reinstall wrote no Home line yet: -Restore must put Home back on HomeTV after it re-enables these.
    if (-not @(Get-Content -LiteralPath $script:UndoFile | Where-Object { $_ -like 'undo cmd package set-home-activity *' }).Count) {
        Add-Undo "cmd package set-home-activity --user 0 $LauncherHome"
    }

    # Small batches, one package per call, each undo line written first; Home is checked after each batch.
    $done = @()
    for ($i = 0; $i -lt $script:todoNow.Count; $i += 5) {
        $batch = @($script:todoNow[$i..([Math]::Min($i + 4, $script:todoNow.Count - 1))])
        $off = @()
        foreach ($p in $batch) {
            Add-Undo "pm enable --user 0 $p"
            $out = Invoke-TvShell "pm disable-user --user 0 $p"
            if ($out -match 'new state: disabled') { $off += $p } else { Say "The TV did not switch off ${p}: $out" }
        }
        # Resolving to ours is not enough: on some Google TV builds a Home press still opens setupwraith's RecoveryActivity.
        if (-not ((Get-HomeNow).StartsWith("$Launcher/") -and (Test-HomeShowsOurs))) {
            # The whole batch back on (its undo lines are all written), Home set to ours again, and checked: on Android 9
            # and older, re-enabling a Home app doesn't bring the saved default back by itself.
            $notBack = @()
            foreach ($p in $batch) {
                $out = Invoke-TvShell "pm enable --user 0 $p"
                if ($out -notmatch 'new state: enabled') { $notBack += $p }
            }
            [void](Invoke-TvShell "cmd package set-home-activity --user 0 $LauncherHome")
            $homeBack = (Get-HomeNow).StartsWith("$Launcher/") -and (Test-HomeShowsOurs)
            if ($notBack.Count -gt 0 -or -not $homeBack) {
                Stop-Install ("Switching off $($batch -join ', ') moved Home away from the HomeTV Launcher, and it did not all come back" +
                    $(if ($notBack.Count -gt 0) { " (still off: $($notBack -join ', '))" } else { '' }) +
                    ". Keep debugging on and run: powershell -ExecutionPolicy Bypass -File family-install.ps1 -Restore `"$script:BackupDir`"")
            }
            Say "Home moved away from the HomeTV Launcher after switching off $($batch -join ', '), so those are back on, Home is the HomeTV Launcher again, and the rest of the list is left as it was."
            break
        }
        $done += $off
        Say "Switched off: $($off -join ', ')"
    }
    if ($done.Count -gt 0) {
        Say 'Some TVs switch an app or two back on at every restart (TCL''s User Center does). That is the TV, not a fault: leave it on.'
    }
    return $done
}

# What the person helping gets to make a list from: the TV's make, model and firmware, and its packages. No serial,
# address, account or network name.
function Write-Report([string]$Folder, [string[]]$Packages, [string[]]$Disabled) {
    $props = 'ro.product.manufacturer', 'ro.product.brand', 'ro.product.model', 'ro.product.device', 'ro.build.version.release',
        'ro.build.version.sdk', 'ro.build.display.id'
    $lines = @('# HomeTV install report: the TV, and its apps, for making a debloat list (tools/family-install.md).')
    foreach ($p in $props) { $lines += "$p=$(Get-Prop $p)" }
    $lines += '', '# Installed packages (disabled ones marked)'
    $lines += @($Packages | Sort-Object | ForEach-Object { if ($Disabled -contains $_) { "$_ (disabled)" } else { $_ } })
    [IO.File]::WriteAllText((Join-Path $Folder 'report.txt'), (($lines -join "`n") + "`n"))
}

# ---- The install ---------------------------------------------------------------------------------------------------
function Invoke-Install {
    Step 'HomeTV Launcher installer'
    Say 'This puts the HomeTV Launcher on an Android TV or Google TV on this network and makes it the home screen.'
    Say 'It works on Android TV and Google TV sets and boxes. It cannot work on Samsung, LG or Fire TV.'
    Say "It backs the TV up to this laptop first, and every step can be undone. It takes about 15 minutes. Files go to $WorkDir."

    New-Item -ItemType Directory -Force $WorkDir | Out-Null
    Step 'Checking the downloads'
    $script:Adb = Get-PinnedAdb
    $apk = Get-CheckedApk
    if (-not $List -and -not $script:ListsDir) { $script:ListsDir = Get-CheckedLists }
    Say 'The launcher and adb are the files this installer expects.'

    Step 'Getting the TV ready'
    # Asked before debugging goes on: some boxes switch a USB network adapter off the moment USB debugging is on
    # (docs/TROUBLESHOOTING.md), and then the laptop can't reach the TV at all.
    Say 'Some TVs cut power to their USB ports while debugging is on, so a USB stick, speaker or adapter there stops working until the end.'
    $usbThings = Ask-Text 'What is plugged into the TV''s USB ports? (Enter for nothing)'
    $usbEthernet = Ask-YesNo 'Does the TV''s network cable go into a USB adapter, rather than straight into the TV?' $false
    if ($usbEthernet) {
        Say 'Some boxes switch that adapter off as soon as USB debugging is on, and then this laptop cannot reach the TV.'
        Say 'So first connect the TV to Wi-Fi too: Settings > Network & Internet > your Wi-Fi network. Use the Wi-Fi address below.'
        Wait-Done 'Connect the TV to Wi-Fi.'
    }
    Say 'On the TV, with the remote:'
    Say '  1. Settings > Device Preferences (or System) > About: press OK on "Build" 7 times, until it says you are a developer.'
    Say '  2. Go back to Device Preferences (or System) > Developer options.'
    Say '  3. Turn on "USB debugging". If you also see "Network debugging" or "ADB debugging", turn that on too.'
    Wait-Done 'Do that now.'
    if ($usbEthernet) {
        Say 'Do the whole install in one go: the adapter may only come back once debugging is off again at the end.'
    }

    Step 'Connecting'
    $connected = Connect-Tv
    $tvHostName = $connected[0]
    $script:Serial = $connected[1]
    $wireless = [bool]$connected[2]
    Say "Connected to the TV at $($script:Serial)."

    $sdkText = Get-Prop 'ro.build.version.sdk'
    $model = Get-Prop 'ro.product.model'
    $maker = Get-Prop 'ro.product.manufacturer'
    $android = Get-Prop 'ro.build.version.release'
    $sdk = 0
    if (-not [int]::TryParse($sdkText, [ref]$sdk)) { Stop-Install "The TV did not say its Android version (got '$sdkText'). Nothing was changed." }
    Say "TV: $maker $model, Android $android (SDK $sdk)."
    if ($sdk -lt $MinSdk) {
        Stop-Install "This TV runs Android $android (SDK $sdk). The launcher needs Android 8 (SDK $MinSdk) or newer, so it cannot be installed here. Nothing was changed."
    }
    if ($maker -match '^Amazon') {
        Stop-Install 'This is a Fire TV. Its home screen cannot be replaced by this installer. Nothing was changed.'
    }
    if ($usbEthernet) {
        $transports = Invoke-TvShell 'dumpsys connectivity | grep -o ''Transports: [A-Z]*'''
        if ($transports -match 'WIFI' -and $transports -notmatch 'ETHERNET') {
            Say 'Note: the TV is on Wi-Fi right now, not the cable adapter. That is fine for the install; at the end, unplug and replug the adapter.'
        }
    }
    $packages = @((Invoke-TvShell 'pm list packages') -split "`n" | ForEach-Object { $_.Trim() -replace '^package:', '' } | Where-Object { $_ })
    $disabledBefore = @((Invoke-TvShell 'pm list packages -d') -split "`n" | ForEach-Object { $_.Trim() -replace '^package:', '' } | Where-Object { $_ })
    if ($packages.Count -lt 5) { Stop-Install 'The TV did not list its apps. Nothing was changed.' }
    $isCvte = @($packages | Where-Object { $_ -like 'com.cvte.*' }).Count -gt 0
    $isGoogleTv = $packages -contains $GoogleTvLauncher

    # ---- Backup, before anything changes --------------------------------------------------------------------------
    Step 'Backing up the TV to this laptop'
    $safeModel = ($model -replace '[^A-Za-z0-9._-]', '_')
    $backup = Join-Path $WorkDir ("backups\{0}-{1}" -f $safeModel, (Get-Date -Format 'yyyyMMdd-HHmmss'))
    $script:BackupDir = $backup
    New-Item -ItemType Directory -Force $backup | Out-Null
    $saves = [ordered]@{
        'packages.txt' = 'pm list packages -f'; 'packages-disabled.txt' = 'pm list packages -d'; 'getprop.txt' = 'getprop'
        'settings-global.txt' = 'settings list global'; 'settings-secure.txt' = 'settings list secure'
        'settings-system.txt' = 'settings list system'; 'home.txt' = $HomeQuery; 'home-candidates.txt' = $HomeClaimsQuery
    }
    foreach ($name in $saves.Keys) {
        $text = Invoke-TvShell $saves[$name]
        [IO.File]::WriteAllText((Join-Path $backup $name), "$text`n")
    }
    if ((Get-Item (Join-Path $backup 'packages.txt')).Length -lt 100) { Stop-Install 'The backup came back empty. Nothing was changed.' }
    $script:UndoFile = Join-Path $backup 'undo.txt'
    @("# HomeTV install on $maker $model, Android $android, $(Get-Date -Format 'yyyy-MM-dd HH:mm').",
      '# To undo it: powershell -ExecutionPolicy Bypass -File family-install.ps1 -Restore <this folder>',
      '# runs the undo lines from the bottom up. Each was written before its change was made.',
      "serial $($script:Serial)") | Set-Content -LiteralPath $script:UndoFile -Encoding ascii
    if ($PSCommandPath -and (Split-Path -Parent $PSCommandPath) -ne $WorkDir) {
        Copy-Item -Force -LiteralPath $PSCommandPath -Destination (Join-Path $WorkDir 'family-install.ps1')
    }
    Write-Report $backup $packages $disabledBefore
    if ($script:ListNames.Count -eq 0) { Select-DebloatList $model }
    Say "Saved in $backup"

    # ---- Which launcher hands over Home (launcher/build-and-install.sh's rules) ------------------------------------
    $homeNow = Get-HomeNow
    $homePkg = Get-PackageOf $homeNow
    $stock = $null
    $alsoDisable = @()
    $toDisable = @()
    $mode = 'replace'
    if ($homePkg -eq $Launcher) {
        $mode = 'reinstall'
        Say 'Home already belongs to the HomeTV Launcher: this run updates it and leaves Home as it is.'
    } elseif ($isCvte -and $homePkg -like 'com.cvte.tv.launcher*') {
        # CVTE: the stock launcher stays enabled; Home is set, the TV restarts once, and Home and ADB must come back (#741).
        $mode = 'cvte'
        $stock = $homePkg
    } elseif ($KnownStockLaunchers -contains $homePkg) {
        $stock = $homePkg
    } elseif ($homePkg -eq 'android') {
        $claims = @((Invoke-TvShell $HomeClaimsQuery) -split "`n" | ForEach-Object { Get-PackageOf $_.Trim() } | Where-Object { $_ } | Sort-Object -Unique)
        $found = @($KnownStockLaunchers | Where-Object { $claims -contains $_ })
        if ($found.Count -ne 1) {
            Stop-Install "The TV has no default home screen yet and $($found.Count) known home apps claim it. Nothing was changed: on the TV, press Home and pick one as the default, then run this again."
        }
        $stock = $found[0]
    } else {
        Stop-Install "Home belongs to '$homeNow', which this installer does not know. Nothing was changed. Send that line to the person helping you."
    }
    if ($stock) { Say "Stock home screen: $stock" }
    $stockHome = $null
    if ($stock) { $stockHome = ((Invoke-TvShell "$HomeQuery $stock") -split "`n" | Select-Object -Last 1).Trim() }

    # Google TV: the setup app takes Home back unless it is off too, and it owns the Cast credentials and maybe Wi-Fi.
    if ($mode -eq 'replace' -and $isGoogleTv -and ($packages -contains $SetupWraith) -and ($disabledBefore -notcontains $SetupWraith)) {
        $wifi = Invoke-TvShell 'dumpsys wifi'
        $owners = @([regex]::Matches($wifi, 'cname=[^ ]*') | ForEach-Object { $_.Value })
        if ($owners.Count -eq 0) {
            Stop-Install 'This is a Google TV, and the installer could not check which app owns its saved Wi-Fi network. Nothing was changed; tell the person helping you.'
        }
        if (@($owners | Where-Object { $_ -match 'setupwraith' }).Count -gt 0) {
            Stop-Install ('This Google TV''s Wi-Fi network was saved during its first setup, and switching the setup app off would ' +
                'delete it and take the TV off Wi-Fi. Nothing was changed. On the TV: Settings > Network & Internet > your network > ' +
                'Forget network, join it again with its password, then run this again.')
        }
        Say 'On Google TV, the HomeTV Launcher can only be Home once the Google TV home and its setup app are switched off.'
        Say 'On most Google TVs that also stops casting from a phone (the Cast button in YouTube or Netflix).'
        if (Ask-YesNo 'Do you cast to this TV from a phone?' $false) {
            if (-not (Ask-YesNo 'Casting will probably stop working. Go ahead anyway?' $false)) {
                Stop-Install 'Stopped, so casting keeps working. Nothing was changed.' 0
            }
        }
        $alsoDisable = @($SetupWraith)
    }

    if (-not (Ask-YesNo 'Ready to install the HomeTV Launcher and make it the home screen?' $true)) {
        Stop-Install 'Stopped. Nothing was changed.' 0
    }

    # ---- Install, on its own: a failed install must not reach the steps that switch Home ---------------------------
    Step 'Installing'
    $pushed = Invoke-Adb @('-s', $script:Serial, 'push', $apk, '/data/local/tmp/hometv.apk')
    if ($script:AdbExit -ne 0) { Stop-Install "Could not copy the launcher to the TV: $pushed. Nothing was changed." }
    $installed = Invoke-TvShell 'pm install -r /data/local/tmp/hometv.apk'
    [void](Invoke-TvShell 'rm -f /data/local/tmp/hometv.apk')
    Say $installed
    if ($installed -notmatch 'Success') {
        if ($installed -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
            Stop-Install 'A HomeTV Launcher signed with another key is already on this TV, so this one cannot replace it. Nothing else was changed; tell the person helping you (never uninstall it yourself: its lists would go).'
        }
        if ($installed -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
            Stop-Install 'This TV already has a newer HomeTV Launcher. Nothing was changed.' 0
        }
        if ($installed -match 'INSTALL_FAILED_VERIFICATION_FAILURE') {
            Stop-Install 'This TV refuses apps from outside its store, so the launcher cannot go on it. Nothing else was changed.'
        }
        Stop-Install 'The TV did not install the launcher (see the line above). The home screen was left as it was.'
    }

    # Replacing a running Home lets Android restart it half-updated (#1101): stop it, and put back a key helper the
    # force stop switched off (cleared first, so Android binds it again). Not on a CVTE board: there no helper goes on
    # in the pass that moves Home, before its one reboot (#741).
    if ($mode -eq 'cvte') {
        [void](Invoke-TvShell 'am force-stop com.hometv.launcher')
    } else {
        [void](Invoke-TvShell ('s=$(settings get secure enabled_accessibility_services); a=$(settings get secure accessibility_enabled); ' +
            'am force-stop com.hometv.launcher; case $s in *com.hometv.launcher*) settings put secure enabled_accessibility_services ' +
            '''''; settings put secure enabled_accessibility_services $s; settings put secure accessibility_enabled $a ;; esac'))
    }
    # Started on its own before Home is touched: "Status: ok" proves the activity came up (#239).
    $started = Invoke-TvShell "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $LauncherHome"
    if ($started -notmatch 'Status: ok' -or $started -match '(?m)^Error') {
        Stop-Install "The launcher is installed but did not start, so the home screen was left as it was: $started"
    }
    [void](Invoke-TvShell "pm grant $Launcher android.permission.ACCESS_FINE_LOCATION")
    [void](Invoke-TvShell "cmd package compile -m speed -f $Launcher")

    if ($mode -eq 'reinstall') {
        Say 'Updated.'
    } else {
        Step 'Making it the home screen'
        if ($mode -eq 'replace')  { $toDisable = @($stock) + $alsoDisable }
        # Puts the stock home screen back (everything this run switched off, and Home), checks it, and stops.
        $rollback = {
            param([string]$Why)
            Say "$Why Putting the old home screen back..."
            foreach ($pkg in $toDisable) { [void](Invoke-TvShell "pm enable --user 0 $pkg") }
            if ($stockHome) { [void](Invoke-TvShell "cmd package set-home-activity --user 0 $stockHome") }
            $back = Get-HomeNow
            if ($stock -and $back.StartsWith("$stock/")) {
                Stop-Install "$Why The TV's own home screen is back. The launcher stays installed but is not Home. Send this to the person helping you ($maker $model)."
            }
            Stop-Install "$Why The old home screen did not come back (Home is '$back'). Keep debugging on and run: powershell -ExecutionPolicy Bypass -File family-install.ps1 -Restore `"$backup`""
        }
        if ($stockHome) { Add-Undo "cmd package set-home-activity --user 0 $stockHome" }
        foreach ($pkg in $toDisable) {
            Add-Undo "pm enable --user 0 $pkg"
            $out = Invoke-TvShell "pm disable-user --user 0 $pkg"
            Say $out
            # Judged by the package's state too: some firmware prints nothing, or an error, and changes nothing.
            $off = @((Invoke-TvShell 'pm list packages -d') -split "`n" | ForEach-Object { $_.Trim() -replace '^package:', '' })
            if ($out -notmatch 'new state: disabled' -or $off -notcontains $pkg) {
                & $rollback "The TV refused to switch off $pkg, which has to be off for the HomeTV Launcher to be Home."
            }
        }
        [void](Invoke-TvShell "cmd package set-home-activity --user 0 $LauncherHome")

        $ok = (Get-HomeNow).StartsWith("$Launcher/")
        if ($ok -and $mode -eq 'cvte') {
            Say 'The TV restarts once now, to check that it comes back to the new home screen. This takes a minute or two.'
            [void](Invoke-Adb @('-s', $script:Serial, 'reboot'))
            if (-not (Wait-TvBack $tvHostName $wireless)) {
                Stop-Install "The TV did not come back after the restart. If it shows a home screen, all is well; if it stays on its start-up animation, tell the person helping you. The undo file is in $backup."
            }
            $ok = (Get-HomeNow).StartsWith("$Launcher/")
        }
        if ($ok) { $ok = Test-HomeShowsOurs }
        if (-not $ok) { & $rollback 'Home did not switch to the HomeTV Launcher.' }
        Say 'The HomeTV Launcher is the home screen.'
    }

    # In-app updates (Family install step 5): Android asks before any update installs, but only an app allowed to "install
    # unknown apps" may ask. Granted here while ADB is on, and read back; without it, Launcher settings > About opens
    # Android's screen to allow it. Undone by -Restore, to the mode it had before (nothing to undo when it was allowed).
    # A mode that can't be read is left alone, since its undo could not put it back.
    $wasMode = Get-AppOpMode $Launcher 'REQUEST_INSTALL_PACKAGES'
    $updatesGranted = $false
    if ($null -eq $wasMode) {
        Say 'Could not read whether the launcher may install its own updates, so that setting was left as it is. Launcher settings > About will show how to allow it.'
    } else {
        if ($wasMode -ne 'allow') {
            Add-Undo "appops set $Launcher REQUEST_INSTALL_PACKAGES $wasMode"
            [void](Invoke-TvShell "appops set $Launcher REQUEST_INSTALL_PACKAGES allow")
        }
        $updatesGranted = (Get-AppOpMode $Launcher 'REQUEST_INSTALL_PACKAGES') -eq 'allow'
        if (-not $updatesGranted) {
            Say 'This TV did not let the launcher install its own updates. It still works; Launcher settings > About will show how to allow it, or run this installer again for an update.'
        }
    }

    $debloated = @()
    if ($script:ListNames.Count -gt 0) { $debloated = @(Invoke-Debloat $script:ListNames) }

    # Last on the TV, after every Home check: First run shows by itself on the first Home start after an install, and
    # the checks above end it (#98). Only after a fresh install, and nothing presses Home after it.
    if ($packages -notcontains $Launcher) {
        [void](Invoke-TvShell "am start -a com.hometv.launcher.action.FIRST_RUN -n $LauncherHome")
        Step 'First run'
        Say 'First run is open on the TV: it sets up the weather, your streaming services, the apps on Home and your keys.'
        Wait-Done 'Go through it now with the remote, before debugging goes off.'
    }

    # ---- Debugging off ---------------------------------------------------------------------------------------------
    Step 'Switching debugging off'
    Say 'Debugging lets any computer on this network control the TV, so it goes off now. On the TV:'
    Say '  1. Developer options > "Revoke USB debugging authorisations" > OK.'
    Say '  2. Turn off "USB debugging", and "Network debugging" or "Wireless debugging" if they are there.'
    Say '  3. Restart the TV (hold the remote''s power button > Restart, or unplug it for 10 seconds).'
    [void](Invoke-Adb @('disconnect', $script:Serial))
    if ($script:OwnServer) { [void](Invoke-Adb @('kill-server')) }
    Wait-Done 'Do that now, and wait until the TV shows the HomeTV home screen.'
    if ($usbEthernet) { Wait-Done 'Unplug the USB network adapter and plug it back in, then check the TV is on the cable again.' }
    # Only a refused connection proves the port is closed; a TV that is off or out of reach doesn't answer either.
    while ($true) {
        $state = Get-PortState $tvHostName $AdbPort
        if ($state -eq 'closed') { Say "Debugging is off: the TV refuses connections on port $AdbPort."; break }
        if ($state -eq 'open') { Say "The TV still answers on port ${AdbPort}: debugging is still on." }
        else { Say "Could not check port $AdbPort on the TV (no answer): is it on, and on this network?" }
        if (-not (Ask-YesNo 'Check the steps above on the TV, then check again?' $false)) {
            if ($state -eq 'open') {
                Stop-Install 'The launcher is installed, but debugging is still on. Switch it off in Developer options and restart the TV.' 2
            }
            Stop-Install 'The launcher is installed, but this laptop could not confirm debugging is off. On the TV, check Developer options: USB debugging and Network debugging must be off.' 2
        }
    }
    # Wireless debugging gets a new port after a restart, so the laptop cannot check it: the person reads it on the TV.
    if ($wireless -and -not (Ask-YesNo 'On the TV, does Developer options > Wireless debugging say Off?' $false)) {
        Stop-Install 'The launcher is installed, but Wireless debugging may still be on. On the TV: Developer options > Wireless debugging > Off.' 2
    }

    [IO.File]::WriteAllText((Join-Path $backup 'summary.txt'), (@(
        "HomeTV Launcher $ReleaseTag on $maker $model, Android $android (SDK $sdk).",
        "Mode: $mode. Stock home: $stock. Disabled: $($toDisable -join ', '). From the list: $($debloated -join ', ').",
        "USB devices: $usbThings. USB network adapter: $usbEthernet. Updates allowed: $updatesGranted.") -join "`n") + "`n")
    Step 'Done'
    Say 'Press Home on the remote to see the HomeTV Launcher. Everything First run set up is in Launcher settings too.'
    Say "The backup and undo file are in $backup."
}

try {
    # Absolute, since .NET file calls do not follow PowerShell's current folder.
    $resolve = { param($p) if ($p) { $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($p) } else { $p } }
    $WorkDir = & $resolve $WorkDir
    $Restore = & $resolve $Restore
    $DownloadFrom = & $resolve $DownloadFrom
    $ApkPath = & $resolve $ApkPath
    $List = & $resolve $List
    $ListsFrom = & $resolve $ListsFrom
    if ($AdbPath) { $AdbPath = & $resolve $AdbPath }
    New-Item -ItemType Directory -Force $WorkDir | Out-Null
    if ($Restore) {
        $script:Adb = Get-PinnedAdb
        Invoke-Restore
    } else {
        $stamped = -not $ReleaseTag.StartsWith('@')
        if ($stamped -and ($ApkPath -or $CertSha256)) {
            Stop-Install "This copy installs release $ReleaseTag only; -ApkPath and -CertSha256 are for the repository's copy."
        }
        if (-not $stamped -and -not ($ApkPath -and $CertSha256)) {
            Stop-Install 'This copy is not from a release. Download family-install.ps1 from the release page (tools/family-install.md); for a test, pass -ApkPath and -CertSha256.'
        }
        if ($List) { $script:ListNames = @(Read-DebloatList $List) }
        elseif ($ListsFrom) { $script:ListsDir = $ListsFrom }
        Invoke-Install
    }
    exit 0
} catch [InstallStop] {
    $stop = $_.Exception
    $color = if ($stop.Code -eq 0) { 'Yellow' } else { 'Red' }
    Write-Host ''
    Write-Host $stop.Message -ForegroundColor $color
    exit $stop.Code
} finally {
    # Its own adb server ends with it, whatever happened, so the next run (or -Restore) can replace adb.exe.
    if ($script:Adb -and $script:OwnServer) { [void](Invoke-Adb @('kill-server')) }
}
