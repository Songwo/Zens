param(
    [string]$TargetDoc = "docs\赵青松论文7.4_按要求改写.docx"
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
    for ($i = 1; $i -le $Document.Paragraphs.Count; $i++) {
        if ((Get-CleanParagraphText $Document.Paragraphs.Item($i)) -eq $ExactText) {
            return $i
        }
    }
    throw "未找到段落: $ExactText"
}

function Get-SectionBodyBounds {
    param(
        $Document,
        [string]$StartHeading,
        [string]$EndHeading
    )
    $startIndex = Find-ParagraphIndex -Document $Document -ExactText $StartHeading
    $endIndex = Find-ParagraphIndex -Document $Document -ExactText $EndHeading
    return @{
        Start = $Document.Paragraphs.Item($startIndex).Range.End
        End = $Document.Paragraphs.Item($endIndex).Range.Start
    }
}

function Replace-SectionBody {
    param(
        $Document,
        [string]$StartHeading,
        [string]$EndHeading,
        [string]$BodyText
    )
    $bounds = Get-SectionBodyBounds -Document $Document -StartHeading $StartHeading -EndHeading $EndHeading
    $range = $Document.Range($bounds.Start, $bounds.End)
    $range.Text = Convert-ToWordText $BodyText
}

function Type-Paragraph {
    param($Selection, [string]$Text)
    $Selection.TypeText($Text)
    $Selection.TypeParagraph()
}

function Add-Table {
    param(
        $Document,
        $Selection,
        [object[][]]$Rows
    )
    $rowCount = $Rows.Count
    $colCount = $Rows[0].Count
    $table = $Document.Tables.Add($Selection.Range, $rowCount, $colCount)
    try { $table.Style = 'Table Grid' } catch {}
    $table.Borders.Enable = 1
    $table.Range.Font.Name = '宋体'
    $table.Range.Font.Size = 10.5
    $table.Rows.Item(1).Range.Bold = 1
    $table.Rows.Alignment = 1
    for ($r = 0; $r -lt $rowCount; $r++) {
        for ($c = 0; $c -lt $colCount; $c++) {
            $table.Cell($r + 1, $c + 1).Range.Text = [string]$Rows[$r][$c]
        }
    }
    $Selection.SetRange($table.Range.End, $table.Range.End)
    $Selection.TypeParagraph()
}

$chapter41Body = @"
普通用户是 Campus Pulse 中内容生产和互动最活跃的角色，其实现不是简单的“内容发布模块”，而是围绕“进入系统、发布内容、参与互动、沉淀个人资产”四条链路展开。前端由认证页、帖子编辑器、帖子详情页、个人中心、私信页与设置页等页面组成，后端由认证、帖子、评论、关注、通知与私信等接口共同支撑。
（1）注册登录与账号安全实现。用户首次进入系统时，可通过用户名或邮箱加密码完成注册与登录，也可使用邮箱验证码登录和 GitHub 第三方登录。登录成功后，后端同时签发 accessToken 和 refreshToken，并把会话编号与设备编号写入令牌；客户端之后发起刷新请求时，还要携带设备标识参与校验。若设备不一致、refreshToken 失效或请求时间戳异常，服务端会直接拒绝访问，从而保证账号不会被异常设备继续使用。用户在设置页还可以初始化并启用 TOTP 二步验证，开启后登录流程会增加动态验证码校验步骤。
（2）帖子发布、草稿保存与重新提交实现。普通用户点击首页顶部“发布动态”后，会打开帖子编辑器。该编辑器支持选择发布板块、输入标题与 Markdown 正文、添加最多 5 个标签、上传封面图片、向正文插入图片或视频、插入目录标签以及调用 AI 提取标签。对于尚未写完的内容，系统提供“本地暂存 + 服务端草稿”双重草稿机制：当标题和板块已填写完整时可直接保存到“我的草稿”，否则先暂存本地，避免关闭弹窗后内容丢失。正式发布时，后端先校验标题长度、正文长度和板块参数，再执行敏感词过滤、摘要生成、情感分析、审核状态写入、缓存失效和经验值更新。新帖子不会直接绕过治理流程，而是以待审核状态进入内容管理列表；如果作者收到“打回修改”通知，则可以在个人中心重新打开帖子、修改内容后再次提交审核。
（3）帖子浏览、搜索与推荐阅读实现。用户可以通过首页、板块页、热门页、精华页、搜索页和趋势页浏览内容。帖子详情页除正文展示外，还会加载相关推荐和作者信息卡，系统在用户打开帖子时记录浏览日志，用于后续热度计算、阅读历史和趋势分析。搜索页支持关键词检索，板块页支持按分区聚合浏览，热门页则结合热度值展示当前活跃内容，从而把普通用户的阅读行为沉淀为可计算的数据。
（4）评论互动、点赞收藏与关系维护实现。用户在帖子详情页可发表评论、回复楼中楼内容，并可对帖子和评论进行点赞；对感兴趣的帖子可加入收藏，对活跃作者可执行关注。对应的后端服务在写入互动记录的同时，还会同步更新帖子评论数、点赞数、最后回复时间和最后活跃时间。若互动行为涉及他人内容，通知模块会把消息落库后再通过 WebSocket 推送给目标用户，前端实时刷新角标和通知列表。用户进入私信页后，还可和其他成员建立一对一会话，系统通过 conversationId 聚合同一对用户的全部双向消息，便于后续分页查询与未读统计。
（5）个人中心与个性化设置实现。普通用户在个人中心不仅可以查看自己已发布的帖子，还可以按标签页管理草稿、收藏、关注、粉丝、关注话题、浏览历史和消息通知。设置页则承担资料维护和安全配置功能，用户可修改昵称、简介、学校专业等个人资料，设置资料卡片主题与背景图，开启或关闭邮件同步通知，启用或停用二步验证，并维护密码与登录偏好。通过这些功能，普通用户在系统中的行为不再是一次性的页面点击，而是形成了可持续运营的个人内容资产和关系资产。
"@

