param(
    [string]$TargetDoc = "docs\赵青松论文7.6.docx",
    [switch]$SkipBackup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetPath = Join-Path $repoRoot $TargetDoc

if (-not (Test-Path $targetPath)) {
    throw "未找到目标文档: $targetPath"
}

function Normalize-Text {
    param([string]$Text)
    if ($null -eq $Text) { return '' }
    return ($Text -replace '\s+', '')
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

function Get-BodyChildren {
    param([xml]$Xml)
    $body = $Xml.SelectSingleNode("/*[local-name()='document']/*[local-name()='body']")
    return @($body.ChildNodes | Where-Object { $_.LocalName -in @('p','tbl') })
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

if (-not $SkipBackup) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backupPath = Join-Path (Split-Path -Parent $targetPath) ("赵青松论文7.6_表头统一前备份_{0}.docx" -f $stamp)
    Copy-Item -LiteralPath $targetPath -Destination $backupPath -Force
    Write-Output "已创建备份: $backupPath"
}

$headers = @('测试用例', '预计结果', '实际操作结果', '测试结果')

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [System.IO.File]::Open($targetPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite)
try {
    $zip = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Update, $false)
    try {
        $entry = $zip.GetEntry('word/document.xml')
        if ($null -eq $entry) { throw '目标文档缺少 word/document.xml' }

        $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
        try {
            [xml]$xml = $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }

        $nodes = Get-BodyChildren -Xml $xml
        $start = Find-ParagraphNodeIndex -Nodes $nodes -ExactText '认证与会话功能测试'
        $end = Find-ParagraphNodeIndex -Nodes $nodes -ExactText '结  论'

        for ($i = $start; $i -lt $end; $i++) {
            if ($nodes[$i].LocalName -ne 'tbl') { continue }
            $rows = @($nodes[$i].SelectNodes("./*[local-name()='tr']"))
            if ($rows.Count -eq 0) { continue }
            $cells = @($rows[0].SelectNodes("./*[local-name()='tc']"))
            if ($cells.Count -lt 4) { continue }
            for ($c = 0; $c -lt 4; $c++) {
                Set-CellText -CellNode $cells[$c] -Text $headers[$c]
            }
        }

        $entry.Delete()
        $newEntry = $zip.CreateEntry('word/document.xml')
        $writer = New-Object System.IO.StreamWriter($newEntry.Open(), [System.Text.UTF8Encoding]::new($false))
        try {
            $writer.Write($xml.OuterXml)
        } finally {
            $writer.Dispose()
        }

        Write-Output "第五章测试表表头已统一: $targetPath"
    } finally {
        $zip.Dispose()
    }
} finally {
    $stream.Dispose()
}
