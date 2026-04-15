param(
    [string]$TargetDoc = "docs\赵青松论文7.6.docx",
    [string]$SourceDoc = "docs\赵青松论文7.6_XML直改前备份_20260413-190713.docx",
    [switch]$SkipBackup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetPath = Join-Path $repoRoot $TargetDoc
$sourcePath = Join-Path $repoRoot $SourceDoc

if (-not (Test-Path $targetPath)) { throw "未找到目标文档: $targetPath" }
if (-not (Test-Path $sourcePath)) { throw "未找到源文档: $sourcePath" }

function Normalize-Text {
    param([string]$Text)
    if ($null -eq $Text) { return '' }
    return ($Text -replace '\s+', '')
}

function Get-Body {
    param([xml]$Xml)
    return $Xml.SelectSingleNode("/*[local-name()='document']/*[local-name()='body']")
}

function Get-BodyChildren {
    param([xml]$Xml)
    return @((Get-Body -Xml $Xml).ChildNodes | Where-Object { $_.LocalName -in @('p', 'tbl') })
}

function Get-ParagraphText {
    param([System.Xml.XmlNode]$Node)
    if ($Node.LocalName -ne 'p') { return '' }
    $parts = @()
    foreach ($t in $Node.SelectNodes(".//*[local-name()='t']")) {
        $parts += [string]$t.InnerText
    }
    return ($parts -join '')
}

function Find-ParagraphNodeIndex {
    param(
        [System.Xml.XmlNode[]]$Nodes,
        [string]$ExactText
    )

    $target = Normalize-Text $ExactText
    for ($i = 0; $i -lt $Nodes.Count; $i++) {
        if ($Nodes[$i].LocalName -ne 'p') { continue }
        if ((Normalize-Text (Get-ParagraphText -Node $Nodes[$i])) -eq $target) {
            return $i
        }
    }
    throw "未找到段落: $ExactText"
}

function Read-DocXml {
    param([string]$Path)

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Read, $false)
        try {
            $entry = $zip.GetEntry('word/document.xml')
            if ($null -eq $entry) { throw "word/document.xml 不存在: $Path" }
            $sr = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
            try {
                [xml]$xml = $sr.ReadToEnd()
                return $xml
            }
            finally {
                $sr.Dispose()
            }
        }
        finally {
            $zip.Dispose()
        }
    }
    finally {
        $fs.Dispose()
    }
}

function Set-ParagraphTextValue {
    param(
        [System.Xml.XmlNode]$ParagraphNode,
        [string]$Text
    )

    $tNodes = @($ParagraphNode.SelectNodes(".//*[local-name()='t']"))
    if ($tNodes.Count -eq 0) { return }
    $tNodes[0].InnerText = $Text
    for ($i = $tNodes.Count - 1; $i -ge 1; $i--) {
        $run = $tNodes[$i].ParentNode
        if ($null -ne $run -and $null -ne $run.ParentNode) {
            $run.ParentNode.RemoveChild($run) | Out-Null
        }
    }
}

function Set-CellText {
    param(
        [System.Xml.XmlNode]$CellNode,
        [string]$Text
    )

    $tNodes = @($CellNode.SelectNodes(".//*[local-name()='t']"))
    if ($tNodes.Count -eq 0) { return }
    $tNodes[0].InnerText = $Text
    for ($i = $tNodes.Count - 1; $i -ge 1; $i--) {
        $run = $tNodes[$i].ParentNode
        if ($null -ne $run -and $null -ne $run.ParentNode) {
            $run.ParentNode.RemoveChild($run) | Out-Null
        }
    }
}

function Set-TableData {
    param(
        [System.Xml.XmlNode]$TableNode,
        [object[][]]$Rows
    )

    $rowNodes = @($TableNode.SelectNodes("./*[local-name()='tr']"))
    if ($rowNodes.Count -lt $Rows.Count) {
        $templateRow = $rowNodes[$rowNodes.Count - 1]
        for ($i = $rowNodes.Count; $i -lt $Rows.Count; $i++) {
            $clone = $templateRow.CloneNode($true)
            $TableNode.AppendChild($clone) | Out-Null
        }
        $rowNodes = @($TableNode.SelectNodes("./*[local-name()='tr']"))
    }
    elseif ($rowNodes.Count -gt $Rows.Count) {
        for ($i = $rowNodes.Count - 1; $i -ge $Rows.Count; $i--) {
            $TableNode.RemoveChild($rowNodes[$i]) | Out-Null
        }
        $rowNodes = @($TableNode.SelectNodes("./*[local-name()='tr']"))
    }

    for ($r = 0; $r -lt $Rows.Count; $r++) {
        $cells = @($rowNodes[$r].SelectNodes("./*[local-name()='tc']"))
        for ($c = 0; $c -lt $Rows[$r].Count; $c++) {
            Set-CellText -CellNode $cells[$c] -Text ([string]$Rows[$r][$c])
        }
    }
}

