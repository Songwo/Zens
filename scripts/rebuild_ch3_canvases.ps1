param(
    [Parameter(Mandatory = $true)]
    [string]$DocPath,

    [string]$TablesJsonPart1 = (Join-Path $PSScriptRoot '..\chapter3_tables_part1.json'),
    [string]$TablesJsonPart2 = (Join-Path $PSScriptRoot '..\chapter3_tables_part2.json')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-CleanJson {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "JSON not found: $Path"
    }

    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Get-TablesByIndex {
    param(
        [string]$Part1,
        [string]$Part2
    )

    $map = @{}
    foreach ($table in @((Get-CleanJson -Path $Part1) + (Get-CleanJson -Path $Part2))) {
        $map[[int]$table.tableIndex] = $table
    }

    return $map
}

function Get-CommentsForTable {
    param(
        [hashtable]$TablesByIndex,
        [int]$TableIndex
    )

    if (-not $TablesByIndex.ContainsKey($TableIndex)) {
        throw "Missing table index: $TableIndex"
    }

    return @($TablesByIndex[$TableIndex].fields | ForEach-Object { $_.comment })
}

function Set-CommonLineFill {
    param($Shape)

    $Shape.Line.Visible = -1
    $Shape.Line.ForeColor.RGB = 0
    $Shape.Line.Weight = 0.75
    $Shape.Fill.Visible = -1
    $Shape.Fill.ForeColor.RGB = 16777215
    $Shape.Fill.Transparency = 0
}

function Set-CommonText {
    param(
        $Shape,
        [string]$Text
    )

    $Shape.TextFrame.TextRange.Text = $Text
    $Shape.TextFrame.TextRange.Font.NameFarEast = '宋体'
    $Shape.TextFrame.TextRange.Font.Name = '宋体'
    $Shape.TextFrame.TextRange.Font.Size = 10.5
    $Shape.TextFrame.TextRange.Font.Bold = 0
    $Shape.TextFrame.TextRange.ParagraphFormat.Alignment = 1
    $Shape.TextFrame.MarginLeft = 1.5
    $Shape.TextFrame.MarginRight = 1.5
    $Shape.TextFrame.MarginTop = 0.5
    $Shape.TextFrame.MarginBottom = 0.5
    try {
        $Shape.TextFrame.VerticalAnchor = 3
    } catch {
    }
}

function Get-TextWidth {
    param(
        [string]$Text,
        [double]$MinWidth,
        [double]$MaxWidth
    )

    $width = 46 + ($Text.Length * 8.6)
    if ($width -lt $MinWidth) {
        $width = $MinWidth
    }
    if ($width -gt $MaxWidth) {
        $width = $MaxWidth
    }

    return [math]::Round($width, 1)
}

function Clear-CanvasItems {
    param($CanvasShape)

    for ($i = $CanvasShape.CanvasItems.Count; $i -ge 1; $i--) {
        $CanvasShape.CanvasItems.Item($i).Delete()
    }
}

function Add-LineSegment {
    param(
        $CanvasShape,
        [double]$X1,
        [double]$Y1,
        [double]$X2,
        [double]$Y2
    )

    $thickness = 0.8
    if ([math]::Abs($Y1 - $Y2) -lt 0.01) {
        $left = [math]::Min($X1, $X2)
        $top = $Y1 - ($thickness / 2.0)
        $width = [math]::Max(1.0, [math]::Abs($X2 - $X1))
        $height = $thickness
    } elseif ([math]::Abs($X1 - $X2) -lt 0.01) {
        $left = $X1 - ($thickness / 2.0)
        $top = [math]::Min($Y1, $Y2)
        $width = $thickness
        $height = [math]::Max(1.0, [math]::Abs($Y2 - $Y1))
    } else {
        throw "Add-LineSegment only supports horizontal or vertical segments."
    }

    $shape = $CanvasShape.CanvasItems.AddShape(1, [math]::Round($left, 1), [math]::Round($top, 1), [math]::Round($width, 1), [math]::Round($height, 1))
    $shape.Line.Visible = 0
    $shape.Fill.Visible = -1
    $shape.Fill.ForeColor.RGB = 0
    return $shape
}

function Add-Line {
    param(
        $CanvasShape,
        [double]$X1,
        [double]$Y1,
        [double]$X2,
        [double]$Y2
    )

    if ([math]::Abs($X1 - $X2) -lt 0.01 -or [math]::Abs($Y1 - $Y2) -lt 0.01) {
        Add-LineSegment -CanvasShape $CanvasShape -X1 $X1 -Y1 $Y1 -X2 $X2 -Y2 $Y2 | Out-Null
        return
    }

    Add-LineSegment -CanvasShape $CanvasShape -X1 $X1 -Y1 $Y1 -X2 $X2 -Y2 $Y1 | Out-Null
    Add-LineSegment -CanvasShape $CanvasShape -X1 $X2 -Y1 $Y1 -X2 $X2 -Y2 $Y2 | Out-Null
}

function Add-EntityRect {
    param(
        $CanvasShape,
        [double]$Left,
        [double]$Top,
        [double]$Width,
        [double]$Height,
        [string]$Text
    )

    $shape = $CanvasShape.CanvasItems.AddShape(1, $Left, $Top, $Width, $Height)
    Set-CommonLineFill -Shape $shape
    Set-CommonText -Shape $shape -Text $Text
    return $shape
}

function Add-AttrOval {
    param(
        $CanvasShape,
        [double]$Left,
        [double]$Top,
        [double]$Width,
        [double]$Height,
        [string]$Text
    )

    $shape = $CanvasShape.CanvasItems.AddShape(9, $Left, $Top, $Width, $Height)
    Set-CommonLineFill -Shape $shape
    Set-CommonText -Shape $shape -Text $Text
    return $shape
}

function Get-ColumnSplit {
    param(
        [string[]]$Items,
        [int]$ColumnCount
    )

    $result = @()
    $baseSize = [math]::Floor($Items.Count / $ColumnCount)
    $extra = $Items.Count % $ColumnCount
    $cursor = 0

    for ($col = 0; $col -lt $ColumnCount; $col++) {
        $take = $baseSize
        if ($col -lt $extra) {
            $take++
        }

        $bucket = @()
        for ($i = 0; $i -lt $take; $i++) {
            $bucket += $Items[$cursor]
            $cursor++
        }
        $result += ,$bucket
    }

    return $result
}

function Rebuild-EntityCanvas {
    param(
        $CanvasShape,
        [string]$EntityName,
        [string[]]$Attributes
    )

    $width = [double]$CanvasShape.Width
    $columnCount = if ($Attributes.Count -gt 20) { 4 } else { 2 }
    $attrHeight = 22.0
    $vGap = 4.0
    $outerMargin = 8.0
    $innerGap = if ($columnCount -eq 4) { 6.0 } else { 10.0 }
    $centerGap = if ($columnCount -eq 4) { 10.0 } else { 18.0 }
    $entityWidth = if ($EntityName.Length -le 3) { 50.0 } elseif ($EntityName.Length -le 5) { 66.0 } else { 82.0 }
    $entityHeight = 20.0
    $splits = Get-ColumnSplit -Items $Attributes -ColumnCount $columnCount
    $maxRows = ($splits | ForEach-Object { $_.Count } | Measure-Object -Maximum).Maximum
    $height = [math]::Max([double]$CanvasShape.Height, 16.0 + ($maxRows * $attrHeight) + ([math]::Max(0, $maxRows - 1) * $vGap))
    $CanvasShape.Height = $height

    Clear-CanvasItems -CanvasShape $CanvasShape

    $entityLeft = [math]::Round(($width - $entityWidth) / 2.0, 1)
    $entityTop = [math]::Round(($height - $entityHeight) / 2.0, 1)
    $entityRight = $entityLeft + $entityWidth
    $entityCenterY = $entityTop + ($entityHeight / 2.0)

    if ($columnCount -eq 2) {
        $slotWidth = [math]::Round(($width - (2 * $outerMargin) - $entityWidth - (2 * $centerGap)) / 2.0, 1)
        $leftOrigin = $outerMargin
        $rightOrigin = $entityRight + $centerGap
        $columnOrigins = @($leftOrigin, $rightOrigin)
        $sides = @('left', 'right')
        $maxWidth = $slotWidth
    } else {
        $slotWidth = [math]::Round(($width - (2 * $outerMargin) - $entityWidth - (2 * $centerGap) - (2 * $innerGap)) / 4.0, 1)
        $leftOuter = $outerMargin
        $leftInner = $leftOuter + $slotWidth + $innerGap
        $rightInner = $entityRight + $centerGap
        $rightOuter = $rightInner + $slotWidth + $innerGap
        $columnOrigins = @($leftOuter, $leftInner, $rightInner, $rightOuter)
        $sides = @('left', 'left', 'right', 'right')
        $maxWidth = $slotWidth
    }

    $leftItems = @()
    $rightItems = @()

    for ($col = 0; $col -lt $columnCount; $col++) {
        $items = @($splits[$col])
        if ($items.Count -eq 0) {
            continue
        }

        $stackHeight = ($items.Count * $attrHeight) + ([math]::Max(0, $items.Count - 1) * $vGap)
        $startY = [math]::Round(($height - $stackHeight) / 2.0, 1)
        $origin = $columnOrigins[$col]
        $side = $sides[$col]

        for ($row = 0; $row -lt $items.Count; $row++) {
            $label = [string]$items[$row]
            $top = [math]::Round($startY + ($row * ($attrHeight + $vGap)), 1)
            $shapeWidth = Get-TextWidth -Text $label -MinWidth 56.0 -MaxWidth $maxWidth

            if ($side -eq 'left') {
                $left = [math]::Round($origin + ($slotWidth - $shapeWidth), 1)
            } else {
                $left = [math]::Round($origin, 1)
            }

            Add-AttrOval -CanvasShape $CanvasShape -Left $left -Top $top -Width $shapeWidth -Height $attrHeight -Text $label | Out-Null

            $itemInfo = [pscustomobject]@{
                Left = $left
                Top = $top
                Width = $shapeWidth
                CenterY = $top + ($attrHeight / 2.0)
            }

            if ($side -eq 'left') {
                $leftItems += $itemInfo
            } else {
                $rightItems += $itemInfo
            }
        }
    }

    if ($leftItems.Count -gt 0) {
        $leftTrunkX = [math]::Round($entityLeft - 12.0, 1)
        $leftMinY = ($leftItems | ForEach-Object { $_.CenterY } | Measure-Object -Minimum).Minimum
        $leftMaxY = ($leftItems | ForEach-Object { $_.CenterY } | Measure-Object -Maximum).Maximum
        Add-Line -CanvasShape $CanvasShape -X1 $leftTrunkX -Y1 $leftMinY -X2 $leftTrunkX -Y2 $leftMaxY
        foreach ($item in $leftItems) {
            Add-Line -CanvasShape $CanvasShape -X1 ($item.Left + $item.Width) -Y1 $item.CenterY -X2 $leftTrunkX -Y2 $item.CenterY
        }
        Add-Line -CanvasShape $CanvasShape -X1 $leftTrunkX -Y1 $entityCenterY -X2 $entityLeft -Y2 $entityCenterY
    }

    if ($rightItems.Count -gt 0) {
        $rightTrunkX = [math]::Round($entityRight + 12.0, 1)
        $rightMinY = ($rightItems | ForEach-Object { $_.CenterY } | Measure-Object -Minimum).Minimum
        $rightMaxY = ($rightItems | ForEach-Object { $_.CenterY } | Measure-Object -Maximum).Maximum
        Add-Line -CanvasShape $CanvasShape -X1 $rightTrunkX -Y1 $rightMinY -X2 $rightTrunkX -Y2 $rightMaxY
        foreach ($item in $rightItems) {
            Add-Line -CanvasShape $CanvasShape -X1 $rightTrunkX -Y1 $item.CenterY -X2 $item.Left -Y2 $item.CenterY
        }
        Add-Line -CanvasShape $CanvasShape -X1 $entityRight -Y1 $entityCenterY -X2 $rightTrunkX -Y2 $entityCenterY
    }

    Add-EntityRect -CanvasShape $CanvasShape -Left $entityLeft -Top $entityTop -Width $entityWidth -Height $entityHeight -Text $EntityName | Out-Null
}

function Add-LabelDiamond {
    param(
        $CanvasShape,
        [double]$Left,
        [double]$Top,
        [double]$Width,
        [double]$Height,
        [string]$Text
    )

    $shape = $CanvasShape.CanvasItems.AddShape(63, $Left, $Top, $Width, $Height)
    Set-CommonLineFill -Shape $shape
    Set-CommonText -Shape $shape -Text $Text
    return $shape
}

function Add-Relation {
    param(
        $CanvasShape,
        [hashtable]$Nodes,
        [string]$From,
        [string]$To,
        [double]$DiamondLeft,
        [double]$DiamondTop,
        [double]$DiamondWidth,
        [double]$DiamondHeight,
        [string]$Label
    )

    $fromNode = $Nodes[$From]
    $toNode = $Nodes[$To]
    $diamondCenterX = $DiamondLeft + ($DiamondWidth / 2.0)
    $diamondCenterY = $DiamondTop + ($DiamondHeight / 2.0)
    $fromCenterX = $fromNode.Left + ($fromNode.Width / 2.0)
    $fromCenterY = $fromNode.Top + ($fromNode.Height / 2.0)
    $toCenterX = $toNode.Left + ($toNode.Width / 2.0)
    $toCenterY = $toNode.Top + ($toNode.Height / 2.0)

    Add-Line -CanvasShape $CanvasShape -X1 $fromCenterX -Y1 $fromCenterY -X2 $diamondCenterX -Y2 $diamondCenterY | Out-Null
    Add-Line -CanvasShape $CanvasShape -X1 $diamondCenterX -Y1 $diamondCenterY -X2 $toCenterX -Y2 $toCenterY | Out-Null
    Add-LabelDiamond -CanvasShape $CanvasShape -Left $DiamondLeft -Top $DiamondTop -Width $DiamondWidth -Height $DiamondHeight -Text $Label | Out-Null
}

function Rebuild-OverallErCanvas {
    param($CanvasShape)

    $CanvasShape.Height = 318.0
    Clear-CanvasItems -CanvasShape $CanvasShape

    $nodes = @{}

    $layout = @(
        @{ Name = '等级经验日志'; Left = 10; Top = 10; Width = 78; Height = 20 },
        @{ Name = '通知'; Left = 100; Top = 10; Width = 50; Height = 20 },
        @{ Name = '用户'; Left = 185; Top = 10; Width = 50; Height = 20 },
        @{ Name = '私信'; Left = 270; Top = 10; Width = 50; Height = 20 },
        @{ Name = '邀请码'; Left = 345; Top = 10; Width = 66; Height = 20 },
        @{ Name = '关注关系'; Left = 5; Top = 88; Width = 66; Height = 20 },
        @{ Name = '帖子点赞'; Left = 85; Top = 88; Width = 66; Height = 20 },
        @{ Name = '帖子'; Left = 185; Top = 88; Width = 50; Height = 20 },
        @{ Name = '帖子收藏'; Left = 260; Top = 88; Width = 66; Height = 20 },
        @{ Name = '举报'; Left = 350; Top = 88; Width = 50; Height = 20 },
        @{ Name = '浏览日志'; Left = 10; Top = 166; Width = 66; Height = 20 },
        @{ Name = '评论'; Left = 102; Top = 166; Width = 50; Height = 20 },
        @{ Name = '评论点赞'; Left = 178; Top = 166; Width = 66; Height = 20 },
        @{ Name = '板块'; Left = 270; Top = 166; Width = 50; Height = 20 },
        @{ Name = '标签'; Left = 350; Top = 166; Width = 50; Height = 20 },
        @{ Name = '热度快照'; Left = 12; Top = 244; Width = 66; Height = 20 },
        @{ Name = '趋势统计'; Left = 102; Top = 244; Width = 66; Height = 20 },
        @{ Name = '版主申请'; Left = 190; Top = 244; Width = 66; Height = 20 },
        @{ Name = '发展历程'; Left = 285; Top = 244; Width = 66; Height = 20 }
    )

    foreach ($item in $layout) {
        $nodes[$item.Name] = Add-EntityRect -CanvasShape $CanvasShape -Left $item.Left -Top $item.Top -Width $item.Width -Height $item.Height -Text $item.Name
    }

    $relations = @(
        @{ From = '用户'; To = '等级经验日志'; Left = 118; Top = 8; Width = 58; Height = 22; Label = '产生' },
        @{ From = '用户'; To = '通知'; Left = 150; Top = 34; Width = 50; Height = 22; Label = '接收' },
        @{ From = '用户'; To = '私信'; Left = 230; Top = 34; Width = 50; Height = 22; Label = '收发' },
        @{ From = '用户'; To = '邀请码'; Left = 288; Top = 8; Width = 68; Height = 22; Label = '创建/使用' },
        @{ From = '用户'; To = '帖子'; Left = 184; Top = 48; Width = 50; Height = 22; Label = '发布' },
        @{ From = '用户'; To = '关注关系'; Left = 78; Top = 48; Width = 50; Height = 22; Label = '建立' },
        @{ From = '用户'; To = '帖子点赞'; Left = 118; Top = 70; Width = 50; Height = 22; Label = '点赞' },
        @{ From = '用户'; To = '帖子收藏'; Left = 248; Top = 70; Width = 50; Height = 22; Label = '收藏' },
        @{ From = '用户'; To = '举报'; Left = 310; Top = 48; Width = 50; Height = 22; Label = '提交' },
        @{ From = '用户'; To = '版主申请'; Left = 222; Top = 206; Width = 50; Height = 22; Label = '申请' },
        @{ From = '用户'; To = '发展历程'; Left = 250; Top = 146; Width = 50; Height = 22; Label = '维护' },
        @{ From = '帖子'; To = '帖子点赞'; Left = 132; Top = 108; Width = 50; Height = 22; Label = '对应' },
        @{ From = '帖子'; To = '帖子收藏'; Left = 236; Top = 108; Width = 50; Height = 22; Label = '对应' },
        @{ From = '帖子'; To = '评论'; Left = 146; Top = 128; Width = 50; Height = 22; Label = '包含' },
        @{ From = '帖子'; To = '浏览日志'; Left = 86; Top = 150; Width = 50; Height = 22; Label = '记录' },
        @{ From = '帖子'; To = '板块'; Left = 238; Top = 126; Width = 50; Height = 22; Label = '归属' },
        @{ From = '帖子'; To = '标签'; Left = 318; Top = 126; Width = 50; Height = 22; Label = '关联' },
        @{ From = '帖子'; To = '热度快照'; Left = 90; Top = 210; Width = 58; Height = 22; Label = '采样' },
        @{ From = '评论'; To = '评论点赞'; Left = 150; Top = 184; Width = 50; Height = 22; Label = '被赞' },
        @{ From = '板块'; To = '趋势统计'; Left = 180; Top = 210; Width = 50; Height = 22; Label = '统计' },
        @{ From = '板块'; To = '版主申请'; Left = 254; Top = 210; Width = 50; Height = 22; Label = '审核' }
    )

    foreach ($relation in $relations) {
        Add-Relation -CanvasShape $CanvasShape -Nodes $nodes -From $relation.From -To $relation.To -DiamondLeft $relation.Left -DiamondTop $relation.Top -DiamondWidth $relation.Width -DiamondHeight $relation.Height -Label $relation.Label
    }
}

$entityMappings = @(
    @{ ShapeIndex = 18; Entity = '用户'; TableIndex = 1 },
    @{ ShapeIndex = 17; Entity = '标签'; TableIndex = 2 },
    @{ ShapeIndex = 16; Entity = '板块'; TableIndex = 3 },
    @{ ShapeIndex = 15; Entity = '帖子'; TableIndex = 4 },
    @{ ShapeIndex = 14; Entity = '帖子点赞'; TableIndex = 5 },
    @{ ShapeIndex = 13; Entity = '帖子收藏'; TableIndex = 6 },
    @{ ShapeIndex = 20; Entity = '评论'; TableIndex = 7 },
    @{ ShapeIndex = 12; Entity = '浏览日志'; TableIndex = 8 },
    @{ ShapeIndex = 11; Entity = '等级经验日志'; TableIndex = 9 },
    @{ ShapeIndex = 21; Entity = '通知'; TableIndex = 10 },
    @{ ShapeIndex = 22; Entity = '私信'; TableIndex = 11 },
    @{ ShapeIndex = 23; Entity = '举报'; TableIndex = 12 },
    @{ ShapeIndex = 10; Entity = '关注关系'; TableIndex = 13 },
    @{ ShapeIndex = 24; Entity = '发展历程'; TableIndex = 14 },
    @{ ShapeIndex = 25; Entity = '版主申请'; TableIndex = 15 },
    @{ ShapeIndex = 9; Entity = '评论点赞'; TableIndex = 16 },
    @{ ShapeIndex = 8; Entity = '热度快照'; TableIndex = 17 },
    @{ ShapeIndex = 26; Entity = '趋势统计'; TableIndex = 18 },
    @{ ShapeIndex = 27; Entity = '邀请码'; TableIndex = 19 }
)

$word = $null
$doc = $null

try {
    $tablesByIndex = Get-TablesByIndex -Part1 $TablesJsonPart1 -Part2 $TablesJsonPart2
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $doc = $word.Documents.Open($DocPath, $false, $false)

    foreach ($mapping in $entityMappings) {
        $canvas = $doc.Shapes.Item([int]$mapping.ShapeIndex)
        $attrs = Get-CommentsForTable -TablesByIndex $tablesByIndex -TableIndex ([int]$mapping.TableIndex)
        Rebuild-EntityCanvas -CanvasShape $canvas -EntityName $mapping.Entity -Attributes $attrs
        Write-Output ("UPDATED entity canvas {0}: {1} ({2} fields)" -f $mapping.ShapeIndex, $mapping.Entity, $attrs.Count)
    }

    Rebuild-OverallErCanvas -CanvasShape $doc.Shapes.Item(7)
    Write-Output 'UPDATED overall E-R canvas 7'

    $doc.Save()
    Write-Output ('SAVED ' + $DocPath)
}
finally {
    if ($doc -ne $null) {
        $doc.Close([ref]0)
    }
    if ($word -ne $null) {
        $word.Quit()
    }
}
