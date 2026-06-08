package com.travel.controller;

import com.travel.common.ApiResponse;
import com.travel.model.entity.Facility;
import com.travel.model.vo.facility.FacilityNearbyVO;
import com.travel.service.FacilityService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场所/设施查询接口。
 */
@RestController
@RequestMapping("/api/facility")
public class FacilityController
{

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService)
    {
        this.facilityService = facilityService;
    }

    /**
     * 附近设施。
     */
    @GetMapping("/nearby")
    public ApiResponse<List<FacilityNearbyVO>> nearby(@RequestParam(value = "lat", required = false) Double lat,
                                                      @RequestParam(value = "lng", required = false) Double lng,
                                                      @RequestParam(value = "anchorPoiId", required = false) Long anchorPoiId,
                                                      @RequestParam(value = "radius", required = false) Integer radius,
                                                      @RequestParam(value = "type", required = false) String type,
                                                      @RequestParam(value = "areaId", required = false) Long areaId)
    {
        int r = radius == null ? 500 : radius;
        if (anchorPoiId != null)
        {
            return ApiResponse.success(facilityService.nearbyByAnchor(anchorPoiId, r, type, areaId), "查询成功");
        }
        if (lat == null || lng == null)
        {
            return ApiResponse.failure(400, "须提供 anchorPoiId 或 lat/lng");
        }
        return ApiResponse.success(facilityService.nearby(lat, lng, r, type, areaId), "查询成功");
    }

    /**
     * 设施搜索。
     */
    @GetMapping("/search")
    public ApiResponse<?> search(@RequestParam(value = "keyword", required = false) String keyword,
                                 @RequestParam(value = "type", required = false) String type,
                                 @RequestParam(value = "areaId", required = false) Long areaId,
                                 @RequestParam(value = "anchorPoiId", required = false) Long anchorPoiId,
                                 @RequestParam(value = "radius", required = false) Integer radius,
                                 @RequestParam(value = "limit", required = false) Integer limit)
    {
        int l = limit == null ? 50 : limit;
        if (anchorPoiId != null)
        {
            int r = radius == null ? 500 : radius;
            return ApiResponse.success(
                facilityService.searchNearAnchor(keyword, type, areaId, anchorPoiId, r, l),
                "查询成功"
            );
        }
        return ApiResponse.success(facilityService.search(keyword, type, areaId, l), "查询成功");
    }

    /**
     * 设施详情。
     */
    @GetMapping("/detail/{id}")
    public ApiResponse<Facility> detail(@PathVariable("id") @NotNull Long id)
    {
        return ApiResponse.success(facilityService.detail(id), "获取成功");
    }
}

