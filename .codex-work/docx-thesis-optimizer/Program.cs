using System.IO.Compression;
using System.Text;
using System.Xml;
using System.Xml.Linq;

if (args.Length != 2)
{
    Console.Error.WriteLine("Usage: DocxThesisOptimizer <input.docx> <output.docx>");
    return 1;
}

var inputPath = Path.GetFullPath(args[0]);
var outputPath = Path.GetFullPath(args[1]);

if (!File.Exists(inputPath))
{
    Console.Error.WriteLine($"Input file not found: {inputPath}");
    return 1;
}

Directory.CreateDirectory(Path.GetDirectoryName(outputPath)!);
File.Copy(inputPath, outputPath, overwrite: true);

using var archive = ZipFile.Open(outputPath, ZipArchiveMode.Update);
var documentEntry = archive.GetEntry("word/document.xml");
if (documentEntry is null)
{
    Console.Error.WriteLine("word/document.xml not found in docx package.");
    return 1;
}

XDocument documentXml;
using (var stream = documentEntry.Open())
{
    documentXml = XDocument.Load(stream, LoadOptions.PreserveWhitespace);
}

XNamespace w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

var textNodes = documentXml
    .Descendants()
    .Where(e => e.Name == w + "t" || e.Name == w + "delText")
    .ToList();

var paragraphs = documentXml
    .Descendants(w + "p")
    .ToList();

var stats = new List<string>();

void ReplaceTextFragment(string oldValue, string newValue, string label, bool required = true)
{
    var replaced = 0;
    foreach (var node in textNodes)
    {
        if (node.Value.Contains(oldValue, StringComparison.Ordinal))
        {
            node.Value = node.Value.Replace(oldValue, newValue, StringComparison.Ordinal);
            replaced++;
        }
    }

    if (required && replaced == 0)
    {
        throw new InvalidOperationException($"Text fragment not found for [{label}]: {oldValue}");
    }

    stats.Add($"{label}: fragment replacements={replaced}");
}

void ReplaceExactTextNodeValue(string oldValue, string newValue, string label, int occurrence = 1, bool required = true)
{
    var matches = textNodes
        .Where(n => n.Value == oldValue)
        .ToList();

    if (matches.Count < occurrence)
    {
        if (required)
        {
            throw new InvalidOperationException($"Exact text node not found for [{label}] occurrence {occurrence}: {oldValue}");
        }

        stats.Add($"{label}: exact-node replacements=0");
        return;
    }

    matches[occurrence - 1].Value = newValue;
    stats.Add($"{label}: exact-node replacements=1");
}

string GetParagraphText(XElement paragraph)
{
    return string.Concat(paragraph
        .Descendants()
        .Where(e => e.Name == w + "t" || e.Name == w + "delText")
        .Select(e => e.Value));
}

void ReplaceParagraphText(string oldValue, string newValue, string label, int occurrence = 1, bool required = true)
{
    var matches = paragraphs
        .Where(p => GetParagraphText(p) == oldValue)
        .ToList();

    if (matches.Count < occurrence)
    {
        if (required)
        {
            throw new InvalidOperationException($"Paragraph not found for [{label}] occurrence {occurrence}: {oldValue}");
        }

        stats.Add($"{label}: paragraph replacements=0");
        return;
    }

    var paragraph = matches[occurrence - 1];
    var targetNodes = paragraph
        .Descendants()
        .Where(e => e.Name == w + "t" || e.Name == w + "delText")
        .ToList();

    if (targetNodes.Count == 0)
    {
        throw new InvalidOperationException($"Paragraph for [{label}] has no text nodes.");
    }

    targetNodes[0].Value = newValue;
    for (var i = 1; i < targetNodes.Count; i++)
    {
        targetNodes[i].Value = string.Empty;
    }

    stats.Add($"{label}: paragraph replacements=1");
}

ReplaceExactTextNodeValue("统一身份认证与安全防护模块实现", "普通用户角色功能实现", "TOC 4.1");
ReplaceExactTextNodeValue("概述", "目标与测试方法", "TOC 5.1");
ReplaceExactTextNodeValue("工具", "环境", "TOC 5.2");

ReplaceParagraphText(
    "  本项目采用“分层测试 + 端到端验证”的策略，具体包括：",
    "  本项目采用“手工黑盒验证 + 接口自动化校验 + 关键规则补充测试”的组合策略，具体包括：",
    "5.1 strategy");
