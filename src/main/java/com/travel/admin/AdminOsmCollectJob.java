package com.travel.admin;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理端 OSM 采集异步任务（内存态，dev 演示用）。
 */
public class AdminOsmCollectJob
{

    private final String taskId;

    private final String kind;

    private volatile String status = "PENDING";

    private volatile String phase = "queued";

    private volatile String message = "任务已排队";

    private volatile LocalDateTime startedAt;

    private volatile LocalDateTime finishedAt;

    private final Map<String, Object> result = new ConcurrentHashMap<>();

    public AdminOsmCollectJob(String taskId, String kind)
    {
        this.taskId = taskId;
        this.kind = kind;
    }

    public String getTaskId()
    {
        return taskId;
    }

    public String getKind()
    {
        return kind;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getPhase()
    {
        return phase;
    }

    public void setPhase(String phase)
    {
        this.phase = phase;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public LocalDateTime getStartedAt()
    {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt)
    {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt()
    {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt)
    {
        this.finishedAt = finishedAt;
    }

    public Map<String, Object> getResult()
    {
        return result;
    }

    public void setResult(Map<String, Object> result)
    {
        this.result.clear();
        if (result != null)
        {
            this.result.putAll(result);
        }
    }
}
