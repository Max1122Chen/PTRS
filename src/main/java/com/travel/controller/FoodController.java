package com.travel.controller;

import com.travel.common.ApiResponse;
import com.travel.model.dto.food.FoodRateRequest;
import com.travel.model.entity.Food;
import com.travel.model.vo.food.FoodDetailVO;
import com.travel.model.vo.food.FoodRecommendVO;
import com.travel.security.SecurityUtil;
import com.travel.service.FoodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 美食相关接口。
 */
@RestController
@RequestMapping("/api/food")
public class FoodController
{

    private final FoodService foodService;

    public FoodController(FoodService foodService)
    {
        this.foodService = foodService;
    }

    /**
     * 美食推荐。
     *
     * <p>
     * 文档要求：按热度/评价/距离排序，默认权重 0.3/0.5/0.2，且 Top-10 不进行完全排序。
     * </p>
     */
    @GetMapping("/recommendation")
    public ApiResponse<List<FoodRecommendVO>> recommendation(@RequestParam("areaId") @NotNull Long areaId,
                                                             @RequestParam(value = "anchorPoiId", required = false) Long anchorPoiId,
                                                             @RequestParam(value = "lat", required = false) Double lat,
                                                             @RequestParam(value = "lng", required = false) Double lng,
                                                             @RequestParam(value = "radius", required = false) Integer radius,
                                                             @RequestParam(value = "wHeat", required = false) Double wHeat,
                                                             @RequestParam(value = "wRating", required = false) Double wRating,
                                                             @RequestParam(value = "wDistance", required = false) Double wDistance,
                                                             @RequestParam(value = "page", required = false) Integer page,
                                                             @RequestParam(value = "size", required = false) Integer size)
    {
        return ApiResponse.success(
            foodService.recommend(areaId, anchorPoiId, lat, lng, radius, wHeat, wRating, wDistance, page, size),
            "获取成功"
        );
    }

    /**
     * 美食搜索。
     */
    @GetMapping("/search")
    public ApiResponse<List<Food>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                          @RequestParam(value = "cuisine", required = false) String cuisine,
                                          @RequestParam(value = "areaId", required = false) Long areaId,
                                          @RequestParam(value = "page", required = false) Integer page,
                                          @RequestParam(value = "size", required = false) Integer size)
    {
        return ApiResponse.success(foodService.search(keyword, cuisine, areaId, page, size), "查询成功");
    }

    /**
     * 美食详情。
     */
    @GetMapping("/detail/{id}")
    public ApiResponse<Food> detail(@PathVariable("id") @NotNull Long id)
    {
        return ApiResponse.success(foodService.detail(id), "获取成功");
    }

    /**
     * 美食详情视图（补充餐厅与景区名称）。
     */
    @GetMapping("/detail-view/{id}")
    public ApiResponse<FoodDetailVO> detailView(@PathVariable("id") @NotNull Long id)
    {
        return ApiResponse.success(foodService.detailView(id), "获取成功");
    }

    /**
     * 美食评分（需登录）。
     */
    @PostMapping("/rate")
    public ApiResponse<Void> rate(@Valid @RequestBody FoodRateRequest request)
    {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null)
        {
            return ApiResponse.failure(401, "未登录或令牌无效");
        }
        foodService.rate(userId, request.getFoodId(), request.getRating(), request.getComment());
        return ApiResponse.successMessage("评分成功");
    }
}