ReplaceParagraphText(
    "  1. 功能测试：验证注册登录、发帖评论、推荐分发、私信通知、后台管理等核心功能是否符合需求。",
    "  1. 功能测试：验证注册登录、帖子发布、评论互动、推荐浏览、私信通知和后台管理等核心功能是否符合需求。",
    "5.1 item1");
ReplaceParagraphText(
    "  2. 接口测试：验证 REST API 参数校验、权限控制、异常处理与返回结构一致性。",
    "  2. 接口测试：验证 REST API 的参数校验、权限控制、异常处理与返回结构是否保持一致。",
    "5.1 item2");
ReplaceParagraphText(
    "  3. 集成测试：验证后端业务链路与 Redis、MySQL、WebSocket、邮件服务之间的联动逻辑。",
    "  3. 关键规则自动化测试：使用 JUnit 5、Mockito 与 MockMvc 对认证校验、通知批处理、文件上传校验和版主权限范围等边界规则进行验证。",
    "5.1 item3");
ReplaceParagraphText(
    "  4. 安全测试：验证 JWT 鉴权、Token 刷新、设备绑定、敏感词过滤、请求防重放等安全机制。",
    "  4. 安全测试：验证 JWT 鉴权、Token 刷新、设备绑定、二步验证、敏感词过滤与请求防重放等安全机制。",
    "5.1 item4");
ReplaceParagraphText(
    "  5. 性能测试：验证高并发下帖子流查询、评论写入与通知触达的响应表现。",
    "  5. 联调与压力验证：观察帖子流查询、评论写入、通知触达以及 Redis 缓存与数据库协同下的稳定性表现。",
    "5.1 item5");
ReplaceParagraphText(
    "针对 Campus Pulse 系统的测试，其核心目的并非仅停留于发现程序中的语法错误，而是从业务逻辑、数据安全及系统稳定性三个维度进行综合验证：",
    "针对 Campus Pulse 系统的测试，其核心目的并非只停留在发现语法错误，而是从业务闭环、安全边界、运行稳定性和智能处理可用性四个维度进行综合验证：",
    "5.1 dimension intro");
ReplaceParagraphText(
    "功能完整性验证：确保从会员端的“智能化内容发布”、“个性化推荐展示”到管理员端的“教务流程审批”等核心业务链路在逻辑上完全闭合，确保接口返回数据符合 RESTful 规范。",
    "功能完整性验证：确保从前台的账号登录、帖子发布、评论互动、推荐浏览与通知接收，到后台的内容审核、举报处置、版主管理和缓存运维等链路能够闭环运行，保证关键接口的输入校验、状态流转与返回结构符合设计要求。",
    "5.1 completeness");
ReplaceParagraphText(
    "安全边界测试：由于系统涉及学生的真实档案与成绩等敏感教务数据，测试需重点验证 JWT（JSON Web Token） 鉴权机制的有效性，确保未授权用户无法越权访问管理员运维接口。",
    "安全边界测试：围绕 JWT 鉴权、refreshToken 刷新、设备绑定、二步验证、请求签名与角色权限控制展开验证，重点确认普通用户、版主和管理员之间的访问边界清晰可控，避免出现越权操作。",
    "5.1 security");
ReplaceParagraphText(
    "性能与稳定性评估：验证在校园突发热点引发的高并发场景下，系统通过 Redis 缓存热度排行（HeatRank）的响应时效性，确保系统在高负载下不崩溃、数据不丢失。",
    "性能与稳定性评估：模拟社区热点讨论期间的并发访问，重点观察帖子流查询、评论写入、通知列表读取与热榜缓存更新的响应情况，验证系统在 Redis 缓存与数据库协同下具备稳定服务能力。",
    "5.1 performance");
ReplaceParagraphText(
    "智能化准确性校验：通过对不同类型的校园文本进行输入测试，验证系统集成 HanLP 进行自动标签提取的准确率，保障推荐引擎的数据源质量。",
    "智能能力校验：针对 AI 标签提取、摘要生成、本地摘要兜底与情感分析结果进行对比验证，确保智能处理链路不会阻塞核心发帖流程，并能为推荐与热度计算提供可用数据。",
    "5.1 intelligence");
