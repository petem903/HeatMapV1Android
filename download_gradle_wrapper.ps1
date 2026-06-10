$wrapperJarUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
$wrapperDir = "$PSScriptRoot\gradle\wrapper"
$wrapperJar = "$wrapperDir\gradle-wrapper.jar"

if (Test-Path $wrapperJar) {
    Write-Host "Wrapper JAR exists"
    exit 0
}

if (-not (Test-Path $wrapperDir)) {
    New-Item -ItemType Directory -Path $wrapperDir -Force | Out-Null
}

Write-Host "Downloading Gradle wrapper JAR..."
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::DefaultConnectionLimit = 5

$client = New-Object System.Net.WebClient
$client.DownloadFile($wrapperJarUrl, $wrapperJar)

if (-not (Test-Path $wrapperJar)) {
    Write-Host "Download failed - file not found"
    exit 1
}

Write-Host "Setup successful"
Get-Item $wrapperJar | Select-Object FullName, Length
exit 0
