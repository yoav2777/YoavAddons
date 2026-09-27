param([string]$Src, [string]$Out, [string]$Top = 'Bazaar-Analyzer')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$srcFull = [System.IO.Path]::GetFullPath($Src).TrimEnd('\') + '\'
if (Test-Path -LiteralPath $Out) { throw "$Out already exists" }
$tmp = $Out + '.part'
if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Force }
# top-level files first, then app/ (same order as the v11 zip)
$files = @(Get-ChildItem -LiteralPath $srcFull -File | Sort-Object Name) + @(Get-ChildItem -LiteralPath (Join-Path $srcFull 'app') -File -Recurse | Sort-Object FullName)
$zip = [System.IO.Compression.ZipFile]::Open($tmp, 'Create')
try {
  foreach ($f in $files) {
    $rel = $f.FullName.Substring($srcFull.Length).Replace('\', '/')
    $e = $zip.CreateEntry("$Top/$rel", [System.IO.Compression.CompressionLevel]::Optimal)
    $e.LastWriteTime = $f.LastWriteTime
    $s = $e.Open()
    try { $b = [System.IO.File]::ReadAllBytes($f.FullName); $s.Write($b, 0, $b.Length) } finally { $s.Dispose() }
  }
} finally { $zip.Dispose() }
Move-Item -LiteralPath $tmp -Destination $Out
"wrote $Out ($($files.Count) files)"
