package com.travel.service.impl;

import com.travel.admin.AdminOsmCollectJob;
import com.travel.service.AdminOsmCollectService;
import com.travel.service.AdminService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OSM 采集后台任务：立即返回 taskId，客户端轮询状态。
 */
@Service
public class AdminOsmCollectServiceImpl implements AdminOsmCollectService
{

    private final AdminService adminService;

    private final ThreadPoolTaskExecutor executor;

    private final ConcurrentHashMap<String, AdminOsmCollectJob> jobs = new ConcurrentHashMap<>();

    public AdminOsmCollectServiceImpl(AdminService adminService,
                                      @Qualifier("adminOsmCollectExecutor") ThreadPoolTaskExecutor executor)
    {
        this.adminService = adminService;
        this.executor = executor;
    }

    @Override
    public Map<String, Object> submitGenerateTask(String placeName,
                                                  String query,
                                                  String selectedPlaceId,
                                                  String selectedOsmType,
                                                  String selectedOsmId,
                                                  boolean force,
                                                  boolean buildFrontend,
                                                  boolean collectIndoor)
    {
        String taskId = newTaskId();
        AdminOsmCollectJob job = new AdminOsmCollectJob(taskId, "generate");
        jobs.put(taskId, job);
        executor.execute(() -> runGenerate(job, placeName, query, selectedPlaceId, selectedOsmType, selectedOsmId, force,
            buildFrontend, collectIndoor));
        return Map.of("taskId", taskId, "status", job.getStatus(), "message", "采集任务已提交");
    }

    @Override
    public Map<String, Object> submitImportTask(String placeName, boolean force, boolean collectIndoor)
    {
        String taskId = newTaskId();
        AdminOsmCollectJob job = new AdminOsmCollectJob(taskId, "import");
        jobs.put(taskId, job);
        executor.execute(() -> runImport(job, placeName, force, collectIndoor));
        return Map.of("taskId", taskId, "status", job.getStatus(), "message", "导入任务已提交");
    }

    @Override
    public Map<String, Object> getTask(String taskId)
    {
        AdminOsmCollectJob job = jobs.get(taskId);
        if (job == null)
        {
            throw new IllegalArgumentException("任务不存在或已过期: " + taskId);
        }
        return toView(job);
    }

    private void runGenerate(AdminOsmCollectJob job,
                             String placeName,
                             String query,
                             String selectedPlaceId,
                             String selectedOsmType,
                             String selectedOsmId,
                             boolean force,
                             boolean buildFrontend,
                             boolean collectIndoor)
    {
        job.setStatus("RUNNING");
        job.setPhase("collect");
        job.setMessage("正在执行 OSM 采集脚本…");
        job.setStartedAt(LocalDateTime.now());
        try
        {
            Map<String, Object> result = adminService.generateFromSelectedOsm(placeName, query, selectedPlaceId,
                selectedOsmType, selectedOsmId, force, buildFrontend, collectIndoor,
                (phase, message) ->
                {
                    job.setPhase(phase);
                    job.setMessage(message);
                });
            finishWithResult(job, result);
        }
        catch (Exception ex)
        {
            fail(job, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private void runImport(AdminOsmCollectJob job, String placeName, boolean force, boolean collectIndoor)
    {
        job.setStatus("RUNNING");
        job.setPhase("collect");
        job.setMessage("正在按地名导入 OSM 数据…");
        job.setStartedAt(LocalDateTime.now());
        try
        {
            Map<String, Object> result = adminService.runPlaceSeedTask(placeName, force, collectIndoor);
            finishWithResult(job, result);
        }
        catch (Exception ex)
        {
            fail(job, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private void finishWithResult(AdminOsmCollectJob job, Map<String, Object> result)
    {
        job.setResult(result);
        String seedStatus = result == null ? "" : String.valueOf(result.getOrDefault("status", ""));
        job.setStatus(mapTerminalStatus(seedStatus));
        Object msg = result == null ? null : result.get("message");
        job.setMessage(msg == null ? "任务结束" : String.valueOf(msg));
        job.setPhase("done");
        job.setFinishedAt(LocalDateTime.now());
    }

    private void fail(AdminOsmCollectJob job, String message)
    {
        job.setStatus("FAILED");
        job.setPhase("failed");
        job.setMessage(message);
        job.setFinishedAt(LocalDateTime.now());
    }

    private static String mapTerminalStatus(String seedStatus)
    {
        if (seedStatus == null)
        {
            return "FAILED";
        }
        return switch (seedStatus)
        {
            case "success" -> "SUCCESS";
            case "skipped" -> "SKIPPED";
            default -> "FAILED";
        };
    }

    private static String newTaskId()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Map<String, Object> toView(AdminOsmCollectJob job)
    {
        Map<String, Object> view = new HashMap<>();
        view.put("taskId", job.getTaskId());
        view.put("kind", job.getKind());
        view.put("status", job.getStatus());
        view.put("phase", job.getPhase());
        view.put("message", job.getMessage());
        view.put("startedAt", job.getStartedAt());
        view.put("finishedAt", job.getFinishedAt());
        if (!job.getResult().isEmpty())
        {
            view.put("result", new HashMap<>(job.getResult()));
        }
        return view;
    }
}
