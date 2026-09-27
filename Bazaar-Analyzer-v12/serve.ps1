# Serves the built Bazaar Analyzer site from the "app" folder on this PC only (127.0.0.1) and opens it.
# Uses only what Windows ships with, so nothing needs to be installed.
param(
  [int]$Port = 47831,
  [switch]$NoBrowser
)
$ErrorActionPreference = 'Stop'

$root = Join-Path $PSScriptRoot 'app'
if (-not (Test-Path -LiteralPath (Join-Path $root 'index.html'))) {
  Write-Host 'Cannot find the "app" folder next to this script. Extract the whole zip first, then run again.' -ForegroundColor Red
  exit 1
}
$rootFull = [System.IO.Path]::GetFullPath($root).TrimEnd('\') + '\'

$mime = @{
  '.html' = 'text/html; charset=utf-8'
  '.js'   = 'text/javascript; charset=utf-8'
  '.mjs'  = 'text/javascript; charset=utf-8'
  '.css'  = 'text/css; charset=utf-8'
  '.json' = 'application/json; charset=utf-8'
  '.png'  = 'image/png'
  '.svg'  = 'image/svg+xml'
  '.ico'  = 'image/x-icon'
  '.txt'  = 'text/plain; charset=utf-8'
  '.map'  = 'application/json'
  '.woff2' = 'font/woff2'
}

# The port is fixed on purpose: your watchlist, alerts and drawings are saved per address, so a changing
# port would look like lost data. Only if it is busy do we try the next ones.
$listener = $null
foreach ($p in $Port..($Port + 19)) {
  $l = New-Object System.Net.HttpListener
  $l.Prefixes.Add("http://127.0.0.1:$p/")
  try {
    $l.Start()
    $listener = $l
    $Port = $p
    break
  } catch {
    $l.Close()
  }
}
if (-not $listener) {
  Write-Host "Could not open a local port ($Port-$($Port + 19)). Close other programs using them and try again." -ForegroundColor Red
  exit 1
}

# Lets the mod start this site by itself next time (its trade button reads this file when the site is not running).
try {
  $saveDir = Join-Path $env:LOCALAPPDATA 'BazaarAnalyzer'
  New-Item -ItemType Directory -Force -Path $saveDir | Out-Null
  [System.IO.File]::WriteAllText((Join-Path $saveDir 'site-folder.txt'), $PSScriptRoot)
} catch { }

$url = "http://127.0.0.1:$Port/"
Write-Host ''
Write-Host '  Bazaar Analyzer is running.' -ForegroundColor Green
Write-Host "  Address:  $url"
Write-Host '  Keep this window open while you use it (price alerts only work while it is open).'
Write-Host '  Close this window to stop.'
Write-Host ''
if ($Port -ne 47831) { Write-Host "  Note: the usual port was busy, so this run uses $Port (saved data is per address)." -ForegroundColor Yellow }
if (-not $NoBrowser) { Start-Process $url }

try {
  while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    try {
      $rel = [Uri]::UnescapeDataString($ctx.Request.Url.AbsolutePath).TrimStart('/')
      if ($rel -eq '') { $rel = 'index.html' }
      $full = $null
      try { $full = [System.IO.Path]::GetFullPath((Join-Path $root $rel)) } catch { $full = $null }
      # only ever serve files inside the app folder (blocks ../ tricks)
      if ($full -and $full.StartsWith($rootFull, [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $full -PathType Leaf)) {
        $ext = [System.IO.Path]::GetExtension($full).ToLowerInvariant()
        $type = $mime[$ext]
        if (-not $type) { $type = 'application/octet-stream' }
        $bytes = [System.IO.File]::ReadAllBytes($full)
        $ctx.Response.StatusCode = 200
        $ctx.Response.ContentType = $type
        $ctx.Response.Headers.Add('Cache-Control', $(if ($ext -eq '.html' -or $ext -eq '.json') { 'no-cache' } else { 'public, max-age=86400' }))
        $ctx.Response.ContentLength64 = $bytes.Length
        if ($ctx.Request.HttpMethod -ne 'HEAD') { $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length) }
      } else {
        $ctx.Response.StatusCode = 404
      }
    } catch {
      # a browser cancelling a request is normal; ignore and keep serving
    } finally {
      try { $ctx.Response.Close() } catch { }
    }
  }
} finally {
  $listener.Stop()
}
