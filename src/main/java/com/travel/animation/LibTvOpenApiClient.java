package com.travel.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.config.AnimationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LibTV agent-im OpenAPI（Bearer + 上传 + 会话）。
 *
 * @see <a href="https://github.com/libtv-labs/libtv-skills">libtv-skills README</a>
 */
@Component
public class LibTvOpenApiClient
{

    private static final Logger log = LoggerFactory.getLogger(LibTvOpenApiClient.class);

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    public LibTvOpenApiClient(RestTemplate restTemplate, ObjectMapper objectMapper)
    {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String uploadFile(Path localFile, AnimationProperties.Libtv cfg) throws Exception
    {
        if (!cfg.hasCredentials())
        {
            throw new IllegalStateException("LibTV 未配置 access-key");
        }
        String base = normalizeBase(cfg.getBaseUrl());
        String uploadUrl = base + cfg.getUploadPath();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("accessKey", cfg.getAccessKey().trim());
        body.add("file", new FileSystemResource(localFile.toFile()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(cfg.getAccessKey().trim());

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(uploadUrl, entity, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        JsonNode data = root.path("data");
        String url = data.path("url").asText(null);
        if (url == null || url.isBlank())
        {
            throw new IllegalStateException("LibTV 上传未返回 url，body=" + resp.getBody());
        }
        log.debug("LibTV uploaded {} -> {}", localFile.getFileName(), url);
        return url;
    }

    /**
     * 创建会话并发送第一条消息，返回 sessionId。
     */
    public String createSession(String message, AnimationProperties.Libtv cfg) throws Exception
    {
        String base = normalizeBase(cfg.getBaseUrl());
        String url = base + "/openapi/session";
        HttpHeaders headers = jsonHeaders(cfg);
        String json = objectMapper.createObjectNode().put("message", message).toString();
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        JsonNode data = root.path("data");
        String sessionId = data.path("sessionId").asText(null);
        if (sessionId == null || sessionId.isBlank())
        {
            throw new IllegalStateException("LibTV 创建会话失败 body=" + resp.getBody());
        }
        return sessionId;
    }

    /**
     * 向已有会话追加一条用户消息（与创建会话共用 {@code POST /openapi/session}，body 含 sessionId）。
     */
    public void appendSessionMessage(String sessionId, String message, AnimationProperties.Libtv cfg) throws Exception
    {
        if (!cfg.hasCredentials())
        {
            throw new IllegalStateException("LibTV 未配置 access-key");
        }
        if (sessionId == null || sessionId.isBlank())
        {
            throw new IllegalArgumentException("sessionId 为空");
        }
        String base = normalizeBase(cfg.getBaseUrl());
        String url = base + "/openapi/session";
        HttpHeaders headers = jsonHeaders(cfg);
        String json = objectMapper.createObjectNode()
            .put("sessionId", sessionId)
            .put("message", message)
            .toString();
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        int code = root.path("code").asInt(0);
        if (code != 0 && code != 200)
        {
            throw new IllegalStateException("LibTV 追加会话消息失败 code=" + code + " body=" + resp.getBody());
        }
        log.debug("LibTV appended message to session {}", sessionId);
    }

    public JsonNode querySession(String sessionId, int afterSeq, AnimationProperties.Libtv cfg) throws Exception
    {
        String base = normalizeBase(cfg.getBaseUrl());
        String url = base + "/openapi/session/" + sessionId;
        if (afterSeq > 0)
        {
            url += "?afterSeq=" + afterSeq;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(cfg.getAccessKey().trim());
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        return root.path("data");
    }

    /**
     * 轮询直到解析出视频 URL、交互模式下的「等待用户」、或超时。
     *
     * @param startLastSeq 使用增量 {@code afterSeq} 起点；断点续轮询时传入 job 中保存的游标。
     * @param interactiveLibTv 为 true 时，检测到助手索要参数则暂停并 {@code return null}，不自动代答。
     * @return 成片直链；交互暂停时返回 {@code null}（非超时）
     */
    public String waitForVideoUrl(String sessionId, AnimationProperties.Libtv cfg, DiaryAnimationJob job,
        int startLastSeq, boolean interactiveLibTv) throws Exception
    {
        long deadline = System.currentTimeMillis() + cfg.getPollTimeoutMs();
        int lastSeq = Math.max(0, startLastSeq);
        int poll = 0;
        int autoRepliesSent = 0;
        int lastAutoReplyAssistantSeq = -1;
        while (System.currentTimeMillis() < deadline)
        {
            if (job != null && job.isCancellationRequested())
            {
                throw new AnimationCancelledException();
            }
            Thread.sleep(Math.max(500L, cfg.getPollIntervalMs()));
            poll++;
            if (job != null)
            {
                job.setLibtvPollCount(poll);
            }
            JsonNode data = querySession(sessionId, lastSeq, cfg);
            JsonNode messages = data.path("messages");
            int msgCount = messages.isArray() ? messages.size() : 0;
            StringBuilder hint = new StringBuilder();
            hint.append("LibTV：第 ").append(poll).append(" 次拉取会话，当前增量消息 ").append(msgCount).append(" 条");
            String lastAssistantSnippet = null;
            int assistantAskSeq = -1;
            String assistantAskContent = null;
            if (messages.isArray())
            {
                for (JsonNode msg : messages)
                {
                    int seq = msg.path("seq").asInt(0);
                    if (seq > lastSeq)
                    {
                        lastSeq = seq;
                    }
                    String role = msg.path("role").asText("");
                    String content = contentToString(msg.path("content"));
                    if (job != null)
                    {
                        job.upsertLibTvServerMessage(seq, role, content);
                    }
                    if (!"assistant".equalsIgnoreCase(role))
                    {
                        continue;
                    }
                    String videoUrl = extractVideoUrl(content);
                    if (videoUrl != null)
                    {
                        if (job != null)
                        {
                            job.setLibTvLastSeq(lastSeq);
                            job.setAwaitingUserInput(false);
                        }
                        touch(job, "LIBTV_DONE", "LibTV：已解析到成片链接，正在下载到本机…");
                        return videoUrl;
                    }
                    if (content != null && !content.isBlank())
                    {
                        if (seq >= assistantAskSeq)
                        {
                            assistantAskSeq = seq;
                            assistantAskContent = content;
                        }
                        lastAssistantSnippet = abbrev(content.replace('\n', ' ').trim(), 120);
                    }
                }
            }
            if (job != null)
            {
                job.setLibTvLastSeq(lastSeq);
            }
            if (assistantAskSeq >= 0 && assistantAskContent != null
                && assistantIndicatesRefusal(assistantAskContent))
            {
                String excerpt = abbrev(assistantAskContent.replace('\n', ' ').trim(), 400);
                if (job != null)
                {
                    job.appendLog("[LIBTV_REFUSAL] 检测到致歉/无法继续类反馈，结束会话：" + excerpt);
                }
                throw new IllegalStateException("云端助手表示无法继续生成：" + abbrev(assistantAskContent, 280));
            }
            if (interactiveLibTv
                && assistantAskSeq >= 0
                && assistantAskContent != null
                && assistantSeemsToAskUser(assistantAskContent))
            {
                if (job != null)
                {
                    job.setAwaitingUserInput(true);
                }
                touch(job, "LIBTV_WAIT_USER", "LibTV：助手提出待确认问题，请在本页输入回复后发送。");
                return null;
            }
            if (cfg.isAutoReplyOnClarification()
                && !interactiveLibTv
                && assistantAskSeq >= 0
                && assistantAskContent != null
                && assistantAskSeq != lastAutoReplyAssistantSeq
                && autoRepliesSent < cfg.getMaxAutoReplies()
                && assistantSeemsToAskUser(assistantAskContent))
            {
                appendSessionMessage(sessionId, cfg.getAutoConfirmText(), cfg);
                lastAutoReplyAssistantSeq = assistantAskSeq;
                autoRepliesSent++;
                touch(job, "LIBTV_REPLY",
                    "LibTV：检测到助手请求确认参数（seq=" + assistantAskSeq + "），已自动回复默认参数（第 "
                        + autoRepliesSent + "/" + cfg.getMaxAutoReplies() + " 次），等待继续生成…");
                continue;
            }
            if (lastAssistantSnippet != null)
            {
                hint.append("；最近助手回复：").append(lastAssistantSnippet);
            }
            touch(job, "LIBTV_POLL", hint.toString());
        }
        if (job != null)
        {
            job.appendLog("[LIBTV_TIMEOUT] 会话超时仍未解析到成片链接 sessionId=" + sessionId);
        }
        throw new IllegalStateException("LibTV 会话超时仍未解析到视频链接");
    }

    /**
     * 启发式：助手是否明确拒绝或致歉无法产出（需停止轮询，避免无休止追问）。
     */
    private static boolean assistantIndicatesRefusal(String text)
    {
        if (text == null || text.isBlank())
        {
            return false;
        }
        if (extractVideoUrl(text) != null)
        {
            return false;
        }
        String t = text;
        boolean sorry = t.contains("抱歉") || t.contains("不好意思");
        boolean cannot = t.contains("无法") || t.contains("不能生成") || t.contains("无法生成")
            || t.contains("不支持") || t.contains("做不到") || t.contains("暂不")
            || t.contains("无权") || t.contains("不能满足");
        if (sorry && cannot)
        {
            return true;
        }
        if (t.contains("无法为您"))
        {
            return true;
        }
        return t.contains("暂时无法") && (t.contains("生成") || t.contains("提供") || t.contains("制作"));
    }

    /**
     * 启发式：助手是否在索要比例/风格等参数（无人值守时需自动补答）。
     */
    private static boolean assistantSeemsToAskUser(String text)
    {
        if (text == null || text.isBlank())
        {
            return false;
        }
        String t = text;
        if (extractVideoUrl(t) != null)
        {
            return false;
        }
        boolean q = t.indexOf('？') >= 0 || t.indexOf('?') >= 0;
        boolean topic = t.contains("比例") || t.contains("风格") || t.contains("竖版") || t.contains("横版")
            || t.contains("纪实") || t.contains("治愈");
        boolean choose = t.contains("选") || t.contains("还是") || t.contains("确认") || t.contains("请问")
            || t.contains("参数");
        boolean numberedChoice = t.matches("(?s).*[1-9][）)].*") && (t.contains("还是") || t.contains("选"));
        return (q && choose && topic) || numberedChoice;
    }

    private static void touch(DiaryAnimationJob job, String stage, String detail)
    {
        if (job != null)
        {
            job.progress(stage, detail);
        }
    }

    private static String abbrev(String s, int max)
    {
        if (s == null)
        {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String contentToString(JsonNode contentNode)
    {
        if (contentNode.isMissingNode() || contentNode.isNull())
        {
            return "";
        }
        if (contentNode.isTextual())
        {
            return contentNode.asText();
        }
        return contentNode.toString();
    }

    private static final Pattern VIDEO_URL_PATTERN =
        Pattern.compile("https?://[^\\s\"'<>]+\\.(?:mp4|webm|mov)(?:\\?[^\\s\"'<>]*)?",
            Pattern.CASE_INSENSITIVE);

    /** 备用：链接中段含 .mp4 等但不符合「路径尾缀」形式时 */
    private static final Pattern ANY_HTTPS = Pattern.compile("(https?://[^\\s\"'<>]+)");

    private static String extractVideoUrl(String text)
    {
        if (text == null)
        {
            return null;
        }
        Matcher m = VIDEO_URL_PATTERN.matcher(text);
        if (m.find())
        {
            return m.group();
        }
        Matcher loose = ANY_HTTPS.matcher(text);
        while (loose.find())
        {
            String u = loose.group(1);
            String low = u.toLowerCase();
            if (low.contains(".mp4") || low.contains(".webm") || low.contains(".mov"))
            {
                return u;
            }
        }
        return null;
    }

    private HttpHeaders jsonHeaders(AnimationProperties.Libtv cfg)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cfg.getAccessKey().trim());
        return headers;
    }

    private String normalizeBase(String baseUrl)
    {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/"))
        {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }
}