$chapter42Body = @"
版主角色是连接普通用户与管理员之间的分区治理执行者，其权限不是全站通用权限，而是与具体板块绑定的定向权限。系统通过版主申请、审核通过和板块权限回写三个步骤，使版主能力只在被授权板块中生效。
（1）版主申请与权限生效实现。普通用户等级达到 Lv5 后，可在板块页打开“版主申请中心”填写申请理由并提交。前端会实时读取当前等级和该板块是否已存在待审核记录；后端在提交时再次校验用户等级、板块状态、重复申请和已是版主等条件，满足要求后把申请写入版主申请表，并同步向管理员发送待审核通知。管理员审批通过后，用户资料中的可管理板块集合会被刷新，版主后台随即出现“我的板块”“内容管理”“举报管理”等入口。
（2）板块内容审核实现。版主进入内容管理页后，只能看到自己负责板块下的帖子列表，不能越权查看其他板块内容。该页面支持按板块和关键词筛选帖子，并可对待审核帖子执行通过审核、打回修改、删除、板块置顶和设为精华等操作。若选择打回修改，系统会要求填写明确原因，然后把帖子状态改为已打回并向作者发送系统通知；若审核通过，则帖子从待审核状态进入正常发布状态。
（3）板块运营维护实现。除了审核发帖，版主还可以对板块内优质内容执行板块置顶和精华设置，便于把高质量帖子固定在分区顶部，提高新用户进入板块后的内容命中率。相关操作由后端按“当前用户是否拥有该板块管理权”进行校验，因此版主可以管理本区内容排序，但不能执行全站级配置和跨板块运营。
（4）举报处理与社区秩序维护实现。版主在举报管理页可查看当前板块内帖子或评论的举报记录，列表中会展示举报人、目标内容摘要、所属板块、举报原因和当前状态。版主可将举报标记为已处理、已忽略，或者对违规帖子执行“打回修改”。当系统进入打回流程时，会先把举报状态放入排队中和处理中，再异步修改帖子状态、刷新缓存并通知作者，减少高频举报场景下的阻塞。通过这一机制，版主能够在自己负责的分区内独立完成内容治理，降低管理员处理日常事务的压力。
"@

