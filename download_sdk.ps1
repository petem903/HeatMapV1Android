# Download Android SDK platform-34 and build-tools 34.0.0 via .NET (firewall allows
# .NET HTTPS but resets the JVM's, so sdkmanager cannot fetch these itself).
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$tmp = "$env:TEMP\sdkdl"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

function Get-Zip($url, $out) {
    Write-Host "Downloading $url"
    (New-Object System.Net.WebClient).DownloadFile($url, $out)
    Write-Host ("  -> {0:N1} MB" -f ((Get-Item $out).Length / 1MB))
}

function Install-Pkg($url, $destDir) {
    $zip = "$tmp\pkg.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Get-Zip $url $zip
    $ex = "$tmp\ex"
    if (Test-Path $ex) { Remove-Item $ex -Recurse -Force }
    Expand-Archive $zip $ex -Force
    $root = (Get-ChildItem $ex -Directory | Select-Object -First 1).FullName
    if (Test-Path $destDir) { Remove-Item $destDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path (Split-Path $destDir) | Out-Null
    Move-Item $root $destDir
    Write-Host "Installed -> $destDir"
}

# Build-tools 34.0.0
Install-Pkg "https://dl.google.com/android/repository/build-tools_r34-windows.zip" "$sdk\build-tools\34.0.0"

# Platform android-34 (rev 3)
Install-Pkg "https://dl.google.com/android/repository/platform-34_r03.zip" "$sdk\platforms\android-34"

Write-Host "DONE"
Get-ChildItem "$sdk\build-tools" | Select-Object Name
Get-ChildItem "$sdk\platforms" | Select-Object Name
