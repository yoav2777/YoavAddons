# Screenshot the Minecraft dev client window (the java process started by 'gradlew runClient' in Bazaar-Mod).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File shot.ps1 -Out C:\path\shot.png [-Match Bazaar-Mod]
# Uses PrintWindow(PW_RENDERFULLCONTENT) so the window may be covered by other windows; it must not be minimized.
param(
    [Parameter(Mandatory = $true)][string]$Out,
    [string]$Match = 'Bazaar-Mod'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ShotNative {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@
[void][ShotNative]::SetProcessDPIAware()

$procs = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -and $_.CommandLine.Contains($Match) }
$hwnd = [IntPtr]::Zero
foreach ($p in $procs) {
    $gp = Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue
    if ($gp -and $gp.MainWindowHandle -ne [IntPtr]::Zero) { $hwnd = $gp.MainWindowHandle; break }
}
if ($hwnd -eq [IntPtr]::Zero) { Write-Error "No java window whose command line contains '$Match' was found."; exit 1 }

if ([ShotNative]::IsIconic($hwnd)) { [void][ShotNative]::ShowWindow($hwnd, 9); Start-Sleep -Milliseconds 500 }
$r = New-Object ShotNative+RECT
[void][ShotNative]::GetWindowRect($hwnd, [ref]$r)
$w = $r.Right - $r.Left; $h = $r.Bottom - $r.Top
if ($w -le 0 -or $h -le 0) { Write-Error "Window has no size ($w x $h)."; exit 1 }

$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
$ok = [ShotNative]::PrintWindow($hwnd, $hdc, 2)
$g.ReleaseHdc($hdc)
if (-not $ok) { $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size) }
$g.Dispose()
$dir = Split-Path -Parent $Out
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "Saved $Out ($w x $h)"
