package com.travel.service;

import com.travel.model.dto.route.MultiPointRouteRequest;
import com.travel.model.dto.route.RoutePlanRequest;
import com.travel.model.vo.route.RoutePlanVO;

import java.util.List;
import java.util.Map;

/**
 * 路线规划服务。
 */
public interface RouteService
{

    /**
     * 两点路径规划。
     */
    RoutePlanVO plan(RoutePlanRequest request);

    /**
     * 多点路径规划（第一个点为起点；可选回到起点，否则最后一个点为终点）。
     */
    RoutePlanVO planMultiPoint(MultiPointRouteRequest request);

    /**
     * 获取地图数据（节点与边），用于前端绘图。
     *
     * @param areaId 景区/校园 ID
     * @return nodes/edges 结构
     */
    Map<String, Object> getMapData(Long areaId);

    /**
     * 获取可用于路线起终点选择的 POI 候选（不含虚拟道路节点）。
     */
    List<Map<String, Object>> listRoutePoiCandidates(Long areaId);
}