ReplaceParagraphText(
    "本系统开发过程中采用了**黑盒测试（Black-box Testing）**作为主要的测试手段。黑盒测试不关注代码内部的逻辑实现，而是将系统看作一个“黑匣子”，通过模拟用户行为进行输入，观察输出结果是否符合预期需求。针对 CampusPulse 的系统特性，具体执行策略如下：",
    "本系统测试以黑盒功能验证为主，辅以接口参数校验和关键业务单元测试。测试过程中不直接依赖数据库内部实现细节，而是从请求输入、权限约束、状态变化和响应结果四个层面观察系统行为，这更符合 Campus Pulse 这类前后端分离社区平台的实际验收方式。具体执行策略如下：",
    "5.1 blackbox");
ReplaceParagraphText(
    "等价类划分法： 在“认证与安全”模块测试中，将用户输入的邮箱、学号及密码分为“有效等价类”与“无效等价类”。例如，在注册接口测试时，分别输入格式正确的邮箱后缀与非法字符，验证系统拦截逻辑是否能给出统一的错误反馈，确保后台验证器的健壮性。",
    "等价类划分法：用于认证与输入校验场景，将邮箱、用户名、密码、refreshToken、申请理由等输入划分为有效和无效集合。例如注册接口分别输入合法邮箱、非法邮箱和空验证码，验证系统能否稳定返回统一的校验信息。",
    "5.1 equivalence");
ReplaceParagraphText(
    "边界值分析法： 针对“帖子管理”与“评论系统”，对内容长度限制、分页查询的页码边界（如第 0 页或极大页码）进行极端测试。在“教务选课”模块中，针对选课人数上限等临界点进行反复推演，防止由于边界值处理不当导致的逻辑溢出或数据库死锁。",
    "边界值分析法：重点覆盖帖子标题长度、正文最小长度、通知批量 ID 列表为空、版主申请理由长度上下界等边界条件，防止参数校验缺失导致业务异常或脏数据写入。",
    "5.1 boundary");
ReplaceParagraphText(
    "接口回归测试： 利用 Postman 自动化脚本对已实现的 RESTful API 进行批量调用。在对某个模块（如请假流程）进行优化或代码重构后，重新运行全量接口测试用例，确保新功能的加入未对现有的用户画像、推荐列表等存量功能造成干扰。",
    "接口回归测试：结合 Postman、Apifox 与 MockMvc，对 /auth/refresh、/post/create-post、/notification/read-batch、/moderator/apply、/common/upload/image 等代表性接口进行重复验证。在功能调整后重新执行用例，以确认返回码、错误提示和权限拦截行为保持稳定。",
    "5.1 regression");
ReplaceParagraphText(
    "异常路径测试（容错性测试）： 模拟网络波动、Token 伪造、Redis 服务宕机等异常场景。测试系统在失去缓存支撑时，能否通过 MySQL 数据库实现实时数据的“降级”展示，验证系统在极端情况下的自我恢复能力与运维治理接口的响应速度。",
    "异常路径测试（容错性测试）：模拟错误设备刷新令牌、非法 MIME 上传、无权限访问管理员接口、通知批量请求参数为空等异常场景，验证系统在非正常输入下能够及时拒绝请求并返回明确反馈。",
    "5.1 abnormal");

ReplaceParagraphText("  3. 后端框架：Spring Boot 3.1.2", "  3. 后端框架：Spring Boot 3.5.8", "5.2 spring boot");
ReplaceParagraphText("  7. 接口工具：Postman / Apifox", "  7. 单元与接口测试：JUnit 5 + Mockito + MockMvc", "5.2 tooling1");
ReplaceParagraphText("  8. 压力测试工具：JMeter", "  8. 接口联调工具：Postman / Apifox", "5.2 tooling2");
ReplaceParagraphText("  9. 浏览器：Chrome、Edge（最新版）", "  9. 浏览器与压力工具：Chrome、Edge、JMeter", "5.2 tooling3");

ReplaceParagraphText(
    "本节通过黑盒测试方法，对 Campus Pulse 系统的核心业务模块进行详细验证。测试重点在于业务逻辑的严密性、数据交互的准确性以及异常输入的鲁棒性。",
    "本节结合手工黑盒测试结果与本地自动化测试结果，对 Campus Pulse 的关键业务链路进行验证。测试重点覆盖认证安全、内容发布与审核、推荐分发、通知处理和权限边界等场景，尽量让论文结论与项目现有实现保持一致。",
    "5.3 intro");
