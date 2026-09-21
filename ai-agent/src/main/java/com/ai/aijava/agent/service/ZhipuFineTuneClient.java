package com.ai.aijava.agent.service;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 智谱微调 API 客户端（OpenAI 风格 REST，HTTP 直调无额外 SDK 依赖）
 * 流程：上传 JSONL（/files，purpose=fine-tune）→ 创建任务（/fine_tuning/jobs）→ 轮询/取消
 * 底层走 JDK HttpClient（JdkClientHttpRequestFactory），规避 CDN 对默认 HttpURLConnection 的拦截
 */
@Slf4j
@Component
public class ZhipuFineTuneClient {

    private final AgentProperties agentProperties;
    private final RestTemplate restTemplate;

    public ZhipuFineTuneClient(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
        this.restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());
    }

    /**
     * 上传训练文件，返回智谱文件 ID（file-xxx）
     */
    public String uploadFile(Path jsonlPath) {
        String url = baseUrl() + "/files";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(jsonlPath.toFile()));
        body.add("purpose", "fine-tune");
        Map<String, Object> result = exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers));
        String fileId = (String) result.get("id");
        if (fileId == null || fileId.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "智谱文件上传响应缺少文件 ID");
        }
        log.info("微调训练文件已上传 path={} fileId={}", jsonlPath, fileId);
        return fileId;
    }

    /**
     * 创建微调任务，返回智谱任务 ID
     */
    public String createJob(String baseModel, String trainingFileId, String suffix, Map<String, Object> hyperparams) {
        String url = baseUrl() + "/fine_tuning/jobs";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("model", baseModel);
        body.put("training_file", trainingFileId);
        if (suffix != null && !suffix.isBlank()) {
            body.put("suffix", suffix);
        }
        if (hyperparams != null && !hyperparams.isEmpty()) {
            body.put("hyperparameters", hyperparams);
        }
        Map<String, Object> result = exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers));
        String jobId = (String) result.get("id");
        if (jobId == null || jobId.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "智谱微调任务创建响应缺少任务 ID");
        }
        log.info("微调任务已创建 jobId={} baseModel={} fileId={}", jobId, baseModel, trainingFileId);
        return jobId;
    }

    /**
     * 查询微调任务状态（status: validating_files/queued/running/succeeded/failed/cancelled）
     */
    public Map<String, Object> queryJob(String zhipuJobId) {
        String url = baseUrl() + "/fine_tuning/jobs/" + zhipuJobId;
        return exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders()));
    }

    /**
     * 取消微调任务（失败仅记日志，由调用方落库状态）
     */
    public void cancelJob(String zhipuJobId) {
        if (zhipuJobId == null || zhipuJobId.isBlank()) {
            return;
        }
        try {
            String url = baseUrl() + "/fine_tuning/jobs/" + zhipuJobId + "/cancel";
            exchange(url, HttpMethod.POST, new HttpEntity<>(authHeaders()));
        } catch (Exception e) {
            log.warn("取消智谱微调任务失败 zhipuJobId={}", zhipuJobId, e);
        }
    }

    private Map<String, Object> exchange(String url, HttpMethod method, HttpEntity<?> entity) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            return parseBodyOrThrow(response.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // RestTemplate 在 4xx/5xx 时抛异常，body 里才有智谱错误详情
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "智谱 API 错误：" + extractZhipuMessage(e.getResponseBodyAsString(), e.getMessage()));
        }
    }

    private Map<String, Object> parseBodyOrThrow(String body) {
        Map<String, Object> result = JSONUtil.toBean(body, Map.class);
        Object error = result.get("error");
        if (error instanceof Map<?, ?> errorMap && errorMap.get("message") != null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "智谱 API 错误：" + errorMap.get("message"));
        }
        return result;
    }

    private static String extractZhipuMessage(String body, String fallback) {
        if (body == null || body.isBlank()) {
            return fallback == null ? "未知错误" : fallback;
        }
        try {
            Map<String, Object> result = JSONUtil.toBean(body, Map.class);
            Object error = result.get("error");
            if (error instanceof Map<?, ?> errorMap && errorMap.get("message") != null) {
                return String.valueOf(errorMap.get("message"));
            }
        } catch (Exception ignored) {
            // body 非 JSON 时回退原文
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    private HttpHeaders authHeaders() {
        String apiKey = agentProperties.getFineTune().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "智谱 API Key 未配置（agent.fine-tune.api-key）");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        return headers;
    }

    /** 去掉末尾斜杠，避免与路径拼接成双斜杠 */
    private String baseUrl() {
        String url = agentProperties.getFineTune().getBaseUrl();
        if (url == null || url.isBlank()) {
            return "https://open.bigmodel.cn/api/paas/v4";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
