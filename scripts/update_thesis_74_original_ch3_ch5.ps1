param(
    [string]$TargetDoc = "docs\赵青松论文7.4.docx",
    [string]$DiagramDir = "tmp_thesis74\generated_diagrams",
    [switch]$SkipBackup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetPath = Join-Path $repoRoot $TargetDoc
$diagramPath = Join-Path $repoRoot $DiagramDir

if (-not (Test-Path $targetPath)) {
    throw "未找到目标论文文件: $targetPath"
}

if (-not (Test-Path $diagramPath)) {
    & (Join-Path $PSScriptRoot 'generate_thesis_diagrams.ps1') -OutputDir $DiagramDir | Out-Null
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

function Replace-SectionBody {
    param(
        $Document,
        [string]$StartHeading,
        [string]$EndHeading,
        [string]$BodyText
    )

    $startIndex = Find-ParagraphIndex -Document $Document -ExactText $StartHeading
    $endIndex = Find-ParagraphIndex -Document $Document -ExactText $EndHeading
    $range = $Document.Range(
        $Document.Paragraphs.Item($startIndex).Range.End,
        $Document.Paragraphs.Item($endIndex).Range.Start
    )
    $range.Text = Convert-ToWordText $BodyText
}

function Insert-PictureBeforeCaption {
    param(
        $Document,
        [string]$CaptionText,
        [string]$PicturePath,
        [int]$Width
    )

    if (-not (Test-Path $PicturePath)) {
        throw "未找到图片文件: $PicturePath"
    }

    $captionIndex = Find-ParagraphIndex -Document $Document -ExactText $CaptionText
    $pictureParagraphIndex = $captionIndex - 1
    if ($pictureParagraphIndex -lt 1) {
        throw "图题前不存在图片段落: $CaptionText"
    }

    $paragraph = $Document.Paragraphs.Item($pictureParagraphIndex)
    $range = $paragraph.Range.Duplicate
    if ($range.End -gt $range.Start) {
        $range.End = $range.End - 1
    }
    $range.Text = ''

    $shape = $Document.InlineShapes.AddPicture($PicturePath, $false, $true, $range)
    $shape.LockAspectRatio = $true
    $shape.Width = $Width
    $paragraph.Alignment = 1
}

$figureMappings = @(
    @{ Caption = '图3—1 系统功能结构图'; File = 'fig3-1-system-function.png'; Width = 460 },
    @{ Caption = '图3—2 用户实体属性图'; File = 'fig3-2-user.png'; Width = 430 },
    @{ Caption = '图3—3 标签实体属性图'; File = 'fig3-3-tag.png'; Width = 430 },
    @{ Caption = '图3—4 板块实体属性图'; File = 'fig3-4-section.png'; Width = 430 },
    @{ Caption = '图3—5 帖子实体属性图'; File = 'fig3-5-post.png'; Width = 440 },
    @{ Caption = '图3—6 帖子点赞实体属性图'; File = 'fig3-6-post-like.png'; Width = 420 },
    @{ Caption = '图3—7 帖子收藏实体属性图'; File = 'fig3-7-post-collect.png'; Width = 420 },
    @{ Caption = '图3—8 评论实体属性图'; File = 'fig3-8-comment.png'; Width = 430 },
    @{ Caption = '图3—9 浏览日志实体属性图'; File = 'fig3-9-view-log.png'; Width = 430 },
    @{ Caption = '图3—10 等级经验日志实体属性图'; File = 'fig3-10-level-log.png'; Width = 420 },
    @{ Caption = '图3—11 通知实体属性图'; File = 'fig3-11-notification.png'; Width = 430 },
    @{ Caption = '图3—12 私信实体属性图'; File = 'fig3-12-message.png'; Width = 430 },
    @{ Caption = '图3—13 举报实体属性图'; File = 'fig3-13-report.png'; Width = 430 },
    @{ Caption = '图3—14 关注关系实体属性图'; File = 'fig3-14-follow.png'; Width = 420 },
    @{ Caption = '图3—15 版主申请实体属性图'; File = 'fig3-15-moderator-application.png'; Width = 430 },
    @{ Caption = '图3—16 评论点赞实体属性图'; File = 'fig3-16-comment-like.png'; Width = 420 },
    @{ Caption = '图3—17 热度快照实体属性图'; File = 'fig3-17-heat-snapshot.png'; Width = 420 },
    @{ Caption = '图3—18 趋势统计实体属性图'; File = 'fig3-18-trend-stat.png'; Width = 430 },
    @{ Caption = '图3—19 系统总体E-R图'; File = 'fig3-19-overall-er.png'; Width = 500 }
)

$chapter51Body = @"
本章测试直接围绕 Campus Pulse 项目的真实功能展开，重点验证系统在账号认证、内容发布、板块治理、消息处理、文件上传和后台权限控制等关键链路上的可用性与正确性。测试不再使用与本系统无关的示例数据，而是依据当前项目中的控制器测试、服务层测试和前后端联调页面整理测试数据。
本次测试共选取 6 个与系统核心业务直接相关的模块，包括认证与会话、帖子发布与审核、版主申请与后台审核、通知批量处理、文件上传校验、版主分区权限与举报归属。测试方式分为两类：一类为自动化测试，用于验证参数校验、接口返回和权限边界；另一类为页面联调验证，用于核对前端按钮、状态标签、弹窗提示和后台列表是否与业务逻辑一致。
本地环境下共执行 6 个测试类、17 项自动化用例，测试结果全部通过。由此可进一步说明，系统在输入校验、批量操作、上传限制、分区权限和后台审核流转等方面具有较好的稳定性，能够满足校园内容社区的日常运行需求。
"@

$chapter52Body = @"
测试环境配置如下。
（1）操作系统：Windows 11。
（2）JDK：21。
（3）Maven：3.9.11。
（4）后端框架：Spring Boot 3.5.8。
（5）数据库：MySQL 8.0。
（6）缓存：Redis 6.x。
（7）前端框架：Vue 3.5 + TypeScript 5.9 + Vite 7。
（8）自动化测试工具：JUnit 5、Mockito、MockMvc。
（9）接口联调工具：Apifox / Postman。
（10）浏览器：Chrome 与 Edge。
"@

$chapter53Body = @"
本节针对系统中最关键的 6 个业务模块补充测试数据，并给出论文配图时可直接截取的页面位置，便于后续自行补齐截图。
（1）认证与会话模块测试
A1：测试数据为登录账号 202250734、密码 Campus@123、设备标识 device-win11-001。预期结果为登录成功，系统生成访问令牌和刷新令牌，前端进入首页登录态。实际结果为联调通过，可在首页看到已登录头像、消息角标和“发布动态”按钮。
A2：测试数据为注册邮箱 not-an-email、验证码 123456、用户名 user_test。预期结果为系统拦截非法邮箱格式，不进入注册服务。实际结果为自动化测试返回 code=4003，提示信息包含 email，结果通过。
A3：测试数据为刷新令牌 refreshToken 为空字符串。预期结果为系统拦截空令牌，不执行刷新逻辑。实际结果为自动化测试返回 code=4003，提示信息包含 refreshToken，结果通过。
截图位置建议：登录成功后，在首页顶部截取右上角用户头像、消息通知角标以及“发布动态”按钮所在区域，可作为图5—1；若需要补充参数校验截图，可在注册页输入非法邮箱后截取邮箱输入框附近的报错提示。
（2）帖子发布与审核模块测试
P1：测试数据为板块编号 1、标题“这是一个合格的帖子标题”、正文“这里是一段超过三十个字符的帖子正文内容，用于测试接口正常调用逻辑。”、标签“测试,校验”。预期结果为帖子创建成功并进入待审核链路。实际结果为自动化测试通过，创建帖子服务被成功调用。
P2：测试数据为板块编号 1、标题 abc、正文“内容太短”。预期结果为系统拦截标题和正文长度错误，不允许提交。实际结果为自动化测试返回 code=4003，提示同时包含 title 与 content 校验信息，结果通过。
P3：测试数据为草稿标题“待整理的课程项目总结”、板块编号 2、正文“先记录正文结构，稍后继续补充实验数据与结论”。预期结果为保存后可在“我的草稿”中继续编辑。实际结果为联调通过，草稿会显示在个人中心的“我的草稿”标签页。
P4：测试数据为后台对待审核帖子执行“通过审核”或“打回修改”，打回原因为“请补充实验结果与数据来源”。预期结果为帖子状态分别更新为“已发布”或“已打回”，作者收到站内通知。实际结果为联调通过，后台内容管理列表可见对应状态标签。
截图位置建议：在发帖弹窗中截取“选择板块、标题输入框、上传图片、上传视频、保存草稿”区域；后台截图则进入“内容管理”页面，截取帖子状态列中“待审核”或“已打回”的标签，以及“打回帖子”对话框，可作为图5—2。
（3）版主申请与后台审核模块测试
M1：测试数据为板块编号 1、申请理由 too short。预期结果为系统拦截过短申请理由。实际结果为自动化测试返回 code=4003，提示申请理由长度需在 10 到 500 个字符之间，结果通过。
M2：测试数据为板块编号 2、申请理由“我在学习交流板块持续发帖和答疑，希望参与内容审核与秩序维护工作。”。预期结果为创建待审核申请记录。实际结果为联调通过，申请记录可在个人申请状态中查看。
M3：测试数据为普通用户直接访问管理员版主申请接口。预期结果为系统拒绝越权访问。实际结果为自动化测试返回 code=3003，提示“仅管理员可执行该操作”，结果通过。
M4：测试数据为管理员审批通过申请并填写审核备注“欢迎加入版主团队”。预期结果为系统保存审核备注并写入审批结果。实际结果为自动化测试返回 code=2000，审核备注被正确读取，结果通过。
M5：测试数据为管理员拒绝申请并填写审核备注“当前活跃度和管理经验说明不足”。预期结果为系统保存拒绝意见并通知申请人。实际结果为自动化测试返回 code=2000，审核备注被正确读取，结果通过。
截图位置建议：在板块页点击“申请版主”按钮后，截取标题为“版主申请中心”的弹窗；管理员后台则进入侧边栏“版主申请管理”页面，截取申请列表、申请理由和审核操作按钮区域，可作为图5—3。
（4）通知批量处理模块测试
N1：测试数据为通知编号列表 [1,2,2,3]，执行批量已读。预期结果为系统调用批量已读服务，并返回处理成功。实际结果为自动化测试返回 code=2000，服务层收到完整编号列表，结果通过。
N2：测试数据为通知编号列表为空数组，执行批量已读。预期结果为系统拦截空编号列表。实际结果为自动化测试返回 code=4003，提示“通知ID列表不能为空”，结果通过。
N3：测试数据为通知编号列表 [8,9]，执行批量删除。预期结果为系统调用批量删除服务，并返回处理成功。实际结果为自动化测试返回 code=2000，服务层收到正确编号列表，结果通过。
截图位置建议：进入个人中心“消息通知”标签页，勾选多条通知后截取通知列表顶部右侧的“全选筛选结果”“全部已读”“批量已读”“批量删除”按钮区域，可作为图5—4。
（5）文件上传校验模块测试
U1：测试数据为上传文件 test.txt，文件类型 text/plain，上传到图片接口。预期结果为系统拒绝非图片类型文件。实际结果为自动化测试返回 code=5006，提示“只能上传图片文件”，结果通过。
U2：测试数据为上传文件 empty.mp4，文件内容为空，上传到视频接口。预期结果为系统拦截空文件。实际结果为自动化测试返回 code=5004，提示“文件不能为空”，结果通过。
U3：测试数据为文件名 demo.jpg、文件类型 image/png、实际内容为伪造图片数据，上传到图片接口。预期结果为系统识别扩展名与 MIME 类型不匹配并拒绝上传。实际结果为自动化测试返回 code=5006，提示“扩展名与MIME类型不匹配”，结果通过。
截图位置建议：点击首页或个人中心中的“发布动态”打开发帖弹窗，截取正文编辑器上方“上传图片”“上传视频”按钮所在位置；如果需要错误提示图，可故意选择错误文件后截取页面右上角弹出的上传警告信息，可作为图5—5。
（6）版主分区权限与举报归属模块测试
S1：测试数据为版主账号已通过板块 3 和板块 8 的审核。预期结果为系统返回该账号可管理的板块集合，并识别其具备版主管理能力。实际结果为自动化测试正确返回板块集合 {3,8}，结果通过。
S2：测试数据为版主尝试处理本板块帖子、本板块评论、本板块举报，以及其他板块的帖子、评论、举报。预期结果为系统仅允许处理自己负责板块内的数据，不允许越权管理其他板块内容。实际结果为自动化测试对授权与越权两类场景均判断正确，结果通过。
S3：测试数据为举报目标为评论 comment-9，对应帖子所属板块编号为 12。预期结果为系统能够从评论反向解析到所属帖子板块，用于判断举报归属。实际结果为自动化测试正确解析出板块编号 12，结果通过。
截图位置建议：使用版主账号登录后台，先在侧边栏截取“我的板块”页面中已授权板块卡片及“内容管理”“举报管理”按钮；再进入“内容管理”页面，截取页面说明“按当前账号可管理的板块范围加载内容，管理员可查看全部”以及列表数据区域，可作为图5—6。
综合以上 6 个模块，本次共执行 17 项自动化测试，全部通过。测试结果表明，系统在认证校验、内容发布、版主管理、通知批处理、上传约束和分区权限控制等关键业务上运行稳定，能够满足校园内容社区平台的实际使用要求。
"@

$existingWordPids = @()
try {
    $existingWordPids = @(Get-Process WINWORD -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
} catch {
    $existingWordPids = @()
}

if (-not $SkipBackup) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backupPath = Join-Path (Split-Path -Parent $targetPath) ("赵青松论文7.4_修改前备份_{0}.docx" -f $stamp)
    Copy-Item -LiteralPath $targetPath -Destination $backupPath -Force
    Write-Output "已创建备份: $backupPath"
}

$word = $null
$document = $null

try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0

    $document = $word.Documents.Open($targetPath)
    $document.TrackRevisions = $false

    foreach ($figure in $figureMappings) {
        Insert-PictureBeforeCaption -Document $document `
            -CaptionText $figure.Caption `
            -PicturePath (Join-Path $diagramPath $figure.File) `
            -Width $figure.Width
    }
    $document.Save()

    Replace-SectionBody -Document $document -StartHeading '5.1 测试目标与测试方法' -EndHeading '5.2 测试环境' -BodyText $chapter51Body
    Replace-SectionBody -Document $document -StartHeading '5.2 测试环境' -EndHeading '5.3 测试内容' -BodyText $chapter52Body
    Replace-SectionBody -Document $document -StartHeading '5.3 测试内容' -EndHeading '结  论' -BodyText $chapter53Body
    $document.Save()

    $document.Close([ref]0)
    $document = $null
    $word.Quit()
    $word = $null

    Write-Output "原始论文已更新: $targetPath"
} finally {
    if ($document -ne $null) {
        $document.Close([ref]0)
    }
    if ($word -ne $null) {
        $word.Quit()
    }

    try {
        $currentWordPids = @(Get-Process WINWORD -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
        $orphanPids = @($currentWordPids | Where-Object { $_ -notin $existingWordPids })
        if ($orphanPids.Count -gt 0) {
            Stop-Process -Id $orphanPids -Force -ErrorAction SilentlyContinue
        }
    } catch {
        # 忽略清理失败，避免影响最终结果
    }
}
