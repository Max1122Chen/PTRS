package com.travel.service;

import com.travel.model.entity.Food;
import com.travel.model.vo.food.FoodDetailVO;
import com.travel.model.vo.food.FoodRecommendVO;

import java.util.List;

/**
 * 美食服务。
 */
public interface FoodService
{

    /**
     * 美食推荐（按热度/评分/距离综合排序，支持 Top-K）。
     *
     * @param areaId       景区/校园 ID
     * @param lat          用户纬度（可选，提供则启用距离计算）
     * @param lng          用户经度（可选，提供则启用距离计算）
     * @param radiusMeters 距离范围（默认 1000m）
     * @param weightHeat   热度权重（默认 0.3）
     * @param weightRating 评分权重（默认 0.5）
     * @param weightDist   距离权重（默认 0.2）
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @return 推荐列表
     */
    List<FoodRecommendVO> recommend(Long areaId,
                                    Long anchorPoiId,
                                    Double lat,
                                    Double lng,
                                    Integer radiusMeters,
                                    Double weightHeat,
                                    Double weightRating,
                                    Double weightDist,
                                    Integer page,
                                    Integer size);

    /**
     * 美食搜索（名称/描述模糊匹配）。
     */
    List<Food> search(String keyword, String cuisine, Long areaId, Integer page, Integer size);

    /**
     * 美食详情。
     */
    Food detail(Long id);

    /**
     * 美食详情视图（包含餐厅名与景区名）。
     */
    FoodDetailVO detailView(Long id);

    /**
     * 美食评分（1-5），可附带评论。
     */
    void rate(Long userId, Long foodId, double rating, String comment);
}

