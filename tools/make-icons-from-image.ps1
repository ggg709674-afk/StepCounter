Add-Type -AssemblyName System.Drawing

$root   = Resolve-Path "$PSScriptRoot\..\app\src\main\res"
$source = Resolve-Path "$PSScriptRoot\..\555.png"

$fgSizes = @{
    'mipmap-mdpi'    = 108
    'mipmap-hdpi'    = 162
    'mipmap-xhdpi'   = 216
    'mipmap-xxhdpi'  = 324
    'mipmap-xxxhdpi' = 432
}
$legacySizes = @{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}

$white = [System.Drawing.Color]::White
$orig  = [System.Drawing.Image]::FromFile($source.Path)
Write-Output ("Original: " + $orig.Width + "x" + $orig.Height)

function Draw-FromImage {
    param([int]$size, [bool]$round, [double]$fillRatio)

    $bmp = New-Object System.Drawing.Bitmap -ArgumentList $size, $size
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $bgBrush = New-Object System.Drawing.SolidBrush $white
    if ($round) {
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $path.AddEllipse(0, 0, $size, $size)
        $g.FillPath($bgBrush, $path)
        $path.Dispose()
    } else {
        $bgRect = New-Object System.Drawing.Rectangle -ArgumentList 0, 0, $size, $size
        $g.FillRectangle($bgBrush, $bgRect)
    }
    $bgBrush.Dispose()

    $target = [double]$size * $fillRatio
    $sw = [double]$orig.Width
    $sh = [double]$orig.Height
    $scale = [math]::Min($target / $sw, $target / $sh)
    $dw = [int]([math]::Round($sw * $scale))
    $dh = [int]([math]::Round($sh * $scale))
    $dx = [int](($size - $dw) / 2)
    $dy = [int](($size - $dh) / 2)

    $g.DrawImage($orig, $dx, $dy, $dw, $dh)
    $g.Dispose()
    return $bmp
}

foreach ($entry in $fgSizes.GetEnumerator()) {
    $dir = Join-Path $root $entry.Key
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

    $bmp = Draw-FromImage -size $entry.Value -round $false -fillRatio 0.60
    $bmp.Save((Join-Path $dir 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    $legacy = $legacySizes[$entry.Key]
    $bmp = Draw-FromImage -size $legacy -round $false -fillRatio 0.78
    $bmp.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    $bmp = Draw-FromImage -size $legacy -round $true -fillRatio 0.70
    $bmp.Save((Join-Path $dir 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    Write-Output ("Generated: " + $entry.Key)
}

$orig.Dispose()
Write-Output "Done."
