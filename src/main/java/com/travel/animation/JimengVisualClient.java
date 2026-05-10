package com.travel.animation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.travel.config.AnimationProperties;
import com.volcengine.service.visual.IVisualService;
import com.volcengine.service.visual.impl.VisualServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 火山引擎视觉「即梦」视频：官方异步接口 {@code CVSync2AsyncSubmitTask} / {@code CVSync2AsyncGetResult}
 * （与控制台文档及 Java/Go/Python 示例一致；勿与旧版 {@code CVSubmitTask}/{@code CVGetResult} 混用）。
 */
@Component
public class JimengVisualClient
{

    private static final Logger log = LoggerFactory.getLogger(JimengVisualClient.class);

    private final Object sdkLock = new Object();

    /**
     * 生成视频，返回厂商返回的可下载 HTTPS URL（临时 CDN）。
     *
     * @param referenceImagePublicUrls 日记附件经 {@code public-base-url} 拼出的绝对 URL；若仅为 localhost 则内部仍用二进制提交。
     */
    public String generateVideo(String initialPrompt,
                               List<byte[]> referenceImages,
                               List<String> referenceImagePublicUrls,
                               AnimationProperties.Jimeng cfg,
                               DiaryAnimationJob job,
                               AnimationGenParams genParams) throws Exception
    {
        if (!cfg.hasCredentials())
        {
            throw new IllegalStateException("即梦未配置 accessKeyId/secretAccessKey");
        }
        touch(job, "JIMENG_PREPARE", "即梦：正在组装请求（CVSync2AsyncSubmitTask）…");
        String mergedPrompt = mergeQueuedIntoPrompt(initialPrompt, job);
        JSONObject submit = JimengVideoSubmitBuilder.buildSubmitPayload(mergedPrompt, referenceImages,
            referenceImagePublicUrls, cfg, genParams);
        String taskId = submitJimengTask(submit, cfg, job);
        String reqKeyUsed = submit.getString("req_key");
        JSONObject pollReq = JimengVideoSubmitBuilder.buildPollPayload(taskId, reqKeyUsed, cfg);

        long deadline = System.currentTimeMillis() + cfg.getPollTimeoutMs();
        int poll = 0;
        String prompt = mergedPrompt;
        while (System.currentTimeMillis() < deadline)
        {
            if (job != null && job.isCancellationRequested())
            {
                throw new AnimationCancelledException();
            }
            List<String> followUps = job != null ? job.drainJimengFollowUps() : List.of();
            if (!followUps.isEmpty())
            {
                prompt = prompt + "\n【用户补充说明】" + String.join("\n", followUps);
                tryCancelJimengTask(cfg, reqKeyUsed, taskId);
                submit = JimengVideoSubmitBuilder.buildSubmitPayload(prompt, referenceImages,
                    referenceImagePublicUrls, cfg, genParams);
                reqKeyUsed = submit.getString("req_key");
                taskId = submitJimengTask(submit, cfg, job);
                pollReq = JimengVideoSubmitBuilder.buildPollPayload(taskId, reqKeyUsed, cfg);
                touch(job, "JIMENG_RESUBMIT", "即梦：已合并你的补充说明并重新提交云端任务…");
                continue;
            }

            Thread.sleep(Math.max(500L, cfg.getPollIntervalMs()));
            poll++;
            if (job != null)
            {
                job.setJimengPollCount(poll);
            }
            Object resultResp;
            synchronized (sdkLock)
            {
                IVisualService visual = VisualServiceImpl.getInstance();
                visual.setAccessKey(cfg.getAccessKeyId().trim());
                visual.setSecretKey(cfg.getSecretAccessKey().trim());
                resultResp = visual.cvSync2AsyncGetResult(pollReq);
            }
            String raw = JSON.toJSONString(resultResp);
            if (!isVolcBusinessSuccess(resultResp))
            {
                String err = extractVolcErrorDetail(resultResp);
                if (job != null)
                {
                    job.appendLog("[JIMENG_POLL_FAIL] " + err + " 原始：" + abbrev(raw, 4000));
                }
                throw new IllegalStateException("即梦查询失败：" + err);
            }
            String hint = summarizeVolcResultForUser(raw);
            touch(job, "JIMENG_POLL", "即梦：第 " + poll + " 次查询进度"
                + (hint.isEmpty() ? "（处理中）" : " — " + hint));

            String videoUrl = JimengVideoUrlExtractor.extractFirstVideoUrl(resultResp);
            if (videoUrl != null && !videoUrl.isBlank())
            {
                touch(job, "JIMENG_DONE", "即梦：已拿到成片下载地址，正在拉取到本机…");
                return videoUrl.trim();
            }
            if (isTerminalSuccessState(resultResp) && (videoUrl == null || videoUrl.isBlank()))
            {
                if (job != null && job.shouldLogJimengDoneNoUrlDetail())
                {
                    job.appendLog("[JIMENG_DONE_NO_URL] 云端状态已完成但未解析到视频直链，原始响应片段：\n" + abbrev(raw, 8000));
                    touch(job, "JIMENG_PARSE", "即梦：状态已完成但未识别下载地址，详见事件日志中的原始片段");
                }
            }
            if (looksFailed(resultResp))
            {
                String failDetail = abbrev(firstNonBlank(extractDataErrorMsg(resultResp), raw), 800);
                if (job != null)
                {
                    job.appendLog("[JIMENG_FAIL] cvSync2AsyncGetResult 判定失败：" + failDetail);
                    job.appendLog("[JIMENG_FAIL] 原始响应：\n" + abbrev(raw, 6000));
                }
                throw new IllegalStateException("即梦任务失败：" + failDetail);
            }
        }
        if (job != null)
        {
            job.appendLog("[JIMENG_TIMEOUT] 轮询超时未完成，最后 task_id=" + taskId);
        }
        throw new IllegalStateException("即梦任务超时未完成");
    }

