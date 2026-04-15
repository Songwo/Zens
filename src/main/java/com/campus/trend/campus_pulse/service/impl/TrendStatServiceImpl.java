package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.TrendStatService;
import com.campus.trend.campus_pulse.utils.TimeRangeUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrendStatServiceImpl implements TrendStatService {

    private static final int KEYWORD_SOURCE_LIMIT = 100;
    private static final int KEYWORD_RESULT_LIMIT = 30;
    private static final int TAG_RELATION_SOURCE_LIMIT = 200;
    private static final int TAG_RELATION_RESULT_LIMIT = 15;
    private static final int PREDICTION_LIMIT = 5;

    private final PostMapper postMapper;
    private final SectionMapper sectionMapper;
    private final UserMapper userMapper;

    @Override
    public Map<String, Object> getKeywordCloud() {
        List<Post> posts = postMapper.selectList(
                new QueryWrapper<Post>()
                        .select("tags")
                        .isNotNull("tags")
                        .last("LIMIT " + KEYWORD_SOURCE_LIMIT));

        Map<String, Integer> tagFreq = new HashMap<>();
        for (Post post : posts) {
            for (String tag : splitTags(post.getTags())) {
                tagFreq.merge(tag, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> keywords = tagFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(KEYWORD_RESULT_LIMIT)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("keyword", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("keywords", keywords);
        return result;
    }

    @Override
    public List<Map<String, Object>> getPostTrend(int days) {
        int limit = (days > 0 && days <= 90) ? days : 7;
        return postMapper.selectMaps(
                new QueryWrapper<Post>()
                        .select("DATE_FORMAT(create_time, '%Y-%m-%d') as date", "count(*) as count")
                        .ge("create_time", java.time.LocalDateTime.now().minusDays(limit))
                        .groupBy("DATE_FORMAT(create_time, '%Y-%m-%d')")
                        .orderByAsc("date"));
    }

    @Override
    public List<Map<String, Object>> getUserTrend() {
        return userMapper.selectMaps(
                new QueryWrapper<User>()
                        .select("DATE_FORMAT(create_time, '%Y-%m-%d') as date", "count(*) as count")
                        .groupBy("DATE_FORMAT(create_time, '%Y-%m-%d')")
                        .orderByAsc("date")
                        .last("LIMIT 7"));
    }

    @Override
    public List<Map<String, Object>> getTrendPrediction() {
        // Song：基于近7天 vs 前7天的帖子数量计算真实增长率
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime fourteenDaysAgo = now.minusDays(14);

        Map<String, Object> cloud = getKeywordCloud();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keywords = (List<Map<String, Object>>) cloud.getOrDefault("keywords",
                new ArrayList<>());

        List<Map<String, Object>> prediction = new ArrayList<>();
        int limit = Math.min(keywords.size(), PREDICTION_LIMIT);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> kw = keywords.get(i);
            String name = (String) kw.get("keyword");

            // Song：查近7天包含该标签的帖子数
            long recentCount = postMapper.selectCount(
                    new QueryWrapper<Post>()
                            .like("tags", name)
                            .ge("create_time", sevenDaysAgo));
            // Song：查前7天（7~14天前）包含该标签的帖子数
            long previousCount = postMapper.selectCount(
                    new QueryWrapper<Post>()
                            .like("tags", name)
                            .ge("create_time", fourteenDaysAgo)
                            .lt("create_time", sevenDaysAgo));

            double growthRate;
            if (previousCount == 0) {
                growthRate = recentCount > 0 ? 100.0 : 0.0;
            } else {
                growthRate = Math.round(((double)(recentCount - previousCount) / previousCount * 100) * 10) / 10.0;
            }

            String status = growthRate > 20 ? "rising" : (growthRate >= -10 ? "stable" : "falling");

            Map<String, Object> item = new HashMap<>();
            item.put("topic", name);
            item.put("growthRate", growthRate);
            item.put("recentCount", recentCount);
            item.put("status", status);
            item.put("insight", generateInsight(name, status));
            prediction.add(item);
        }
        // Song：按增长率降序排列
        prediction.sort((a, b) -> Double.compare((Double) b.get("growthRate"), (Double) a.get("growthRate")));
        return prediction;
    }

    @Override
    public Map<String, Object> getSectionPie() {
        List<Map<String, Object>> stats = postMapper.selectMaps(
                new QueryWrapper<Post>().select("section_id", "count(*) as count").groupBy("section_id"));

        Set<Long> sectionIds = stats.stream()
                .map(item -> parseLong(item.get("section_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> sectionNameMap = sectionIds.isEmpty()
                ? Collections.emptyMap()
                : sectionMapper.selectBatchIds(sectionIds).stream()
                        .collect(Collectors.toMap(Section::getId, Section::getName, (left, right) -> left));

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> item : stats) {
            Long sectionId = parseLong(item.get("section_id"));
            String sectionName = sectionId == null
                    ? "未分类"
                    : sectionNameMap.getOrDefault(sectionId, "未分类");
            result.put(sectionName, item.get("count"));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getHeatRank() {
        return getHeatRank(1, 10);
    }

    @Override
    public List<Map<String, Object>> getHeatRank(int page, int pageSize) {
        return getHeatRank(page, pageSize, null);
    }

    @Override
    public List<Map<String, Object>> getHeatRank(int page, int pageSize, String timeRange) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;

        QueryWrapper<Post> wrapper = new QueryWrapper<Post>()
                .eq("status", 1);

        LocalDateTime start = TimeRangeUtils.resolveRangeStart(timeRange);
        if (start != null) {
            wrapper.ge("last_activity_at", start)
                    .and(w -> w.gt("view_count", 0).or().gt("comment_count", 0));
        }

        wrapper.orderByDesc("heat_score")
                .orderByDesc("view_count")
                .orderByDesc("id")
                .last("LIMIT " + offset + ", " + safePageSize);

        List<Post> posts = postMapper.selectList(wrapper);

        List<Map<String, Object>> rank = new ArrayList<>();
        for (Post post : posts) {
            Map<String, Object> item = new HashMap<>();
            item.put("postId", post.getId());
            item.put("title", post.getTitle());
            item.put("heatScore", post.getHeatScore() != null ? post.getHeatScore() : 0.0);
            item.put("viewCount", post.getViewCount() != null ? post.getViewCount() : 0);
            rank.add(item);
        }
        return rank;
    }

    @Override
    public Map<String, Object> getCommunityDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        // 这类大屏接口刷新很频繁，能在 SQL 里一次聚合完，就别拆成好几次 count。
        Map<String, Object> postStats = firstRow(postMapper.selectMaps(new QueryWrapper<Post>()
                .select(
                        "COUNT(*) AS totalPosts",
                        "SUM(CASE WHEN DATE(create_time) = CURDATE() THEN 1 ELSE 0 END) AS todayPosts")));
        Map<String, Object> userStats = firstRow(userMapper.selectMaps(new QueryWrapper<User>()
                .select(
                        "COUNT(*) AS totalUsers",
                        "SUM(CASE WHEN DATE(create_time) = CURDATE() THEN 1 ELSE 0 END) AS todayUsers")));

        dashboard.put("totalPosts", toLong(postStats.get("totalPosts")));
        dashboard.put("todayPosts", toLong(postStats.get("todayPosts")));
        dashboard.put("totalUsers", toLong(userStats.get("totalUsers")));
        dashboard.put("todayUsers", toLong(userStats.get("todayUsers")));
        return dashboard;
    }

    @Override
    public Map<String, Object> getTagRelations(String keyword) {
        String normalizedKeyword = normalizeTag(keyword);
        if (normalizedKeyword == null) {
            return Map.of("keyword", "", "nodes", List.of());
        }

        List<Post> posts = postMapper.selectList(
                new QueryWrapper<Post>()
                        .select("tags")
                        .like("tags", normalizedKeyword)
                        .isNotNull("tags")
                        .last("LIMIT " + TAG_RELATION_SOURCE_LIMIT));

        Map<String, Integer> related = new HashMap<>();
        for (Post post : posts) {
            for (String tag : splitTags(post.getTags())) {
                if (!tag.equalsIgnoreCase(normalizedKeyword)) {
                    related.merge(tag, 1, Integer::sum);
                }
            }
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> center = new HashMap<>();
        center.put("name", normalizedKeyword);
        center.put("value", posts.size());
        nodes.add(center);

        related.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TAG_RELATION_RESULT_LIMIT)
                .forEach(e -> {
                    Map<String, Object> node = new HashMap<>();
                    node.put("name", e.getKey());
                    node.put("value", e.getValue());
                    nodes.add(node);
                });

        Map<String, Object> result = new HashMap<>();
        result.put("keyword", normalizedKeyword);
        result.put("nodes", nodes);
        return result;
    }

    @Override
    public Map<String, Object> getUserInsight(String userId) {
        Map<String, Object> stats = firstRow(postMapper.selectMaps(new QueryWrapper<Post>()
                .select(
                        "COUNT(*) AS postCount",
                        "COALESCE(SUM(view_count), 0) AS totalViews",
                        "COALESCE(SUM(like_count), 0) AS totalLikes",
                        "COALESCE(SUM(comment_count), 0) AS totalComments",
                        "COALESCE(SUM(collect_count), 0) AS totalCollects")
                .eq("user_id", userId)));

        Map<String, Object> insight = new HashMap<>();
        insight.put("postCount", toLong(stats.get("postCount")));
        insight.put("totalViews", toLong(stats.get("totalViews")));
        insight.put("totalLikes", toLong(stats.get("totalLikes")));
        insight.put("totalComments", toLong(stats.get("totalComments")));
        insight.put("totalCollects", toLong(stats.get("totalCollects")));
        return insight;
    }

    @Override
    public Map<String, Object> analyzeCodeSnippet(String code, String language) {
        Map<String, Object> result = new HashMap<>();

        if (code == null || code.isBlank()) {
            result.put("riskLevel", "safe");
            result.put("overallScore", 100);
            result.put("security", Map.of("risks", List.of(), "level", "safe"));
            result.put("quality", Map.of("totalLines", 0, "blankLineRate", 0.0, "commentRate", 0.0, "issues", List.of()));
            result.put("complexity", Map.of("cyclomaticComplexity", 0, "level", "low", "maxNestingDepth", 0));
            result.put("style", Map.of("issues", List.of(), "debugStatements", 0, "todoCount", 0));
            result.put("suggestions", List.of());
            return result;
        }

        String lang = language != null ? language.toLowerCase() : "unknown";
        String[] lines = code.split("\n", -1);
        int totalLines = lines.length;

        // ========== 1. 安全风险扫描 ==========
        List<Map<String, Object>> securityRisks = new ArrayList<>();
        // 通用
        checkPattern(securityRisks, code, "eval(", "代码注入", "使用 eval() 可能导致代码注入攻击", "high");
        checkPattern(securityRisks, code, "exec(", "命令执行", "使用 exec() 可能导致命令注入", "high");
        checkPattern(securityRisks, code, "innerHTML", "XSS风险", "使用 innerHTML 可能导致跨站脚本攻击", "medium");
        checkPattern(securityRisks, code, "document.write", "XSS风险", "使用 document.write 可能导致XSS", "medium");
        checkPattern(securityRisks, code, "password", "敏感信息", "代码中可能包含硬编码的密码", "low");
        // Java 特有
        if ("java".equals(lang) || "unknown".equals(lang)) {
            checkPattern(securityRisks, code, "Runtime.getRuntime()", "命令执行", "直接执行系统命令存在安全风险", "high");
            checkPattern(securityRisks, code, "ProcessBuilder", "命令执行", "通过 ProcessBuilder 执行命令需要严格校验输入", "medium");
            checkPattern(securityRisks, code, "System.exit", "危险操作", "System.exit() 会直接终止JVM进程", "high");
            checkPattern(securityRisks, code, "Runtime.exec", "命令执行", "Runtime.exec 可能导致命令注入", "high");
            checkPattern(securityRisks, code, "ObjectInputStream", "反序列化", "反序列化不可信数据可能导致远程代码执行", "high");
            checkPattern(securityRisks, code, "XMLParser", "XXE风险", "XML解析器可能受到XXE注入攻击", "medium");
            checkPattern(securityRisks, code, "Math.random()", "弱随机数", "Math.random() 不适合安全场景，应使用 SecureRandom", "low");
            checkPattern(securityRisks, code, "new File(", "文件操作", "文件操作需注意路径遍历风险", "low");
            checkPattern(securityRisks, code, "FileOutputStream", "文件操作", "文件写入操作需注意安全", "low");
        }
        // SQL
        if ("sql".equals(lang) || code.contains("SELECT") || code.contains("select")) {
            checkPattern(securityRisks, code, "DROP TABLE", "SQL危险", "DROP TABLE 会删除整张数据表", "high");
            checkPattern(securityRisks, code, "DELETE FROM", "SQL危险", "无条件 DELETE 可能清空数据", "medium");
        }
        // 弱哈希
        checkPattern(securityRisks, code, "MD5", "弱哈希", "MD5 已被认为不安全，建议使用 SHA-256 或更强算法", "low");
        checkPattern(securityRisks, code, "SHA1", "弱哈希", "SHA-1 已被认为不安全，建议升级到 SHA-256", "low");
        checkPattern(securityRisks, code, "SHA-1", "弱哈希", "SHA-1 已被认为不安全，建议升级到 SHA-256", "low");

        String securityLevel = "safe";
        for (Map<String, Object> risk : securityRisks) {
            String level = (String) risk.get("level");
            if ("high".equals(level)) { securityLevel = "high"; break; }
            else if ("medium".equals(level) && !"high".equals(securityLevel)) { securityLevel = "medium"; }
            else if ("low".equals(level) && "safe".equals(securityLevel)) { securityLevel = "low"; }
        }

        // ========== 2. 代码质量分析 ==========
        int blankLines = 0;
        int commentLines = 0;
        List<Map<String, Object>> qualityIssues = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blankLines++;
            } else if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("*")
                    || trimmed.startsWith("/*") || trimmed.startsWith("'''") || trimmed.startsWith("\"\"\"")) {
                commentLines++;
            }
        }

        double blankLineRate = totalLines > 0 ? Math.round((double) blankLines / totalLines * 100) / 100.0 : 0;
        double commentRate = totalLines > 0 ? Math.round((double) commentLines / totalLines * 100) / 100.0 : 0;

        if (totalLines > 200) {
            qualityIssues.add(Map.of("type", "文件过长", "description", "文件超过200行（共" + totalLines + "行），建议拆分"));
        }
        if (commentRate < 0.05 && totalLines > 20) {
            qualityIssues.add(Map.of("type", "注释不足", "description", "注释率仅 " + (int)(commentRate * 100) + "%，建议增加关键逻辑注释"));
        }

        // 魔法数字检测
        int magicNumbers = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue;
            if (trimmed.matches(".*[^\\w.]\\d{2,}[^\\w.].*") && !trimmed.contains("import") && !trimmed.contains("package")) {
                magicNumbers++;
            }
        }
        if (magicNumbers > 3) {
            qualityIssues.add(Map.of("type", "魔法数字", "description", "发现 " + magicNumbers + " 处疑似魔法数字，建议提取为命名常量"));
        }

        // ========== 3. 复杂度评估 ==========
        String[] complexityKeywords = {"if ", "if(", "for ", "for(", "while ", "while(", "switch ", "switch(",
                "case ", "catch ", "catch(", "&&", "||", "? "};
        int cyclomaticComplexity = 1;
        int maxNestingDepth = 0;
        int currentNesting = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            for (String kw : complexityKeywords) {
                if (trimmed.contains(kw)) {
                    cyclomaticComplexity++;
                    break;
                }
            }
            for (char c : line.toCharArray()) {
                if (c == '{') currentNesting++;
                if (c == '}') currentNesting = Math.max(0, currentNesting - 1);
            }
            maxNestingDepth = Math.max(maxNestingDepth, currentNesting);
        }

        String complexityLevel;
        if (cyclomaticComplexity <= 5) complexityLevel = "low";
        else if (cyclomaticComplexity <= 10) complexityLevel = "medium";
        else if (cyclomaticComplexity <= 20) complexityLevel = "high";
        else complexityLevel = "very_high";

        // ========== 4. 代码风格检查 ==========
        List<Map<String, Object>> styleIssues = new ArrayList<>();
        int debugStatements = 0;
        int todoCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("console.log") || trimmed.contains("System.out.println")
                    || trimmed.contains("System.out.print(") || trimmed.contains("System.err.print")) {
                debugStatements++;
            }
            if (trimmed.contains("TODO") || trimmed.contains("FIXME") || trimmed.contains("HACK") || trimmed.contains("XXX")) {
                todoCount++;
            }
        }

        if (debugStatements > 0) {
            styleIssues.add(Map.of("type", "调试语句", "description", "发现 " + debugStatements + " 处调试输出语句，发布前请移除"));
        }
        if (todoCount > 0) {
            styleIssues.add(Map.of("type", "待办标记", "description", "发现 " + todoCount + " 处 TODO/FIXME/HACK 标记"));
        }
        if (maxNestingDepth > 4) {
            styleIssues.add(Map.of("type", "嵌套过深", "description", "最大嵌套深度为 " + maxNestingDepth + " 层，建议控制在4层以内"));
        }

        // 检测命名风格混用
        boolean hasCamelCase = code.matches("(?s).*[a-z][A-Z].*");
        boolean hasSnakeCase = code.matches("(?s).*[a-z]_[a-z].*");
        if (hasCamelCase && hasSnakeCase) {
            styleIssues.add(Map.of("type", "命名风格混用", "description", "代码中同时存在 camelCase 和 snake_case 命名风格，建议统一"));
        }

        // ========== 5. 综合评分与建议 ==========
        int score = 100;
        // 安全扣分
        for (Map<String, Object> risk : securityRisks) {
            String level = (String) risk.get("level");
            if ("high".equals(level)) score -= 15;
            else if ("medium".equals(level)) score -= 8;
            else score -= 3;
        }
        // 复杂度扣分
        if ("high".equals(complexityLevel)) score -= 10;
        else if ("very_high".equals(complexityLevel)) score -= 20;
        // 质量扣分
        score -= qualityIssues.size() * 5;
        // 风格扣分
        score -= debugStatements * 3;
        score -= todoCount * 1;
        if (maxNestingDepth > 4) score -= 5;

        score = Math.max(0, Math.min(100, score));

        List<String> suggestions = new ArrayList<>();
        if (!"safe".equals(securityLevel)) suggestions.add("请关注安全风险项，修复高危漏洞");
        if (maxNestingDepth > 4) suggestions.add("减少代码嵌套深度，可通过提前返回或提取方法简化");
        if (debugStatements > 0) suggestions.add("移除调试语句（console.log / System.out.println）");
        if (magicNumbers > 3) suggestions.add("将魔法数字提取为有意义的常量");
        if (commentRate < 0.05 && totalLines > 20) suggestions.add("增加关键业务逻辑的注释说明");
        if ("very_high".equals(complexityLevel)) suggestions.add("圈复杂度过高，建议拆分为更小的函数");
        if (suggestions.isEmpty()) suggestions.add("代码质量良好，继续保持！");

        // ========== 组装结果 ==========
        result.put("riskLevel", securityLevel);
        result.put("overallScore", score);
        result.put("language", lang);
        result.put("security", Map.of("risks", securityRisks, "level", securityLevel));
        result.put("quality", Map.of(
                "totalLines", totalLines,
                "blankLineRate", blankLineRate,
                "commentRate", commentRate,
                "issues", qualityIssues));
        result.put("complexity", Map.of(
                "cyclomaticComplexity", cyclomaticComplexity,
                "level", complexityLevel,
                "maxNestingDepth", maxNestingDepth));
        result.put("style", Map.of(
                "issues", styleIssues,
                "debugStatements", debugStatements,
                "todoCount", todoCount));
        result.put("suggestions", suggestions);

        return result;
    }

    private void checkPattern(List<Map<String, Object>> risks, String code,
                              String pattern, String type, String description, String level) {
        if (code.contains(pattern)) {
            Map<String, Object> risk = new HashMap<>();
            risk.put("type", type);
            risk.put("pattern", pattern);
            risk.put("description", description);
            risk.put("level", level);
            risks.add(risk);
        }
    }

    private String generateInsight(String topic, String status) {
        return switch (status) {
            case "rising" -> "话题「" + topic + "」讨论热度正在急剧上升，建议重点关注。";
            case "stable" -> "「" + topic + "」保持稳定讨论热度，内容趋于高质量深度交流。";
            default -> "「" + topic + "」热度有所回落，讨论焦点可能发生转移。";
        };
    }

    private List<String> splitTags(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rawTags.split("[,，#\\s]+"))
                .map(this::normalizeTag)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizeTag(String tag) {
        if (tag == null) {
            return null;
        }
        String normalized = tag.replace("#", "").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.get(0);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
