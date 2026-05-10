package com.travel.animation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次动画生成任务状态（内存登记，进程内有效）。
 */
public class DiaryAnimationJob
{

    private final String jobId;

    private final Long diaryId;

    private final Long userId;

    private volatile String status;

    private volatile String message;

    private volatile String animationUrl;

    private volatile String provider;

    /**
     * 机器可读阶段：PREPARE / JIMENG_* / LIBTV_* / DOWNLOAD / PERSIST / SUCCEEDED / FAILED。
     */
    private volatile String stage;

    /**
     * 厂商侧任务标识（即梦 task_id 或 LibTV sessionId），便于用户感知「任务在云上」。
     */
    private volatile String externalRef;

    private volatile int jimengPollCount;

    private volatile int libtvPollCount;

    /** 解析后的生成参数（提交任务时写入）。 */
    private AnimationGenParams genParams = AnimationGenParams.defaults();

    /** LibTV 会话轮询游标（增量 afterSeq）。 */
    private volatile int libTvLastSeq;

    /** 助手反问等场景下为 true，前端展示回复框；后台继续轮询由用户触发 resume。 */
    private volatile boolean awaitingUserInput;

    private final Object transcriptLock = new Object();

    private final List<Map<String, Object>> libTvTranscript = new ArrayList<>();

    /** 即梦路径下的「会话」展示（进度与用户补充），结构与 LibTV transcript 一致。 */
    private final List<Map<String, Object>> jimengTranscript = new ArrayList<>();

    private final ConcurrentLinkedQueue<String> jimengFollowUpQueue = new ConcurrentLinkedQueue<>();

    private int jimengTranscriptSeq = 1;

    private int localUserLineSeq;

    private static final int MAX_LOG_LINES = 500;

