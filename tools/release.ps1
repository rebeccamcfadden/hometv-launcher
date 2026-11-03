<#
Builds, signs and uploads a HomeTV Launcher release for family installs (BACKLOG.md, Family install step 3). Read
tools/release.md first: the release key is made once, and every family TV's updates depend on it.

  tools\release.ps1 -NewKey     # once: make the release key outside the repo and write its digest to tools\release-cert.txt
  tools\release.ps1 -DryRun     # build, sign and check a release from this checkout; upload nothing
  tools\release.ps1             # the same from origin/main, then a draft GitHub Release for you to check and publish

The APK is a public build (-PpublicRelease=true: no keys, no household's settings), signed with the release key. The
key's password is asked for and handed to Gradle through this process's environment only. Nothing is committed or
pushed: the release's tag is made by GitHub when you publish the draft.
#>
param(
    [switch]$NewKey,
    [switch]$DryRun,
    # Where the release key lives, outside the repo. Back the whole folder up (tools/release.md).
    [string]$KeyDir = (Join-Path $env:USERPROFILE '.hometv'),
    # The release's notes; a short default points at the checksums and the certificate.
    [string]$Notes = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$repo = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $repo 'launcher'
# Absolute, so Gradle (run from launcher\) reads the same folder a relative -KeyDir named from here.
$KeyDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($KeyDir)
$keystore = Join-Path $KeyDir 'hometv-release.jks'
$signingFile = Join-Path $KeyDir 'release-signing.properties'
$alias = 'hometv'
# The release certificate's SHA-256, committed, so the release (and later the installer) can tell our APK from another.
$certFile = Join-Path $PSScriptRoot 'release-cert.txt'

# Windows PowerShell 5.1 turns a native command's stderr into a terminating error under 'Stop' when it is redirected,
# and keytool, gh, git and Gradle all write progress there. Run them under 'Continue' and judge them by $LASTEXITCODE.
function Invoke-Native([scriptblock]$Block) {
    $saved = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $Block } finally { $ErrorActionPreference = $saved }
}

function Find-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\keytool.exe'))) { return $env:JAVA_HOME }
    $jbr = Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'jbr' } | Where-Object { Test-Path (Join-Path $_ 'bin\keytool.exe') } |
        Select-Object -First 1
    if ($jbr) { return $jbr }
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($java) {
        $home17 = Split-Path -Parent (Split-Path -Parent $java.Source)
        if (Test-Path (Join-Path $home17 'bin\keytool.exe')) { return $home17 }
    }
    throw 'No JDK found: set JAVA_HOME to a JDK 17 (docs/rework.md, Toolchain).'
}

function Find-BuildTools {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $dir = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
        Sort-Object { [version]($_.Name -replace '[^0-9.].*$', '') } -Descending | Select-Object -First 1
    if (-not $dir -or -not (Test-Path (Join-Path $dir.FullName 'apksigner.bat'))) {
        throw "No Android build-tools with apksigner under $sdk (docs/rework.md, Toolchain)."
    }
    return $dir.FullName
}

