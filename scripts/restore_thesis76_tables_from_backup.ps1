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

if (-not (Test-Path $targetPath)) {
    throw "未找到目标文档: $targetPath"
}
if (-not (Test-Path $sourcePath)) {
    throw "未找到源文档: $sourcePath"
}

function Normalize-Text {
    param([string]$Text)
    if ($null -eq $Text) {
        return ''
    }
    return ($Text -replace '\s+', '')
}

function Open-DocumentXml {
    param([string]$DocxPath)

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $fs = [System.IO.File]::Open($DocxPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Read, $false)
        try {
            $entry = $zip.GetEntry('word/document.xml')
            if ($null -eq $entry) {
                throw "word/document.xml 不存在: $DocxPath"
            }
            $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
            try {
                [xml]$xml = $reader.ReadToEnd()
                return $xml
            } finally {
                $reader.Dispose()
            }
        } finally {
            $zip.Dispose()
        }
    } finally {
        $fs.Dispose()
    }
}

function Get-BodyChildren {
    param(
        [xml]$Xml
    )

    $body = $Xml.SelectSingleNode("/*[local-name()='document']/*[local-name()='body']")
    return @($body.ChildNodes | Where-Object { $_.LocalName -in @('p', 'tbl') })
}

function Get-ParagraphText {
    param(
        [System.Xml.XmlNode]$Node
    )

    if ($Node.LocalName -ne 'p') {
        return ''
    }

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
        if ($Nodes[$i].LocalName -ne 'p') {
            continue
        }
        $current = Normalize-Text (Get-ParagraphText -Node $Nodes[$i])
        if ($current -eq $target) {
            return $i
        }
    }

    throw "未找到段落: $ExactText"
}

if (-not $SkipBackup) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backupPath = Join-Path (Split-Path -Parent $targetPath) ("赵青松论文7.6_表格恢复前备份_{0}.docx" -f $stamp)
    Copy-Item -LiteralPath $targetPath -Destination $backupPath -Force
    Write-Output "已创建备份: $backupPath"
}

$sourceXml = Open-DocumentXml -DocxPath $sourcePath
$sourceNodes = Get-BodyChildren -Xml $sourceXml
$sourceStart = Find-ParagraphNodeIndex -Nodes $sourceNodes -ExactText '文件上传与媒体校验功能测试'
$sourceEnd = Find-ParagraphNodeIndex -Nodes $sourceNodes -ExactText '结  论'
$sourceSlice = @()
for ($i = $sourceStart; $i -lt $sourceEnd; $i++) {
    $sourceSlice += $sourceNodes[$i]
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$targetStream = [System.IO.File]::Open($targetPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite)
try {
    $targetZip = New-Object System.IO.Compression.ZipArchive($targetStream, [System.IO.Compression.ZipArchiveMode]::Update, $false)
    try {
        $entry = $targetZip.GetEntry('word/document.xml')
        if ($null -eq $entry) {
            throw '目标文档缺少 word/document.xml'
        }

        $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
        try {
            [xml]$targetXml = $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }

        $targetBody = $targetXml.SelectSingleNode("/*[local-name()='document']/*[local-name()='body']")
        $targetNodes = Get-BodyChildren -Xml $targetXml
        $targetStart = Find-ParagraphNodeIndex -Nodes $targetNodes -ExactText '文件上传与媒体校验功能测试'
        $targetEnd = Find-ParagraphNodeIndex -Nodes $targetNodes -ExactText '结  论'

        for ($i = $targetEnd - 1; $i -ge $targetStart; $i--) {
            $targetBody.RemoveChild($targetNodes[$i]) | Out-Null
        }

        $targetNodes = Get-BodyChildren -Xml $targetXml
        $conclusionIndex = Find-ParagraphNodeIndex -Nodes $targetNodes -ExactText '结  论'
        $conclusionNode = $targetNodes[$conclusionIndex]

        foreach ($node in $sourceSlice) {
            $imported = $targetXml.ImportNode($node, $true)
            $targetBody.InsertBefore($imported, $conclusionNode) | Out-Null
        }

        $entry.Delete()
        $newEntry = $targetZip.CreateEntry('word/document.xml')
        $writer = New-Object System.IO.StreamWriter($newEntry.Open(), [System.Text.UTF8Encoding]::new($false))
        try {
            $writer.Write($targetXml.OuterXml)
        } finally {
            $writer.Dispose()
        }

        Write-Output "已恢复图5—4之后测试表格区域为标准格式: $targetPath"
        Write-Output ("恢复节点数量: {0}" -f $sourceSlice.Count)
    } finally {
        $targetZip.Dispose()
    }
} finally {
    $targetStream.Dispose()
}
