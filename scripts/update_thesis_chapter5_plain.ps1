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
    param($Document, [string]$ExactText)
    $target = ($ExactText -replace '\s+', '')
    for ($i = 1; $i -le $Document.Paragraphs.Count; $i++) {
        $current = (Get-CleanParagraphText $Document.Paragraphs.Item($i)) -replace '\s+', ''
        if ($current -eq $target) {
            return $i
        }
    }
    throw "未找到段落: $ExactText"
}

$chapter5Body = @"
5.1 测试目标与测试方法
系统测试部分不再使用与当前项目无关的业务场景，而是围绕 Campus Pulse 代码仓库中的真实模块展开。本章选取认证与会话、帖子发布与审核、版主申请与后台审核三个与角色行为直接相关的模块作为重点测试对象，并结合控制器参数校验、MockMvc 接口测试与页面联调验证三种方式进行验证。
本次测试在本地使用 JDK 21 与 Maven 3.9.11 执行 AuthControllerValidationTest、PostControllerValidationTest 和 ModeratorApplicationControllerTest 三个测试类，共完成 8 项自动化用例，结果全部通过。其中自动化测试主要验证参数合法性、权限边界和请求体解析是否正确；页面联调主要用于核对登录态、待审核状态和后台审核结果是否能在界面上正确展示。
测试方法主要包括三类。其一，控制器参数校验：验证邮箱格式、refreshToken、标题长度、正文长度和申请理由长度等输入能否被统一拦截。其二，接口权限测试：验证普通用户访问管理员接口时是否会被拒绝，以及管理员审核请求中的审核备注是否能被正确读取。其三，页面联调验证：验证登录成功后页面状态、帖子进入待审核后的后台显示、版主申请提交后的状态变化与通知反馈是否一致。
5.2 测试环境
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
5.3 测试内容
本节选取三个与系统核心角色直接对应的业务模块进行测试，并把自动化测试结果与页面联调要求统一整理为测试数据。若后续需要补充答辩截图，可直接按下述截图建议进行页面截取。
（1）认证与会话模块测试
表5—1 认证与会话模块测试数据
A1：测试数据为登录请求 username=202250734、password=Campus@123、deviceId=device-win11-001。预期结果为返回登录成功结果，生成 accessToken 与 refreshToken，前端进入首页登录态。实际结果为页面联调项，可在登录成功后进入首页并截图核验。
A2：测试数据为注册请求 username=user_test、email=not-an-email、code=123456。预期结果为拦截非法邮箱格式，不调用注册服务。实际结果为自动化测试返回 code=4003，message 包含 email，结果通过。
A3：测试数据为刷新请求 refreshToken=" "。预期结果为拦截空 refreshToken，不调用刷新服务。实际结果为自动化测试返回 code=4003，message 包含 refreshToken，结果通过。
截图建议：登录成功后截取首页顶部已显示用户头像、消息角标和“发布动态”按钮的界面，可作为图5—1。
图5—1 用户登录成功页面
截图建议：非法邮箱注册后截取前端错误提示或接口返回信息页面，可作为图5—2。
图5—2 注册参数校验提示页面
（2）帖子发布与审核模块测试
表5—2 帖子发布与审核模块测试数据
P1：测试数据为发帖请求 sectionId=1、title=这是一个合格的帖子标题、content=这里是一段超过三十个字符的帖子正文内容，用于测试接口正常调用逻辑。、tags=测试,校验、登录用户=10001。预期结果为返回 code=2000，并调用创建帖子服务，帖子进入待审核链路。实际结果为自动化测试通过，PostService.createPost 被成功调用。
P2：测试数据为发帖请求 sectionId=1、title=abc、content=内容太短、tags=测试。预期结果为拦截标题和正文长度错误，不允许落库。实际结果为自动化测试返回 code=4003，message 同时包含 title 与 content 校验信息，结果通过。
P3：测试数据为草稿保存请求 title=待整理的课程项目总结、sectionId=2、content=先记录正文结构，稍后继续补充实验数据与结论。预期结果为返回草稿编号，个人中心“我的草稿”可见。实际结果为页面联调项，可在个人中心草稿列表中截图核验。
P4：测试数据为后台审核操作，对待审核帖子点击“通过审核”或“打回修改”，打回原因为“请补充实验结果与数据来源”。预期结果为帖子状态分别变为已发布或已打回，作者收到系统通知。实际结果为页面联调项，可在内容管理页和通知页截图核验。
截图建议：可截取发帖弹窗提交成功后的提示信息，以及后台内容管理页中帖子处于“待审核”或“已打回”状态的界面，作为图5—3。
图5—3 帖子发布与审核状态页面
（3）版主申请与后台审核模块测试
表5—3 版主申请与后台审核模块测试数据
M1：测试数据为申请请求 sectionId=1、reason=too short。预期结果为拦截过短申请理由。实际结果为自动化测试返回 code=4003，message 提示申请理由长度需在 10 到 500 个字符之间，结果通过。
M2：测试数据为申请请求 sectionId=2、reason=我在学习交流板块持续发帖和答疑，希望参与内容审核与秩序维护工作。预期结果为创建待审核申请记录。实际结果为页面联调项，可在版主申请弹窗与“我的申请”列表截图核验。
M3：测试数据为普通用户访问 /moderator/applications。预期结果为拒绝越权访问。实际结果为自动化测试返回 code=3003，message 为“仅管理员可执行该操作”，结果通过。
M4：测试数据为管理员审批 approve/12，reviewNote=欢迎加入版主团队。预期结果为审批通过并写入审核备注。实际结果为自动化测试返回 code=2000，且 reviewNote 被正确读取，结果通过。
M5：测试数据为管理员拒绝 reject/18，reviewNote=当前活跃度和管理经验说明不足。预期结果为拒绝申请并写入审核意见。实际结果为自动化测试返回 code=2000，且 reviewNote 被正确读取，结果通过。
截图建议：用户在板块页点击“申请版主”后填写申请理由的弹窗界面，可作为图5—4；管理员在后台“版主申请管理”页面完成审批后的记录列表也建议同步留档。
图5—4 版主申请与后台审核页面
综合以上三类模块，本次自动化测试共执行 8 项用例且全部通过，说明系统在输入校验、权限控制和关键业务入口上运行稳定；对需要在论文中展示的页面效果，则可按照上述截图建议补齐图示。
"@

$word = $null
$document = $null

try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $document = $word.Documents.Open($targetPath)

    $chapterHeadingIndex = Find-ParagraphIndex -Document $document -ExactText '第5章 系统测试'
    $conclusionIndex = Find-ParagraphIndex -Document $document -ExactText '结  论'
    $start = $document.Paragraphs.Item($chapterHeadingIndex).Range.End
    $end = $document.Paragraphs.Item($conclusionIndex).Range.Start
    $range = $document.Range($start, $end)
    $range.Text = Convert-ToWordText $chapter5Body

    $document.Save()
    $document.Close([ref]0)
    $document = $null
    $word.Quit()
    $word = $null
    Write-Output "Chapter 5 replaced in $targetPath"
} finally {
    if ($document -ne $null) {
        $document.Close([ref]0)
    }
    if ($word -ne $null) {
        $word.Quit()
    }
}
