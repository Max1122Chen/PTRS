package com.travel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 聊天代理接口。
 *
 * <p>前端只调用本项目后端，由后端转发到第三方模型服务，避免浏览器跨域限制。</p>
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController
{

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiChatController(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody ChatRequest request)
    {
        if (request == null)
        {
            return ApiResponse.failure(400, "请求体不能为空");
        }
        String endpoint = safeTrim(request.getEndpoint());
        String model = safeTrim(request.getModel());
        String apiKey = safeTrim(request.getApiKey());
        if (endpoint == null || apiKey == null)
        {
            return ApiResponse.failure(400, "endpoint 与 apiKey 不能为空");
        }

        List<Map<String, String>> payloadMessages = new ArrayList<>();
        payloadMessages.add(Map.of(
            "role", "system",
            "content", "你是一名旅游助手，回答应简洁、实用，优先给出可执行建议；当信息不足时先提出关键澄清问题。"
        ));
        if (request.getMessages() != null)
        {
            for (ChatMessage item : request.getMessages())
            {
                String role = safeTrim(item.getRole());
                String content = safeTrim(item.getContent());
                if (role == null || content == null)
                {
                    continue;
                }
                payloadMessages.add(Map.of("role", role, "content", content));
            }
        }

        if (payloadMessages.size() <= 1)
        {
            return ApiResponse.failure(400, "消息内容不能为空");
        }

        String finalUrl = normalizeChatCompletionsUrl(endpoint);
        Map<String, Object> body = new HashMap<>();
        body.put("model", model == null ? "gpt-4o-mini" : model);
        body.put("temperature", request.getTemperature() == null ? 0.7 : request.getTemperature());
        body.put("messages", payloadMessages);

        try
        {
            String bodyJson = objectMapper.writeValueAsString(body);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(finalUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300)
            {
                String detail = truncate(response.body(), 300);
                return ApiResponse.failure(502, "模型接口请求失败(" + response.statusCode() + "): " + detail);
            }

            String answer = extractAssistantContent(response.body());
            if (answer == null || answer.isBlank())
            {
                return ApiResponse.failure(502, "模型未返回有效内容，请检查 endpoint/model 是否兼容 chat/completions");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("content", answer);
            data.put("endpointUsed", finalUrl);
            return ApiResponse.success(data, "success");
        }
        catch (Exception ex)
        {
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return ApiResponse.failure(502, "模型代理请求异常: " + detail);
        }
    }

    private String normalizeChatCompletionsUrl(String endpoint)
    {
        String url = endpoint.trim();
        while (url.endsWith("/"))
        {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions"))
        {
            return url;
        }
        if (url.endsWith("/v1"))
        {
            return url + "/chat/completions";
        }
        if (url.endsWith("/api/paas/v4"))
        {
            return url + "/chat/completions";
        }
        return url + "/chat/completions";
    }

    private String extractAssistantContent(String rawJson) throws Exception
    {
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isTextual())
        {
            return contentNode.asText();
        }
        if (contentNode.isArray())
        {
            StringBuilder sb = new StringBuilder();
            for (JsonNode node : contentNode)
            {
                if (node.isTextual())
                {
                    sb.append(node.asText());
                    continue;
                }
                if (node.isObject() && node.path("type").asText("").equals("text"))
                {
                    sb.append(node.path("text").asText(""));
                }
            }
            return sb.toString();
        }
        return null;
    }

    private String safeTrim(String value)
    {
        if (value == null)
        {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private String truncate(String s, int max)
    {
        if (s == null)
        {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static class ChatRequest
    {
        private String endpoint;
        private String model;
        private String apiKey;
        private Double temperature;
        private List<ChatMessage> messages;

        public String getEndpoint()
        {
            return endpoint;
        }

        public void setEndpoint(String endpoint)
        {
            this.endpoint = endpoint;
        }

        public String getModel()
        {
            return model;
        }

        public void setModel(String model)
        {
            this.model = model;
        }

        public String getApiKey()
        {
            return apiKey;
        }

        public void setApiKey(String apiKey)
        {
            this.apiKey = apiKey;
        }

        public Double getTemperature()
        {
            return temperature;
        }

        public void setTemperature(Double temperature)
        {
            this.temperature = temperature;
        }

        public List<ChatMessage> getMessages()
        {
            return messages;
        }

        public void setMessages(List<ChatMessage> messages)
        {
            this.messages = messages;
        }
    }

    public static class ChatMessage
    {
        private String role;
        private String content;

        public String getRole()
        {
            return role;
        }

        public void setRole(String role)
        {
            this.role = role;
        }

        public String getContent()
        {
            return content;
        }

        public void setContent(String content)
        {
            this.content = content;
        }
    }
}