$chapter43Body = @"
管理员角色承担全站运营与维护职责，其功能实现直接对应后台左侧导航栏。相比版主只管理局部板块，管理员能够访问全部分区数据和系统级接口，是平台稳定运行的最终控制层。
（1）数据看板实现。管理员进入后台后首先看到的是数据看板页面，该页面汇总展示用户规模、内容规模、互动总量和今日新增内容，并通过折线图和柱状图展示用户增长趋势与内容发布趋势。该模块直接服务于站点运营判断，便于管理员快速掌握平台活跃度变化。
（2）内容管理实现。管理员在内容管理页可以查看所有板块的帖子列表，并可按板块和关键词筛选。相较版主的局部权限，管理员可对全站帖子执行通过审核、打回修改、删除、板块置顶和设为精华等操作，从而统一处理跨板块内容治理问题。
（3）板块管理实现。板块管理页用于维护社区分区结构，管理员可以新增板块、编辑板块名称与描述、调整图标和排序、启用或删除板块。该模块保证了前台板块导航与后台分区权限的统一来源，是版主治理和用户发帖选区的基础。
（4）用户管理实现。用户管理页集中展示账号角色、状态与基本资料，管理员可执行封禁、解封、删除和角色调整等操作。页面内还限制了“不能操作自己”和“无法操作超级管理员”等边界条件，避免高权限误操作带来系统风险。
（5）举报管理实现。管理员拥有全站举报处理权限，既可以查看普通用户提交的全部帖子与评论举报，也可以按状态筛选待处理、已处理、已忽略和已打回记录。管理员除执行普通处理外，还能结合全站视角处理跨板块敏感内容，保证社区秩序在复杂场景下仍然可控。
（6）缓存管理实现。缓存管理页提供标签缓存、帖子流缓存、推荐缓存、令牌缓存、验证码缓存和登录锁定缓存的统计概览，同时支持快捷清理和按模式统计、清理。管理员在系统缓存异常、用户集中登录或推荐结果滞后时，可直接通过该页面完成运维处置，而无需手工进入 Redis 服务端。
（7）发展历程管理实现。发展历程管理页用于维护站点版本更新、里程碑事件和公告性内容。管理员可新增、编辑和删除发展历程记录，并维护版本号、标题、正文与发布时间，使前台用户能够直观看到平台迭代过程。
（8）版主申请管理实现。版主申请管理页按申请状态展示申请人信息、当前等级、申请板块、申请理由和审核备注。管理员可在该页面直接批准或拒绝申请，并填写审核意见；系统在审批完成后会同步向申请人发送站内通知，必要时还可联动邮件提醒，从而完成版主权限的正式授予或驳回。
（9）邀请码管理实现。邀请码管理页支持批量生成邀请码、设置使用上限与过期时间、复制邀请码、禁用失效邀请码，并统计已使用和已禁用数量。该模块用于控制社区准入节奏和邀请奖励机制，也是平台在特定阶段进行冷启动和用户裂变的重要运营工具。
综上，管理员按照后台导航栏逐项完成数据监控、内容治理、权限管理、系统运维和运营配置，形成了与普通用户、版主明显区分的全局管理闭环。
"@

$chapter51Body = @"
系统测试部分不再使用与当前项目无关的业务场景，而是围绕 Campus Pulse 代码仓库中的真实模块展开。本章选取认证与会话、帖子发布与审核、版主申请与后台审核三个与角色行为直接相关的模块作为重点测试对象，并结合控制器参数校验、MockMvc 接口测试与页面联调验证三种方式进行验证。
本次测试在本地使用 JDK 21 与 Maven 3.9.11 执行 AuthControllerValidationTest、PostControllerValidationTest 和 ModeratorApplicationControllerTest 三个测试类，共完成 8 项自动化用例，结果全部通过。其中自动化测试主要验证参数合法性、权限边界和请求体解析是否正确；页面联调主要用于核对登录态、待审核状态和后台审核结果是否能在界面上正确展示。
因此，本章的测试指标重点不再是“页面能否打开”，而是检查输入数据是否合法、普通用户是否会越权、版主和管理员是否能够只在各自权限范围内操作，以及关键状态流转是否与系统实现保持一致。
"@

$chapter52Body = @"
测试环境配置如下。
（1）操作系统：Windows 11。
（2）JDK：21（项目源码编译目标为 Java 17）。
（3）Maven：3.9.11。
（4）后端框架：Spring Boot 3.5.8。
（5）数据库：MySQL 8.0。
（6）缓存：Redis 6.x。
（7）前端：Vue 3.5 + TypeScript 5.9 + Vite 7。
（8）接口测试框架：JUnit 5 + Mockito + MockMvc。
（9）联调工具：Apifox / Postman。
（10）浏览器：Chrome 与 Edge 最新版。
"@