    private static String mergeQueuedIntoPrompt(String basePrompt, DiaryAnimationJob job)
    {
        if (job == null)
        {
            return basePrompt;
        }
        List<String> fu = job.drainJimengFollowUps();
        if (fu.isEmpty())
        {
            return basePrompt;
        }
        return basePrompt + "\n【用户补充说明】" + String.join("\n", fu);
    }

    /**
     * 提交任务并解析 task_id；失败抛异常。
     */
    private String submitJimengTask(JSONObject submit,
        AnimationProperties.Jimeng cfg,
        DiaryAnimationJob job) throws Exception
    {
        touch(job, "JIMENG_SUBMIT", "即梦：正在提交云端任务（通常很快）…");
        Object submitResp;
        synchronized (sdkLock)
        {
            IVisualService visual = VisualServiceImpl.getInstance();
            visual.setAccessKey(cfg.getAccessKeyId().trim());
            visual.setSecretKey(cfg.getSecretAccessKey().trim());
            try
            {
                submitResp = visual.cvSync2AsyncSubmitTask(submit);
            }
            catch (Exception ex)
            {
                String m = ex.getMessage();
                if (m != null && m.contains("req_key") && m.contains("not supported"))
                {
                    throw new IllegalStateException(
                        "即梦提交失败：当前配置的 req_key 不被服务端接受，请按火山「即梦 AI-视频生成」文档核对 "
                            + "app.animation.jimeng.req-key-text-to-video / req-key-image-to-video。原响应：" + m,
                        ex);
                }
                throw ex;
            }
        }
        if (!isVolcBusinessSuccess(submitResp))
        {
            String rawSubmit = JSON.toJSONString(submitResp);
            String err = extractVolcErrorDetail(submitResp);
            if (job != null)
            {
                job.appendLog("[JIMENG_SUBMIT_FAIL] 业务失败：" + err);
                job.appendLog("[JIMENG_SUBMIT_FAIL] 原始响应：\n" + abbrev(rawSubmit, 8000));
            }
            throw new IllegalStateException("即梦提交失败：" + err);
        }
        String taskId = extractTaskId(submitResp);
        if (taskId == null || taskId.isBlank())
        {
            String rawSubmit = JSON.toJSONString(submitResp);
            if (job != null)
            {
                job.appendLog("[JIMENG_SUBMIT_FAIL] 未解析到 task_id，提交响应全文：\n" + abbrev(rawSubmit, 8000));
            }
            throw new IllegalStateException("即梦提交未返回 task_id，响应=" + abbrev(rawSubmit, 1200));
        }
        log.info("Jimeng CVSync2Async task submitted task_id={}", taskId);
        if (job != null)
        {
            job.setExternalRef(taskId);
        }
        touch(job, "JIMENG_QUEUED", "即梦：任务已提交，task_id=" + abbrev(taskId, 24)
            + "。云端排队与渲染可能需数分钟，请稍候…");
        return taskId;
    }

