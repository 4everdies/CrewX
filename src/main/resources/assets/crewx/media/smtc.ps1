# CrewX - reads the Windows "System Media Transport Controls" (the same source as the volume flyout).
# Works for Spotify, SoundCloud / YouTube / any site in Chrome, Edge, Firefox, Opera..., VLC, etc.
# Protocol: prints one JSON object per line on stdout; reads one command per line on stdin
# (toggle | play | pause | next | prev | seek <ms> | noop).
param([string]$ThumbPath = "")

$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

function Emit($obj) {
    [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress -Depth 3))
    [Console]::Out.Flush()
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
} catch {
    Emit @{ ok = $false; error = 'This Windows version has no media session API' }
    exit 1
}

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]
$asStreamMethod = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsStream' -and $_.GetParameters().Count -eq 1
} | Select-Object -First 1

function Await($operation, [Type]$resultType) {
    try {
        $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($operation))
        if (-not $task.Wait(4000)) { return $null }
        return $task.Result
    } catch {
        return $null
    }
}

$mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$mgr = Await ($mgrType::RequestAsync()) $mgrType
if ($mgr -eq $null) {
    Emit @{ ok = $false; error = 'Could not open the media session manager' }
    exit 1
}

$script:active = $null
$script:lastKey = ''
$script:thumbSig = ''
$script:previousThumbSig = ''
$script:lastAlbum = ''
$script:lastArtist = ''
$script:sameAlbumAsPrevious = $false
$script:trackChangedAt = 0
$script:hasThumb = $false
$script:thumbFile = ''
$script:thumbReads = 0
$script:thumbNextAt = 0
$script:artv = 0
$script:artError = ''
# timeline model of the current song (see Poll-And-Emit)
$script:tlKey = ''
$script:tlLive = $false
$script:tlLastRaw = [long]-1
$script:tlLastUpd = [long]0
$script:tlRawSeenAt = [long]0
$thumbClock = [System.Diagnostics.Stopwatch]::StartNew()

# Covers from earlier runs (one file per version: cover.img.<n>) are never reused.
if ($ThumbPath -ne '') {
    $thumbDir = [System.IO.Path]::GetDirectoryName($ThumbPath)
    $thumbName = [System.IO.Path]::GetFileName($ThumbPath)
    try { Get-ChildItem -LiteralPath $thumbDir -Filter "$thumbName*" | Remove-Item -Force -ErrorAction SilentlyContinue } catch { }
}

function Pick-Session {
    $current = $mgr.GetCurrentSession()
    $best = $null
    foreach ($s in $mgr.GetSessions()) {
        $info = $s.GetPlaybackInfo()
        if ($info -ne $null -and $info.PlaybackStatus.ToString() -eq 'Playing') {
            if ($best -eq $null) { $best = $s }
            if ($current -ne $null -and $s.SourceAppUserModelId -eq $current.SourceAppUserModelId) { $best = $s; break }
        }
    }
    if ($best -eq $null) { $best = $current }
    if ($best -eq $null) { foreach ($s in $mgr.GetSessions()) { $best = $s; break } }
    return $best
}