$table51 = @(
    @('用例编号', '测试数据或操作', '预期结果', '实际结果'),
    @('A1', '登录请求：用户名=202250734；密码=Campus@123；设备标识=device-win11-001', '返回登录成功结果，生成 accessToken 与 refreshToken，前端进入首页登录态', '页面联调项，可在登录成功后截图核验'),
    @('A2', '注册请求：username=user_test；email=not-an-email；code=123456', '拦截非法邮箱格式，不调用注册服务', '自动化测试返回 code=4003，message 包含 email，结果通过'),
    @('A3', '刷新请求：refreshToken=" "', '拦截空 refreshToken，不调用刷新服务', '自动化测试返回 code=4003，message 包含 refreshToken，结果通过')
)

$table52 = @(
    @('用例编号', '测试数据或操作', '预期结果', '实际结果'),
    @('P1', '发帖请求：sectionId=1；title=这是一个合格的帖子标题；content=这里是一段超过三十个字符的帖子正文内容，用于测试接口正常调用逻辑。；tags=测试,校验；登录用户=10001', '返回 code=2000，并调用创建帖子服务，帖子进入待审核链路', '自动化测试通过，PostService.createPost 被成功调用'),
    @('P2', '发帖请求：sectionId=1；title=abc；content=内容太短；tags=测试', '拦截标题和正文长度错误，不允许落库', '自动化测试返回 code=4003，message 同时包含 title 与 content 校验信息，结果通过'),
    @('P3', '草稿保存：title=待整理的课程项目总结；sectionId=2；content=先记录正文结构，稍后继续补充实验数据与结论', '返回草稿编号，个人中心“我的草稿”可见', '页面联调项，可在个人中心草稿列表中截图核验'),
    @('P4', '后台审核：对待审核帖子点击“通过审核”或“打回修改”，打回原因为“请补充实验结果与数据来源”', '帖子状态分别变为已发布或已打回，作者收到系统通知', '页面联调项，可在内容管理页和通知页截图核验')
)

$table53 = @(
    @('用例编号', '测试数据或操作', '预期结果', '实际结果'),
    @('M1', '申请请求：sectionId=1；reason=too short', '拦截过短申请理由', '自动化测试返回 code=4003，message 提示申请理由长度需在 10 到 500 个字符之间，结果通过'),
    @('M2', '申请请求：sectionId=2；reason=我在学习交流板块持续发帖和答疑，希望参与内容审核与秩序维护工作。', '创建待审核申请记录', '页面联调项，可在版主申请弹窗与“我的申请”列表截图核验'),
    @('M3', '普通用户访问 /moderator/applications', '拒绝越权访问', '自动化测试返回 code=3003，message 为“仅管理员可执行该操作”，结果通过'),
    @('M4', '管理员审批：approve/12；reviewNote=欢迎加入版主团队', '审批通过并写入审核备注', '自动化测试返回 code=2000，且 reviewNote 被正确读取，结果通过'),
    @('M5', '管理员拒绝：reject/18；reviewNote=当前活跃度和管理经验说明不足', '拒绝申请并写入审核意见', '自动化测试返回 code=2000，且 reviewNote 被正确读取，结果通过')
)

$word = $null
$document = $null