# A SecureString's text, for the environment variable Gradle and keytool read. It never reaches a file or a command line.
function Read-Secret([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

# "AB:CD:..." from keytool or "abcd..." from apksigner, as 64 lowercase hex digits.
function Normalize-Digest([string]$Text) {
    return ($Text -replace '[^0-9A-Fa-f]', '').ToLowerInvariant()
}

# A properties value's escapes as java.util.Properties reads them: \t \n \r \f, \uXXXX, and \x for any other x.
function ConvertFrom-PropertiesEscape([string]$Text) {
    return [regex]::Replace($Text, '\\(u[0-9A-Fa-f]{4}|.)', {
        param($m)
        $x = $m.Groups[1].Value
        if ($x.Length -eq 5) { return [string][char][Convert]::ToInt32($x.Substring(1), 16) }
        switch -CaseSensitive ($x) { 't' { "`t" } 'n' { "`n" } 'r' { "`r" } 'f' { "`f" } default { $x } }
    })
}

# tv.properties read as Gradle reads it (java.util.Properties.load over UTF-8): blank and #/! comment lines skipped, a
# line ending in an odd number of backslashes continued without the next line's leading blanks, the key ending at the
# first unescaped '=', ':' or blank, one '=' or ':' and the blanks around it skipped, and the escapes decoded.
function Read-JavaProperties([string]$Path) {
    $out = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
    $lines = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) -split "`r`n|`n|`r"
    $blank = " `t`f"
    $odd = { param($s) ([regex]::Match($s, '\\+$').Length % 2) -eq 1 }
    $i = 0
    while ($i -lt $lines.Count) {
        $line = $lines[$i].TrimStart($blank.ToCharArray()); $i++
        if ($line -eq '' -or $line[0] -eq [char]'#' -or $line[0] -eq [char]'!') { continue }
        while ((& $odd $line) -and $i -lt $lines.Count) {
            $line = $line.Substring(0, $line.Length - 1) + $lines[$i].TrimStart($blank.ToCharArray()); $i++
        }
        if (& $odd $line) { $line = $line.Substring(0, $line.Length - 1) }
        $k = 0; $sep = $line.Length
        while ($k -lt $line.Length) {
            $c = $line[$k]
            if ($c -eq [char]'\') { $k += 2; continue }
            if ($c -eq [char]'=' -or $c -eq [char]':' -or $blank.IndexOf($c) -ge 0) { $sep = $k; break }
            $k++
        }
        $sep = [Math]::Min($sep, $line.Length)
        $v = $sep
        while ($v -lt $line.Length -and $blank.IndexOf($line[$v]) -ge 0) { $v++ }
        if ($v -lt $line.Length -and ($line[$v] -eq [char]'=' -or $line[$v] -eq [char]':')) { $v++ }
        while ($v -lt $line.Length -and $blank.IndexOf($line[$v]) -ge 0) { $v++ }
        $out[(ConvertFrom-PropertiesEscape $line.Substring(0, $sep))] = ConvertFrom-PropertiesEscape $line.Substring($v)
    }
    return $out
}

function Get-KeyDigest([string]$Keytool) {
    $out = Invoke-Native { & $Keytool -list -v -keystore $keystore -storetype PKCS12 -alias $alias -storepass:env HOMETV_STORE_PASSWORD 2>&1 }
    if ($LASTEXITCODE -ne 0) { throw 'keytool could not read the release key (wrong password?).' }
    $line = $out | Where-Object { "$_" -match '^\s*SHA256:\s*([0-9A-Fa-f:]+)\s*$' } | Select-Object -First 1
    if (-not $line) { throw 'keytool printed no SHA256 fingerprint for the release key.' }
    $digest = Normalize-Digest (($line -split 'SHA256:')[1])
    if ($digest.Length -ne 64) { throw 'The release key''s SHA256 fingerprint is not 64 hex digits.' }
    return $digest
}

$jdk = Find-Jdk
$env:JAVA_HOME = $jdk
$keytool = Join-Path $jdk 'bin\keytool.exe'

try {
    if ($NewKey) {
        # Never replaced: a new key means every family TV must uninstall the launcher (and lose its lists) to update.
        if ((Test-Path $keystore) -or (Test-Path $signingFile)) {
            throw "A release key is already in $KeyDir. It is never replaced; see tools/release.md before touching it."
        }
        # A committed digest means a key was made before: on a new PC, restore that key from its backup instead.
        if ((Test-Path $certFile) -and (Normalize-Digest (Get-Content $certFile -Raw)).Length -eq 64) {
            throw "tools\release-cert.txt already names a release key. Restore it into $KeyDir from your backup (tools/release.md); a new one is never made."
        }
        if ($env:HOMETV_STORE_PASSWORD) {
            $first = $env:HOMETV_STORE_PASSWORD
        } else {
            $first = Read-Secret 'New release key password (at least 12 characters; keep it in your password manager)'
            # Case-sensitive: the keystore is, and this key can never be replaced.
            if ((Read-Secret 'The same password again') -cne $first) { throw 'The two passwords differ; nothing was made.' }
        }
        if ($first.Length -lt 12) { throw 'The password needs at least 12 characters.' }
        $env:HOMETV_STORE_PASSWORD = $first
        New-Item -ItemType Directory -Force $KeyDir | Out-Null
        # PKCS12 keeps one password for the store and the key.
        Invoke-Native {
            & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 `
                -validity 36500 -dname 'CN=HomeTV Launcher' -storepass:env HOMETV_STORE_PASSWORD `
                -keypass:env HOMETV_STORE_PASSWORD 2>&1 | Out-Null
        }
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $keystore)) {
            # A half-made key must not block the next try.
            Remove-Item $keystore -ErrorAction SilentlyContinue
            throw 'keytool could not make the release key; nothing was kept.'
        }
        # Relative to this file, which Gradle resolves against its folder: no backslashes for Java's properties parser.
        "storeFile=hometv-release.jks`nkeyAlias=$alias`n" | Set-Content -Path $signingFile -NoNewline -Encoding ascii
        $digest = Get-KeyDigest $keytool
        [IO.File]::WriteAllText($certFile, "$digest`n")
        Write-Host "Release key made in $KeyDir."
        Write-Host "Its certificate's SHA-256 is $digest, written to tools\release-cert.txt."
        Write-Host 'Now: back up that folder in two places and keep the password in your password manager'
        Write-Host '(tools/release.md), then commit tools\release-cert.txt through a pull request.'
        return
    }

    # ---- Before building: the checkout, the version and the key --------------------------------------------------
    Push-Location $repo
    try {
        $dirty = Invoke-Native { git status --porcelain }
        if ($LASTEXITCODE -ne 0) { throw 'git status failed.' }
        if ($dirty) { throw 'The checkout has changes; a release is built from committed code only.' }
        $head = (Invoke-Native { git rev-parse HEAD }).Trim()
        if (-not $DryRun) {
            Invoke-Native { git fetch --quiet origin main 2>&1 } | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'git fetch origin main failed.' }
            $main = (Invoke-Native { git rev-parse origin/main }).Trim()
            if ($head -ne $main) { throw "HEAD is not origin/main ($main): check out main and pull, then run this again." }
        }
    } finally { Pop-Location }

    if (-not (Test-Path $certFile)) { throw 'tools\release-cert.txt is missing: make the key first (tools\release.ps1 -NewKey).' }
    $pinned = Normalize-Digest (Get-Content $certFile -Raw)
    if ($pinned.Length -ne 64) { throw 'tools\release-cert.txt does not hold a SHA-256 digest.' }
    if (-not (Test-Path $signingFile)) { throw "No release key in $KeyDir (tools/release.md: restore it from your backup)." }

    $gradleFile = Get-Content (Join-Path $launcher 'app\build.gradle') -Raw
    $codeMatch = [regex]::Match($gradleFile, '(?m)^\s*versionCode\s+(\d+)\s*$')
    $nameMatch = [regex]::Match($gradleFile, "(?m)^\s*versionName\s+'([0-9][0-9A-Za-z.\-]*)'\s*$")
    if (-not $codeMatch.Success -or -not $nameMatch.Success) { throw 'Could not read versionCode and versionName from launcher\app\build.gradle.' }
    $versionCode = [int]$codeMatch.Groups[1].Value
    $versionName = $nameMatch.Groups[1].Value
    $tag = "v$versionName"

    if (-not $DryRun) {
        Push-Location $repo
        try {
            # The repository origin names, as the git checks above use: not gh's default repository or GH_REPO.
            $origin = Invoke-Native { git remote get-url origin }
            if ($LASTEXITCODE -ne 0 -or -not $origin) { throw 'This checkout has no origin remote.' }
            $slug = Invoke-Native { gh repo view "$origin".Trim() --json nameWithOwner -q .nameWithOwner 2>$null }
            if ($LASTEXITCODE -ne 0 -or -not $slug) {
                throw 'gh cannot see this repository: sign gh in with the account that owns it (or set $env:GH_TOKEN for it).'
            }
            $slug = "$slug".Trim()
            # The tag must be new as a release and as a git tag: GitHub would reuse an existing tag and ignore --target.
            Invoke-Native { gh release view $tag --repo $slug 2>&1 } | Out-Null
            if ($LASTEXITCODE -eq 0) { throw "Release $tag exists already: raise versionName and versionCode in a pull request first." }
            Invoke-Native { git ls-remote --exit-code --tags origin "refs/tags/$tag" 2>&1 } | Out-Null
            if ($LASTEXITCODE -eq 0) { throw "The tag $tag exists already: raise versionName and versionCode in a pull request first." }
            if ($LASTEXITCODE -ne 2) { throw 'git ls-remote could not list the tags on origin.' }
            # Updates need a higher versionCode than the last release's, or Android refuses them. No release yet is
            # the only case that skips this; any other failure stops the release.
            $lastTag = Invoke-Native { gh release list --repo $slug --exclude-drafts --limit 1 --json tagName -q '.[0].tagName' 2>$null }
            if ($LASTEXITCODE -ne 0) { throw 'gh could not list the releases.' }
            $lastTag = "$lastTag".Trim()
            if ($lastTag) {
                $tmp = Join-Path ([IO.Path]::GetTempPath()) ("hometv-last-" + [guid]::NewGuid().ToString('N'))
                New-Item -ItemType Directory $tmp | Out-Null
                try {
                    Invoke-Native { gh release download $lastTag --repo $slug --pattern version.json --dir $tmp 2>&1 } | Out-Null
                    $last = Join-Path $tmp 'version.json'
                    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $last)) {
                        throw "Could not read version.json from the last release, $lastTag; the versionCode check cannot be skipped."
                    }
                    $lastCode = [int]((Get-Content $last -Raw | ConvertFrom-Json).versionCode)
                    if ($versionCode -le $lastCode) {
                        throw "versionCode $versionCode is not above $lastTag's ${lastCode}: raise it in a pull request first."
                    }
                } finally { Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue }
            }
        } finally { Pop-Location }
    }

    # ---- Build, test and sign -------------------------------------------------------------------------------------
    if (-not $env:HOMETV_STORE_PASSWORD) { $env:HOMETV_STORE_PASSWORD = Read-Secret 'Release key password' }
    if ((Get-KeyDigest $keytool) -ne $pinned) {
        throw "The release key in $KeyDir is not the one tools\release-cert.txt names. Stop: see tools/release.md."
    }
    Push-Location $launcher
    try {
        Invoke-Native { & .\gradlew.bat --no-daemon clean testReleaseUnitTest lintRelease assembleRelease -PpublicRelease=true "-PreleaseSigning=$signingFile" }
        if ($LASTEXITCODE -ne 0) { throw 'The build, its tests or lint failed; nothing was released.' }
    } finally { Pop-Location }
    $apk = Join-Path $launcher 'app\build\outputs\apk\release\app-release.apk'

    # ---- Check what was built -------------------------------------------------------------------------------------
    $apksigner = Join-Path (Find-BuildTools) 'apksigner.bat'
    $certs = Invoke-Native { & $apksigner verify --print-certs $apk 2>&1 }
    if ($LASTEXITCODE -ne 0) { throw 'apksigner could not verify the APK.' }
    $signers = @($certs | Where-Object { "$_" -match '^Signer #\d+ certificate SHA-256 digest:' })
    if ($signers.Count -ne 1) { throw "The APK has $($signers.Count) signers; a release has exactly one." }
    $signed = Normalize-Digest (($signers[0] -split 'digest:')[1])
    if ($signed -ne $pinned) { throw 'The APK is not signed with the release key tools\release-cert.txt names.' }

    # A key from this machine's tv.properties must not be inside the APK. Names only are printed, never values.
    $localProps = Join-Path $launcher 'tv.properties'
    if (Test-Path $localProps) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead($apk)
        try {
            $blobs = foreach ($entry in $zip.Entries) {
                $stream = $entry.Open()
                try { $ms = New-Object IO.MemoryStream; $stream.CopyTo($ms); [Text.Encoding]::GetEncoding(28591).GetString($ms.ToArray()) }
                finally { $stream.Dispose() }
            }
        } finally { $zip.Dispose() }
        $secretKeys = 'tmdb_api_key', 'watchmode_api_key', 'movie_of_the_night_api_key', 'streaming_availability_api_key', 'note_shortcut_url'
        $props = Read-JavaProperties $localProps
        foreach ($name in $secretKeys) {
            if (-not $props.ContainsKey($name)) { continue }
            # Trimmed, as build.gradle trims what it reads.
            $value = $props[$name].Trim()
            if ($value.Length -lt 8) { continue }
            foreach ($blob in $blobs) {
                if ($blob.Contains($value)) { throw "The APK contains this machine's $name. Stop: nothing was released." }
            }
        }
    }

    # ---- The release's files --------------------------------------------------------------------------------------
    # Already checked above: no release has this tag, so whatever is here is a dry run's or an unpublished try's.
    $out = Join-Path $KeyDir "releases\$tag"
    if (Test-Path $out) { Remove-Item -Recurse -Force $out }
    New-Item -ItemType Directory -Force $out | Out-Null
    $apkName = "hometv-launcher-$tag.apk"
    Copy-Item $apk (Join-Path $out $apkName)
    $apkSha = (Get-FileHash (Join-Path $out $apkName) -Algorithm SHA256).Hash.ToLowerInvariant()
    $version = [ordered]@{ versionCode = $versionCode; versionName = $versionName; apk = $apkName; sha256 = $apkSha;
                           certSha256 = $pinned; minSdk = 26; commit = $head }
    [IO.File]::WriteAllText((Join-Path $out 'version.json'), (($version | ConvertTo-Json) -replace "`r`n", "`n") + "`n")
    # The debloat lists, as this commit has them: the installer suggests the ones whose header names the TV's model, and
    # takes them from here, never from main (tools/family-install.md, step 6).
    $listsName = 'debloat-lists.zip'
    Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
    $zipStream = [IO.File]::Open((Join-Path $out $listsName), [IO.FileMode]::CreateNew)
    try {
        $zip = New-Object IO.Compression.ZipArchive($zipStream, [IO.Compression.ZipArchiveMode]::Create)
        try {
            foreach ($list in Get-ChildItem (Join-Path $repo 'examples') -Directory | Sort-Object Name) {
                $file = Join-Path $list.FullName 'debloat.txt'
                if (-not (Test-Path $file)) { continue }
                [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file, "$($list.Name)/debloat.txt")
                # That TV's config.json too: the installer takes its remote-button keys (step 4b), nothing else of it.
                $config = Join-Path $list.FullName 'config.json'
                if (Test-Path $config) { [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $config, "$($list.Name)/config.json") }
            }
        } finally { $zip.Dispose() }
    } finally { $zipStream.Dispose() }
    $listsSha = (Get-FileHash (Join-Path $out $listsName) -Algorithm SHA256).Hash.ToLowerInvariant()
    # The family installer, stamped with this release, so a copy from it installs exactly this APK (tools/family-install.md).
    $installer = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'family-install.ps1'))
    $stamps = [ordered]@{ "'@RELEASE_TAG@'" = "'$tag'"; "'@RELEASE_APK@'" = "'$apkName'"
                          "'@RELEASE_APK_SHA256@'" = "'$apkSha'"; "'@RELEASE_CERT_SHA256@'" = "'$pinned'"
                          "'@RELEASE_LISTS_SHA256@'" = "'$listsSha'" }
    foreach ($placeholder in $stamps.Keys) {
        if (-not $installer.Contains($placeholder)) { throw "tools\family-install.ps1 has no $placeholder to stamp." }
        $installer = $installer.Replace($placeholder, $stamps[$placeholder])
    }
    if ($installer.Contains('@RELEASE_')) { throw 'tools\family-install.ps1 has a placeholder this script does not stamp.' }
    [IO.File]::WriteAllText((Join-Path $out 'family-install.ps1'), $installer.Replace("`r`n", "`n"))
    $sums = Get-ChildItem $out -File | Where-Object Name -ne 'SHA256SUMS' | Sort-Object Name | ForEach-Object {
        "$((Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($_.Name)"
    }
    [IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS'), (($sums -join "`n") + "`n"))

    Write-Host "Built $tag (versionCode $versionCode), signed with the release key ($pinned)."
    Write-Host "Files in $out :"
    Get-ChildItem $out -File | ForEach-Object { Write-Host ("  {0,-34} {1,10:N0} B" -f $_.Name, $_.Length) }
    if ($DryRun) { Write-Host 'Dry run: nothing was uploaded.'; return }

    if (-not $Notes) {
        $Notes = "HomeTV Launcher $versionName, for family installs. SHA256SUMS lists each file's SHA-256; the APK is " +
                 "signed with the key whose certificate SHA-256 is $pinned (tools/release-cert.txt)."
    }
    Push-Location $repo
    try {
        $files = Get-ChildItem $out -File | ForEach-Object { $_.FullName }
        Invoke-Native { gh release create $tag @files --repo $slug --draft --target $head --title "HomeTV Launcher $versionName" --notes $Notes }
        if ($LASTEXITCODE -ne 0) { throw 'gh release create failed; nothing was published.' }
    } finally { Pop-Location }
    Write-Host "Draft release $tag is on GitHub. Check its files, then publish it there; publishing makes the tag."
} finally {
    Remove-Item Env:\HOMETV_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\HOMETV_KEY_PASSWORD -ErrorAction SilentlyContinue
}