    private void tryCancelJimengTask(AnimationProperties.Jimeng cfg, String reqKey, String taskId)
    {
        if (taskId == null || taskId.isBlank() || reqKey == null || reqKey.isBlank())
        {
            return;
        }
        JSONObject cancel = new JSONObject();
        cancel.put("req_key", reqKey);
        cancel.put("task_id", taskId);
        try
        {
            synchronized (sdkLock)
            {
                IVisualService visual = VisualServiceImpl.getInstance();
                visual.setAccessKey(cfg.getAccessKeyId().trim());
                visual.setSecretKey(cfg.getSecretAccessKey().trim());
                visual.visualCommonRequestForJson(cancel, "CVCancelTask", "2022-08-31");
            }
        }
        catch (Throwable t)
        {
            log.warn("Jimeng CVCancelTask(task_id={}) 忽略: {}", abbrev(taskId, 16), t.getMessage());
        }
    }

    private static void touch(DiaryAnimationJob job, String stage, String detail)
    {
        if (job != null)
        {
            job.progress(stage, detail);
            if (detail != null && !detail.isBlank())
            {
                job.appendJimengTranscriptLine("assistant", detail.trim());
            }
        }
    }

    private static String abbrev(String s, int max)
    {
        if (s == null)
        {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String firstNonBlank(String a, String b)
    {
        if (a != null && !a.isBlank())
        {
            return a;
        }
        return b == null ? "" : b;
    }

    private static String summarizeVolcResultForUser(String rawJson)
    {
        if (rawJson == null || rawJson.isBlank())
        {
            return "";
        }
        try
        {
            JSONObject o = JSON.parseObject(rawJson);
            JSONObject data = o.getJSONObject("data");
            if (data == null)
            {
                return "";
            }
            String[] keys = {"task_status", "status", "state", "phase", "message", "msg"};
            for (String k : keys)
            {
                if (data.containsKey(k))
                {
                    Object v = data.get(k);
                    if (v != null)
                    {
                        String s = String.valueOf(v);
                        if (!s.isBlank() && !"null".equalsIgnoreCase(s))
                        {
                            return k + "=" + abbrev(s, 120);
                        }
                    }
                }
            }
        }
        catch (RuntimeException ignored)
        {
        }
        return "";
    }

    private static String extractDataErrorMsg(Object responseObj)
    {
        try
        {
            JSONObject o = JSON.parseObject(JSON.toJSONString(responseObj));
            JSONObject data = o.getJSONObject("data");
            if (data == null)
            {
                return null;
            }
            String e = data.getString("error_msg");
            if (e != null && !e.isBlank())
            {
                return e;
            }
            return data.getString("error_message");
        }
        catch (RuntimeException ignored)
        {
        }
        return null;
    }

    private static final Pattern TASK_ID_PATTERN = Pattern.compile("\"task_id\"\\s*:\\s*\"([^\"]+)\"");

    private String extractTaskId(Object responseObj)
    {
        String raw = JSON.toJSONString(responseObj);
        Matcher m = TASK_ID_PATTERN.matcher(raw);
        if (m.find())
        {
            return m.group(1);
        }
        JSONObject o = JSON.parseObject(raw);
        JSONObject data = o.getJSONObject("data");
        if (data != null && data.containsKey("task_id"))
        {
            Object tid = data.get("task_id");
            return tid == null ? null : String.valueOf(tid).trim();
        }
        if (o.containsKey("task_id"))
        {
            Object tid = o.get("task_id");
            return tid == null ? null : String.valueOf(tid).trim();
        }
        return null;
    }

    private static String extractVolcErrorDetail(Object responseObj)
    {
        String raw = JSON.toJSONString(responseObj);
        try
        {
            JSONObject o = JSON.parseObject(raw);
            JSONObject rm = o.getJSONObject("ResponseMetadata");
            if (rm != null)
            {
                Object errObj = rm.get("Error");
                if (errObj instanceof JSONObject err)
                {
                    String msg = err.getString("Message");
                    String code = err.getString("Code");
                    int codeN = err.getIntValue("CodeN");
                    StringBuilder sb = new StringBuilder();
                    if (msg != null && !msg.isBlank())
                    {
                        sb.append(msg);
                    }
                    if (code != null && !code.isBlank())
                    {
                        if (sb.length() > 0)
                        {
                            sb.append(' ');
                        }
                        sb.append('(').append(code).append(')');
                    }
                    else if (codeN != 0)
                    {
                        if (sb.length() > 0)
                        {
                            sb.append(' ');
                        }
                        sb.append("(CodeN=").append(codeN).append(')');
                    }
                    if (sb.length() > 0)
                    {
                        return sb.toString();
                    }
                }
            }
            JSONObject data = o.getJSONObject("data");
            if (data != null)
            {
                for (String k : new String[] {"message", "Message", "error_msg", "status_msg", "status_message"})
                {
                    String v = data.getString(k);
                    if (v != null && !v.isBlank() && !"success".equalsIgnoreCase(v))
                    {
                        return v;
                    }
                }
            }
            String msg = o.getString("message");
            if (msg == null)
            {
                msg = o.getString("Message");
            }
            if (msg != null && !msg.isBlank())
            {
                return msg;
            }
            Integer code = o.getInteger("code");
            if (code != null && !isVolcSuccessCode(code))
            {
                return "code=" + code + (msg != null ? " " + msg : "");
            }
        }
        catch (RuntimeException ignored)
        {
        }
        return abbrev(raw, 600);
    }

    private static boolean isVolcSuccessCode(int code)
    {
        return code == 0 || code == 10000 || code == 200 || code == 1000;
    }

    private static boolean isVolcBusinessSuccess(Object responseObj)
    {
        String raw = JSON.toJSONString(responseObj);
        try
        {
            JSONObject o = JSON.parseObject(raw);
            JSONObject rm = o.getJSONObject("ResponseMetadata");
            if (rm != null && rm.get("Error") != null)
            {
                return false;
            }
            Integer code = o.getInteger("code");
            if (code == null)
            {
                return true;
            }
            return isVolcSuccessCode(code);
        }
        catch (RuntimeException ex)
        {
            return true;
        }
    }

    /**
     * data.status / task_status 为 done、success 等终态，且非 failed。
     */
    private static boolean isTerminalSuccessState(Object responseObj)
    {
        try
        {
            JSONObject o = JSON.parseObject(JSON.toJSONString(responseObj));
            JSONObject data = o.getJSONObject("data");
            if (data == null)
            {
                return false;
            }
            String ts = firstNonBlank(data.getString("task_status"), data.getString("status"));
            if (ts == null)
            {
                return false;
            }
            String t = ts.toLowerCase();
            return t.equals("done") || t.equals("success") || t.equals("succeed") || t.equals("completed");
        }
        catch (RuntimeException ignored)
        {
        }
        return false;
    }

    private boolean looksFailed(Object responseObj)
    {
        try
        {
            JSONObject o = JSON.parseObject(JSON.toJSONString(responseObj));
            JSONObject data = o.getJSONObject("data");
            if (data != null)
            {
                String ts = firstNonBlank(data.getString("task_status"), data.getString("status"));
                if (ts != null)
                {
                    String t = ts.toLowerCase();
                    if (t.equals("failed") || t.equals("fail"))
                    {
                        return true;
                    }
                    if (t.equals("done") || t.equals("success") || t.equals("succeed"))
                    {
                        return false;
                    }
                }
                String st = data.getString("status");
                if (st != null && st.equalsIgnoreCase("failed"))
                {
                    return true;
                }
            }
        }
        catch (RuntimeException ignored)
        {
        }
        String raw = JSON.toJSONString(responseObj).toLowerCase();
        return raw.contains("\"task_status\":\"failed\"") || raw.contains("\"task_status\":\"fail\"")
            || raw.contains("\"status\":\"failed\"");
    }
}
