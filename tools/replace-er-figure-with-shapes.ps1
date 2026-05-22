param(
    [Parameter(Mandatory = $true)]
    [string]$DocxPath,

    [Parameter(Mandatory = $true)]
    [string]$ExtractedDir,

    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

if (-not $OutputPath) {
    $OutputPath = $DocxPath
}

function New-TextboxXml {
    param(
        [string]$Text,
        [int]$FontHalfPoints = 20
    )

    return @"
<v:textbox inset="2pt,2pt,2pt,2pt" style="mso-fit-shape-to-text:t">
  <w:txbxContent>
    <w:p>
      <w:pPr>
        <w:jc w:val="center" />
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:hint="eastAsia" w:ascii="宋体" w:hAnsi="宋体" w:eastAsia="宋体" w:cs="宋体" />
          <w:sz w:val="$FontHalfPoints" />
          <w:szCs w:val="$FontHalfPoints" />
        </w:rPr>
        <w:t>$Text</w:t>
      </w:r>
    </w:p>
  </w:txbxContent>
</v:textbox>
"@
}

function New-RectXml {
    param(
        [string]$Id,
        [int]$X,
        [int]$Y,
        [int]$Width,
        [int]$Height,
        [string]$Text
    )

    $textbox = New-TextboxXml -Text $Text -FontHalfPoints 18
    return @"
<v:rect id="$Id" style="position:absolute;left:$X;top:$Y;width:$Width;height:$Height" filled="t" fillcolor="#ffffff" stroked="t" strokecolor="#000000" strokeweight="1pt">
  <v:fill color2="#ffffff" />
  <v:stroke joinstyle="miter" />
  $textbox
</v:rect>
"@
}

function New-DiamondXml {
    param(
        [string]$Id,
        [int]$CenterX,
        [int]$CenterY,
        [int]$Size,
        [string]$Text
    )

    $left = $CenterX - [int]($Size / 2)
    $top = $CenterY - [int]($Size / 2)
    $textbox = New-TextboxXml -Text $Text -FontHalfPoints 18
    return @"
<v:shape id="$Id" type="#_x0000_t4" style="position:absolute;left:$left;top:$top;width:$Size;height:$Size" filled="t" fillcolor="#ffffff" stroked="t" strokecolor="#000000" strokeweight="1pt">
  <v:fill color2="#ffffff" />
  <v:stroke joinstyle="miter" />
  $textbox
</v:shape>
"@
}

function New-LineXml {
    param(
        [string]$Id,
        [int]$X1,
        [int]$Y1,
        [int]$X2,
        [int]$Y2,
        [string]$Weight = '1pt'
    )

    return @"
<v:line id="$Id" from="$X1,$Y1" to="$X2,$Y2" filled="f" stroked="t" strokecolor="#000000" strokeweight="$Weight">
  <v:stroke joinstyle="round" />
</v:line>
"@
}

function Add-PathSegments {
    param(
        [ref]$Buffer,
        [ref]$LineCounter,
        [int[]]$Points,
        [string]$Weight = '1pt'
    )

    for ($i = 0; $i -lt $Points.Length - 2; $i += 2) {
        $x1 = $Points[$i]
        $y1 = $Points[$i + 1]
        $x2 = $Points[$i + 2]
        $y2 = $Points[$i + 3]
        if ($x1 -eq $x2 -and $y1 -eq $y2) {
            continue
        }
        $Buffer.Value += (New-LineXml -Id ("seg_" + $LineCounter.Value) -X1 $x1 -Y1 $y1 -X2 $x2 -Y2 $y2 -Weight $Weight)
        $LineCounter.Value++
    }
}

$documentXmlPath = Join-Path $ExtractedDir 'word\document.xml'

if (-not (Test-Path -LiteralPath $DocxPath)) {
    throw "DOCX not found: $DocxPath"
}
if (-not (Test-Path -LiteralPath $ExtractedDir)) {
    throw "Extracted directory not found: $ExtractedDir"
}

$entities = @(
    @{ id = 'e_dev'; x = 40; y = 60; w = 220; h = 84; text = '发展历程' },
    @{ id = 'e_inv'; x = 280; y = 60; w = 220; h = 84; text = '邀请码' },
    @{ id = 'e_exp'; x = 520; y = 60; w = 220; h = 84; text = '等级经验日志' },
    @{ id = 'e_notify'; x = 40; y = 470; w = 220; h = 84; text = '通知' },
    @{ id = 'e_pm'; x = 40; y = 690; w = 220; h = 84; text = '私信' },
    @{ id = 'e_user'; x = 520; y = 520; w = 220; h = 84; text = '用户' },
    @{ id = 'e_mod'; x = 280; y = 930; w = 220; h = 84; text = '版主申请' },
    @{ id = 'e_follow'; x = 520; y = 930; w = 220; h = 84; text = '关注关系' },
    @{ id = 'e_board'; x = 860; y = 60; w = 220; h = 84; text = '板块' },
    @{ id = 'e_tag'; x = 1100; y = 60; w = 220; h = 84; text = '标签' },
    @{ id = 'e_heat'; x = 1340; y = 60; w = 220; h = 84; text = '热度快照' },
    @{ id = 'e_trend'; x = 1580; y = 60; w = 220; h = 84; text = '趋势统计' },
    @{ id = 'e_post'; x = 860; y = 520; w = 220; h = 84; text = '帖子' },
    @{ id = 'e_comment'; x = 1340; y = 520; w = 220; h = 84; text = '评论' },
    @{ id = 'e_post_like'; x = 1580; y = 430; w = 220; h = 84; text = '帖子点赞' },
    @{ id = 'e_post_fav'; x = 1580; y = 690; w = 220; h = 84; text = '帖子收藏' },
    @{ id = 'e_browse'; x = 860; y = 930; w = 220; h = 84; text = '浏览日志' },
    @{ id = 'e_report'; x = 1100; y = 930; w = 220; h = 84; text = '举报' },
    @{ id = 'e_comment_like'; x = 1340; y = 930; w = 220; h = 84; text = '评论点赞' }
)

$diamonds = @(
    @{ id = 'r_dev'; x = 150; y = 240; size = 60; text = '记' },
    @{ id = 'r_inv'; x = 390; y = 240; size = 60; text = '持' },
    @{ id = 'r_exp'; x = 630; y = 330; size = 60; text = '记' },
    @{ id = 'r_receive'; x = 350; y = 512; size = 60; text = '接' },
    @{ id = 'r_pm'; x = 350; y = 732; size = 60; text = '发' },
    @{ id = 'r_mod'; x = 390; y = 885; size = 60; text = '申' },
    @{ id = 'r_follow'; x = 630; y = 885; size = 60; text = '关' },
    @{ id = 'r_publish'; x = 800; y = 562; size = 60; text = '发' },
    @{ id = 'r_board'; x = 970; y = 240; size = 60; text = '属' },
    @{ id = 'r_tag'; x = 1210; y = 240; size = 60; text = '关' },
    @{ id = 'r_heat'; x = 1450; y = 240; size = 60; text = '采' },
    @{ id = 'r_trend'; x = 1690; y = 240; size = 60; text = '统' },
    @{ id = 'r_comment'; x = 1210; y = 562; size = 60; text = '评' },
    @{ id = 'r_post_like'; x = 1500; y = 472; size = 60; text = '赞' },
    @{ id = 'r_post_fav'; x = 1500; y = 732; size = 60; text = '藏' },
    @{ id = 'r_browse'; x = 970; y = 885; size = 60; text = '浏' },
    @{ id = 'r_report'; x = 1210; y = 885; size = 60; text = '报' },
    @{ id = 'r_comment_like'; x = 1450; y = 885; size = 60; text = '赞' }
)

$paths = @(
    @(150, 144, 150, 210),
    @(150, 270, 150, 410, 630, 410, 630, 520),
    @(390, 144, 390, 210),
    @(390, 270, 390, 430, 630, 430, 630, 520),
    @(630, 144, 630, 300),
    @(630, 360, 630, 520),
    @(260, 512, 320, 512),
    @(380, 512, 450, 512, 450, 562, 520, 562),
    @(260, 732, 320, 732),
    @(380, 732, 450, 732, 450, 562, 520, 562),
    @(630, 604, 630, 840, 390, 840, 390, 855),
    @(390, 915, 390, 930),
    @(630, 604, 630, 855),
    @(630, 915, 630, 930),
    @(970, 144, 970, 210),
    @(970, 270, 970, 520),
    @(1210, 144, 1210, 210),
    @(1210, 270, 1210, 430, 970, 430, 970, 520),
    @(1450, 144, 1450, 210),
    @(1450, 270, 1450, 520),
    @(1690, 144, 1690, 210),
    @(1690, 270, 1690, 410, 970, 410, 970, 520),
    @(1080, 562, 1180, 562),
    @(1240, 562, 1340, 562),
    @(1080, 562, 1080, 472, 1470, 472),
    @(1530, 472, 1580, 472),
    @(1080, 562, 1080, 732, 1470, 732),
    @(1530, 732, 1580, 732),
    @(970, 604, 970, 855),
    @(970, 915, 970, 930),
    @(970, 604, 970, 820, 1210, 820, 1210, 855),
    @(1210, 915, 1210, 930),
    @(1450, 604, 1450, 855),
    @(1450, 915, 1450, 930)
)

$lineBuffer = ""
$lineCounter = 1
Add-PathSegments -Buffer ([ref]$lineBuffer) -LineCounter ([ref]$lineCounter) -Points @(740, 562, 770, 562) -Weight '1.4pt'
Add-PathSegments -Buffer ([ref]$lineBuffer) -LineCounter ([ref]$lineCounter) -Points @(830, 562, 860, 562) -Weight '1.4pt'
foreach ($path in $paths) {
    Add-PathSegments -Buffer ([ref]$lineBuffer) -LineCounter ([ref]$lineCounter) -Points $path -Weight '1pt'
}

$shapeBuffer = $lineBuffer
foreach ($entity in $entities) {
    $shapeBuffer += (New-RectXml -Id $entity.id -X $entity.x -Y $entity.y -Width $entity.w -Height $entity.h -Text $entity.text)
}

foreach ($diamond in $diamonds) {
    $shapeBuffer += (New-DiamondXml -Id $diamond.id -CenterX $diamond.x -CenterY $diamond.y -Size $diamond.size -Text $diamond.text)
}

$replacementParagraph = @"
<w:p w14:paraId="3836BDC4" xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml" xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:pPr>
    <w:spacing w:line="360" w:lineRule="auto" />
    <w:ind w:firstLine="420" w:firstLineChars="200" />
    <w:jc w:val="center" />
    <w:rPr>
      <w:rFonts w:hint="eastAsia" w:ascii="黑体" w:hAnsi="宋体" w:eastAsia="黑体" w:cs="黑体" />
      <w:szCs w:val="21" />
      <w:lang w:bidi="ar" />
    </w:rPr>
  </w:pPr>
  <w:r>
    <w:pict>
      <v:group id="画布 390" style="height:280pt;width:500pt;" coordorigin="0,0" coordsize="1900,1100" editas="canvas" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:v="urn:schemas-microsoft-com:vml">
        <o:lock v:ext="edit" />
        $shapeBuffer
      </v:group>
    </w:pict>
  </w:r>
</w:p>
"@

[xml]$documentDom = Get-Content -LiteralPath $documentXmlPath -Raw -Encoding UTF8
$ns = New-Object System.Xml.XmlNamespaceManager($documentDom.NameTable)
$ns.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')

$captionParagraph = $documentDom.SelectSingleNode("//w:p[contains(string(.), '图3—19 系统核心业务E-R图')]", $ns)
if (-not $captionParagraph) {
    throw 'Could not locate the caption paragraph for 图3—19.'
}

$figureParagraph = $captionParagraph.PreviousSibling
while ($figureParagraph -and $figureParagraph.NodeType -ne [System.Xml.XmlNodeType]::Element) {
    $figureParagraph = $figureParagraph.PreviousSibling
}

if (-not $figureParagraph -or $figureParagraph.Name -ne 'w:p') {
    throw 'Could not locate the figure paragraph immediately before the caption.'
}

if ($figureParagraph.OuterXml -notmatch '画布 390') {
    throw 'The located paragraph before the caption is not the expected drawing canvas block.'
}

$replacementDom = New-Object System.Xml.XmlDocument
$replacementDom.LoadXml("<root xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' xmlns:w14='http://schemas.microsoft.com/office/word/2010/wordml' xmlns:v='urn:schemas-microsoft-com:vml' xmlns:o='urn:schemas-microsoft-com:office:office'>$replacementParagraph</root>")
$newFigureParagraph = $documentDom.ImportNode($replacementDom.DocumentElement.FirstChild, $true)
$null = $captionParagraph.ParentNode.ReplaceChild($newFigureParagraph, $figureParagraph)

$documentDom.Save($documentXmlPath)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$tempDocxPath = Join-Path ([System.IO.Path]::GetDirectoryName($OutputPath)) ([System.IO.Path]::GetFileNameWithoutExtension($OutputPath) + '.tmp.docx')
if (Test-Path -LiteralPath $tempDocxPath) {
    Remove-Item -LiteralPath $tempDocxPath -Force
}

[System.IO.Compression.ZipFile]::CreateFromDirectory($ExtractedDir, $tempDocxPath)
Copy-Item -LiteralPath $tempDocxPath -Destination $OutputPath -Force
Remove-Item -LiteralPath $tempDocxPath -Force

Write-Output "UPDATED_DOCX=$OutputPath"
Write-Output 'FIGURE_MODE=VML_SHAPES'

