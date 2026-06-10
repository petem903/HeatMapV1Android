$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$tmp = "$env:TEMP\sdkdl"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

$url = "https://dl.google.com/android/repository/platform-34-ext7_r03.zip"
$zip = "$tmp\plat34.zip"
Write-Host "Downloading $url"
(New-Object System.Net.WebClient).DownloadFile($url, $zip)
Write-Host ("  -> {0:N1} MB" -f ((Get-Item $zip).Length / 1MB))

$ex = "$tmp\pfx"
if (Test-Path $ex) { Remove-Item $ex -Recurse -Force }
Expand-Archive $zip $ex -Force
$root = (Get-ChildItem $ex -Directory | Select-Object -First 1).FullName
$dest = "$sdk\platforms\android-34"
if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
Move-Item $root $dest
Write-Host "Installed -> $dest"
Get-ChildItem "$sdk\platforms" | Select-Object Name | ForEach-Object { Write-Host $_.Name }
Write-Host "DONE"
