# Generates Zora launcher icons (MIT logo, white bars on black)
# as legacy PNG mipmaps. Geometry matches res/drawable/ic_launcher_foreground.xml
Add-Type -AssemblyName System.Drawing

# MIT logo bars in the 108x108 adaptive-icon coordinate space (scaled x0.18 from the 321x166 original)
$bars = @(
    @(25.1, 39.1, 6.3, 29.9),   # M - left bar
    @(35.4, 39.1, 6.3, 20.3),   # M - middle bar (short)
    @(45.6, 39.1, 6.3, 29.9),   # M - right bar
    @(55.9, 39.1, 6.3, 5.9),    # I - dot
    @(55.9, 48.6, 6.3, 20.4),   # I - stem
    @(66.3, 48.6, 6.3, 20.4),   # T - stem
    @(66.3, 39.1, 16.6, 5.9)    # T - crossbar
)

function New-SquareIcon([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Black)
    $white = [System.Drawing.Brushes]::White
    $scale = $size / 108.0
    foreach ($b in $bars) {
        $g.FillRectangle($white, [float]($b[0] * $scale), [float]($b[1] * $scale), [float]($b[2] * $scale), [float]($b[3] * $scale))
    }
    $g.Dispose()
    return $bmp
}

function New-RoundIcon([int]$size) {
    $src = New-SquareIcon $size
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gp = New-Object System.Drawing.Drawing2D.GraphicsPath
    $gp.AddEllipse([float]0, [float]0, [float]$size, [float]$size)
    $g.SetClip($gp)
    $g.DrawImage($src, [System.Drawing.Rectangle]::new(0, 0, $size, $size))
    $gp.Dispose()
    $g.Dispose()
    $src.Dispose()
    return $bmp
}

$res = 'C:\Users\user\AndroidStudioProjects\Zora\app\src\main\res'
$densities = @{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}
foreach ($d in $densities.GetEnumerator()) {
    $dir = Join-Path $res $d.Key
    $sq = New-SquareIcon $d.Value
    $sq.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $sq.Dispose()
    $rd = New-RoundIcon $d.Value
    $rd.Save((Join-Path $dir 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $rd.Dispose()
    Write-Host ("Generated {0}: ic_launcher.png + ic_launcher_round.png ({1}px)" -f $d.Key, $d.Value)
}
Write-Host 'All icons generated OK'
