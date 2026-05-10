package com.travel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日记旅游动画生成（即梦主力 + LibTV 备用）配置。
 */
@ConfigurationProperties(prefix = "app.animation")
public class AnimationProperties
{

    /**
     * 总开关：关闭后接口返回 503。
     */
    private boolean enabled = true;

    /**
     * 浏览器/网关访问本服务的根 URL，用于把日记里的相对 /media 路径转为厂商可拉取的绝对 URL（LibTV 会话文案中的参考图链接）。
     * 例如 https://your-domain.com 或 http://192.168.1.5:8080
     */
    private String publicBaseUrl = "http://localhost:8080";

    private final Jimeng jimeng = new Jimeng();

    private final Libtv libtv = new Libtv();

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getPublicBaseUrl()
    {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl)
    {
        this.publicBaseUrl = publicBaseUrl;
    }

    public Jimeng getJimeng()
    {
        return jimeng;
    }

    public Libtv getLibtv()
    {
        return libtv;
    }

    public static class Jimeng
    {
        /**
         * 是否启用即梦（火山视觉 CV）链路。
         */
        private boolean enabled = true;

        private String accessKeyId = "";

        private String secretAccessKey = "";

        /**
         * 图生视频（首帧）默认 req_key，参见火山「即梦 AI-视频生成」接口文档。
         */
        private String reqKeyImageToVideo = "jimeng_i2v_first_v30";

        /**
         * 无参考图时退回文生视频的 req_key（火山「即梦·视频生成」文档中的文生视频标识；旧名 jimeng_t2v_v30 已被服务端拒绝）。
         */
        private String reqKeyTextToVideo = "jimeng_ti2v_v30_pro";

        /**
         * 追加到 CVSync2AsyncSubmitTask 请求体的 JSON 片段（与文档字段对齐），例如 aspect_ratio；勿在此处写 req_key（会被覆盖）。
         */
        private String extraSubmitJson = "{\"aspect_ratio\":\"16:9\"}";

        /**
         * 随机种子，-1 表示随机（与常见即梦示例一致）。
         */
        private int seed = -1;

        /**
         * AIGC 显式标识（JSON 对象字符串），轮询 CVSync2AsyncGetResult 时放入 req_json.aigc_meta；空则不传 req_json。
         * 合规字段示例见火山即梦文档；可参考博客中的 content_producer / producer_id 等。
         */
        private String aigcMetaJson = "";

        private long pollIntervalMs = 3000L;

        private long pollTimeoutMs = 900_000L;

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public String getAccessKeyId()
        {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId)
        {
            this.accessKeyId = accessKeyId;
        }

        public String getSecretAccessKey()
        {
            return secretAccessKey;
        }

        public void setSecretAccessKey(String secretAccessKey)
        {
            this.secretAccessKey = secretAccessKey;
        }

        public String getReqKeyImageToVideo()
        {
            return reqKeyImageToVideo;
        }

        public void setReqKeyImageToVideo(String reqKeyImageToVideo)
        {
            this.reqKeyImageToVideo = reqKeyImageToVideo;
        }

        public String getReqKeyTextToVideo()
        {
            return reqKeyTextToVideo;
        }

        public void setReqKeyTextToVideo(String reqKeyTextToVideo)
        {
            this.reqKeyTextToVideo = reqKeyTextToVideo;
        }

        public String getExtraSubmitJson()
        {
            return extraSubmitJson;
        }

        public void setExtraSubmitJson(String extraSubmitJson)
        {
            this.extraSubmitJson = extraSubmitJson;
        }

        public int getSeed()
        {
            return seed;
        }

        public void setSeed(int seed)
        {
            this.seed = seed;
        }

        public String getAigcMetaJson()
        {
            return aigcMetaJson;
        }

        public void setAigcMetaJson(String aigcMetaJson)
        {
            this.aigcMetaJson = aigcMetaJson == null ? "" : aigcMetaJson;
        }

        public long getPollIntervalMs()
        {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs)
        {
            this.pollIntervalMs = pollIntervalMs;
        }

        public long getPollTimeoutMs()
        {
            return pollTimeoutMs;
        }

        public void setPollTimeoutMs(long pollTimeoutMs)
        {
            this.pollTimeoutMs = pollTimeoutMs;
        }

        public boolean hasCredentials()
        {
            return accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank();
        }
    }

    public static class Libtv
    {
        private boolean enabled = true;

        private String accessKey = "";

        private String baseUrl = "https://im.liblib.tv";

        /**
         * 与官方脚本一致：POST /openapi/upload（multipart）。
         */
        private String uploadPath = "/openapi/upload";

        private long pollIntervalMs = 8000L;

        private long pollTimeoutMs = 900_000L;

        /**
         * 当 LibTV 助手反问参数（比例/风格等）而无人值守时，是否自动追加一条「默认参数」消息推进生成。
         */
        private boolean autoReplyOnClarification = true;

        /**
         * 单次会话内最多自动追加几条（防止对话死循环）。
         */
        private int maxAutoReplies = 3;

        /**
         * 检测到反问后自动发送的确认文案（可通过 yml 覆盖）。
         */
        private String autoConfirmText =
            "以上参数请全部采用以下默认值，请直接生成成片，不要继续提问：横版 16:9，写实纪实风格，时长约 5～8 秒。"
                + " 完成后请在回复中给出可直接下载的 mp4 视频链接。";

        /**
         * 拼在「日记正文提示」前的系统前缀，降低助手反问概率。
         */
        private String firstMessagePreamble =
            "【系统自动约束】请根据下方日记与参考图生成一段旅游短视频。"
                + " 默认参数：横版 16:9、写实纪实风格、时长约 5～8 秒。"
                + " 请勿向用户反问比例或风格；若必须选择请直接使用上述默认值并直接出片；"
                + " 完成后请给出可直接下载的 mp4 链接。\n\n";

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public String getAccessKey()
        {
            return accessKey;
        }

        public void setAccessKey(String accessKey)
        {
            this.accessKey = accessKey;
        }

        public String getBaseUrl()
        {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }

        public String getUploadPath()
        {
            return uploadPath;
        }

        public void setUploadPath(String uploadPath)
        {
            this.uploadPath = uploadPath;
        }

        public long getPollIntervalMs()
        {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs)
        {
            this.pollIntervalMs = pollIntervalMs;
        }

        public long getPollTimeoutMs()
        {
            return pollTimeoutMs;
        }

        public void setPollTimeoutMs(long pollTimeoutMs)
        {
            this.pollTimeoutMs = pollTimeoutMs;
        }

        public boolean isAutoReplyOnClarification()
        {
            return autoReplyOnClarification;
        }

        public void setAutoReplyOnClarification(boolean autoReplyOnClarification)
        {
            this.autoReplyOnClarification = autoReplyOnClarification;
        }

        public int getMaxAutoReplies()
        {
            return maxAutoReplies;
        }

        public void setMaxAutoReplies(int maxAutoReplies)
        {
            this.maxAutoReplies = maxAutoReplies;
        }

        public String getAutoConfirmText()
        {
            return autoConfirmText;
        }

        public void setAutoConfirmText(String autoConfirmText)
        {
            this.autoConfirmText = autoConfirmText;
        }

        public String getFirstMessagePreamble()
        {
            return firstMessagePreamble;
        }

        public void setFirstMessagePreamble(String firstMessagePreamble)
        {
            this.firstMessagePreamble = firstMessagePreamble;
        }

        public boolean hasCredentials()
        {
            return accessKey != null && !accessKey.isBlank();
        }
    }
}
