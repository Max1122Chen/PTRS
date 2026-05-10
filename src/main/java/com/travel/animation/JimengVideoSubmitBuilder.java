package com.travel.animation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.travel.config.AnimationProperties;

import java.util.Base64;
import java.util.List;

/**
 * 火山视觉「即梦」异步视频：{@code CVSync2AsyncSubmitTask} / {@code CVSync2AsyncGetResult} 请求体拼装。
 * <p>
 * 字段名与官方文档/控制台示例对齐：{@code req_key}、{@code prompt}、{@code seed}、{@code frames}、{@code aspect_ratio}；
 * 图生支持 {@code image_urls}（公网可拉取）或 {@code binary_data_base64}。
 * </p>
 */
public final class JimengVideoSubmitBuilder
{

    /** 文档常见建议：提示词不宜过长 */
    public static final int PROMPT_MAX_CHARS = 400;

    private JimengVideoSubmitBuilder()
    {
    }

    /**
     * 常见档位：约 5s→121 帧、约 10s→241 帧（与控制台示例一致；具体以产品线文档为准）。
     */
    public static int framesForDurationSeconds(int durationSec)
    {
        int d = Math.min(120, Math.max(3, durationSec));
        return d <= 7 ? 121 : 241;
    }

    public static String clampPrompt(String prompt)
    {
        if (prompt == null)
        {
            return "";
        }
        String t = prompt.trim();
        if (t.length() <= PROMPT_MAX_CHARS)
        {
            return t;
        }
        return t.substring(0, PROMPT_MAX_CHARS);
    }

    /**
     * @param referenceImagePublicUrls 日记附件的绝对 URL（若为本机/内网则退回 base64）
     */
    public static JSONObject buildSubmitPayload(String prompt,
        List<byte[]> referenceImages,
        List<String> referenceImagePublicUrls,
        AnimationProperties.Jimeng cfg,
        AnimationGenParams genParams)
    {
        JSONObject req = new JSONObject();
        mergeExtraJson(req, cfg.getExtraSubmitJson());

        byte[] firstImageBytes = null;
        if (referenceImages != null && !referenceImages.isEmpty())
        {
            firstImageBytes = referenceImages.get(0);
        }
        boolean hasBytes = firstImageBytes != null && firstImageBytes.length > 0;
        boolean useHttpUrls = preferHttpImageUrls(referenceImagePublicUrls);

        String reqKey;
        if (hasBytes || useHttpUrls)
        {
            reqKey = cfg.getReqKeyImageToVideo();
        }
        else
        {
            reqKey = cfg.getReqKeyTextToVideo();
        }

        req.put("req_key", reqKey);
        req.put("prompt", clampPrompt(prompt));
        req.put("seed", cfg.getSeed());
        int durSec = genParams != null ? genParams.getDurationSec() : 8;
        req.put("frames", framesForDurationSeconds(durSec));

        if (useHttpUrls)
        {
            JSONArray urls = new JSONArray();
            for (String u : referenceImagePublicUrls)
            {
                if (u != null && !u.isBlank())
                {
                    urls.add(u.trim());
                }
            }
            if (!urls.isEmpty())
            {
                req.put("image_urls", urls);
            }
        }
        else if (hasBytes)
        {
            JSONArray arr = new JSONArray();
            arr.add(Base64.getEncoder().encodeToString(firstImageBytes));
            req.put("binary_data_base64", arr);
        }

        if (genParams != null && genParams.getAspectRatio() != null && !genParams.getAspectRatio().isBlank())
        {
            req.put("aspect_ratio", genParams.getAspectRatio());
        }
        return req;
    }

    public static JSONObject buildPollPayload(String taskId, String reqKey, AnimationProperties.Jimeng cfg)
    {
        JSONObject pollReq = new JSONObject();
        pollReq.put("req_key", reqKey);
        pollReq.put("task_id", taskId);
        attachPollReqJson(pollReq, cfg);
        return pollReq;
    }

    private static void attachPollReqJson(JSONObject pollReq, AnimationProperties.Jimeng cfg)
    {
        String meta = cfg.getAigcMetaJson();
        if (meta == null || meta.isBlank())
        {
            return;
        }
        try
        {
            JSONObject aigc = JSON.parseObject(meta);
            if (aigc == null || aigc.isEmpty())
            {
                return;
            }
            JSONObject wrap = new JSONObject();
            wrap.put("aigc_meta", aigc);
            pollReq.put("req_json", wrap.toJSONString());
        }
        catch (RuntimeException ignored)
        {
        }
    }

    private static void mergeExtraJson(JSONObject req, String extraSubmitJson)
    {
        if (extraSubmitJson == null || extraSubmitJson.isBlank())
        {
            return;
        }
        try
        {
            JSONObject extra = JSON.parseObject(extraSubmitJson);
            if (extra != null)
            {
                for (String key : extra.keySet())
                {
                    req.put(key, extra.get(key));
                }
            }
        }
        catch (RuntimeException ignored)
        {
        }
    }

    /**
     * 仅当首图为公网 http(s) 且非 localhost loopback 时使用 {@code image_urls}，否则仍走 base64。
     */
    static boolean preferHttpImageUrls(List<String> referenceImagePublicUrls)
    {
        if (referenceImagePublicUrls == null || referenceImagePublicUrls.isEmpty())
        {
            return false;
        }
        String u = referenceImagePublicUrls.get(0);
        if (u == null || u.isBlank())
        {
            return false;
        }
        String t = u.trim().toLowerCase();
        if (!t.startsWith("http://") && !t.startsWith("https://"))
        {
            return false;
        }
        return !t.contains("localhost") && !t.contains("127.0.0.1") && !t.contains("0.0.0.0");
    }
}