try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $document = $word.Documents.Open($targetPath)

    Replace-SectionBody -Document $document -StartHeading '4.1 普通用户角色功能实现' -EndHeading '4.2 版主角色功能实现' -BodyText $chapter41Body
    $document.Save()
    Replace-SectionBody -Document $document -StartHeading '4.2 版主角色功能实现' -EndHeading '4.3 管理员角色功能实现' -BodyText $chapter42Body
    $document.Save()
    Replace-SectionBody -Document $document -StartHeading '4.3 管理员角色功能实现' -EndHeading '第5章 系统测试' -BodyText $chapter43Body
    $document.Save()
    Replace-SectionBody -Document $document -StartHeading '5.1 测试目标与测试方法' -EndHeading '5.2 测试环境' -BodyText $chapter51Body
    Replace-SectionBody -Document $document -StartHeading '5.2 测试环境' -EndHeading '5.3 测试内容' -BodyText $chapter52Body
    $document.Save()

    $bodyBounds = Get-SectionBodyBounds -Document $document -StartHeading '5.3 测试内容' -EndHeading '结  论'
    $bodyRange = $document.Range($bodyBounds.Start, $bodyBounds.End)
    $bodyRange.Text = ''

    $selection = $word.Selection
    $selection.SetRange($bodyBounds.Start, $bodyBounds.Start)
    $selection.Collapse(1)

    Type-Paragraph -Selection $selection -Text '本节选取三个与系统核心角色直接对应的业务模块进行测试，并把自动化测试结果与页面联调要求统一整理为测试数据表。若后续需要补充答辩截图，可直接按表后给出的截图建议进行页面截取。'

    Type-Paragraph -Selection $selection -Text '（1）认证与会话模块测试'
    Type-Paragraph -Selection $selection -Text '认证与会话模块重点验证登录、注册参数校验、refreshToken 刷新和设备绑定的边界行为，既考察正常登录链路，也考察异常输入能否被控制器及时拦截。'
    Type-Paragraph -Selection $selection -Text '表5—1 认证与会话模块测试数据表'
    Add-Table -Document $document -Selection $selection -Rows $table51
    Type-Paragraph -Selection $selection -Text '认证与会话模块的自动化结果表明，控制器层能够在注册和令牌刷新阶段提前拦截非法参数，避免异常请求继续进入业务服务层。'
    Type-Paragraph -Selection $selection -Text '截图建议：登录成功后截取首页顶部已显示用户头像、消息角标和“发布动态”按钮的界面，可作为图5—1。'
    Type-Paragraph -Selection $selection -Text '图5—1 用户登录成功页面'

    Type-Paragraph -Selection $selection -Text '（2）帖子发布与审核模块测试'
    Type-Paragraph -Selection $selection -Text '帖子发布与审核模块重点验证标题、正文、板块、标签等核心字段的合法性，以及帖子提交后是否按“待审核—已发布/已打回”的路径流转。'
    Type-Paragraph -Selection $selection -Text '表5—2 帖子发布与审核模块测试数据表'
    Add-Table -Document $document -Selection $selection -Rows $table52
    Type-Paragraph -Selection $selection -Text '帖子发布模块的自动化测试验证了标题与正文长度约束，页面联调则重点观察发布成功后是否进入待审核状态，以及版主或管理员打回时作者是否能在通知和个人中心收到修改提示。'
    Type-Paragraph -Selection $selection -Text '截图建议：可截取发帖弹窗提交成功后的提示信息，以及后台内容管理页中帖子处于“待审核”或“已打回”状态的界面，作为图5—2。'
    Type-Paragraph -Selection $selection -Text '图5—2 帖子发布与审核状态页面'

    Type-Paragraph -Selection $selection -Text '（3）版主申请与后台审核模块测试'
    Type-Paragraph -Selection $selection -Text '版主申请模块同时覆盖普通用户申请、管理员审核和越权拦截三类场景，重点验证申请理由长度、普通用户是否可以访问管理员接口，以及审批请求体中的审核备注能否被正确读取。'
    Type-Paragraph -Selection $selection -Text '表5—3 版主申请与后台审核模块测试数据表'
    Add-Table -Document $document -Selection $selection -Rows $table53
    Type-Paragraph -Selection $selection -Text '版主申请模块的自动化测试说明接口能够正确识别申请理由长度、普通用户越权访问以及管理员审批时传入的 reviewNote 字段；结合页面联调，可完整覆盖“提交申请—后台审核—结果通知”的闭环。'
    Type-Paragraph -Selection $selection -Text '截图建议：用户在板块页点击“申请版主”后填写申请理由的弹窗界面，可作为图5—3。'
    Type-Paragraph -Selection $selection -Text '图5—3 版主申请填写页面'
    Type-Paragraph -Selection $selection -Text '截图建议：管理员在后台“版主申请管理”页面完成审批后的记录列表，可作为图5—4。'
    Type-Paragraph -Selection $selection -Text '图5—4 管理员审核版主申请页面'
    Type-Paragraph -Selection $selection -Text '综合以上三类模块，本次自动化测试共执行 8 项用例且全部通过，说明系统在输入校验、权限控制和关键业务入口上运行稳定；对需要在论文中展示的页面效果，则可按照上述截图建议补齐图示。'

    $document.Save()
    $document.Close([ref]0)
    $document = $null
    $word.Quit()
    $word = $null
    Write-Output "Text sections updated in $targetPath"
} finally {
    if ($document -ne $null) {
        $document.Close([ref]0)
    }
    if ($word -ne $null) {
        $word.Quit()
    }
}
