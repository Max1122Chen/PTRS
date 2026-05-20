package com.travel.controller;

import com.travel.common.ApiResponse;
import com.travel.indoor.IndoorPathResult;
import com.travel.model.dto.indoor.IndoorPlanRequest;
import com.travel.service.IndoorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/indoor")
public class IndoorController
{

    private final IndoorService indoorService;

    public IndoorController(IndoorService indoorService)
    {
        this.indoorService = indoorService;
    }

    @GetMapping("/buildings")
    public ApiResponse<List<Map<String, Object>>> listBuildings(@RequestParam(required = false) Long areaId)
    {
        return ApiResponse.success(indoorService.listBuildings(areaId), "获取成功");
    }

    @GetMapping("/{buildingPoiId}/meta")
    public ApiResponse<Map<String, Object>> meta(@PathVariable long buildingPoiId)
    {
        return ApiResponse.success(indoorService.getMeta(buildingPoiId), "获取成功");
    }

    @GetMapping("/{buildingPoiId}/floor/{level}")
    public ApiResponse<Map<String, Object>> floor(@PathVariable long buildingPoiId, @PathVariable String level)
    {
        return ApiResponse.success(indoorService.getFloorGraph(buildingPoiId, level), "获取成功");
    }

    @PostMapping("/{buildingPoiId}/plan")
    public ApiResponse<IndoorPathResult> plan(@PathVariable long buildingPoiId,
                                              @Valid @RequestBody IndoorPlanRequest request)
    {
        IndoorPathResult result = indoorService.plan(buildingPoiId, request);
        if (result.getPath() == null || result.getPath().isEmpty())
        {
            return ApiResponse.failure(400, "起点与终点在室内图中不连通");
        }
        return ApiResponse.success(result, "规划成功");
    }
}
