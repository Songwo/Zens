param(
    [string]$OutputDir = "tmp_thesis74\generated_diagrams"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

function Resolve-HexColor {
    param([string]$Hex)
    $clean = $Hex.TrimStart('#')
    if ($clean.Length -eq 6) {
        return [System.Drawing.Color]::FromArgb(
            255,
            [Convert]::ToInt32($clean.Substring(0, 2), 16),
            [Convert]::ToInt32($clean.Substring(2, 2), 16),
            [Convert]::ToInt32($clean.Substring(4, 2), 16)
        )
    }
    throw "不支持的颜色值: $Hex"
}

function New-FontObject {
    param(
        [float]$Size,
        [System.Drawing.FontStyle]$Style = [System.Drawing.FontStyle]::Regular
    )
    return New-Object System.Drawing.Font("Microsoft YaHei UI", $Size, $Style, [System.Drawing.GraphicsUnit]::Pixel)
}

function New-Brush {
    param([string]$Hex)
    return New-Object System.Drawing.SolidBrush (Resolve-HexColor $Hex)
}

function New-PenObject {
    param(
        [string]$Hex,
        [float]$Width = 1.0
    )
    return New-Object System.Drawing.Pen (Resolve-HexColor $Hex), $Width
}

function New-RoundedPath {
    param(
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [float]$Radius
    )

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $Radius * 2
    $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
    $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
    $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function Draw-RoundedBox {
    param(
        [System.Drawing.Graphics]$Graphics,
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [float]$Radius,
        [string]$FillHex,
        [string]$BorderHex,
        [float]$BorderWidth = 2.0
    )

    $path = New-RoundedPath -X $X -Y $Y -Width $Width -Height $Height -Radius $Radius
    $fillBrush = New-Brush $FillHex
    $borderPen = New-PenObject -Hex $BorderHex -Width $BorderWidth
    try {
        $Graphics.FillPath($fillBrush, $path)
        $Graphics.DrawPath($borderPen, $path)
    } finally {
        $path.Dispose()
        $fillBrush.Dispose()
        $borderPen.Dispose()
    }
}

function Draw-TextBlock {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [System.Drawing.Font]$Font,
        [string]$ColorHex,
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [string]$Align = 'Center',
        [string]$LineAlign = 'Center'
    )

    $brush = New-Brush $ColorHex
    $rect = New-Object System.Drawing.RectangleF($X, $Y, $Width, $Height)
    $format = New-Object System.Drawing.StringFormat
    $format.Trimming = [System.Drawing.StringTrimming]::EllipsisWord
    $format.FormatFlags = [System.Drawing.StringFormatFlags]::LineLimit
    switch ($Align) {
        'Near' { $format.Alignment = [System.Drawing.StringAlignment]::Near }
        'Far' { $format.Alignment = [System.Drawing.StringAlignment]::Far }
        default { $format.Alignment = [System.Drawing.StringAlignment]::Center }
    }
    switch ($LineAlign) {
        'Near' { $format.LineAlignment = [System.Drawing.StringAlignment]::Near }
        'Far' { $format.LineAlignment = [System.Drawing.StringAlignment]::Far }
        default { $format.LineAlignment = [System.Drawing.StringAlignment]::Center }
    }
    try {
        $Graphics.DrawString($Text, $Font, $brush, $rect, $format)
    } finally {
        $brush.Dispose()
        $format.Dispose()
    }
}

function Draw-CanvasBackground {
    param(
        [System.Drawing.Graphics]$Graphics,
        [int]$Width,
        [int]$Height
    )

    $Graphics.Clear((Resolve-HexColor '#F7F6F2'))

    $accentBrush = New-Brush '#EDE8DD'
    $smallBrush = New-Brush '#F1ECE2'
    try {
        $Graphics.FillEllipse($accentBrush, -120, -160, 520, 360)
        $Graphics.FillEllipse($accentBrush, $Width - 420, $Height - 260, 520, 320)
        $Graphics.FillEllipse($smallBrush, $Width - 250, 80, 180, 180)
        $Graphics.FillEllipse($smallBrush, 120, $Height - 180, 220, 140)
    } finally {
        $accentBrush.Dispose()
        $smallBrush.Dispose()
    }
}

function Save-EntityDiagram {
    param(
        [string]$Path,
        [string]$Title,
        [string[]]$Attributes,
        [string]$AccentHex
    )

    $width = 1600
    $height = 900
    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $titleFont = New-FontObject -Size 38 -Style Bold
    $subtitleFont = New-FontObject -Size 20
    $entityFont = New-FontObject -Size 34 -Style Bold
    $attributeFont = New-FontObject -Size 20

    try {
        Draw-CanvasBackground -Graphics $graphics -Width $width -Height $height

        Draw-TextBlock -Graphics $graphics -Text $Title -Font $titleFont -ColorHex '#2E2A26' `
            -X 200 -Y 40 -Width 1200 -Height 70
        Draw-TextBlock -Graphics $graphics -Text '核心属性采用中文业务语义表达' -Font $subtitleFont -ColorHex '#74695C' `
            -X 420 -Y 110 -Width 760 -Height 40

        $entityX = 515
        $entityY = 330
        $entityW = 570
        $entityH = 150
        Draw-RoundedBox -Graphics $graphics -X $entityX -Y $entityY -Width $entityW -Height $entityH -Radius 34 `
            -FillHex '#FFFDF8' -BorderHex $AccentHex -BorderWidth 4
        Draw-TextBlock -Graphics $graphics -Text $Title -Font $entityFont -ColorHex '#2E2A26' `
            -X $entityX -Y ($entityY + 28) -Width $entityW -Height 50
        Draw-TextBlock -Graphics $graphics -Text '数据库概念实体' -Font $subtitleFont -ColorHex '#857564' `
            -X $entityX -Y ($entityY + 84) -Width $entityW -Height 28

        $leftItems = @()
        $rightItems = @()
        for ($i = 0; $i -lt $Attributes.Count; $i++) {
            if ($i % 2 -eq 0) {
                $leftItems += $Attributes[$i]
            } else {
                $rightItems += $Attributes[$i]
            }
        }

        $boxW = 300
        $boxH = 56
        $leftX = 90
        $rightX = 1210
        $topY = 160
        $stepY = 88
        $leftAnchorY = $entityY + ($entityH / 2)
        $rightAnchorY = $entityY + ($entityH / 2)

        $linePen = New-PenObject -Hex '#B6AA9B' -Width 2.4
        $nodeBrush = New-Brush $AccentHex
        try {
            for ($i = 0; $i -lt $leftItems.Count; $i++) {
                $boxY = $topY + ($i * $stepY)
                Draw-RoundedBox -Graphics $graphics -X $leftX -Y $boxY -Width $boxW -Height $boxH -Radius 18 `
                    -FillHex '#FFF7EC' -BorderHex '#D8C7B5' -BorderWidth 2
                Draw-TextBlock -Graphics $graphics -Text $leftItems[$i] -Font $attributeFont -ColorHex '#3A342D' `
                    -X $leftX + 16 -Y ($boxY + 8) -Width ($boxW - 32) -Height ($boxH - 16)
                $graphics.DrawLine($linePen, $leftX + $boxW, $boxY + ($boxH / 2), $entityX, $leftAnchorY)
                $graphics.FillEllipse($nodeBrush, $leftX + $boxW - 8, $boxY + ($boxH / 2) - 8, 16, 16)
            }

            for ($i = 0; $i -lt $rightItems.Count; $i++) {
                $boxY = $topY + ($i * $stepY)
                Draw-RoundedBox -Graphics $graphics -X $rightX -Y $boxY -Width $boxW -Height $boxH -Radius 18 `
                    -FillHex '#FFF7EC' -BorderHex '#D8C7B5' -BorderWidth 2
                Draw-TextBlock -Graphics $graphics -Text $rightItems[$i] -Font $attributeFont -ColorHex '#3A342D' `
                    -X $rightX + 16 -Y ($boxY + 8) -Width ($boxW - 32) -Height ($boxH - 16)
                $graphics.DrawLine($linePen, $rightX, $boxY + ($boxH / 2), $entityX + $entityW, $rightAnchorY)
                $graphics.FillEllipse($nodeBrush, $rightX - 8, $boxY + ($boxH / 2) - 8, 16, 16)
            }
        } finally {
            $linePen.Dispose()
            $nodeBrush.Dispose()
        }

        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $titleFont.Dispose()
        $subtitleFont.Dispose()
        $entityFont.Dispose()
        $attributeFont.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function Save-SystemFunctionDiagram {
    param([string]$Path)

    $width = 1600
    $height = 960
    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $titleFont = New-FontObject -Size 38 -Style Bold
    $headFont = New-FontObject -Size 28 -Style Bold
    $itemFont = New-FontObject -Size 21
    $centerFont = New-FontObject -Size 34 -Style Bold
    $subFont = New-FontObject -Size 19

    try {
        Draw-CanvasBackground -Graphics $graphics -Width $width -Height $height

        Draw-TextBlock -Graphics $graphics -Text '系统功能结构图' -Font $titleFont -ColorHex '#2E2A26' `
            -X 520 -Y 36 -Width 560 -Height 70

        Draw-RoundedBox -Graphics $graphics -X 490 -Y 370 -Width 620 -Height 170 -Radius 36 `
            -FillHex '#FFFDF8' -BorderHex '#7A5C46' -BorderWidth 4
        Draw-TextBlock -Graphics $graphics -Text '校园智能内容社区与趋势决策平台' -Font $centerFont -ColorHex '#2E2A26' `
            -X 520 -Y 402 -Width 560 -Height 56
        Draw-TextBlock -Graphics $graphics -Text '围绕用户交流、分区治理、后台运维与趋势分析构建完整业务闭环' -Font $subFont -ColorHex '#7D6C5A' `
            -X 520 -Y 468 -Width 560 -Height 34

        $modules = @(
            @{
                X = 90; Y = 150; W = 420; H = 220; Fill = '#FCEDE6'; Border = '#D07850'; Title = '普通用户功能';
                Items = @('注册登录与会话维护', '发帖、存草稿与匿名发布', '评论、点赞、收藏与关注', '私信通知与个人中心', '搜索浏览与推荐阅读')
            },
            @{
                X = 1090; Y = 150; W = 420; H = 220; Fill = '#EEF6E8'; Border = '#5F8B4D'; Title = '版主功能';
                Items = @('我的板块工作台', '帖子审核与打回修改', '板块置顶与精华设置', '举报处理与状态流转', '分区权限自治管理')
            },
            @{
                X = 90; Y = 580; W = 420; H = 250; Fill = '#EDF3FB'; Border = '#5C7FB2'; Title = '管理员功能';
                Items = @('数据看板', '内容管理', '板块管理', '用户管理', '举报管理', '缓存管理', '发展历程管理', '版主申请管理', '邀请码管理')
            },
            @{
                X = 1090; Y = 580; W = 420; H = 250; Fill = '#FFF3D7'; Border = '#C58A16'; Title = '数据分析与推荐功能';
                Items = @('热度排行', '板块分布统计', '发帖趋势分析', '关键词聚合', '话题预测', '个性化推荐分发')
            }
        )

        $connectorPen = New-PenObject -Hex '#BCA993' -Width 3.0
        try {
            foreach ($module in $modules) {
                Draw-RoundedBox -Graphics $graphics -X $module.X -Y $module.Y -Width $module.W -Height $module.H -Radius 28 `
                    -FillHex $module.Fill -BorderHex $module.Border -BorderWidth 3.5
                Draw-TextBlock -Graphics $graphics -Text $module.Title -Font $headFont -ColorHex '#2E2A26' `
                    -X ($module.X + 20) -Y ($module.Y + 16) -Width ($module.W - 40) -Height 42

                $itemY = $module.Y + 66
                foreach ($item in $module.Items) {
                    Draw-TextBlock -Graphics $graphics -Text "• $item" -Font $itemFont -ColorHex '#413A33' `
                        -X ($module.X + 28) -Y $itemY -Width ($module.W - 56) -Height 28 -Align Near
                    $itemY += 30
                }

                $centerX = if ($module.X -lt 800) { $module.X + $module.W } else { $module.X }
                $centerY = $module.Y + ($module.H / 2)
                $targetX = if ($module.X -lt 800) { 490 } else { 1110 }
                $targetY = 455
                $graphics.DrawLine($connectorPen, $centerX, $centerY, $targetX, $targetY)
            }
        } finally {
            $connectorPen.Dispose()
        }

        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $titleFont.Dispose()
        $headFont.Dispose()
        $itemFont.Dispose()
        $centerFont.Dispose()
        $subFont.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function Save-OverallErDiagram {
    param([string]$Path)

    $width = 1800
    $height = 1100
    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $titleFont = New-FontObject -Size 40 -Style Bold
    $boxFont = New-FontObject -Size 24 -Style Bold
    $lineFont = New-FontObject -Size 18
    $labelFont = New-FontObject -Size 19 -Style Bold

    $boxes = @(
        @{ Key = 'user'; X = 720; Y = 120; W = 360; H = 92; Fill = '#FDEEDF'; Border = '#D07A4D'; Text = '用户' },
        @{ Key = 'section'; X = 1260; Y = 250; W = 320; H = 92; Fill = '#E8F3E2'; Border = '#5D8D4A'; Text = '板块' },
        @{ Key = 'post'; X = 720; Y = 420; W = 360; H = 108; Fill = '#FFFDF8'; Border = '#7A5C46'; Text = '帖子' },
        @{ Key = 'comment'; X = 280; Y = 270; W = 320; H = 92; Fill = '#EDF4FB'; Border = '#5B7DAF'; Text = '评论' },
        @{ Key = 'postLike'; X = 170; Y = 460; W = 320; H = 92; Fill = '#FCE7E6'; Border = '#B85C56'; Text = '帖子点赞' },
        @{ Key = 'postCollect'; X = 170; Y = 620; W = 320; H = 92; Fill = '#FFF0DB'; Border = '#D19827'; Text = '帖子收藏' },
        @{ Key = 'viewLog'; X = 300; Y = 810; W = 320; H = 92; Fill = '#F2ECE4'; Border = '#8A7A68'; Text = '浏览日志' },
        @{ Key = 'report'; X = 1240; Y = 500; W = 340; H = 92; Fill = '#F9E7E7'; Border = '#C15B5B'; Text = '举报' },
        @{ Key = 'notification'; X = 1220; Y = 670; W = 360; H = 92; Fill = '#EAF1FB'; Border = '#577FB5'; Text = '通知' },
        @{ Key = 'message'; X = 1220; Y = 840; W = 360; H = 92; Fill = '#EDE8FA'; Border = '#6C60AD'; Text = '私信' },
        @{ Key = 'modApply'; X = 720; Y = 840; W = 360; H = 92; Fill = '#EEF7E6'; Border = '#6A964C'; Text = '版主申请' },
        @{ Key = 'heat'; X = 720; Y = 610; W = 360; H = 92; Fill = '#FFF2DB'; Border = '#C8901D'; Text = '热度快照' },
        @{ Key = 'trend'; X = 720; Y = 980; W = 360; H = 92; Fill = '#F6EEDF'; Border = '#A77C2A'; Text = '趋势统计' }
    )

    $boxIndex = @{}

    try {
        Draw-CanvasBackground -Graphics $graphics -Width $width -Height $height
        Draw-TextBlock -Graphics $graphics -Text '系统总体实体关系图' -Font $titleFont -ColorHex '#2E2A26' `
            -X 560 -Y 28 -Width 680 -Height 72

        foreach ($box in $boxes) {
            Draw-RoundedBox -Graphics $graphics -X $box.X -Y $box.Y -Width $box.W -Height $box.H -Radius 26 `
                -FillHex $box.Fill -BorderHex $box.Border -BorderWidth 3.2
            Draw-TextBlock -Graphics $graphics -Text $box.Text -Font $boxFont -ColorHex '#2E2A26' `
                -X $box.X -Y ($box.Y + 18) -Width $box.W -Height 42
            $boxIndex[$box.Key] = $box
        }

        $pen = New-PenObject -Hex '#B9A590' -Width 3.0
        try {
            $relations = @(
                @{ From = 'user'; To = 'post'; Label = '发布'; LX = 885; LY = 285 },
                @{ From = 'section'; To = 'post'; Label = '归属'; LX = 1180; LY = 350 },
                @{ From = 'user'; To = 'comment'; Label = '发表评论'; LX = 625; LY = 235 },
                @{ From = 'post'; To = 'comment'; Label = '产生'; LX = 620; LY = 405 },
                @{ From = 'user'; To = 'postLike'; Label = '点赞'; LX = 585; LY = 370 },
                @{ From = 'post'; To = 'postLike'; Label = '被点赞'; LX = 610; LY = 500 },
                @{ From = 'user'; To = 'postCollect'; Label = '收藏'; LX = 590; LY = 525 },
                @{ From = 'post'; To = 'postCollect'; Label = '被收藏'; LX = 610; LY = 620 },
                @{ From = 'user'; To = 'viewLog'; Label = '浏览'; LX = 620; LY = 690 },
                @{ From = 'post'; To = 'viewLog'; Label = '记录'; LX = 650; LY = 760 },
                @{ From = 'user'; To = 'report'; Label = '提交举报'; LX = 1130; LY = 340 },
                @{ From = 'post'; To = 'report'; Label = '被举报'; LX = 1125; LY = 470 },
                @{ From = 'user'; To = 'notification'; Label = '接收'; LX = 1120; LY = 565 },
                @{ From = 'report'; To = 'notification'; Label = '触发通知'; LX = 1420; LY = 630 },
                @{ From = 'user'; To = 'message'; Label = '收发'; LX = 1120; LY = 790 },
                @{ From = 'user'; To = 'modApply'; Label = '申请'; LX = 910; LY = 760 },
                @{ From = 'section'; To = 'modApply'; Label = '申请板块'; LX = 1215; LY = 630 },
                @{ From = 'post'; To = 'heat'; Label = '形成热度'; LX = 900; LY = 575 },
                @{ From = 'post'; To = 'trend'; Label = '汇总统计'; LX = 905; LY = 900 },
                @{ From = 'section'; To = 'trend'; Label = '板块维度'; LX = 1240; LY = 830 }
            )

            foreach ($relation in $relations) {
                $from = $boxIndex[$relation.From]
                $to = $boxIndex[$relation.To]
                $fx = $from.X + ($from.W / 2)
                $fy = $from.Y + ($from.H / 2)
                $tx = $to.X + ($to.W / 2)
                $ty = $to.Y + ($to.H / 2)
                $graphics.DrawLine($pen, $fx, $fy, $tx, $ty)
                Draw-RoundedBox -Graphics $graphics -X ($relation.LX - 42) -Y ($relation.LY - 16) -Width 84 -Height 34 -Radius 12 `
                    -FillHex '#FFFDF8' -BorderHex '#E0D6C7' -BorderWidth 1.4
                Draw-TextBlock -Graphics $graphics -Text $relation.Label -Font $lineFont -ColorHex '#5D5247' `
                    -X ($relation.LX - 40) -Y ($relation.LY - 12) -Width 80 -Height 24
            }
        } finally {
            $pen.Dispose()
        }

        Draw-TextBlock -Graphics $graphics -Text '内容中心' -Font $labelFont -ColorHex '#7A5C46' `
            -X 760 -Y 365 -Width 280 -Height 30
        Draw-TextBlock -Graphics $graphics -Text '互动反馈' -Font $labelFont -ColorHex '#7A5C46' `
            -X 180 -Y 735 -Width 280 -Height 30
        Draw-TextBlock -Graphics $graphics -Text '治理审核' -Font $labelFont -ColorHex '#7A5C46' `
            -X 1270 -Y 445 -Width 280 -Height 30
        Draw-TextBlock -Graphics $graphics -Text '统计分析' -Font $labelFont -ColorHex '#7A5C46' `
            -X 770 -Y 930 -Width 280 -Height 30

        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $titleFont.Dispose()
        $boxFont.Dispose()
        $lineFont.Dispose()
        $labelFont.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$diagramDir = Join-Path $repoRoot $OutputDir
New-Item -ItemType Directory -Force -Path $diagramDir | Out-Null

$entityDefinitions = @(
    @{ File = 'fig3-2-user.png'; Title = '用户实体'; Accent = '#D07A4D'; Attributes = @('用户编号', '学号工号', '邮箱地址', '加密密码', '昵称', '个人头像', '角色类型', '等级信息', '账号状态', '兴趣标签', '贡献值', '最后活跃时间', '二步验证状态', '创建时间') },
    @{ File = 'fig3-3-tag.png'; Title = '标签实体'; Accent = '#C8901D'; Attributes = @('标签编号', '标签名称', '标签类型', '热度值', '创建时间') },
    @{ File = 'fig3-4-section.png'; Title = '板块实体'; Accent = '#5D8D4A'; Attributes = @('板块编号', '板块名称', '板块描述', '图标标识', '排序值', '启用状态', '创建时间') },
    @{ File = 'fig3-5-post.png'; Title = '帖子实体'; Accent = '#7A5C46'; Attributes = @('帖子编号', '作者编号', '所属板块', '帖子标题', '正文内容', '内容摘要', '标签集合', '匿名标记', '位置名称', '审核状态', '浏览数量', '点赞数量', '收藏数量', '评论数量', '热度值', '最后活跃时间', '创建时间') },
    @{ File = 'fig3-6-post-like.png'; Title = '帖子点赞实体'; Accent = '#B85C56'; Attributes = @('点赞记录编号', '帖子编号', '用户编号', '点赞时间') },
    @{ File = 'fig3-7-post-collect.png'; Title = '帖子收藏实体'; Accent = '#D19827'; Attributes = @('收藏记录编号', '帖子编号', '用户编号', '收藏时间') },
    @{ File = 'fig3-8-comment.png'; Title = '评论实体'; Accent = '#5B7DAF'; Attributes = @('评论编号', '帖子编号', '评论用户编号', '评论内容', '父评论编号', '被回复用户编号', '匿名标记', '点赞数量', '创建时间') },
    @{ File = 'fig3-9-view-log.png'; Title = '浏览日志实体'; Accent = '#8A7A68'; Attributes = @('日志编号', '帖子编号', '用户编号', '访问地址', '设备标识', '浏览时间') },
    @{ File = 'fig3-10-level-log.png'; Title = '等级经验日志实体'; Accent = '#A56E3B'; Attributes = @('日志编号', '用户编号', '经验变动值', '变动原因', '记录时间') },
    @{ File = 'fig3-11-notification.png'; Title = '通知实体'; Accent = '#577FB5'; Attributes = @('通知编号', '接收用户编号', '通知类型', '通知标题', '通知内容', '关联资源编号', '触发用户编号', '已读标记', '创建时间') },
    @{ File = 'fig3-12-message.png'; Title = '私信实体'; Accent = '#6C60AD'; Attributes = @('消息编号', '会话编号', '发送用户编号', '接收用户编号', '消息内容', '已读标记', '发送时间') },
    @{ File = 'fig3-13-report.png'; Title = '举报实体'; Accent = '#C15B5B'; Attributes = @('举报编号', '目标类型', '目标编号', '举报原因', '补充说明', '举报用户编号', '处理状态', '创建时间', '更新时间') },
    @{ File = 'fig3-14-follow.png'; Title = '关注关系实体'; Accent = '#3F7E76'; Attributes = @('关注记录编号', '关注者编号', '被关注者编号', '关注时间') },
    @{ File = 'fig3-15-moderator-application.png'; Title = '版主申请实体'; Accent = '#6A964C'; Attributes = @('申请编号', '申请用户编号', '申请板块编号', '申请理由', '审核状态', '审核备注', '审核人编号', '审核时间', '创建时间') },
    @{ File = 'fig3-16-comment-like.png'; Title = '评论点赞实体'; Accent = '#B0624B'; Attributes = @('点赞记录编号', '评论编号', '用户编号', '点赞时间') },
    @{ File = 'fig3-17-heat-snapshot.png'; Title = '热度快照实体'; Accent = '#C58A16'; Attributes = @('快照编号', '帖子编号', '热度值', '快照时间') },
    @{ File = 'fig3-18-trend-stat.png'; Title = '趋势统计实体'; Accent = '#A77C2A'; Attributes = @('统计编号', '统计维度', '统计对象', '关联板块编号', '发帖数量', '浏览数量', '点赞数量', '评论数量', '热度值', '统计日期', '创建时间') }
)

foreach ($definition in $entityDefinitions) {
    Save-EntityDiagram -Path (Join-Path $diagramDir $definition.File) `
        -Title $definition.Title `
        -Attributes $definition.Attributes `
        -AccentHex $definition.Accent
}

Save-SystemFunctionDiagram -Path (Join-Path $diagramDir 'fig3-1-system-function.png')
Save-OverallErDiagram -Path (Join-Path $diagramDir 'fig3-19-overall-er.png')

Write-Output "Diagrams generated in $diagramDir"