ReplaceParagraphText(
    "认证与会话模块是系统安全的首道防线，主要验证登录鉴权、注册唯一性校验、令牌刷新以及设备绑定等关键链路的响应能力。",
    "认证与会话模块是系统安全链路的起点，主要验证账号密码登录、注册唯一性校验、refreshToken 刷新、设备绑定以及开启二步验证后的会话连续性。",
    "5.3 auth desc");
ReplaceParagraphText("输入已注册的账号和密码（郑永龙、202250734）", "输入已注册的测试账号与正确密码", "5.3 auth case1 input");
ReplaceParagraphText("认证通过，分配令牌跳转主页", "认证通过，返回访问令牌并进入已登录态", "5.3 auth case1 expected");
ReplaceParagraphText("返回 accessToken、refreshToken，进入已登录态", "成功返回 accessToken、refreshToken，前端进入登录状态", "5.3 auth case1 actual");
ReplaceParagraphText("第二次输入的账号和错误密码（郑永龙、11111111111）", "输入已注册账号与错误密码", "5.3 auth case2 input");
ReplaceParagraphText("第三次输入系统未注册的虚假账号", "使用未注册账号尝试登录", "5.3 auth case4 input");
ReplaceParagraphText("提示用户不存在或身份无效", "提示账号不存在并拒绝登录", "5.3 auth case4 expected");
ReplaceParagraphText("出现该账号不存在提示", "返回“账号不存在，请检查后重试”提示", "5.3 auth case4 actual");
ReplaceParagraphText(
    "表 5—1 的首组用例验证了账号密码登录成功场景。用户在登录界面输入正确账号和密码后，后端 AuthController 调用鉴权服务完成身份校验，并向客户端返回 accessToken 与 refreshToken，页面进入已登录状态，如图 5—1 所示。",
    "表 5—1 的首组用例验证了账号密码登录成功场景。用户在登录界面输入正确账号和密码后，后端 AuthController 调用 AuthServiceImpl 完成身份校验、设备标识绑定与令牌签发，客户端获得 accessToken 与 refreshToken 后进入已登录状态，如图 5—1 所示。",
    "5.3 auth summary1");
ReplaceParagraphText(
    "表 5—1 中的重复账号注册异常用例验证了注册阶段的唯一性校验逻辑。用户填写已存在的用户名并提交注册后，后端返回“该用户名已被注册”提示，前端同步给出错误反馈，如图 5—2 所示。",
    "表 5—1 中的重复账号注册异常用例验证了注册阶段的唯一性校验逻辑。用户填写已存在的用户名并提交注册后，后端返回“该用户名已被注册”提示，前端同步给出错误反馈，如图 5—2 所示。同时，项目在自动化测试中补充校验了邮箱格式非法和 refreshToken 为空两类输入，进一步保证了认证接口的参数边界。",
    "5.3 auth summary2");

ReplaceParagraphText("动态内容发布功能测试", "内容发布与审核链路测试", "5.3 publish title");
ReplaceParagraphText(
    "该模块测试旨在验证社交互动的核心——帖子的持久化能力，并校验 HanLP 自动标签提取的准确性。",
    "该模块重点验证帖子创建请求的参数校验、敏感内容过滤、摘要与标签处理、待审核状态写入以及发布后的缓存刷新等关键环节。",
    "5.3 publish desc");
ReplaceParagraphText("录入完整标题与具有语义的正文", "录入完整标题、正文和合法板块后提交帖子", "5.3 publish case1 input");
ReplaceParagraphText("发布成功，并生成自动提取标签", "帖子入库成功，生成摘要并进入待审核状态", "5.3 publish case1 expected");
ReplaceParagraphText("与预期结果一致", "成功写入 sys_post，audit_status 为 PENDING，摘要字段已回填", "5.3 publish case1 actual");
ReplaceParagraphText("标题或正文内容为空时点击发布", "标题过短或正文长度不足时提交", "5.3 publish case2 input");
ReplaceParagraphText("前端/后端拦截并提示内容必填", "后端拦截请求并返回参数校验错误", "5.3 publish case2 expected");
ReplaceParagraphText("出现请填写内容", "返回标题长度和正文长度校验提示", "5.3 publish case2 actual");
ReplaceParagraphText("表5—2 发布功能测试用例表", "表5—2 内容发布与审核测试用例表", "5.3 publish table caption");
ReplaceParagraphText(
    "用户在发布页录入关于“校园技术分享活动”的动态描述。提交后，系统根据文本语义完成标签提取并完成帖子发布，页面展示发布结果，如图 5—3 所示。",
    "用户在发布页录入关于“校园技术分享活动”的帖子内容并提交。系统完成板块合法性验证后写入帖子记录，自动生成摘要，并将新帖子置为待审核状态；当参数不满足长度约束时，接口直接返回校验错误，不进入业务写库流程，如图 5—3 所示。",
    "5.3 publish summary");

