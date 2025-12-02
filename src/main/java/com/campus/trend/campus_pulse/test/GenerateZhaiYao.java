package com.campus.trend.campus_pulse.test;

import com.hankcs.hanlp.HanLP;

import java.util.List;

public class GenerateZhaiYao {

    public static void main(String[] args) {
        String text = "CampusPulse 是一个面向高校的智能化内容与趋势分析平台后端系统。" +
                "该系统采用现代化的技术架构，实现了用户认证、个人信息管理、内容发布等核心功能，" +
                "并通过收集用户行为数据，为后续的数据分析模块（热度排行、词云展示、个性化推荐等）" +
                "提供坚实的基础。";

        List<String> summary = HanLP.extractSummary(text,5);

        summary.forEach(System.out::println);
    }


}
