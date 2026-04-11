package com.travel.controller;

import com.travel.common.ApiResponse;
import com.travel.model.dto.route.MultiPointRouteRequest;
import com.travel.model.dto.route.RoutePlanRequest;
import com.travel.model.vo.route.RoutePlanVO;
import com.travel.service.PoiTypeService;
import com.travel.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 路线规划相关接口。
 */
@RestController
@RequestMapping("/api/route")
public class RouteController
{

    private final RouteService routeService;

    private final PoiTypeService poiTypeService;

    public RouteController(RouteService routeService, PoiTypeService poiTypeService)
    {
        this.routeService = routeService;
        this.poiTypeService = poiTypeService;
    }

    /**
     * 两点路径规划。
     */
    @PostMapping
    public ApiResponse<RoutePlanVO> plan(@Valid @RequestBody RoutePlanRequest request)
    {
        return ApiResponse.success(routeService.plan(request), "规划成功");
    }

    /**
     * 多点路径规划。
     */
    @PostMapping("/multi-point")
    public ApiResponse<RoutePlanVO> planMultiPoint(@Valid @RequestBody MultiPointRouteRequest request)
    {
        return ApiResponse.success(routeService.planMultiPoint(request), "规划成功");
    }

    /**
     * 获取地图数据（节点/边）。
     */
    @GetMapping("/map-data")
    public ApiResponse<Map<String, Object>> mapData(@RequestParam(required = false) Long areaId)
    {
        return ApiResponse.success(routeService.getMapData(areaId), "获取成功");
    }

    /**
     * 获取路线规划的起终点候选 POI（不包含虚拟道路节点）。
     */
    @GetMapping("/poi-candidates")
    public ApiResponse<List<Map<String, Object>>> poiCandidates(@RequestParam(required = false) Long areaId)
    {
        return ApiResponse.success(routeService.listRoutePoiCandidates(areaId), "获取成功");
    }

    /**
     * 获取 POI 类型字典。
     */
    @GetMapping("/poi-types")
    public ApiResponse<List<Map<String, Object>>> poiTypes()
    {
        return ApiResponse.success(poiTypeService.listPoiTypes(), "获取成功");
    }
}

