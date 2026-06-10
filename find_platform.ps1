[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$xml = (New-Object System.Net.WebClient).DownloadString("https://dl.google.com/android/repository/repository2-3.xml")
$matches = [regex]::Matches($xml, 'platform-34[^<"]*\.zip')
$matches | ForEach-Object { $_.Value } | Sort-Object -Unique | ForEach-Object { Write-Host $_ }
Write-Host "----PLATFORMS API34 CONTEXT----"
# find <remotePackage path="platforms;android-34"> ... archive url
$idx = $xml.IndexOf('platforms;android-34"')
if ($idx -gt 0) {
    $seg = $xml.Substring($idx, [Math]::Min(2000, $xml.Length - $idx))
    $u = [regex]::Matches($seg, '<url>([^<]+)</url>')
    $u | ForEach-Object { Write-Host $_.Groups[1].Value }
}
