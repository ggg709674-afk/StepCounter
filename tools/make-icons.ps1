Add-Type -AssemblyName System.Drawing

$root = Resolve-Path "$PSScriptRoot\..\app\src\main\res"

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

$primary     = [System.Drawing.Color]::FromArgb(99,102,241)
$primaryDark = [System.Drawing.Color]::FromArgb(79,70,229)

function Draw-Icon {
    param([int]$size, [bool]$withBg, [bool]$round)

    $bmp = [System.Drawing.Bitmap]::new($size, $size)
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.TextRenderingHint  = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    if ($withBg) {
        $rect  = [System.Drawing.Rectangle]::new(0, 0, $size, $size)
        $brush = [System.Drawing.Drawing2D.LinearGradientBrush]::new($rect, $primary, $primaryDark, 45.0)
        if ($round) {
            $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
            $path.AddEllipse(0, 0, $size, $size)
            $g.FillPath($brush, $path)
            $path.Dispose()
        } else {
            $g.FillRectangle($brush, $rect)
        }
        $brush.Dispose()
    }

    $fontSize = [single]([math]::Round($size * 0.21))
    $font = $null
    foreach ($name in @('Arial Black','Segoe UI Black','Impact','Arial')) {
        try {
            $font = [System.Drawing.Font]::new($name, $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
            break
        } catch {}
    }

    $brushW = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
    $sf = [System.Drawing.StringFormat]::new()
    $sf.Alignment     = [System.Drawing.StringAlignment]::Center
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Center

    $lineH = $fontSize * 1.0
    $cy = $size / 2.0

    $top = [System.Drawing.RectangleF]::new(0.0, [single]($cy - $lineH), [single]$size, [single]$lineH)
    $bot = [System.Drawing.RectangleF]::new(0.0, [single]$cy,            [single]$size, [single]$lineH)

    $g.DrawString("WOO", $font, $brushW, $top, $sf)
    $g.DrawString("ZOO", $font, $brushW, $bot, $sf)

    $brushW.Dispose()
    $font.Dispose()
    $g.Dispose()
    return $bmp
}

foreach ($entry in $fgSizes.GetEnumerator()) {
    $dir = Join-Path $root $entry.Key
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

    $bmp = Draw-Icon -size $entry.Value -withBg $false -round $false
    $bmp.Save((Join-Path $dir 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    $legacy = $legacySizes[$entry.Key]
    $bmp = Draw-Icon -size $legacy -withBg $true -round $false
    $bmp.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    $bmp = Draw-Icon -size $legacy -withBg $true -round $true
    $bmp.Save((Join-Path $dir 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    Write-Output ("Generated: " + $entry.Key)
}

Write-Output "Done."
