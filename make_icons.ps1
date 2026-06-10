# Generates launcher icon mipmaps from the user-supplied logo (Image.jpeg).
# Center-crops to square, resizes to each density, writes PNG over ic_launcher.png.
param(
    [string]$Source = (Join-Path $PSScriptRoot 'Image.jpeg'),
    [string]$ResDir = (Join-Path $PSScriptRoot 'app\src\main\res')
)
Add-Type -AssemblyName System.Drawing

$densities = @{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}

$src = [System.Drawing.Image]::FromFile($Source)
# center-crop to square
$side = [Math]::Min($src.Width, $src.Height)
$cropX = [int](($src.Width  - $side) / 2)
$cropY = [int](($src.Height - $side) / 2)

foreach ($d in $densities.GetEnumerator()) {
    $size = $d.Value
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $destRect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
    $srcRect  = New-Object System.Drawing.Rectangle($cropX, $cropY, $side, $side)
    $g.DrawImage($src, $destRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $out = Join-Path $ResDir "$($d.Key)\ic_launcher.png"
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "wrote $out ($size x $size)"
}
$src.Dispose()
Write-Host "DONE"
