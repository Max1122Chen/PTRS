package com.travel.service;

import com.travel.model.dto.diary.AnimationGenerateRequest;
import com.travel.model.dto.diary.DiaryAnimationJobMessageRequest;

import java.util.Map;

/**
 * 日记旅游动画生成（即梦 + LibTV）。
 */
public interface DiaryAnimationService
{

    /**
     * 提交异步任务；返回 map 含 {@code jobId}、{@code generationParams}（与请求解析结果一致，便于前端立刻展示）。
     */
    Map<String, Object> submitGenerate(Long userId, Long diaryId, AnimationGenerateRequest request);

    Map<String, Object> getJobStatus(String jobId);

    /**
     * 向进行中的任务追加用户消息：LibTV 走会话 API；即梦路径并入补充说明队列并在后台重投。
     */
    void postJobMessage(Long userId, String jobId, DiaryAnimationJobMessageRequest body);

    /**
     * 请求取消进行中的任务（停止本地轮询；无法撤销云端已提交任务）。
     */
    void cancelJob(Long userId, String jobId);
}
