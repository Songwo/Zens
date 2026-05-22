$ErrorActionPreference = 'Stop'

param(
    [Parameter(Mandatory = $true)]
    [string]$DocxPath,

    [Parameter(Mandatory = $true)]
    [string]$ExtractedDir,

    [Parameter(Mandatory = $true)]
    [string]$ImagePath
)

function Get-NextImageName {
    param([string]$MediaDir)

    $numbers = Get-ChildItem -LiteralPath $MediaDir -Filter 'image*.png' |
        ForEach-Object {
            if ($_.BaseName -match '^image(\d+)$') {
                [int]$matches[1]
            }
        }

    $maxNumber = ($numbers | Measure-Object -Maximum).Maximum
    if (-not $maxNumber) {
        $maxNumber = 0
    }

    return "image$($maxNumber + 1).png"
}

function Get-NextRelId {
    param([xml]$RelationshipsXml)

    $ns = New-Object System.Xml.XmlNamespaceManager($RelationshipsXml.NameTable)
    $ns.AddNamespace('pkg', $RelationshipsXml.DocumentElement.NamespaceURI)

    $numbers = $RelationshipsXml.SelectNodes('//pkg:Relationship', $ns) |
        ForEach-Object {
            if ($_.Id -match '^rId(\d+)$') {
                [int]$matches[1]
            }
        }

    $maxNumber = ($numbers | Measure-Object -Maximum).Maximum
    if (-not $maxNumber) {
        $maxNumber = 0
    }

    return "rId$($maxNumber + 1)"
}

$wordDir = Join-Path $ExtractedDir 'word'
$mediaDir = Join-Path $wordDir 'media'
$relsPath = Join-Path $wordDir '_rels\document.xml.rels'
$documentXmlPath = Join-Path $wordDir 'document.xml'

if (-not (Test-Path -LiteralPath $DocxPath)) {
    throw "DOCX not found: $DocxPath"
}
if (-not (Test-Path -LiteralPath $ExtractedDir)) {
    throw "Extracted directory not found: $ExtractedDir"
}
if (-not (Test-Path -LiteralPath $ImagePath)) {
    throw "Image not found: $ImagePath"
}

[xml]$relsXml = Get-Content -LiteralPath $relsPath -Raw
$newRelId = Get-NextRelId -RelationshipsXml $relsXml
$newImageName = Get-NextImageName -MediaDir $mediaDir
$newImagePath = Join-Path $mediaDir $newImageName
Copy-Item -LiteralPath $ImagePath -Destination $newImagePath -Force

$relationship = $relsXml.CreateElement('Relationship', $relsXml.DocumentElement.NamespaceURI)
$null = $relationship.SetAttribute('Id', $newRelId)
$null = $relationship.SetAttribute('Type', 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/image')
$null = $relationship.SetAttribute('Target', "media/$newImageName")
$null = $relsXml.DocumentElement.AppendChild($relationship)
$relsXml.Save($relsPath)

$documentXml = Get-Content -LiteralPath $documentXmlPath -Raw
$captionToken = '系统核心业务E-R图'
$captionIndex = $documentXml.IndexOf($captionToken)
if ($captionIndex -lt 0) {
    throw 'Could not locate figure caption text for 图3—19.'
}

$captionParagraphStart = $documentXml.LastIndexOf('<w:p', $captionIndex)
$figureParagraphStart = $documentXml.LastIndexOf('<w:p', $captionParagraphStart - 1)
$figureParagraphEnd = $documentXml.IndexOf('</w:p>', $figureParagraphStart)
if ($figureParagraphStart -lt 0 -or $figureParagraphEnd -lt 0) {
    throw 'Could not isolate the figure paragraph before the caption.'
}

$figureParagraphLength = $figureParagraphEnd + 6 - $figureParagraphStart
$figureParagraph = $documentXml.Substring($figureParagraphStart, $figureParagraphLength)
if ($figureParagraph -notmatch '画布 390') {
    throw 'The located paragraph does not match the expected original E-R figure block.'
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
    <w:drawing>
      <wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        <wp:extent cx="5450000" cy="2903000" />
        <wp:effectExtent l="0" t="0" r="0" b="0" />
        <wp:docPr id="5000" name="系统核心业务E-R图" />
        <wp:cNvGraphicFramePr>
          <a:graphicFrameLocks noChangeAspect="1" />
        </wp:cNvGraphicFramePr>
        <a:graphic>
          <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
            <pic:pic>
              <pic:nvPicPr>
                <pic:cNvPr id="5001" name="system-core-business-er-final.png" />
                <pic:cNvPicPr />
              </pic:nvPicPr>
              <pic:blipFill>
                <a:blip r:embed="$newRelId" />
                <a:stretch>
                  <a:fillRect />
                </a:stretch>
              </pic:blipFill>
              <pic:spPr>
                <a:xfrm>
                  <a:off x="0" y="0" />
                  <a:ext cx="5450000" cy="2903000" />
                </a:xfrm>
                <a:prstGeom prst="rect">
                  <a:avLst />
                </a:prstGeom>
              </pic:spPr>
            </pic:pic>
          </a:graphicData>
        </a:graphic>
      </wp:inline>
    </w:drawing>
  </w:r>
</w:p>
"@

$updatedDocumentXml =
    $documentXml.Substring(0, $figureParagraphStart) +
    $replacementParagraph +
    $documentXml.Substring($figureParagraphStart + $figureParagraphLength)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($documentXmlPath, $updatedDocumentXml, $utf8NoBom)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$tempDocxPath = Join-Path ([System.IO.Path]::GetDirectoryName($DocxPath)) ([System.IO.Path]::GetFileNameWithoutExtension($DocxPath) + '.tmp.docx')
if (Test-Path -LiteralPath $tempDocxPath) {
    Remove-Item -LiteralPath $tempDocxPath -Force
}

[System.IO.Compression.ZipFile]::CreateFromDirectory($ExtractedDir, $tempDocxPath)
Copy-Item -LiteralPath $tempDocxPath -Destination $DocxPath -Force
Remove-Item -LiteralPath $tempDocxPath -Force

Write-Output "UPDATED_DOCX=$DocxPath"
Write-Output "NEW_REL_ID=$newRelId"
Write-Output "NEW_IMAGE=$newImageName"

