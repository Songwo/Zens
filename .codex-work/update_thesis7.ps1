$ErrorActionPreference = 'Stop'

$repoRoot = 'D:\2026毕业设计\DaiMa\campus-pulse(back)\campus-pulse'
$docxPath = Join-Path $repoRoot 'docs\赵青松论文7.docx'
$workDir = Join-Path $repoRoot 'tmp_thesis7_edit'
$unzippedDir = Join-Path $workDir 'unzipped'
$xmlPath = Join-Path $unzippedDir 'word\document.xml'
$zipPath = Join-Path $workDir 'thesis7_modified.zip'

$replacements = [ordered]@{
    38  = '随着技术分享、开源协作与在线交流平台的持续发展，技术社区已经成为开发者发布经验、获取知识和开展协同讨论的重要载体。传统社区系统通常只具备基础发帖与评论功能，在内容审核、趋势分析、推荐分发和后台治理方面支撑不足，难以满足社区持续运营的需要。因此，设计并实现一个兼顾帖子发布、互动交流、治理审核和数据分析的技术社区系统，具有较强的现实意义和应用价值。'
    39  = '本系统基于 Spring Boot 与 Vue 构建，采用 Spring Security+JWT+Redis 完成身份认证与会话控制。面向普通用户，系统实现了注册登录、帖子发布与草稿保存、帖子检索、评论回复、点赞收藏、关注私信、通知查看和个性化推荐等功能；面向版主，系统实现了帖子审核、帖子打回、板块置顶、精华设置与举报处理；面向管理员，系统实现了帖子管理、板块管理、用户管理、版主申请审核、邀请码管理、缓存管理、发展历程管理和站点统计等功能。同时系统结合 AI 摘要、标签提取、情感分析、热度排行和趋势统计，为社区运营提供辅助决策支持。'
    40  = '关键词：技术社区；帖子管理；内容治理；趋势分析；权限控制'
    49  = 'With the continuous development of technology sharing, open-source collaboration, and online communication platforms, technical communities have become important spaces for developers to publish experience, acquire knowledge, and participate in collaborative discussion. Traditional community systems usually focus only on basic posting and commenting, but provide insufficient support for content review, trend analysis, recommendation delivery, and backend governance. Therefore, designing and implementing a technical community system that integrates post publishing, interaction, governance review, and data analysis has clear practical value.'
    50  = 'The system is built with Spring Boot and Vue, and uses Spring Security, JWT, and Redis to complete identity authentication and session control. For ordinary users, it supports registration and login, post publishing and draft saving, post retrieval, comment reply, like and favorite, follow and private message, notification viewing, and personalized recommendation. For moderators, it supports post approval, post rejection, section pinning, featured setting, and report handling. For administrators, it supports post management, section management, user management, moderator application review, invitation code management, cache management, changelog management, and site statistics. In addition, the system integrates AI summary, tag extraction, sentiment analysis, heat ranking, and trend statistics to provide decision support for community operation.'
    51  = 'Key words: technical community; post management; content governance; trend analysis; access control'
    89  = '本课题围绕技术社区系统的核心业务闭环展开研究与实现。首先，在基础业务层面，完成用户注册登录、帖子发布与草稿保存、帖子搜索、帖子详情浏览、评论回复、点赞收藏、关注、私信和通知等前台功能，实现普通用户的完整使用链路。'
    90  = '其次，在社区治理层面，围绕不同角色构建后台能力：版主可对负责板块内的帖子执行审核通过、打回修改、板块置顶、设置精华和处理举报；管理员可执行帖子管理、用户封禁与角色分配、板块维护、版主申请审核、邀请码管理、缓存管理和发展历程管理，形成可落地的运营与治理支撑。'
    91  = '再次，在数据分析与系统保障层面，系统实现了 AI 标签与摘要提取、情感分析、热度排行、趋势统计、混合推荐、JWT 身份认证、设备绑定、请求签名防重放、WebSocket 实时通知和 Redis 缓存优化等机制。通过上述设计，系统形成从帖子发布、互动反馈到治理审核、推荐分发和趋势分析的完整业务链路。'
    124 = '本系统面向技术社区的日常交流与运营治理场景，核心需求是实现“安全发布、有效互动、分级治理、辅助决策”的一体化支撑。系统需覆盖用户从注册登录到内容消费的完整流程，并为版主和管理员提供可直接执行的治理工具。同时，平台还应具备稳定的安全防护、缓存优化与趋势分析能力，在中小规模并发下保持良好响应和可维护性。'
    126 = '（1）普通用户功能需求。系统应为普通用户提供注册、密码登录、邮箱验证码登录、GitHub 登录、退出登录和个人资料维护功能。在内容使用环节，用户可按板块浏览帖子、通过关键词检索帖子、查看帖子详情和推荐列表；在内容生产环节，用户可发布帖子、保存草稿、修改本人帖子、删除本人帖子，并为帖子设置标签、摘要、封面图、匿名标记和所属板块。'
    127 = '（2）社区互动功能需求。系统应支持用户对帖子发表评论、回复评论、点赞帖子、点赞评论、收藏帖子、关注用户、发送私信、查看站内通知和管理已读状态。系统还需记录浏览行为，实时更新帖子浏览数、点赞数、收藏数和评论数，并将互动结果同步到热度计算、消息提醒和推荐分发链路，为后续统计分析提供行为数据基础。'
    128 = '（3）版主功能需求。系统应为版主提供板块级后台能力，只能处理自己负责板块的数据。具体包括查看板块待审核帖子、执行帖子通过或打回、设置板块置顶、设置精华、查看本板块举报记录，以及对举报执行已处理、忽略和打回修改等操作，从而提升分区治理效率。'
    129 = '（4）管理员功能需求。系统应为管理员提供站点级治理与运维能力，包括查询帖子、审核帖子、删除帖子、设置全局置顶、管理用户封禁与解封及角色分配、新增编辑删除和启停板块、审核版主申请、生成和禁用邀请码、管理发展历程、查看数据看板，以及按模式清理 Redis 缓存。同时系统还需具备 AI 摘要与标签提取、情感分析、热度排行、趋势统计、请求签名防重放和二步验证等支撑能力。'
    166 = '经过对技术社区业务的梳理，系统按照“普通用户模块、版主模块、管理员模块、数据分析与安全支撑模块”进行功能划分。其中，普通用户模块由注册登录、个人资料、帖子发布、帖子搜索、评论回复、点赞收藏、关注私信、通知中心和推荐浏览组成；版主模块由待审核帖子管理、帖子打回、板块置顶、精华设置和举报处理组成；管理员模块由帖子管理、用户管理、板块管理、版主申请审核、邀请码管理、缓存管理、发展历程管理和数据看板组成。'
    167 = '各模块并非孤立运行，而是围绕帖子对象形成完整业务闭环：普通用户发布帖子并参与评论互动，版主对所属板块帖子进行审核和治理，管理员在站点级别完成全局配置和运维控制，数据分析与安全支撑模块则为热度排行、趋势统计、推荐分发、JWT 鉴权、设备绑定和请求防重放提供底层能力。系统功能结构如图3-1所示。'
    305 = '普通用户是 Campus Pulse 的主要使用主体，其完整使用链路具体包括注册登录、个人资料修改、帖子发布与草稿保存、帖子检索、评论回复、点赞收藏、关注、私信、通知查看和推荐浏览。系统前台围绕这些具体操作组织页面与接口，确保用户能够完成从进入社区到参与讨论的完整闭环。'
    306 = '在账号与会话实现方面，AuthController 提供发送验证码、注册、密码登录、邮箱验证码登录、GitHub 登录、刷新令牌、退出登录和 2FA 校验等接口；服务端同时签发 accessToken 与 refreshToken，并把 sid、did 等信息写入令牌声明，再借助 Redis 维护 auth:access、auth:refresh 和 auth:device 等键空间，实现会话校验与主动失效控制。'
    307 = '这样即使令牌被窃取，只要设备不一致或会话已失效，过滤器也会拒绝请求。相比仅校验 JWT 签名的传统方案，该机制进一步提高了会话可控性和账号安全性。'
    308 = '系统还实现了基于 TOTP 的二步验证流程。用户可在设置页初始化 2FA，系统生成密钥、otpauth URI 与二维码，用户在认证器 App 完成绑定后，登录阶段将触发动态口令校验，从而提高高价值账号的防护等级。'
    309 = '此外，请求安全层对 POST、PUT、DELETE 等变更请求校验 X-Request-Timestamp、X-Request-Nonce 和 X-Request-Signature，并将随机串写入 Redis 防止重放。该机制可有效抑制接口重放攻击和部分中间人篡改风险。'
    312 = '帖子发布功能由 PostController 的 create-post、save-draft、update-post 和 delete 等接口组成，对应前端发帖弹窗、个人中心草稿箱和帖子编辑页面。用户填写标题、正文、板块、标签、封面图和匿名标记后，系统先校验必填项和板块合法性，再保存为草稿或正式帖子，实现帖子新增、修改、删除和草稿续写。'
    313 = '在内容安全方面，系统使用 DFA 敏感词算法对标题、正文、评论和个人资料字段进行扫描。命中敏感词后采用替换策略处理，既保证发布流程连续性，又降低违规内容传播风险。'
    314 = '在智能处理方面，系统接入 AI 服务用于标签提取与摘要重新生成。发帖和摘要重生成功能分别通过 extract-tags 与 summary/regenerate 接口暴露；若外部 AI 服务异常，系统自动回退到本地摘要策略，保证帖子发布链路稳定可用。'
    315 = '帖子检索与展示通过 search-lists、/{id} 和 pinned 等接口实现，系统支持按关键词、板块、时间范围、标签以及最新或热门排序查询帖子，并对置顶帖子、普通帖子采用差异化排序规则，提升用户查找内容的效率。'
    316 = '帖子模型除基本字段外，还扩展了 globalPin、categoryPin、pinOrder、pinExpireAt、lastReplyAt 和 lastActivityAt 等字段。结合检索接口中的排序规则，系统能够实现全局置顶优先、板块置顶次之、普通帖子按活跃时间倒序的信息流组织方式，保证重要信息和活跃讨论都能获得合适曝光。'
    332 = '版主角色面向板块治理场景。前端通过 MyModerationPage 展示负责板块列表，后台通过 PostsManagePage 和 ReportsManagePage 限定管理范围。版主可按板块查看帖子列表和待审核帖子，对帖子执行审核通过、打回修改、板块置顶和设置精华等操作。'
    333 = '在举报处理方面，版主可查看本板块举报记录，针对被举报帖子或评论执行已处理、忽略或打回修改等操作；若选择打回帖子，系统会将帖子状态改为草稿并通过 NotificationService 向作者发送整改通知，从而形成举报提交、版主核查和结果反馈的闭环流程。'
    334 = '板块级权限由 moderatedSectionIds 与 SectionModeratorService 共同约束，保证版主只能管理自己负责板块的数据。该设计既提升了内容治理效率，也避免了越权操作。'
    337 = '管理员角色负责站点级后台治理，其功能不再笼统描述为后台管理，而是具体划分为帖子管理、用户管理、板块管理、版主申请审核、邀请码管理、缓存管理、发展历程管理和数据统计八类操作。前端分别对应 DashboardPage、PostsManagePage、UsersManagePage、SectionsManagePage、ModeratorApplicationsManagePage、InviteCodesPage、CacheManagePage 和 ChangelogManagePage 等页面。'
    338 = '在帖子管理方面，管理员可查询全部帖子，执行审核通过、打回修改、删除、全局置顶、板块置顶和设置精华等操作；在用户管理方面，可执行用户查询、封禁、解封、删除与角色分配；在板块管理方面，可新增、编辑、删除和启停板块，保证社区内容分区有序运行。'
    339 = '在运维与运营方面，管理员可审核版主申请、生成或禁用邀请码、对发展历程进行新增修改删除和发布管理、查看站点统计与趋势分析结果，并通过 CacheManagementController 完成缓存概览、模式计数、标签缓存清理和令牌缓存清理，从而支撑站点级治理与长期运行。'
    344 = '系统测试的目标是验证 Campus Pulse 在认证安全、帖子发布、互动通知、版主治理和后台运维等核心链路上的正确性与稳定性，确保论文中的测试内容与项目实际实现保持一致。'
    345 = '本项目采用控制器校验测试、服务逻辑测试与功能用例验证相结合的方式，具体包括：'
    346 = '1. 认证与安全测试：验证注册参数校验、登录凭证校验、refreshToken 校验、设备绑定和请求防重放链路。'
    347 = '2. 内容发布与交互测试：验证帖子发布参数、评论互动、通知批量已读与删除、上传文件类型校验等关键接口。'
    348 = '3. 版主与管理员权限测试：验证版主申请接口、板块权限范围以及管理员访问边界。'
    349 = '4. 服务协同测试：验证控制器、业务服务、Redis、MySQL 与通知链路在典型场景下的调用关系是否正确。'
    350 = '5. 页面功能验证：结合浏览器端实际操作，检查登录、发帖、推荐浏览和后台管理页面的展示结果是否与接口返回一致。'
    351 = '结合现有测试代码与人工验证结果，测试重点不再使用与本项目无关的其他业务场景，而是围绕账号认证、帖子发布、通知管理、版主申请、文件上传和板块权限六类核心对象展开。'
    352 = '在自动化测试方面，项目编写了 AuthControllerValidationTest、PostControllerValidationTest、NotificationControllerBatchTest、ModeratorApplicationControllerTest、UploadControllerValidationTest 和 SectionModeratorServiceImplTest 等用例，分别对参数校验、批量通知、版主权限范围和上传安全进行验证。'
    353 = '从用例结果来看，非法注册邮箱、空白 refreshToken、过短帖子标题、空通知 ID 集合、过短版主申请理由和 MIME 不匹配文件均能被系统正确拦截，说明输入校验与权限控制链路工作正常。'
    354 = '同时，版主权限范围测试验证了 moderatedSectionIds 只能作用于已批准板块，未授权版主无法处理其他板块的帖子、评论和举报，保证后台治理操作具备明确边界。'
    355 = '因此，第五章测试内容能够较全面地反映系统在接口校验、角色权限和核心业务链路上的运行情况。'
    357 = '本系统以黑盒测试为主，结合 MockMvc 控制器测试和服务单元测试开展验证。黑盒测试从用户输入与接口输出角度检查业务是否符合预期，MockMvc 用于模拟 HTTP 请求并断言返回码、返回信息和服务调用参数。'
    358 = '等价类划分法用于注册、登录、版主申请和通知批量操作等输入校验场景，例如把邮箱、refreshToken、帖子标题和申请理由划分为合法输入与非法输入两类，检查系统是否给出统一错误提示。'
    359 = '边界值分析法用于帖子标题长度、正文长度、版主申请理由长度以及批量通知 ID 集合非空约束等临界条件，防止因长度或数量边界处理不当导致接口异常。'
    360 = '权限与范围测试用于验证普通用户、版主和管理员的访问边界，例如普通用户不能访问版主申请审核接口，版主只能处理自己负责板块的帖子与举报，管理员才能执行用户封禁和全局配置操作。'
    361 = '异常输入测试主要覆盖上传模块与安全过滤场景，例如上传空文件、错误 MIME 类型文件或扩展名与 MIME 不匹配文件，验证系统能够及时拒绝非法请求并返回明确提示。'
    366 = '  3. 后端框架：Spring Boot 3.5.8'
    370 = '  7. 接口与自动化测试工具：MockMvc / Postman / Apifox'
    371 = '  8. 构建工具：Maven 3.9+'
    374 = '本节结合表5-1至表5-3，对认证会话、帖子发布和推荐展示三个核心模块进行验证，并补充说明后台权限与接口校验结果。测试重点放在输入合法性、业务返回结果和角色访问边界是否满足系统设计要求。'
    376 = '认证与会话模块是系统安全入口，重点验证账号密码登录、重复注册拦截、refreshToken 校验和异常设备识别等链路是否正确。表5-1 所示用例既覆盖正常登录，也覆盖错误密码、重复注册和异常设备等异常场景。'
    386 = '该模块测试主要验证帖子发布参数校验、内容入库和标签提取链路是否正常工作，并检查标题或正文为空等异常输入时系统是否能够阻止提交。'
    393 = '该模块主要验证推荐接口在登录态与冷启动场景下的返回差异，以及推荐页面能否根据接口结果完成正常渲染和展示。'
    405 = '通过对用户、内容、互动、治理和统计五大业务域的逻辑抽象，本系统将帖子、评论、点赞、收藏、举报、通知、版主审核与趋势分析统一在同一业务框架下，并利用 Redis 缓存保障热度排行与 Token 鉴权的高并发响应能力。经测试，系统各功能接口均符合预期设计要求，能够稳定支撑帖子发布、内容治理、个性化推荐及趋势可视化分析等核心业务逻辑。'
    445 = '首先，我要特别感谢我的指导教师吴亚明老师。在整个毕业设计的选题、系统架构搭建及论文撰写过程中，吴老师给予了我极大的支持。他严谨的治学态度和深厚的专业造诣令我受益匪浅。特别是在系统功能梳理、社区治理模块设计和论文修改的关键阶段，吴老师敏锐的指导帮助我不断校正研究方向，确保项目与论文能够持续完善。'
}

