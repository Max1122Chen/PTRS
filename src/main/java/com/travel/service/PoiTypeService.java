package com.travel.service;

import java.util.List;
import java.util.Map;

/**
 * POI 类型字典服务。
 */
public interface PoiTypeService
{

    /**
     * 返回 POI 类型列表。
     */
    List<Map<String, Object>> listPoiTypes();

    /**
     * 返回 code -> label 的映射。
     */
    Map<String, String> codeLabelMap();
}
