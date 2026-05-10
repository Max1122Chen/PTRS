package com.travel.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.animation.AnimationCancelledException;
import com.travel.animation.AnimationGenParams;
import com.travel.animation.AnimationPromptComposer;
import com.travel.animation.DiaryAnimationJob;
import com.travel.animation.JimengVisualClient;
import com.travel.animation.LibTvOpenApiClient;
import com.travel.animation.MediaPathResolver;
import com.travel.model.dto.diary.AnimationGenerateRequest;
import com.travel.model.dto.diary.DiaryAnimationJobMessageRequest;
import com.travel.config.AnimationProperties;
import com.travel.mapper.DiaryMapper;
import com.travel.model.entity.Diary;
import com.travel.service.DiaryAnimationService;
import com.travel.storage.InMemoryStore;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 即梦优先、LibTV 备用；本地图片读盘后编码上传或 Base64 提交。
 */
@Service
public class DiaryAnimationServiceImpl implements DiaryAnimationService
{

    private static final Logger log = LoggerFactory.getLogger(DiaryAnimationServiceImpl.class);

    private final AnimationProperties animationProperties;

    private final JimengVisualClient jimengVisualClient;

    private final LibTvOpenApiClient libTvOpenApiClient;

    private final MediaPathResolver mediaPathResolver;

    private final DiaryMapper diaryMapper;

    private final InMemoryStore store;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    private final org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor animationExecutor;

    private final Map<String, DiaryAnimationJob> jobs = new ConcurrentHashMap<>();

    @Value("${app.media.base-path:data/media}")
    private String mediaBasePath;

    @Value("${app.media.url-prefix:/media}")
    private String mediaUrlPrefix;

    @Value("${app.debug.ignore-db-connection-failure:false}")
    private boolean ignoreDbConnectionFailure;

    public DiaryAnimationServiceImpl(
        AnimationProperties animationProperties,
        JimengVisualClient jimengVisualClient,
        LibTvOpenApiClient libTvOpenApiClient,
        MediaPathResolver mediaPathResolver,
        DiaryMapper diaryMapper,
        InMemoryStore store,
        ObjectMapper objectMapper,
        RestTemplate restTemplate,
        @Qualifier("animationTaskExecutor") org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor animationExecutor)
    {
        this.animationProperties = animationProperties;
        this.jimengVisualClient = jimengVisualClient;
        this.libTvOpenApiClient = libTvOpenApiClient;
        this.mediaPathResolver = mediaPathResolver;
        this.diaryMapper = diaryMapper;
        this.store = store;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.animationExecutor = animationExecutor;
    }

    @Override
    public Map<String, Object> submitGenerate(Long userId, Long diaryId, AnimationGenerateRequest request)
    {
        if (!animationProperties.isEnabled())
        {
            throw new IllegalStateException("动画生成已关闭（app.animation.enabled=false）");
        }
        Diary diary = store.findDiaryById(diaryId);
        if (diary == null)
        {
            throw new IllegalArgumentException("日记不存在");
        }
        if (!diary.getUserId().equals(userId))
        {
            throw new IllegalArgumentException("无权操作该日记");
        }
        String jobId = UUID.randomUUID().toString().replace("-", "");
        DiaryAnimationJob job = new DiaryAnimationJob(jobId, diaryId, userId);
        job.setGenParams(AnimationGenParams.fromRequest(request));
        jobs.put(jobId, job);
        animationExecutor.execute(() -> runJob(job));
        Map<String, Object> out = new HashMap<>();
        out.put("jobId", jobId);
        out.put("generationParams", generationParamsToMap(job.getGenParams()));
        return out;
    }