if (-not $SkipBackup) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backupPath = Join-Path (Split-Path -Parent $targetPath) ("赵青松论文7.6_重生成图5-4后表格前备份_{0}.docx" -f $stamp)
    Copy-Item -LiteralPath $targetPath -Destination $backupPath -Force
    Write-Output "已创建备份: $backupPath"
}

$sections = @(
    @{
        Heading = '文件上传与媒体校验功能测试'
        Body = '该模块主要验证图片、视频等媒体资源在上传阶段的文件类型、空文件和 MIME 一致性校验是否能够正确生效，并进一步检查上传成功后的前端预览反馈是否闭环。'
        Caption = '表5—4 文件上传与媒体校验功能测试用例表'
        Desc = '表 5—4 的首组用例用于验证系统对非法上传类型的拦截能力。当用户误将文本文件提交至图片接口时，后端不会生成媒体地址，前端同步展示错误提示，从而避免异常文件进入帖子正文。'
        Rows = @(
            @('测试用例', '预计结果', '实际操作结果', '测试结果'),
            @('向图片上传接口提交 test.txt，文件类型为 text/plain', '识别为非法文件类型，拒绝上传', '返回“只能上传图片文件”提示，不写入媒体记录', '成功'),
            @('向视频上传接口提交空文件 empty.mp4', '识别为空文件，终止上传流程', '返回“文件不能为空”提示，不生成访问地址', '成功'),
            @('上传扩展名为 demo.jpg、MIME 为 image/png 的伪造图片', '校验扩展名与 MIME 类型不一致并拒绝上传', '返回“扩展名与MIME类型不匹配”提示', '成功'),
            @('上传合法 PNG 图片并插入发帖编辑器', '上传成功，返回媒体 URL，前端可正常预览', '页面显示上传成功预览图，可继续随帖子提交', '成功')
        )
    },
    @{
        Heading = '通知与消息处理功能测试'
        Body = '通知与消息模块主要验证批量已读、批量删除、未读统计和私信会话刷新等场景下的数据一致性，重点检查前端角标、列表状态与后端记录是否同步更新。'
        Caption = '表5—5 通知与消息处理功能测试用例表'
        Desc = '表 5—5 表明，通知中心在批量操作后能够及时刷新未读角标和消息列表；私信发送成功后，会话列表中的最后消息、未读数量与消息详情可以保持一致，说明前后端反馈链路完整。'
        Rows = @(
            @('测试用例', '预计结果', '实际操作结果', '测试结果'),
            @('通知中心勾选多条通知后执行批量已读', '更新所选通知为已读状态，未读角标同步减少', '通知列表状态立即变化，未读数量同步刷新', '成功'),
            @('传入空通知编号列表调用批量已读接口', '识别为空参数并拒绝处理', '返回“通知ID列表不能为空”提示', '成功'),
            @('选择两条历史通知执行批量删除', '删除指定通知记录，前端列表同步移除', '批量删除后列表中对应通知消失', '成功'),
            @('向已建立会话的用户发送一条私信', '消息成功落库，会话列表刷新最后一条消息和未读数', '接收方消息中心出现未读标记，打开会话后状态更新', '成功')
        )
    },
    @{
        Heading = '版主管理与举报处理功能测试'
        Body = '该模块重点验证版主分区权限边界、举报归属解析和处理结果写回等场景，确保版主只能在自己负责的板块内执行审核治理操作，不会越权处理其他分区内容。'
        Caption = '表5—6 版主管理与举报处理功能测试用例表'
        Desc = '表 5—6 的相关用例验证了系统在分区治理中的权限闭环。版主只能查看并处理其负责板块内的帖子、评论和举报记录；当举报目标为评论时，系统还能通过评论反向解析所属帖子板块，进而完成归属判断与权限校验。'
        Rows = @(
            @('测试用例', '预计结果', '实际操作结果', '测试结果'),
            @('版主账号仅被授权管理板块 3 和板块 8', '后台仅返回可管理板块集合，不能查看其他板块数据', '“我的板块”页面只展示板块 3、板块 8', '成功'),
            @('版主尝试处理非本人负责板块的帖子审核', '识别为越权操作并拒绝执行', '返回权限不足提示，帖子状态保持不变', '成功'),
            @('版主处理本板块评论举报并标记为已处理', '举报状态更新为已处理，并记录处理人和处理时间', '举报管理列表中状态已更新，详情中显示处理记录', '成功'),
            @('对评论举报目标进行归属解析，定位其所属帖子板块', '能够从评论反向解析所属帖子板块，用于权限判断', '系统正确识别举报归属板块并完成权限校验', '成功')
        )
    }
)

