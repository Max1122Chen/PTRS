package com.travel.controller;

import com.travel.common.ApiResponse;
import com.travel.common.PageData;
import com.travel.model.entity.Food;
import com.travel.model.entity.Poi;
import com.travel.model.entity.Road;
import com.travel.model.entity.ScenicArea;
import com.travel.security.AuthUser;
import com.travel.security.SecurityUtil;
import com.travel.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端数据管理接口。
 *
 * <p>
 * 简化实现：仅允许 ADMIN 角色访问。
 * </p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController
{

    private final AdminService adminService;
    private final boolean devToolsEnabled;
    private final boolean authEnabled;

    public AdminController(AdminService adminService,
                           @Value("${app.admin.dev-tools.enabled:true}") boolean devToolsEnabled,
                           @Value("${app.security.auth-enabled:true}") boolean authEnabled)
    {
        this.adminService = adminService;
        this.devToolsEnabled = devToolsEnabled;
        this.authEnabled = authEnabled;
    }

    @PostMapping("/scenic-area")
    public ApiResponse<ScenicArea> addScenicArea(@Valid @RequestBody ScenicArea scenicArea)
    {
        if (!isAdmin())
        {
            return ApiResponse.failure(403, "无权限");
        }
        return ApiResponse.success(adminService.addScenicArea(scenicArea), "添加成功");
    }

    @GetMapping("/scenic-area")
    public ApiResponse<PageData<ScenicArea>> listScenicAreas(@RequestParam(value = "page", required = false) Integer page,
                                                             @RequestParam(value = "size", required = false) Integer size,
                                                             @RequestParam(value = "type", required = false) String type)
    {
        if (!isAdmin())
        {
            return ApiResponse.failure(403, "无权限");
        }
        return ApiResponse.success(adminService.listScenicAreas(page, size, type), "获取成功");
    }

    @PostMapping("/poi")
    public ApiResponse<Poi> addPoi(@Valid @RequestBody Poi poi)
    {
        if (!isAdmin())
        {
            return ApiResponse.failure(403, "无权限");
        }
        return ApiResponse.success(adminService.addPoi(poi), "添加成功");
    }

    @PostMapping("/building")
    public ApiResponse<Poi> addBuilding(@Valid @RequestBody Poi poi)
    {
        return addPoi(poi);
    }

    @PostMapping("/road")
    public ApiResponse<Road> addRoad(@Valid @RequestBody Road road)
    {
        if (!isAdmin())
        {
            return ApiResponse.failure(403, "无权限");
        }
        return ApiResponse.success(adminService.addRoad(road), "添加成功");
    }

    @PostMapping("/food")
    public ApiResponse<Food> addFood(@Valid @RequestBody Food food)
    {
        if (!isAdmin())
        {
            return ApiResponse.failure(403, "无权限");
        }
        return ApiResponse.success(adminService.addFood(food), "添加成功");
    }

    @PostMapping("/dev/import-place")
    public ApiResponse<Map<String, Object>> importPlace(@RequestBody Map<String, Object> payload)
    {
        if (!devToolsEnabled)
        {
            return ApiResponse.failure(403, "开发工具接口未启用");
        }
        if (!isAdmin())
        {
            return ApiResponse.failure(403, "无权限");
        }
        String placeName = payload == null ? null : String.valueOf(payload.getOrDefault("placeName", ""));
        boolean force = payload != null && Boolean.TRUE.equals(payload.get("force"));
        return ApiResponse.success(adminService.runPlaceSeedTask(placeName, force), "执行完成");
    }

    @GetMapping("/dev/local-place-search")
    public ApiResponse<java.util.List<Map<String, Object>>> localPlaceSearch(@RequestParam("keyword") String keyword)
    {
        if (!devToolsEnabled)
        {
            return ApiResponse.failure(403, "开发工具接口未启用");
        }
        if (!isAdmin())
        {
            return ApiResponse.failure(403, "无权限");
        }
        return ApiResponse.success(adminService.searchLocalPlaceMatches(keyword), "获取成功");
    }

    @GetMapping("/dev/osm-search")
    public ApiResponse<java.util.List<Map<String, Object>>> osmSearch(@RequestParam("keyword") String keyword)
    {
        if (!devToolsEnabled)
        {
            return ApiResponse.failure(403, "开发工具接口未启用");
        }
        if (!isAdmin())
        {
            return ApiResponse.failure(403, "无权限");
        }
        try
        {
            return ApiResponse.success(adminService.searchOsmCandidates(keyword), "获取成功");
        }
        catch (IllegalStateException ex)
        {
            return ApiResponse.failure(502, ex.getMessage());
        }
    }

    @PostMapping("/dev/generate-from-osm")
    public ApiResponse<Map<String, Object>> generateFromOsm(@RequestBody Map<String, Object> payload)
    {
        if (!devToolsEnabled)
        {
            return ApiResponse.failure(403, "开发工具接口未启用");
        }
        if (!isAdmin())
        {
            return ApiResponse.failure(403, "无权限");
        }
        String placeName = payload == null ? null : String.valueOf(payload.getOrDefault("placeName", ""));
        String query = payload == null ? null : String.valueOf(payload.getOrDefault("query", ""));
        Map<String, Object> selectedOsm = payload != null && payload.get("selectedOsm") instanceof Map<?, ?>
            ? (Map<String, Object>) payload.get("selectedOsm")
            : null;
        String selectedPlaceId = selectedOsm == null ? null : String.valueOf(selectedOsm.getOrDefault("placeId", ""));
        String selectedOsmType = selectedOsm == null ? null : String.valueOf(selectedOsm.getOrDefault("osmType", ""));
        String selectedOsmId = selectedOsm == null ? null : String.valueOf(selectedOsm.getOrDefault("osmId", ""));
        boolean force = payload != null && Boolean.TRUE.equals(payload.get("force"));
        boolean buildFrontend = payload == null || !payload.containsKey("buildFrontend") || Boolean.TRUE.equals(payload.get("buildFrontend"));
        return ApiResponse.success(
            adminService.generateFromSelectedOsm(placeName, query, selectedPlaceId, selectedOsmType, selectedOsmId, force, buildFrontend),
            "执行完成"
        );
    }

    private boolean isAdmin()
    {
        // In dev mode auth may be disabled; allow admin endpoints for local debugging.
        if (!authEnabled)
        {
            return true;
        }
        AuthUser user = SecurityUtil.getCurrentUser();
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}

