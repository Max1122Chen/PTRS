package com.travel.service;

import java.util.Map;

/**
 * 管理端 OSM 采集异步任务（避免 HTTP 长连接 502 / 代理超时）。
 */
public interface AdminOsmCollectService
{

    Map<String, Object> submitGenerateTask(String placeName,
                                           String query,
                                           String selectedPlaceId,
                                           String selectedOsmType,
                                           String selectedOsmId,
                                           boolean force,
                                           boolean buildFrontend,
                                           boolean collectIndoor);

    Map<String, Object> submitImportTask(String placeName, boolean force, boolean collectIndoor);

    Map<String, Object> getTask(String taskId);
}