$targetXml = Read-DocXml -Path $targetPath
$sourceXml = Read-DocXml -Path $sourcePath

$targetBody = Get-Body -Xml $targetXml
$targetNodes = Get-BodyChildren -Xml $targetXml
$sourceNodes = Get-BodyChildren -Xml $sourceXml

$figureIndex = Find-ParagraphNodeIndex -Nodes $targetNodes -ExactText '图5—4 推荐界面'
$deleteStart = $figureIndex + 1
$conclusionIndex = Find-ParagraphNodeIndex -Nodes $targetNodes -ExactText '结  论'
for ($i = $conclusionIndex - 1; $i -ge $deleteStart; $i--) {
    $targetBody.RemoveChild($targetNodes[$i]) | Out-Null
}

$targetNodes = Get-BodyChildren -Xml $targetXml
$figureIndex = Find-ParagraphNodeIndex -Nodes $targetNodes -ExactText '图5—4 推荐界面'
$insertBeforeIndex = Find-ParagraphNodeIndex -Nodes $targetNodes -ExactText '结  论'
$insertBeforeNode = $targetNodes[$insertBeforeIndex]

$templateHeading = $sourceNodes[(Find-ParagraphNodeIndex -Nodes $sourceNodes -ExactText '推荐功能测试')]
$templateBody = $sourceNodes[(Find-ParagraphNodeIndex -Nodes $sourceNodes -ExactText '推荐模块主要验证登录态与冷启动场景下的结果可用性，以及详情页相关推荐是否能够根据当前帖子和用户上下文返回合理结果。')]
$templateTable = $sourceNodes[(Find-ParagraphNodeIndex -Nodes $sourceNodes -ExactText '表5—3 推荐功能测试用例表') - 1]
$templateCaption = $sourceNodes[(Find-ParagraphNodeIndex -Nodes $sourceNodes -ExactText '表5—3 推荐功能测试用例表')]
$templateDesc = $sourceNodes[(Find-ParagraphNodeIndex -Nodes $sourceNodes -ExactText '经联调验证，登录用户可获得由兴趣标签、协同过滤结果和全校热门内容组成的混合推荐；未登录用户则稳定回退到热门推荐列表。该结果与 `RecommendController` 和 `PostRecommendServiceImpl` 的业务逻辑一致。')]
$templateBlank = $sourceNodes[(Find-ParagraphNodeIndex -Nodes $sourceNodes -ExactText '图5—4 推荐界面') - 1]

foreach ($section in $sections) {
    $headingNode = $targetXml.ImportNode($templateHeading, $true)
    Set-ParagraphTextValue -ParagraphNode $headingNode -Text $section.Heading
    $targetBody.InsertBefore($headingNode, $insertBeforeNode) | Out-Null

    $bodyNode = $targetXml.ImportNode($templateBody, $true)
    Set-ParagraphTextValue -ParagraphNode $bodyNode -Text $section.Body
    $targetBody.InsertBefore($bodyNode, $insertBeforeNode) | Out-Null

    $tableNode = $targetXml.ImportNode($templateTable, $true)
    Set-TableData -TableNode $tableNode -Rows $section.Rows
    $targetBody.InsertBefore($tableNode, $insertBeforeNode) | Out-Null

    $captionNode = $targetXml.ImportNode($templateCaption, $true)
    Set-ParagraphTextValue -ParagraphNode $captionNode -Text $section.Caption
    $targetBody.InsertBefore($captionNode, $insertBeforeNode) | Out-Null

    $blankNode = $targetXml.ImportNode($templateBlank, $true)
    $targetBody.InsertBefore($blankNode, $insertBeforeNode) | Out-Null

    $descNode = $targetXml.ImportNode($templateDesc, $true)
    Set-ParagraphTextValue -ParagraphNode $descNode -Text $section.Desc
    $targetBody.InsertBefore($descNode, $insertBeforeNode) | Out-Null
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [System.IO.File]::Open($targetPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite)
try {
    $zip = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Update, $false)
    try {
        $entry = $zip.GetEntry('word/document.xml')
        if ($null -eq $entry) { throw '目标文档缺少 word/document.xml' }
        $entry.Delete()
        $newEntry = $zip.CreateEntry('word/document.xml')
        $writer = New-Object System.IO.StreamWriter($newEntry.Open(), [System.Text.UTF8Encoding]::new($false))
        try {
            $writer.Write($targetXml.OuterXml)
        }
        finally {
            $writer.Dispose()
        }
    }
    finally {
        $zip.Dispose()
    }
}
finally {
    $stream.Dispose()
}

Write-Output "已删除图5—4后旧测试区，并按标准模板重生成三张测试表: $targetPath"