    private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 按时间顺序追加，供前端展示完整轨迹（含即梦异常栈）。 */
    private final CopyOnWriteArrayList<String> eventLog = new CopyOnWriteArrayList<>();

    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);

    /** 即梦轮询：仅在首次「已完成却无视频直链」时写详细日志，避免刷屏 */
    private final AtomicInteger jimengDoneNoUrlLogCount = new AtomicInteger(0);

    public DiaryAnimationJob(String jobId, Long diaryId, Long userId)
    {
        this.jobId = jobId;
        this.diaryId = diaryId;
        this.userId = userId;
        this.status = "RUNNING";
        this.stage = "QUEUED";
        this.message = "任务已排队，等待后台开始处理…";
        appendLog("[QUEUED] " + this.message);
    }

    /**
     * 追加一行日志（不限于 progress；异常详情等也可写入）。
     */
    public void appendLog(String line)
    {
        if (line == null)
        {
            return;
        }
        String t = line.trim();
        if (t.isEmpty())
        {
            return;
        }
        if (t.length() > 16000)
        {
            t = t.substring(0, 16000) + "…(截断)";
        }
        String entry = LocalDateTime.now().format(LOG_TS) + " " + t;
        eventLog.add(entry);
        while (eventLog.size() > MAX_LOG_LINES)
        {
            eventLog.remove(0);
        }
    }

    public List<String> snapshotLog()
    {
        return new ArrayList<>(eventLog);
    }

    public boolean isCancellationRequested()
    {
        return cancellationRequested.get();
    }

    /**
     * 用户点击取消：后续轮询检测到后抛出 {@link AnimationCancelledException} 并结束任务。
     */
    public void requestCancel()
    {
        cancellationRequested.set(true);
        appendLog("[系统] 用户请求取消，后台将在下一轮检测后停止与厂商交互");
    }

    /**
     * 更新进度（线程安全，供异步任务与厂商客户端回调）。
     */
    public void progress(String stage, String detail)
    {
        if (stage != null && !stage.isBlank())
        {
            this.stage = stage.trim();
        }
        if (detail != null && !detail.isBlank())
        {
            this.message = detail.trim();
            appendLog("[" + (this.stage == null ? "-" : this.stage) + "] " + detail.trim());
        }
    }

    public String getJobId()
    {
        return jobId;
    }

    public Long getDiaryId()
    {
        return diaryId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getAnimationUrl()
    {
        return animationUrl;
    }

    public void setAnimationUrl(String animationUrl)
    {
        this.animationUrl = animationUrl;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public String getStage()
    {
        return stage;
    }

    public void setStage(String stage)
    {
        this.stage = stage;
    }

    public String getExternalRef()
    {
        return externalRef;
    }

    public void setExternalRef(String externalRef)
    {
        this.externalRef = externalRef;
    }

    public int getJimengPollCount()
    {
        return jimengPollCount;
    }

    public void setJimengPollCount(int jimengPollCount)
    {
        this.jimengPollCount = jimengPollCount;
    }

    public int getLibtvPollCount()
    {
        return libtvPollCount;
    }

    public void setLibtvPollCount(int libtvPollCount)
    {
        this.libtvPollCount = libtvPollCount;
    }

    public AnimationGenParams getGenParams()
    {
        return genParams;
    }

    public void setGenParams(AnimationGenParams genParams)
    {
        if (genParams != null)
        {
            this.genParams = genParams;
        }
    }

    public int getLibTvLastSeq()
    {
        return libTvLastSeq;
    }

    public void setLibTvLastSeq(int libTvLastSeq)
    {
        this.libTvLastSeq = libTvLastSeq;
    }

    public boolean isAwaitingUserInput()
    {
        return awaitingUserInput;
    }

    public void setAwaitingUserInput(boolean awaitingUserInput)
    {
        this.awaitingUserInput = awaitingUserInput;
    }

    /**
     * 合并 LibTV 会话增量消息（按 seq 去重更新）。
     */
    public void upsertLibTvServerMessage(int seq, String role, String content)
    {
        if (content == null)
        {
            content = "";
        }
        synchronized (transcriptLock)
        {
            for (int i = 0; i < libTvTranscript.size(); i++)
            {
                Map<String, Object> row = libTvTranscript.get(i);
                Object s = row.get("seq");
                if (s instanceof Number && ((Number) s).intValue() == seq)
                {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("seq", seq);
                    m.put("role", role == null ? "" : role);
                    m.put("content", content);
                    libTvTranscript.set(i, m);
                    sortTranscriptLocked();
                    return;
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", seq);
            m.put("role", role == null ? "" : role);
            m.put("content", content);
            libTvTranscript.add(m);
            sortTranscriptLocked();
        }
    }

    /**
     * 记录本站用户刚发出的文本（在厂商回写前便于展示）。
     */
    public void appendLocalUserLine(String content)
    {
        if (content == null || content.isBlank())
        {
            return;
        }
        synchronized (transcriptLock)
        {
            localUserLineSeq--;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", localUserLineSeq);
            m.put("role", "user");
            m.put("content", content.trim());
            libTvTranscript.add(m);
            sortTranscriptLocked();
        }
    }

    private void sortTranscriptLocked()
    {
        libTvTranscript.sort(Comparator.comparingInt(a ->
        {
            Object s = a.get("seq");
            return s instanceof Number ? ((Number) s).intValue() : 0;
        }));
    }

    public List<Map<String, Object>> snapshotLibTvTranscript()
    {
        synchronized (transcriptLock)
        {
            return new ArrayList<>(libTvTranscript);
        }
    }

    /**
     * 用户在即梦阶段发送的补充说明（异步线程会 drain 后并入提示词并尽可能取消旧任务重投）。
     */
    public void enqueueJimengUserMessage(String text)
    {
        if (text == null || text.isBlank())
        {
            return;
        }
        jimengFollowUpQueue.offer(text.trim());
        appendJimengTranscriptLine("user", text.trim());
    }

    /**
     * @return 本轮待并入提示词的补充说明（FIFO），并从队列移除
     */
    public List<String> drainJimengFollowUps()
    {
        List<String> out = new ArrayList<>();
        String s;
        while ((s = jimengFollowUpQueue.poll()) != null)
        {
            out.add(s);
        }
        return out;
    }

    public void appendJimengTranscriptLine(String role, String content)
    {
        if (content == null || content.isBlank())
        {
            return;
        }
        synchronized (transcriptLock)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", jimengTranscriptSeq++);
            m.put("role", role == null ? "" : role);
            m.put("content", content.trim());
            jimengTranscript.add(m);
        }
    }

    public List<Map<String, Object>> snapshotJimengTranscript()
    {
        synchronized (transcriptLock)
        {
            return new ArrayList<>(jimengTranscript);
        }
    }

    /**
     * @return 是否应输出「完成但无 URL」的详细日志（每任务最多 1 次）
     */
    public boolean shouldLogJimengDoneNoUrlDetail()
    {
        return jimengDoneNoUrlLogCount.getAndIncrement() == 0;
    }
}
