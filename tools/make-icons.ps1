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
$white       = [System.Drawing.Color]::White

function Draw-Icon {
    param([int]$size, [bool]$withBg, [bool]$round)

    $bmp = [System.Drawing.Bitmap]::new($size, $size)
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.TextRenderingHint  = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    if ($withBg) {
        $bgBrush = [System.Drawing.SolidBrush]::new($white)
        if ($round) {
            $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
            $path.AddEllipse(0, 0, $size, $size)
            $g.FillPath($bgBrush, $path)
            $path.Dispose()
        } else {
            $g.FillRectangle($bgBrush, [System.Drawing.Rectangle]::new(0, 0, $size, $size))
        }
        $bgBrush.Dispose()
    }

    # WOOZOO 한 줄, 안전영역(가운데 ~62%) 안에 들어가게 폰트 크기 결정
    $fontSize = [single]([math]::Round($size * 0.135))
    $font = $null
    foreach ($name in @('Arial Black','Segoe UI Black','Impact','Arial')) {
        try {
            $font = [System.Drawing.Font]::new($name, $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
            break
        } catch {}
    }

    $textBrush = [System.Drawing.SolidBrush]::new($primary)
    $sf = [System.Drawing.StringFormat]::new()
    $sf.Alignment     = [System.Drawing.StringAlignment]::Center
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Center

    $rect = [System.Drawing.RectangleF]::new(0.0, 0.0, [single]$size, [single]$size)
    $g.DrawString("WOOZOO", $font, $textBrush, $rect, $sf)

    $textBrush.Dispose()
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