# Reads the whole thumbnail stream. Two ways, because the .NET stream bridge is not available to every
# PowerShell/Windows combination: AsStream first, then the WinRT DataReader.
function Read-ThumbnailBytes($thumbRef) {
    $stream = Await ($thumbRef.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
    if ($stream -eq $null) { throw 'OpenReadAsync gave no stream' }
    $firstError = ''
    try {
        try {
            # PowerShell 5.1 sees the WinRT stream as __ComObject and fails overload
            # selection; reflection invokes the IRandomAccessStream overload directly.
            $net = $asStreamMethod.Invoke($null, @($stream))
            $mem = New-Object System.IO.MemoryStream
            $net.CopyTo($mem)
            $bytes = $mem.ToArray()
            if ($bytes.Length -gt 0) { return ,$bytes }
            $firstError = 'empty stream'
        } catch { $firstError = $_.Exception.Message }
        try {
            $size = [uint32]$stream.Size
            if ($size -eq 0) { throw 'thumbnail size is 0' }
            $reader = [Windows.Storage.Streams.DataReader]::new($stream.GetInputStreamAt(0))
            $loaded = Await ($reader.LoadAsync($size)) ([uint32])
            if ($loaded -eq $null -or [uint32]$loaded -eq 0) { throw 'DataReader loaded 0 bytes' }
            $buffer = New-Object byte[] ([int]$loaded)
            $reader.ReadBytes($buffer)
            return ,$buffer
        } catch {
            throw "AsStream: $firstError; DataReader: $($_.Exception.Message)"
        }
    } finally {
        # cleanup must never discard bytes that were already read
        try { $stream.Dispose() } catch { }
    }
}

function Update-Thumbnail($props, $key) {
    if ($key -ne $script:lastKey) {
        $script:previousThumbSig = $script:thumbSig
        $album = [string]$props.AlbumTitle
        $artist = [string]$props.Artist
        $script:sameAlbumAsPrevious = $album -ne '' -and $album -eq $script:lastAlbum -and $artist -eq $script:lastArtist
        $script:lastAlbum = $album
        $script:lastArtist = $artist
        $script:lastKey = $key
        $script:thumbSig = ''
        $script:trackChangedAt = $thumbClock.ElapsedMilliseconds
        $script:hasThumb = $false
        $script:thumbReads = 0
        $script:thumbNextAt = 0
        $script:artError = ''
    }
    if ($ThumbPath -eq '' -or $thumbClock.ElapsedMilliseconds -lt $script:thumbNextAt) { return }
    # A player can publish the new title before replacing the old thumbnail. Keep checking after
    # the first seconds too, because some players update their art late.
    $script:thumbReads++
    if ($script:thumbReads -gt 12) { $script:thumbNextAt = $thumbClock.ElapsedMilliseconds + 3000 }
    if ($props.Thumbnail -eq $null) { return }
    try {
        $bytes = Read-ThumbnailBytes $props.Thumbnail
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try { $sig = [Convert]::ToBase64String($sha.ComputeHash($bytes)) }
        finally { $sha.Dispose() }
        if (-not $script:hasThumb) {
            # A genuinely new cover is ready immediately. Only hold the old bytes when the
            # title changed but the artwork did not, unless both songs share the same album.
            if ($sig -eq $script:previousThumbSig -and -not $script:sameAlbumAsPrevious -and
                    $thumbClock.ElapsedMilliseconds - $script:trackChangedAt -lt 2500) { return }
        }
        if ($sig -ne $script:thumbSig) {
            # a new file per version: the game never reads a file that is being rewritten
            $file = "$ThumbPath.$($script:artv + 1)"
            [System.IO.File]::WriteAllBytes($file, $bytes)
            $old = $script:thumbFile
            $script:thumbFile = $file
            $script:thumbSig = $sig
            $script:artv++
            if ($old -ne '' -and $old -ne $file) { try { [System.IO.File]::Delete($old) } catch { } }
        }
        $script:hasThumb = $true
        $script:artError = ''
    } catch {
        $script:artError = [string]$_.Exception.Message
    }
}

function Poll-And-Emit {
    $s = Pick-Session
    $script:active = $s
    if ($s -eq $null) { Emit @{ ok = $true; none = $true }; return }
    $info = $s.GetPlaybackInfo()
    $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    if ($info -eq $null -or $props -eq $null -or ([string]$props.Title) -eq '') { Emit @{ ok = $true; none = $true }; return }

    $status = $info.PlaybackStatus.ToString()
    $app = [string]$s.SourceAppUserModelId
    $key = "$app|$($props.Title)|$($props.Artist)|$($props.AlbumTitle)"
    $pos = [long]0
    $dur = [long]0
    $tl = $s.GetTimelineProperties()
    if ($tl -ne $null) {
        $start = [long]$tl.StartTime.TotalMilliseconds
        $dur = [long]$tl.EndTime.TotalMilliseconds - $start
        $raw = [long]$tl.Position.TotalMilliseconds - $start
        $upd = [long]$tl.LastUpdatedTime.UtcTicks
        $now = $thumbClock.ElapsedMilliseconds
        if ($key -ne $script:tlKey) {
            $script:tlKey = $key
            $script:tlLive = $false
            $script:tlLastRaw = $raw
            $script:tlLastUpd = $upd
            $script:tlRawSeenAt = $now
        } elseif ($raw -ne $script:tlLastRaw) {
            # The position moved. If the player did not touch LastUpdatedTime with it, it keeps the position itself
            # up to date ("live"); adding the time since LastUpdatedTime on top of that counted the same seconds
            # twice and ran the lyrics ahead of the song.
            if ($upd -eq $script:tlLastUpd -and $status -eq 'Playing') { $script:tlLive = $true }
            $script:tlLastRaw = $raw
            $script:tlLastUpd = $upd
            $script:tlRawSeenAt = $now
        } else {
            $script:tlLastUpd = $upd
        }
        $pos = $raw
        if ($status -eq 'Playing') {
            if ($script:tlLive) {
                # live players: advance from the moment this position was first seen
                $pos += [long]($now - $script:tlRawSeenAt)
            } else {
                # players that publish a position with its timestamp (Spotify): advance from that timestamp
                $age = ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds
                if ($age -gt 0 -and $age -lt 600000) { $pos += [long]$age }
            }
        }
        if ($dur -lt 0) { $dur = 0 }
        if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
        if ($pos -lt 0) { $pos = 0 }
    }

    Update-Thumbnail $props $key
    $ctl = $info.Controls
    Emit ([ordered]@{
        ok = $true
        app = $app
        status = $status
        title = [string]$props.Title
        artist = [string]$props.Artist
        album = [string]$props.AlbumTitle
        pos = $pos
        dur = $dur
        art = $(if ($script:hasThumb) { $script:artv } else { 0 })
        artFile = $(if ($script:hasThumb) { $script:thumbFile } else { '' })
        artErr = $script:artError
        seek = [bool]($ctl -ne $null -and $ctl.IsPlaybackPositionEnabled -and $dur -gt 0)
        next = [bool]($ctl -ne $null -and $ctl.IsNextEnabled)
        prev = [bool]($ctl -ne $null -and $ctl.IsPreviousEnabled)
    })
}

function Handle-Command([string]$line) {
    $s = $script:active
    if ($s -eq $null -or $line -eq $null) { return }
    $parts = $line.Trim().Split(' ')
    switch ($parts[0]) {
        'toggle' { $null = $s.TryTogglePlayPauseAsync() }
        'play'   { $null = $s.TryPlayAsync() }
        'pause'  { $null = $s.TryPauseAsync() }
        'next'   { $null = $s.TrySkipNextAsync() }
        'prev'   { $null = $s.TrySkipPreviousAsync() }
        'seek'   { if ($parts.Length -gt 1) { $null = $s.TryChangePlaybackPositionAsync([long]$parts[1] * 10000) } }
    }
}

# stdin is read on a background runspace. Do NOT use [Console]::In.ReadLineAsync() here: Console.In is a
# SyncTextReader whose ReadLineAsync() is really a blocking ReadLine(), so the loop below would freeze
# until the game sent a command and the card would never receive the song that is playing.
$commands = New-Object 'System.Collections.Concurrent.BlockingCollection[string]'
$stdinReader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), (New-Object System.Text.UTF8Encoding($false)))
$readerShell = [PowerShell]::Create()
$null = $readerShell.AddScript({
    param($reader, $queue)
    try {
        while ($true) {
            $text = $reader.ReadLine()
            if ($text -eq $null) { break }
            $queue.Add($text)
        }
    } catch { }
    $queue.CompleteAdding()                     # the game closed our stdin (or exited): stop
}).AddArgument($stdinReader).AddArgument($commands)
$null = $readerShell.BeginInvoke()

$clock = [System.Diagnostics.Stopwatch]::StartNew()
$lastPoll = -1000
while ($true) {
    $line = $null
    if ($commands.TryTake([ref]$line, 50)) {    # also acts as the 50 ms idle sleep
        try { Handle-Command $line } catch { }
        while ($commands.TryTake([ref]$line)) { try { Handle-Command $line } catch { } }
        Start-Sleep -Milliseconds 120           # let the player apply the command before reporting
        $lastPoll = -1000
    } elseif ($commands.IsCompleted) {
        break
    }
    if (($clock.ElapsedMilliseconds - $lastPoll) -ge 500) {
        $lastPoll = $clock.ElapsedMilliseconds
        try { Poll-And-Emit } catch { Emit @{ ok = $true; none = $true } }
    }
}