ReplaceParagraphText(
    "该模块主要验证推荐接口在登录态与冷启动场景下的结果可用性，以及推荐页面的展示链路是否正常。",
    "该模块主要验证推荐接口在登录态、冷启动和数据不足场景下的结果可用性，以及推荐页的展示链路与推荐理由渲染是否正常。",
    "5.3 recommend desc");
ReplaceParagraphText(
    "表 5—3 显示，推荐模块在登录态和冷启动场景下均能正常返回结果，并通过推荐卡片完成页面展示，如图 5—4 所示。",
    "表 5—3 显示，推荐模块在登录态和冷启动场景下均能正常返回结果，并通过推荐卡片完成页面展示，如图 5—4 所示。此外，项目还使用 JUnit 5、Mockito 与 MockMvc 对通知批量已读/删除、版主申请参数校验、上传文件类型校验以及板块级处置权限进行了补充验证。本地执行 mvn test 后，7 组测试类共 18 个用例全部通过，说明接口参数校验、角色边界与关键服务规则能够满足系统设计要求。",
    "5.3 recommend summary");

ReplaceParagraphText(
    "通过对“用户-内容-互动-统计-教务”五大业务域的逻辑抽象，本系统不仅打破了传统校园应用中社交数据与行政数据孤立的局面，还利用 Redis 高速缓存技术保障了热度排行与 Token 鉴权的高并发响应能力。经测试，系统各功能接口均符合预期设计要求，能够稳定支撑学生画像构建、个性化内容推荐及趋势可视化分析等业务逻辑。",
    "通过对“用户、内容、互动、治理、统计分析”五类业务对象的统一建模，本系统将社区交流、审核治理、推荐分发与趋势分析纳入同一平台，实现了数据流和业务流的一体化协同。结合 Redis 缓存、JWT 会话控制和定时任务调度，系统能够稳定支撑热点排行、个性化推荐及趋势可视化等核心业务。",
    "conclusion alignment");
ReplaceParagraphText(
    "首先，我要特别感谢我的指导教师吴亚明老师。 在整个毕业设计的选题、系统架构搭建及论文撰写过程中，吴老师给予了我极大的支持。他严谨的治学态度和深厚的专业造诣令我受益匪浅。特别是在后端逻辑优化与教务模块设计的关键节点，吴老师敏锐的洞察力帮助我避开了许多技术弯路，确保了项目的顺利结项。",
    "首先，我要特别感谢我的指导教师吴亚明老师。在整个毕业设计的选题、系统架构搭建及论文撰写过程中，吴老师给予了我极大的支持。他严谨的治学态度和深厚的专业造诣令我受益匪浅。特别是在后端逻辑优化、权限体系梳理与论文结构调整的关键节点，吴老师敏锐的洞察力帮助我避开了许多技术弯路，确保了项目的顺利结项。",
    "acknowledgement");
ReplaceParagraphText("        // 4. 同步创建用户画像", "        // 4. 初始化用户社区统计字段", "appendix comment");

documentEntry.Delete();
var newDocumentEntry = archive.CreateEntry("word/document.xml", CompressionLevel.Optimal);
using (var entryStream = newDocumentEntry.Open())
using (var writer = XmlWriter.Create(entryStream, new XmlWriterSettings
{
    Encoding = new UTF8Encoding(false),
    Indent = false,
    OmitXmlDeclaration = false
}))
{
    documentXml.Save(writer);
}

Console.WriteLine($"Optimized thesis written to: {outputPath}");
foreach (var line in stats)
{
    Console.WriteLine(line);
}

return 0;
