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
    # Take every question's default answer and skip the pauses (tests, or the project lead on a call).
    [switch]$NoPrompt
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
    # Unpacked from the checked zip each run, so a changed adb.exe on disk is never used. A server left by an earlier
    # run that stopped half-way still holds adb.exe open: stop it first (it is this installer's own, on its own port).
    if (Test-Path -LiteralPath $dir) {
        $old = Join-Path $dir 'platform-tools\adb.exe'
        if ($script:OwnServer -and (Test-Path -LiteralPath $old)) {
            $saved = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
            try { & $old kill-server 2>&1 | Out-Null } finally { $ErrorActionPreference = $saved }
            Start-Sleep -Seconds 1
        }
        try { Remove-Item -LiteralPath $dir -Recurse -Force }
        catch { Stop-Install "Could not replace the old copy of adb in $dir. Close any other window running this installer, or restart the laptop, then run it again. Nothing was changed on the TV." }
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($zip, $dir)
    if (-not (Test-Path -LiteralPath $adb)) { Stop-Install 'The platform-tools zip has no adb.exe.' }
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

function Test-PortOpen([string]$HostName, [int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        if (-not $task.Wait(3000)) { return $false }
        return $client.Connected
    } catch {
        return $false
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
    [array]::Reverse($undo)
    Step "Undoing the install on $($script:Serial)"
    [void](Invoke-Adb @('start-server'))
    [void](Invoke-Adb @('connect', $script:Serial))
    if (-not (Wait-Authorized $script:Serial)) {
        Stop-Install "The TV does not answer at $($script:Serial). Switch debugging on again (tools/family-install.md), or pass -TvHost and -AdbPort."
    }
    foreach ($command in $undo) {
        Say "TV: $command"
        Say (Invoke-TvShell $command)
    }
    Say "Home is now: $(Get-HomeNow)"
    Say 'Done. Switch debugging off on the TV again (Developer options).'
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
    Say 'The launcher and adb are the files this installer expects.'

    Step 'Getting the TV ready'
    Say 'On the TV, with the remote:'
    Say '  1. Settings > Device Preferences (or System) > About: press OK on "Build" 7 times, until it says you are a developer.'
    Say '  2. Go back to Device Preferences (or System) > Developer options.'
    Say '  3. Turn on "USB debugging". If you also see "Network debugging" or "ADB debugging", turn that on too.'
    Wait-Done 'Do that now.'
    Say 'Some TVs cut power to their USB ports while debugging is on, so a USB stick, speaker or adapter there stops working until the end.'
    $usbThings = Ask-Text 'What is plugged into the TV''s USB ports? (Enter for nothing)'
    $usbEthernet = Ask-YesNo 'Does the TV''s network cable go into a USB adapter, rather than straight into the TV?' $false
    if ($usbEthernet) {
        Say 'Then do the whole install in one go: the adapter may only come back once debugging is off again at the end.'
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
        Say 'Home already belongs to the HomeTV Launcher: this run updates it and changes nothing else.'
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
    # force stop switched off (cleared first, so Android binds it again).
    [void](Invoke-TvShell ('s=$(settings get secure enabled_accessibility_services); a=$(settings get secure accessibility_enabled); ' +
        'am force-stop com.hometv.launcher; case $s in *com.hometv.launcher*) settings put secure enabled_accessibility_services ' +
        '''''; settings put secure enabled_accessibility_services $s; settings put secure accessibility_enabled $a ;; esac'))
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
        if ($stockHome) { Add-Undo "cmd package set-home-activity --user 0 $stockHome" }
        foreach ($pkg in $toDisable) {
            Add-Undo "pm enable --user 0 $pkg"
            $out = Invoke-TvShell "pm disable-user --user 0 $pkg"
            Say $out
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
        if (-not $ok) {
            Say 'Home did not switch to the HomeTV Launcher. Putting the old home screen back...'
            foreach ($pkg in $toDisable) { [void](Invoke-TvShell "pm enable --user 0 $pkg") }
            if ($stockHome) { [void](Invoke-TvShell "cmd package set-home-activity --user 0 $stockHome") }
            $back = Get-HomeNow
            if ($stock -and $back.StartsWith("$stock/")) {
                Stop-Install "The TV's own home screen is back. The launcher stays installed but is not Home. Send this to the person helping you: Home did not move to $Launcher on $maker $model."
            }
            Stop-Install "The old home screen did not come back (Home is '$back'). Keep debugging on and run: powershell -ExecutionPolicy Bypass -File family-install.ps1 -Restore `"$backup`""
        }
        Say 'The HomeTV Launcher is the home screen.'
    }

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
    [void](Invoke-Adb @('kill-server'))
    Wait-Done 'Do that now, and wait until the TV shows the HomeTV home screen.'
    if ($usbEthernet) { Wait-Done 'Unplug the USB network adapter and plug it back in, then check the TV is on the cable again.' }
    $ports = @($AdbPort)
    if ($wireless) { $ports += [int](($script:Serial -split ':')[-1]) }
    while ($true) {
        $open = @($ports | Where-Object { Test-PortOpen $tvHostName $_ })
        if ($open.Count -eq 0) { Say 'Debugging is off: the TV no longer answers on its debugging port.'; break }
        Say "The TV still answers on port $($open -join ', '): debugging is still on."
        if (-not (Ask-YesNo 'Check the steps above on the TV, then check again?' $false)) {
            Stop-Install 'The launcher is installed, but debugging is still on. Switch it off in Developer options and restart the TV.' 2
        }
    }

    [IO.File]::WriteAllText((Join-Path $backup 'summary.txt'), (@(
        "HomeTV Launcher $ReleaseTag on $maker $model, Android $android (SDK $sdk).",
        "Mode: $mode. Stock home: $stock. Disabled: $($toDisable -join ', ').",
        "USB devices: $usbThings. USB network adapter: $usbEthernet.") -join "`n") + "`n")
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