    @Override
    public void postJobMessage(Long userId, String jobId, DiaryAnimationJobMessageRequest body)
    {
        if (body == null || body.getMessage() == null || body.getMessage().isBlank())
        {
            throw new IllegalArgumentException("message 不能为空");
        }
        String text = body.getMessage().trim();
        if (text.length() > 4000)
        {
            throw new IllegalArgumentException("message 过长（最多 4000 字）");
        }
        DiaryAnimationJob job = jobs.get(jobId);
        if (job == null)
        {
            throw new IllegalArgumentException("任务不存在或已过期");
        }
        if (!job.getUserId().equals(userId))
        {
            throw new IllegalArgumentException("无权操作该任务");
        }
        if (!"RUNNING".equals(job.getStatus()))
        {
            throw new IllegalStateException("任务已结束，无法继续对话");
        }
        if (job.isCancellationRequested())
        {
            throw new IllegalStateException("任务正在取消或已取消，无法发送消息");
        }
        if ("jimeng".equals(job.getProvider()))
        {
            job.enqueueJimengUserMessage(text);
            job.appendJimengTranscriptLine("assistant", "（即梦通道）已接收你的说明；后台将合并进提示词并尽量取消旧任务后重新提交。");
            job.progress("JIMENG_USER_QUEUE", "即梦：已记录你的补充说明，将并入提示词并尝试取消旧任务后重新提交…");
            return;
        }
        if (!"libtv".equals(job.getProvider()) || job.getExternalRef() == null || job.getExternalRef().isBlank())
        {
            throw new IllegalStateException("当前任务未进入可对话通道（请等待服务商阶段就绪后再试）");
        }
        job.appendLocalUserLine(text);
        try
        {
            libTvOpenApiClient.appendSessionMessage(job.getExternalRef(), text, animationProperties.getLibtv());
        }
        catch (Exception ex)
        {
            log.warn("LibTV 追加消息失败 jobId={} cause={}", jobId, ex.getMessage());
            throw new IllegalStateException("转发消息失败：" + abbrev(ex.getMessage(), 200));
        }
        if (job.isAwaitingUserInput())
        {
            job.setAwaitingUserInput(false);
            job.progress("LIBTV_RESUME", "已发送你的回复，继续拉取云端进度…");
            animationExecutor.execute(() -> resumeLibTvPoll(job));
        }
        else
        {
            job.progress("LIBTV_USER_MSG", "已转发你的说明，云端处理中…");
        }
    }

