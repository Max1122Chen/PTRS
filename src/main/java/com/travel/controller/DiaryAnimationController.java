package com.travel.controller;

import com.travel.common.ApiResponse;
import com.travel.model.dto.diary.AnimationGenerateRequest;
import com.travel.model.dto.diary.DiaryAnimationJobMessageRequest;
import com.travel.security.SecurityUtil;
import com.travel.service.DiaryAnimationService;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 日记旅游动画生成（即梦 / LibTV 由后端编排，前端仅见任务状态与本站成片 URL）。
 */
@RestController
@RequestMapping("/api/diary")
public class DiaryAnimationController
{

    private final DiaryAnimationService diaryAnimationService;

    public DiaryAnimationController(DiaryAnimationService diaryAnimationService)
    {
        this.diaryAnimationService = diaryAnimationService;
    }

    @PostMapping("/{id}/animation/generate")
    public ApiResponse<Map<String, Object>> generate(@PathVariable("id") @NotNull Long diaryId,
        @RequestBody(required = false) AnimationGenerateRequest body)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        try
        {
            Map<String, Object> data = diaryAnimationService.submitGenerate(userId, diaryId, body);
            return ApiResponse.success(data, "已提交生成任务");
        }
        catch (IllegalArgumentException ex)
        {
            return ApiResponse.failure(400, ex.getMessage());
        }
        catch (IllegalStateException ex)
        {
            return ApiResponse.failure(503, ex.getMessage());
        }
    }

    /**
     * 向进行中的生成任务追加用户消息（LibTV 会话 / 即梦补充说明队列）。
     */
    @PostMapping("/animation/jobs/{jobId}/message")
    public ApiResponse<Void> postJobMessage(@PathVariable("jobId") String jobId,
        @RequestBody(required = false) DiaryAnimationJobMessageRequest body)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        if (body == null)
        {
            return ApiResponse.failure(400, "请求体不能为空");
        }
        try
        {
            diaryAnimationService.postJobMessage(userId, jobId, body);
            return ApiResponse.success(null, "已转发");
        }
        catch (IllegalArgumentException ex)
        {
            return ApiResponse.failure(400, ex.getMessage());
        }
        catch (IllegalStateException ex)
        {
            return ApiResponse.failure(400, ex.getMessage());
        }
    }

    /**
     * 取消进行中的生成任务（停止本地轮询；云端任务可能仍在排队）。
     */
    @PostMapping("/animation/jobs/{jobId}/cancel")
    public ApiResponse<Void> cancelJob(@PathVariable("jobId") String jobId)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        try
        {
            diaryAnimationService.cancelJob(userId, jobId);
            return ApiResponse.success(null, "已请求取消");
        }
        catch (IllegalArgumentException ex)
        {
            return ApiResponse.failure(400, ex.getMessage());
        }
        catch (IllegalStateException ex)
        {
            return ApiResponse.failure(400, ex.getMessage());
        }
    }

    @GetMapping("/animation/jobs/{jobId}")
    public ApiResponse<Map<String, Object>> jobStatus(@PathVariable("jobId") String jobId)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        Map<String, Object> data = diaryAnimationService.getJobStatus(jobId);
        if (data == null)
        {
            return ApiResponse.failure(404, "任务不存在或已过期");
        }
        if (!userId.equals(asLong(data.get("userId"))))
        {
            return ApiResponse.failure(403, "无权查看该任务");
        }
        return ApiResponse.success(data, "查询成功");
    }

    private static Long asLong(Object o)
    {
        if (o instanceof Number n)
        {
            return n.longValue();
        }
        return null;
    }
}
