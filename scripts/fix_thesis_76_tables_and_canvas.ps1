param(
    [string]$TargetDoc = "docs\赵青松论文7.6.docx"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetPath = Join-Path $repoRoot $TargetDoc

if (-not (Test-Path $targetPath)) {
    throw "未找到目标论文文件: $targetPath"
}

function Get-CleanParagraphText {
    param($Paragraph)
    return (($Paragraph.Range.Text -replace '[\r\a\v\f]+', ' ') -replace '\s+', ' ').Trim()
}

function Find-ParagraphIndex {
    param(
        $Document,
        [string]$ExactText
    )

    $target = ($ExactText -replace '\s+', '')
    for ($i = 1; $i -le $Document.Paragraphs.Count; $i++) {
        $current = (Get-CleanParagraphText $Document.Paragraphs.Item($i)) -replace '\s+', ''
        if ($current -eq $target) {
            return $i
        }
    }

    throw "未找到段落: $ExactText"
}

function Get-NextTableAfterParagraph {
    param(
        $Document,
        [int]$ParagraphIndex
    )

    $start = $Document.Paragraphs.Item($ParagraphIndex).Range.Start
    $candidate = $null
    foreach ($tbl in $Document.Tables) {
        if ($tbl.Range.Start -gt $start) {
            if ($null -eq $candidate -or $tbl.Range.Start -lt $candidate.Range.Start) {
                $candidate = $tbl
            }
        }
    }

    if ($null -eq $candidate) {
        throw "在段落 $ParagraphIndex 后未找到表格"
    }

    return $candidate
}

function Copy-TableFormat {
    param(
        $SourceTable,
        $TargetTable
    )

    try { $TargetTable.Style = $SourceTable.Style } catch {}
    try { $TargetTable.Borders.Enable = $SourceTable.Borders.Enable } catch {}
    try { $TargetTable.Rows.Alignment = $SourceTable.Rows.Alignment } catch {}
    try { $TargetTable.Range.Font.Name = $SourceTable.Range.Font.Name } catch {}
    try { $TargetTable.Range.Font.Size = $SourceTable.Range.Font.Size } catch {}
    try { $TargetTable.Range.ParagraphFormat.Alignment = $SourceTable.Range.ParagraphFormat.Alignment } catch {}
    try { $TargetTable.AllowAutoFit = $SourceTable.AllowAutoFit } catch {}
    try { $TargetTable.PreferredWidthType = $SourceTable.PreferredWidthType } catch {}
    try { $TargetTable.PreferredWidth = $SourceTable.PreferredWidth } catch {}

    if ($SourceTable.Columns.Count -eq $TargetTable.Columns.Count) {
        for ($i = 1; $i -le $SourceTable.Columns.Count; $i++) {
            try { $TargetTable.Columns.Item($i).Width = $SourceTable.Columns.Item($i).Width } catch {}
        }
    }

    try { $TargetTable.Rows.Item(1).Range.Bold = $SourceTable.Rows.Item(1).Range.Bold } catch {}
}

function Copy-ParagraphFormat {
    param(
        $SourceParagraph,
        $TargetParagraph
    )

    try { $TargetParagraph.Range.Style = $SourceParagraph.Range.Style } catch {}
    try { $TargetParagraph.Alignment = $SourceParagraph.Alignment } catch {}
    try { $TargetParagraph.Range.Font.Name = $SourceParagraph.Range.Font.Name } catch {}
    try { $TargetParagraph.Range.Font.Size = $SourceParagraph.Range.Font.Size } catch {}
    try { $TargetParagraph.Range.Font.Bold = $SourceParagraph.Range.Font.Bold } catch {}
}

function Replace-CanvasTextInOpenXml {
    param(
        [string]$DocxPath,
        [hashtable]$TextMap,
        [ref]$Counter
    )

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $fileStream = [System.IO.File]::Open($DocxPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite)
    try {
        $archive = New-Object System.IO.Compression.ZipArchive($fileStream, [System.IO.Compression.ZipArchiveMode]::Update, $false)
        try {
            $entry = $archive.GetEntry('word/document.xml')
            if ($null -eq $entry) {
                throw 'word/document.xml 不存在'
            }

            $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
            $xmlText = $reader.ReadToEnd()
            $reader.Dispose()

            [xml]$xml = $xmlText
            $ns = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
            $ns.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')

            $nodes = $xml.SelectNodes('//w:t[ancestor::w:pict or ancestor::w:txbxContent]', $ns)
            foreach ($node in $nodes) {
                $current = [string]$node.InnerText
                if ($TextMap.ContainsKey($current)) {
                    $node.InnerText = $TextMap[$current]
                    $Counter.Value++
                }
            }

            $entry.Delete()
            $newEntry = $archive.CreateEntry('word/document.xml')
            $writer = New-Object System.IO.StreamWriter($newEntry.Open(), [System.Text.UTF8Encoding]::new($false))
            $writer.Write($xml.OuterXml)
            $writer.Dispose()
        } finally {
            $archive.Dispose()
        }
    } finally {
        $fileStream.Dispose()
    }
}

$word = $null
$document = $null
$newWordPids = @()

$canvasTextMap = @{
    'active_region' = '活跃地区'
    'avatar' = '头像'
    'bio' = '简介'
    'contribution_val' = '贡献值'
    'description' = '描述'
    'email' = '邮箱'
    'enrollment_year' = '入学年份'
    'follower_id' = '粉丝编号'
    'following_id' = '关注对象编号'
    'gender' = '性别'
    'heat' = '热度'
    'interest_tags' = '兴趣标签'
    'level' = '等级'
    'major' = '专业'
    'name' = '名称'
    'nickname' = '昵称'
    'parent_id' = '父评论编号'
    'password' = '密码'
    'role' = '角色'
    'school' = '学校'
    'sort_order' = '排序值'
    'stat_date' = '统计日期'
    'stat_key' = '统计键'
    'stat_type' = '统计类型'
    'Token' = '令牌'
    'Redis/' = 'Redis'
    'total_likes_received' = '获赞总数'
    'total_posts' = '发帖总数'
    'two_factor_enabled' = '双重验证状态'
    'username' = '用户名'
    '2FA' = '二步验证'
}

try {
    $existingWordPids = @(Get-Process WINWORD -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)

    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $document = $word.Documents.Open($targetPath)

    $sourceCaptionIndex = Find-ParagraphIndex -Document $document -ExactText '表5—1 认证与会话功能测试用例表'
    $sourceCaption = $document.Paragraphs.Item($sourceCaptionIndex)
    $sourceTable = Get-NextTableAfterParagraph -Document $document -ParagraphIndex $sourceCaptionIndex

    foreach ($captionText in @(
        '表5—4 文件上传与媒体校验功能测试用例表',
        '表5—5 通知与消息处理功能测试用例表',
        '表5—6 版主管理与举报处理功能测试用例表'
    )) {
        $captionIndex = Find-ParagraphIndex -Document $document -ExactText $captionText
        $captionParagraph = $document.Paragraphs.Item($captionIndex)
        $targetTable = Get-NextTableAfterParagraph -Document $document -ParagraphIndex $captionIndex
        Copy-ParagraphFormat -SourceParagraph $sourceCaption -TargetParagraph $captionParagraph
        Copy-TableFormat -SourceTable $sourceTable -TargetTable $targetTable
    }

    $document.Save()
    $document.Close([ref]0)
    $document = $null
    $word.Quit()
    $word = $null

    $replaceCounter = 0
    Replace-CanvasTextInOpenXml -DocxPath $targetPath -TextMap $canvasTextMap -Counter ([ref]$replaceCounter)

    $currentWordPids = @(Get-Process WINWORD -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
    $newWordPids = @($currentWordPids | Where-Object { $_ -notin $existingWordPids })

    Write-Output ("表格格式已对齐，画布新增替换数量: {0}" -f $replaceCounter)
} finally {
    if ($document -ne $null) {
        $document.Close([ref]0)
    }
    if ($word -ne $null) {
        $word.Quit()
    }
    if ($newWordPids.Count -gt 0) {
        try {
            Stop-Process -Id $newWordPids -Force -ErrorAction SilentlyContinue
        } catch {
        }
    }
}