    @Override
    public Map<String, Object> getJobStatus(String jobId)
    {
        DiaryAnimationJob job = jobs.get(jobId);
        if (job == null)
        {
            return null;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("jobId", job.getJobId());
        m.put("diaryId", job.getDiaryId());
        m.put("userId", job.getUserId());
        m.put("status", job.getStatus());
        m.put("stage", job.getStage());
        m.put("message", job.getMessage());
        m.put("externalRef", job.getExternalRef());
        m.put("jimengPollCount", job.getJimengPollCount());
        m.put("libtvPollCount", job.getLibtvPollCount());
        m.put("animationUrl", job.getAnimationUrl());
        m.put("provider", job.getProvider());
        m.put("awaitingUserInput", job.isAwaitingUserInput());
        AnimationGenParams gp = job.getGenParams();
        if (gp != null)
        {
            Map<String, Object> gen = new HashMap<>();
            gen.put("aspectRatio", gp.getAspectRatio());
            gen.put("styleKey", gp.getStyleKey());
            gen.put("styleLabel", gp.getStyleLabel());
            gen.put("durationSec", gp.getDurationSec());
            gen.put("extraPrompt", gp.getExtraPrompt());
            gen.put("interactive", gp.isInteractive());
            m.put("generationParams", gen);
        }
        m.put("libTvTranscript", job.snapshotLibTvTranscript());
        m.put("jimengTranscript", job.snapshotJimengTranscript());
        m.put("eventLog", job.snapshotLog());
        return m;
    }

    private static Map<String, Object> generationParamsToMap(AnimationGenParams gp)
    {
        Map<String, Object> gen = new HashMap<>();
        if (gp == null)
        {
            return gen;
        }
        gen.put("aspectRatio", gp.getAspectRatio());
        gen.put("styleKey", gp.getStyleKey());
        gen.put("styleLabel", gp.getStyleLabel());
        gen.put("durationSec", gp.getDurationSec());
        gen.put("extraPrompt", gp.getExtraPrompt());
        gen.put("interactive", gp.isInteractive());
        return gen;
    }

    @Override
    public void cancelJob(Long userId, String jobId)
    {
        DiaryAnimationJob job = jobs.get(jobId);
        if (job == null)
        {
            throw new IllegalArgumentException("任务不存在或已过期");
        }
        if (!job.getUserId().equals(userId))
        {
            throw new IllegalArgumentException("无权操作该任务");
        }
        if (!"RUNNING".equals(job.getStatus()))
        {
            throw new IllegalStateException("任务不在进行中，无法取消");
        }
        job.requestCancel();
        if (job.isAwaitingUserInput())
        {
            cancelFinish(job);
        }
    }

    private void runJob(DiaryAnimationJob job)
    {
        try
        {
            job.progress("PREPARE", "正在读取日记与本地图片…");
            Diary diary = store.findDiaryById(job.getDiaryId());
            if (diary == null)
            {
                fail(job, "日记已不存在");
                return;
            }
            AnimationGenParams gp = job.getGenParams();
            if (gp == null)
            {
                gp = AnimationGenParams.defaults();
                job.setGenParams(gp);
            }
            String diaryPart = buildDiaryExcerpt(diary);
            String promptForJimeng = buildAugmentedPrompt(diaryPart, gp);
            List<byte[]> imageBytes = loadImageBytes(diary);
            List<String> publicImageUrls = resolvePublicImageUrls(diary);
            List<Path> imagePaths = loadImagePaths(diary);

            String vendorUrl = null;
            String provider = null;

            if (animationProperties.getJimeng().isEnabled() && animationProperties.getJimeng().hasCredentials())
            {
                try
                {
                    job.setProvider("jimeng");
                    vendorUrl = jimengVisualClient.generateVideo(promptForJimeng, imageBytes, publicImageUrls,
                        animationProperties.getJimeng(), job, gp);
                    provider = "jimeng";
                }
                catch (AnimationCancelledException ex)
                {
                    throw ex;
                }
                catch (Exception ex)
                {
                    log.warn("即梦生成失败，尝试 LibTV。cause={}", ex.getMessage());
                    job.appendLog("[即梦] 异常类型：" + ex.getClass().getName());
                    job.appendLog("[即梦] 消息：" + (ex.getMessage() == null ? "" : ex.getMessage()));
                    job.appendLog("[即梦] 堆栈：\n" + stackTraceString(ex));
                    job.progress("JIMENG_FAILED", "即梦未成功：" + abbrev(ex.getMessage(), 200) + "。正在切换到 LibTV…");
                    job.setJimengPollCount(0);
                    job.setExternalRef(null);
                }
            }

            if (vendorUrl == null && animationProperties.getLibtv().isEnabled() && animationProperties.getLibtv().hasCredentials())
            {
                job.setProvider("libtv");
                vendorUrl = runLibTv(diaryPart, diary, imagePaths, job);
                provider = "libtv";
            }

            if (vendorUrl == null || vendorUrl.isBlank())
            {
                if ("libtv".equals(provider) && job.isAwaitingUserInput())
                {
                    job.progress("LIBTV_WAIT_USER", "可在下方查看云端助手说明并回复，以继续生成。");
                    return;
                }
                fail(job, "即梦与 LibTV 均未返回成片，请检查密钥、额度及网络");
                return;
            }

            finishVendorSuccess(job, vendorUrl, provider);
        }
        catch (AnimationCancelledException ex)
        {
            cancelFinish(job);
        }
        catch (Exception ex)
        {
            log.error("动画任务失败 jobId={}", job.getJobId(), ex);
            job.appendLog("[任务失败] " + ex.getClass().getName() + ": " + ex.getMessage());
            job.appendLog("[任务失败] 堆栈：\n" + stackTraceString(ex));
            fail(job, ex.getMessage() == null ? "未知错误" : ex.getMessage());
        }
    }

    private void resumeLibTvPoll(DiaryAnimationJob job)
    {
        try
        {
            AnimationProperties.Libtv cfg = animationProperties.getLibtv();
            String sessionId = job.getExternalRef();
            if (sessionId == null || sessionId.isBlank())
            {
                fail(job, "LibTV 会话丢失");
                return;
            }
            AnimationGenParams gp = job.getGenParams();
            boolean interactive = gp != null && gp.isInteractive();
            String vendorUrl = libTvOpenApiClient.waitForVideoUrl(sessionId, cfg, job, job.getLibTvLastSeq(),
                interactive);
            if (vendorUrl != null && !vendorUrl.isBlank())
            {
                finishVendorSuccess(job, vendorUrl, "libtv");
            }
            else if (job.isAwaitingUserInput())
            {
                job.progress("LIBTV_WAIT_USER", "可在下方继续回复云端助手…");
            }
            else
            {
                fail(job, "LibTV 未返回成片链接");
            }
        }
        catch (AnimationCancelledException ex)
        {
            cancelFinish(job);
        }
        catch (Exception ex)
        {
            log.error("LibTV 续跑失败 jobId={}", job.getJobId(), ex);
            job.appendLog("[LibTV续跑] " + ex.getMessage());
            fail(job, ex.getMessage() == null ? "未知错误" : ex.getMessage());
        }
    }

    private void finishVendorSuccess(DiaryAnimationJob job, String vendorUrl, String provider) throws Exception
    {
        if (job.isCancellationRequested())
        {
            cancelFinish(job);
            return;
        }
        job.progress("DOWNLOAD", "正在从厂商地址下载成片到本机（可能较慢）…");
        String localUrl = downloadAndPersist(vendorUrl, job.getDiaryId());
        job.progress("PERSIST", "正在写入日记与内存（数据库不可用时仅保留内存）…");
        persistDiaryAnimation(job.getDiaryId(), localUrl);
        job.setProvider(provider);
        job.setAnimationUrl(localUrl);
        job.setStatus("SUCCEEDED");
        job.setStage("SUCCEEDED");
        job.setMessage("完成：成片已保存，可刷新本页播放。");
    }

    private static String abbrev(String s, int max)
    {
        if (s == null)
        {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private String runLibTv(String diaryPart, Diary diary, List<Path> imagePaths, DiaryAnimationJob job) throws Exception
    {
        AnimationProperties.Libtv cfg = animationProperties.getLibtv();
        AnimationGenParams gp = job.getGenParams();
        job.progress("LIBTV_PREPARE", "LibTV：正在组装文案与参考素材…");
        StringBuilder msg = new StringBuilder();
        String preamble = cfg.getFirstMessagePreamble();
        if (preamble != null && !preamble.isBlank())
        {
            msg.append(preamble);
        }
        msg.append(AnimationPromptComposer.sharedGenerationParamsBlock(gp)).append('\n');
        msg.append("请根据以下旅行日记生成一段简短旅游视频。\n");
        msg.append(diaryPart).append('\n');
        if (!imagePaths.isEmpty())
        {
            msg.append("参考图片（已上传）：\n");
            int n = imagePaths.size();
            int i = 0;
            for (Path p : imagePaths)
            {
                i++;
                job.progress("LIBTV_UPLOAD", "LibTV：正在上传第 " + i + "/" + n + " 张参考图到厂商 OSS…");
                String oss = libTvOpenApiClient.uploadFile(p, cfg);
                msg.append(oss).append('\n');
            }
        }
        else
        {
            List<String> urls = parseImageUrls(diary.getImages());
            for (String u : urls)
            {
                String abs = mediaPathResolver.toPublicAbsoluteUrl(u, animationProperties.getPublicBaseUrl(), mediaUrlPrefix);
                if (abs != null)
                {
                    msg.append("参考图片URL：").append(abs).append('\n');
                }
            }
        }
        job.progress("LIBTV_SESSION", "LibTV：正在创建会话并下发视频生成指令…");
        String sessionId = libTvOpenApiClient.createSession(msg.toString(), cfg);
        job.setExternalRef(sessionId);
        job.progress("LIBTV_WAIT", "LibTV：会话已建立 session=" + abbrev(sessionId, 20)
            + "… 云端 Agent 编排可能较慢，将周期性拉取进度。");
        return libTvOpenApiClient.waitForVideoUrl(sessionId, cfg, job, job.getLibTvLastSeq(),
            gp.isInteractive());
    }

    private String downloadAndPersist(String vendorVideoUrl, Long diaryId) throws Exception
    {
        ResponseEntity<byte[]> resp = restTemplate.getForEntity(vendorVideoUrl, byte[].class);
        byte[] body = resp.getBody();
        if (body == null || body.length == 0)
        {
            throw new IllegalStateException("下载成片为空");
        }
        Path base = Paths.get(mediaBasePath).toAbsolutePath().normalize();
        Path dir = base.resolve("video").resolve("animation").normalize();
        if (!dir.startsWith(base))
        {
            throw new IllegalStateException("非法保存路径");
        }
        Files.createDirectories(dir);
        String name = "diary-" + diaryId + "-" + UUID.randomUUID().toString().replace("-", "") + ".mp4";
        Path file = dir.resolve(name);
        Files.write(file, body);
        String prefix = normalizeUrlPrefix(mediaUrlPrefix);
        return prefix + "/video/animation/" + name;
    }

    private void persistDiaryAnimation(Long diaryId, String animationUrl)
    {
        Diary mem = store.findDiaryById(diaryId);
        if (mem != null)
        {
            mem.setAnimationUrl(animationUrl);
            mem.setUpdateTime(LocalDateTime.now());
            store.updateDiary(mem);
        }
        try
        {
            LambdaUpdateWrapper<Diary> uw = new LambdaUpdateWrapper<>();
            uw.eq(Diary::getId, diaryId)
                .set(Diary::getAnimationUrl, animationUrl)
                .set(Diary::getUpdateTime, LocalDateTime.now());
            diaryMapper.update(null, uw);
        }
        catch (RuntimeException ex)
        {
            if (ignoreDbConnectionFailure && isDbUnavailable(ex))
            {
                log.warn("动画 URL 写库跳过（数据库不可用） diaryId={}", diaryId);
                return;
            }
            throw ex;
        }
    }

    private boolean isDbUnavailable(Throwable ex)
    {
        Throwable current = ex;
        while (current != null)
        {
            String name = current.getClass().getName();
            if (name.contains("DataSourceDisableException")
                || name.contains("CannotCreateTransactionException")
                || name.contains("CannotGetJdbcConnectionException")
                || name.contains("DataAccessResourceFailureException")
                || name.contains("CommunicationsException"))
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void cancelFinish(DiaryAnimationJob job)
    {
        if ("CANCELLED".equals(job.getStatus()))
        {
            return;
        }
        job.setAwaitingUserInput(false);
        job.setStatus("CANCELLED");
        job.setStage("CANCELLED");
        job.setMessage("任务已取消");
        job.appendLog("[系统] 结束任务（CANCELLED），已停止本地轮询");
    }

    private void fail(DiaryAnimationJob job, String message)
    {
        String m = message == null ? "未知错误" : message;
        job.appendLog("[FAILED] " + m);
        job.setStatus("FAILED");
        job.setStage("FAILED");
        job.setMessage(m);
    }

    private static String stackTraceString(Throwable ex)
    {
        if (ex == null)
        {
            return "";
        }
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 12000 ? s.substring(0, 12000) + "\n...(截断)" : s;
    }

    private String buildDiaryExcerpt(Diary diary)
    {
        String title = diary.getTitle() == null ? "" : diary.getTitle().trim();
        String content = diary.getContent() == null ? "" : diary.getContent().trim();
        if (content.length() > 800)
        {
            content = content.substring(0, 800) + "...";
        }
        return "标题：" + title + "\n正文摘录：" + content;
    }

    private String buildAugmentedPrompt(String diaryPart, AnimationGenParams gp)
    {
        return AnimationPromptComposer.buildJimengPromptBody(diaryPart, gp);
    }

    private List<String> parseImageUrls(String imagesJson)
    {
        if (StringUtils.isBlank(imagesJson))
        {
            return List.of();
        }
        try
        {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
        }
        catch (Exception ex)
        {
            return List.of();
        }
    }

    private List<Path> loadImagePaths(Diary diary)
    {
        List<Path> out = new ArrayList<>();
        for (String url : parseImageUrls(diary.getImages()))
        {
            Path p = mediaPathResolver.resolveLocalFile(url, mediaBasePath, mediaUrlPrefix);
            if (p != null)
            {
                out.add(p);
            }
        }
        return out;
    }

    private List<byte[]> loadImageBytes(Diary diary)
    {
        List<byte[]> out = new ArrayList<>();
        for (Path p : loadImagePaths(diary))
        {
            try
            {
                out.add(Files.readAllBytes(p));
                break;
            }
            catch (Exception ex)
            {
                log.warn("读取本地图片失败 {} : {}", p, ex.getMessage());
            }
        }
        return out;
    }

    /**
     * 将日记附件路径转为绝对 URL，供即梦 {@code image_urls} 使用（公网 base 且非 localhost 时优先走 URL）。
     */
    private List<String> resolvePublicImageUrls(Diary diary)
    {
        String base = animationProperties.getPublicBaseUrl();
        if (base == null || base.isBlank())
        {
            return List.of();
        }
        String normalized = base.trim();
        while (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        List<String> urls = new ArrayList<>();
        for (String rel : parseImageUrls(diary.getImages()))
        {
            if (rel == null || rel.isBlank())
            {
                continue;
            }
            String r = rel.trim();
            if (r.startsWith("http://") || r.startsWith("https://"))
            {
                urls.add(r);
                break;
            }
            if (r.startsWith("/"))
            {
                urls.add(normalized + r);
                break;
            }
        }
        return urls;
    }

    private String normalizeUrlPrefix(String prefix)
    {
        String out = (prefix == null || prefix.isBlank()) ? "/media" : prefix.trim();
        if (!out.startsWith("/"))
        {
            out = "/" + out;
        }
        while (out.endsWith("/"))
        {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