if (Test-Path -LiteralPath $workDir) {
    Remove-Item -LiteralPath $workDir -Recurse -Force
}

New-Item -ItemType Directory -Path $workDir | Out-Null
Copy-Item -LiteralPath $docxPath -Destination (Join-Path $workDir 'thesis7.zip')
Expand-Archive -LiteralPath (Join-Path $workDir 'thesis7.zip') -DestinationPath $unzippedDir -Force

$doc = New-Object System.Xml.XmlDocument
$doc.PreserveWhitespace = $true
$doc.Load($xmlPath)

$ns = New-Object System.Xml.XmlNamespaceManager($doc.NameTable)
$ns.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')

$paragraphs = $doc.SelectNodes('//w:body/w:p', $ns)

foreach ($entry in $replacements.GetEnumerator()) {
    $index = [int]$entry.Key
    $text = [string]$entry.Value
    $paragraph = $paragraphs.Item($index)
    if ($null -eq $paragraph) {
        throw "Paragraph index $index not found."
    }

    $textNodes = $paragraph.SelectNodes('.//w:t', $ns)
    if ($null -eq $textNodes -or $textNodes.Count -eq 0) {
        throw "Paragraph index $index has no text nodes."
    }

    $first = $textNodes.Item(0)
    $first.InnerText = $text

    if ($text.StartsWith(' ') -or $text.EndsWith(' ')) {
        $spaceAttr = $first.Attributes.GetNamedItem('xml:space')
        if ($null -eq $spaceAttr) {
            $spaceAttr = $doc.CreateAttribute('xml', 'space', 'http://www.w3.org/XML/1998/namespace')
            $first.Attributes.Append($spaceAttr) | Out-Null
        }
        $spaceAttr.Value = 'preserve'
    }

    for ($i = 1; $i -lt $textNodes.Count; $i++) {
        $textNodes.Item($i).InnerText = ''
    }
}

$doc.Save($xmlPath)

if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

Add-Type -AssemblyName 'System.IO.Compression.FileSystem'
[System.IO.Compression.ZipFile]::CreateFromDirectory($unzippedDir, $zipPath)
Copy-Item -LiteralPath $zipPath -Destination $docxPath -Force

Write-Output "Updated: $docxPath"
