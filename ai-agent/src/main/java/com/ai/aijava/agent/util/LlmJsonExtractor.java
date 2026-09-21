package com.ai.aijava.agent.util;

/**
 * LLM 输出 JSON 提取工具：剥离 markdown 代码围栏，截取首个 JSON 数组/对象
 * LLM 即使被要求"仅输出 JSON"也常带 ```json 围栏或解释性文字，需防御性清洗
 */
public final class LlmJsonExtractor {

    private LlmJsonExtractor() {
    }

    /**
     * 提取首个 JSON 数组文本；找不到返回 "[]"
     */
    public static String extractArray(String raw) {
        return extract(raw, '[', ']', "[]");
    }

    /**
     * 提取首个 JSON 对象文本；找不到返回 "{}"
     */
    public static String extractObject(String raw) {
        return extract(raw, '{', '}', "{}");
    }

    private static String extract(String raw, char open, char close, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String text = raw.replace("```json", "").replace("```", "").trim();
        int start = text.indexOf(open);
        int end = text.lastIndexOf(close);
        if (start < 0 || end <= start) {
            return fallback;
        }
        return text.substring(start, end + 1);
    }
}
