param(
    [string]$TargetDoc = "docs\赵青松论文7.6.docx",
    [switch]$SkipBackup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetPath = Join-Path $repoRoot $TargetDoc

if (-not (Test-Path $targetPath)) {
    throw "未找到目标论文文件: $targetPath"
}

function Convert-ToWordText {
    param([string]$Text)
    $normalized = $Text.Trim()
    $normalized = $normalized -replace "`r?`n", "`r"
    return $normalized + "`r"
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

function Find-ParagraphIndexOrNull {
    param(
        $Document,
        [string]$ExactText
    )

    try {
        return Find-ParagraphIndex -Document $Document -ExactText $ExactText
    } catch {
        return $null
    }
}

function Replace-ParagraphText {
    param(
        $Document,
        [string]$AnchorHeading,
        [int]$Offset,
        [string]$Text
    )

    $index = Find-ParagraphIndex -Document $Document -ExactText $AnchorHeading
    $target = $Document.Paragraphs.Item($index + $Offset)
    $target.Range.Text = Convert-ToWordText $Text
}

function Type-Paragraph {
    param(
        $Selection,
        [string]$Text,
        $Style = $null
    )

    if ($null -ne $Style) {
        try {
            $Selection.Style = $Style
        } catch {
        }
    }

    $Selection.TypeText($Text)
    $Selection.TypeParagraph()
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

            $textNodes = $xml.SelectNodes('//w:t', $ns)
            foreach ($node in $textNodes) {
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

if (-not $SkipBackup) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backupPath = Join-Path (Split-Path -Parent $targetPath) ("赵青松论文7.6_修改前备份_{0}.docx" -f $stamp)
    Copy-Item -LiteralPath $targetPath -Destination $backupPath -Force
    Write-Output "已创建备份: $backupPath"
}

$introText = '本节通过典型场景验证 Campus Pulse 的关键业务模块，测试重点放在参数合法性、权限约束、数据一致性和前后端反馈是否闭环。'
$authDescText = '认证与会话模块是系统安全的第一道防线，主要验证账号密码登录、邮箱验证码登录、刷新令牌、设备绑定和异常输入拦截等场景是否能够得到正确处理。'

$canvasTextMap = @{
    'id' = '编号'
    'user_id' = '用户编号'
    'section_id' = '板块编号'
    'title' = '标题'
    'content' = '内容'
    'location_name' = '位置名称'
    'summary' = '摘要'
    'cover_image' = '封面图'
    'images' = '图片集'
    'tags' = '标签集'
    'is_anonymous' = '匿名标记'
    'status' = '状态'
    'audit_status' = '审核状态'
    'global_pin' = '全站置顶'
    'category_pin' = '板块置顶'
    'view_count' = '浏览量'
    'like_count' = '点赞量'
    'collect_count' = '收藏量'
    'comment_count' = '评论量'
    'heat_score' = '热度值'
    'sentiment_score' = '情感值'
    'post_id' = '帖子编号'
    'comment_id' = '评论编号'
    'device' = '设备标识'
    'create_time' = '创建时间'
    'created_at' = '创建时间'
    'exp_delta' = '经验变动值'
    'reason' = '原因'
    'type' = '类型'
    'conversation_id' = '会话编号'
    'sender_id' = '发送方编号'
    'receiver_id' = '接收方编号'
    'target_type' = '目标类型'
    'target_id' = '目标编号'
    'reporter_id' = '举报人编号'
    'reviewed_by' = '审核人编号'
    'snap_time' = '快照时间'
    'is_read' = '已读标记'
    'section_name' = '板块名称'
    'post_title' = '帖子标题'
    'file_name' = '文件名称'
    'mime_type' = '媒体类型'
    'username' = '用户名'
    'email' = '邮箱'
    'password' = '密码'
    'nickname' = '昵称'
    'avatar' = '头像'
    'bio' = '个人简介'
    'gender' = '性别'
    'school' = '学校'
    'major' = '专业'
    'enrollment_year' = '入学年份'
    'level' = '等级'
    'experience' = '经验值'
    'interest_tags' = '兴趣标签'
    'role' = '角色'
    'contribution_val' = '贡献值'
    'active_region' = '活跃地点'
    'preferred_cate_json' = '偏好分类'
    'total_posts' = '总发帖数'
    'total_likes_received' = '获赞总数'
    'last_active_time' = '最后活跃时间'
    'github_id' = 'GitHub编号'
    'github_login' = 'GitHub账号'
    'two_factor_enabled' = '二步验证开关'
    'two_factor_secret' = '二步验证密钥'
    'email_notify_enabled' = '邮件通知开关'
    'profile_card_theme' = '资料卡主题'
    'quick_card_theme' = '头像卡主题'
    'profile_card_bg_url' = '资料卡背景图'
    'quick_card_bg_url' = '头像卡背景图'
    'name' = '名称'
    'heat' = '热度值'
    'tag_id' = '标签编号'
    'score' = '权重值'
    'parent_id' = '父级编号'
    'reply_to_user_id' = '回复对象编号'
    'reply_user_id' = '回复用户编号'
    'followee_id' = '被关注人编号'
    'follower_id' = '关注人编号'
    'following_id' = '关注对象编号'
    'related_id' = '关联对象编号'
    'related_user_id' = '关联用户编号'
    'remark' = '备注'
    'review_note' = '审核备注'
    'reviewed_at' = '审核时间'
    'reject_reason' = '驳回原因'
    'creator_id' = '创建人编号'
    'deleted' = '删除标记'
    'description' = '描述'
    'details' = '详情'
    'icon' = '图标'
    'code' = '编码'
    'sort_order' = '排序值'
    'used_by_user_id' = '使用者编号'
    'used_count' = '已使用次数'
    'max_uses' = '最大使用次数'
    'version' = '版本号'
    'stat_date' = '统计日期'
    'stat_key' = '统计键'
    'stat_type' = '统计类型'
    'pin_order' = '置顶排序'
    'pin_expire_at' = '置顶失效时间'
    'post_count' = '帖子数量'
    'last_reply_at' = '最后回复时间'
    'last_activity_at' = '最后活跃时间'
    'timestamp' = '时间戳'
    'update_time' = '更新时间'
    'updated_at' = '更新时间'
    'expire_time' = '过期时间'
    'Varchar' = '字符串'
    'Text' = '长文本'
    'Int' = '整型'
    'Tinyint' = '短整型'
    'Bigint' = '长整型'
    'Datetime' = '日期时间'
    'Date' = '日期'
    'Decimal' = '小数'
    'Double' = '双精度数值'
    'Json' = 'JSON'
}

$word = $null
$document = $null
$newWordPids = @()

try {
    $existingWordPids = @(Get-Process WINWORD -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)

    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $document = $word.Documents.Open($targetPath)
    $document.TrackRevisions = $false

    Replace-ParagraphText -Document $document -AnchorHeading '5.3 测试内容' -Offset 1 -Text $introText
    Replace-ParagraphText -Document $document -AnchorHeading '认证与会话功能测试' -Offset 1 -Text $authDescText

    $sectionHeadingStyle = $document.Paragraphs.Item((Find-ParagraphIndex -Document $document -ExactText '推荐功能测试')).Range.Style
    $bodyStyle = $document.Paragraphs.Item((Find-ParagraphIndex -Document $document -ExactText '推荐功能测试') + 1).Range.Style
    $captionStyle = $document.Paragraphs.Item((Find-ParagraphIndex -Document $document -ExactText '图5—4 推荐界面')).Range.Style

    $conclusionIndex = Find-ParagraphIndex -Document $document -ExactText '结  论'
    $existingGeneratedSectionIndex = Find-ParagraphIndexOrNull -Document $document -ExactText '文件上传与媒体校验功能测试'
    $insertStart = $document.Paragraphs.Item($conclusionIndex).Range.Start
    if ($null -ne $existingGeneratedSectionIndex) {
        $insertStart = $document.Paragraphs.Item($existingGeneratedSectionIndex).Range.Start
        $cleanupRange = $document.Range(
            $insertStart,
            $document.Paragraphs.Item($conclusionIndex).Range.Start
        )
        $cleanupRange.Text = ''
    }

    $selection = $word.Selection
    $selection.SetRange($insertStart, $insertStart)
    $selection.Collapse(1)

    Type-Paragraph -Selection $selection -Text '文件上传与媒体校验功能测试' -Style $sectionHeadingStyle
    Type-Paragraph -Selection $selection -Text '该模块重点验证图片与视频资源在上传阶段的文件类型、空文件和 MIME 一致性校验是否能够正确生效，同时检查上传成功后的前端预览与错误提示是否闭环。测试结果表明，系统能够拦截 test.txt、empty.mp4 以及扩展名与 MIME 类型不匹配的伪造文件，并在上传合法图片后正常返回媒体地址。' -Style $bodyStyle
    Type-Paragraph -Selection $selection -Text '截图建议：点击首页或个人中心中的“发布动态”打开发帖弹窗，截取正文编辑器上方“上传图片”“上传视频”按钮及媒体预览区域；若需体现校验结果，可在选择错误文件后截取页面右上角的上传警告提示，可作为图5—5。' -Style $bodyStyle
    Type-Paragraph -Selection $selection -Text '图5—5 文件上传与媒体校验页面' -Style $captionStyle

    Type-Paragraph -Selection $selection -Text '版主管理与举报处理功能测试' -Style $sectionHeadingStyle
    Type-Paragraph -Selection $selection -Text '该模块重点验证版主分区权限边界、举报归属解析和处理结果写回等场景，确保版主只能在自己负责的板块内执行审核治理操作，不会越权处理其他分区内容。测试结果表明，系统能够正确返回版主可管理板块集合，并在举报目标为评论时通过所属帖子反向解析板块归属，完成权限校验。' -Style $bodyStyle
    Type-Paragraph -Selection $selection -Text '截图建议：使用版主账号登录后台，先在侧边栏截取“我的板块”页面中已授权板块卡片及“内容管理”“举报管理”入口；再进入“举报管理”或“内容管理”页面，截取权限范围说明、状态标签以及处理按钮区域，可作为图5—6。' -Style $bodyStyle
    Type-Paragraph -Selection $selection -Text '图5—6 版主管理与举报处理页面' -Style $captionStyle
    Type-Paragraph -Selection $selection -Text '综合以上六类模块测试结果可以看出，Campus Pulse 在认证校验、内容发布、推荐展示、上传约束、通知处理和分区治理等关键链路上均能得到正确反馈，系统整体运行稳定。' -Style $bodyStyle

    $document.Save()
    $document.Close([ref]0)
    $document = $null
    $word.Quit()
    $word = $null

    $replaceCounter = 0
    Replace-CanvasTextInOpenXml -DocxPath $targetPath -TextMap $canvasTextMap -Counter ([ref]$replaceCounter)

    $currentWordPids = @(Get-Process WINWORD -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
    $newWordPids = @($currentWordPids | Where-Object { $_ -notin $existingWordPids })
    Write-Output ("论文已更新: {0}" -f $targetPath)
    Write-Output ("画布文字替换数量: {0}" -f $replaceCounter)
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
