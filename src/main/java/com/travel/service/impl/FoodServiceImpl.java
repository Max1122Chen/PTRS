package com.travel.service.impl;

import com.travel.algorithm.TopKSelector;
import com.travel.model.entity.Food;
import com.travel.model.entity.ScenicArea;
import com.travel.model.entity.Restaurant;
import com.travel.storage.InMemoryStore;
import com.travel.model.vo.food.FoodDetailVO;
import com.travel.model.vo.food.FoodRecommendVO;
import com.travel.service.FoodService;
import com.travel.util.GeoUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 美食服务实现。
 */
@Service
public class FoodServiceImpl implements FoodService
{

    private static final int DEFAULT_RADIUS_METERS = 1000;

    private static final double DEFAULT_WEIGHT_HEAT = 0.3;

    private static final double DEFAULT_WEIGHT_RATING = 0.5;

    private static final double DEFAULT_WEIGHT_DISTANCE = 0.2;

    private final InMemoryStore store;

    private final TopKSelector<FoodRecommendVO> topKSelector;

    public FoodServiceImpl(InMemoryStore store)
    {
        this.store = store;
        this.topKSelector = new TopKSelector<>();
    }

    @Override
    public List<FoodRecommendVO> recommend(Long areaId,
                                          Double lat,
                                          Double lng,
                                          Integer radiusMeters,
                                          Double weightHeat,
                                          Double weightRating,
                                          Double weightDist,
                                          Integer page,
                                          Integer size)
    {
        if (areaId == null)
        {
            throw new IllegalArgumentException("areaId 不能为空");
        }

        // 如果前端不传经纬度，则尝试用景区自身坐标做距离估计（用于“距离排序”）。
        Double effectiveLat = lat;
        Double effectiveLng = lng;
        if (effectiveLat == null || effectiveLng == null)
        {
            var area = store.findScenicAreaById(areaId);
            if (area != null)
            {
                if (effectiveLat == null)
                {
                    effectiveLat = area.getLatitude();
                }
                if (effectiveLng == null)
                {
                    effectiveLng = area.getLongitude();
                }
            }
        }

        int r = radiusMeters == null || radiusMeters <= 0 ? DEFAULT_RADIUS_METERS : radiusMeters;
        double wHeat = sanitizeWeight(weightHeat, DEFAULT_WEIGHT_HEAT);
        double wRating = sanitizeWeight(weightRating, DEFAULT_WEIGHT_RATING);
        double wDist = sanitizeWeight(weightDist, DEFAULT_WEIGHT_DISTANCE);
        if (wHeat + wRating + wDist <= 0)
        {
            wHeat = DEFAULT_WEIGHT_HEAT;
            wRating = DEFAULT_WEIGHT_RATING;
            wDist = DEFAULT_WEIGHT_DISTANCE;
        }

        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size <= 0 ? 10 : Math.min(size, 50);

        // 取需要的 TopN = page * size，避免全排序
        int topN = p * s;

        List<Food> foods = store.findFoodsByAreaId(areaId);
        if (foods.isEmpty())
        {
            return List.of();
        }

        Map<Long, Restaurant> restaurantMap = new HashMap<>();
        for (Food f : foods)
        {
            Long restaurantId = f.getRestaurantId();
            if (restaurantId == null || restaurantMap.containsKey(restaurantId))
            {
                continue;
            }
            Restaurant restaurant = store.findRestaurantById(restaurantId);
            if (restaurant != null)
            {
                restaurantMap.put(restaurantId, restaurant);
            }
        }

        List<FoodRecommendVO> candidates = new ArrayList<>(foods.size());
        int maxHeat = 0;
        for (Food f : foods)
        {
            if (f.getHeat() != null && f.getHeat() > maxHeat)
            {
                maxHeat = f.getHeat();
            }
        }
        if (maxHeat <= 0)
        {
            maxHeat = 1;
        }

        boolean hasLocation = effectiveLat != null && effectiveLng != null;
        for (Food food : foods)
        {
            Restaurant restaurant = restaurantMap.get(food.getRestaurantId());
            if (restaurant == null)
            {
                continue;
            }

            Double distance = null;
            if (hasLocation && restaurant.getLatitude() != null && restaurant.getLongitude() != null)
            {
                distance = GeoUtil.distanceMeters(effectiveLat, effectiveLng, restaurant.getLatitude(), restaurant.getLongitude());
                if (distance > r)
                {
                    continue;
                }
            }

            FoodRecommendVO vo = new FoodRecommendVO();
            vo.setFood(food);
            vo.setRestaurant(restaurant);
            vo.setDistance(distance);

            double score = calcScore(food, distance, r, maxHeat, wHeat, wRating, wDist);
            vo.setScore(score);
            candidates.add(vo);
        }

        if (candidates.isEmpty())
        {
            return List.of();
        }

        Comparator<FoodRecommendVO> comparator = Comparator.comparingDouble(v -> v.getScore() == null ? 0.0 : v.getScore());
        List<FoodRecommendVO> top = topKSelector.selectTopK(candidates, topN, comparator);

        int from = (p - 1) * s;
        if (from >= top.size())
        {
            return List.of();
        }
        int to = Math.min(from + s, top.size());
        return top.subList(from, to);
    }

    @Override
    public List<Food> search(String keyword, String cuisine, Long areaId, Integer page, Integer size)
    {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size <= 0 ? 10 : Math.min(size, 50);
        int offset = (p - 1) * s;
        int fetch = offset + s;

        List<Food> candidates = store.searchFoods(keyword, cuisine, areaId, fetch);
        if (candidates.isEmpty())
        {
            return List.of();
        }

        // DB 这里是按 heat desc, rating desc 排序；内存里用同样规则近似。
        candidates.sort((a, b) ->
        {
            int heatA = a.getHeat() == null ? 0 : a.getHeat();
            int heatB = b.getHeat() == null ? 0 : b.getHeat();
            if (heatA != heatB)
            {
                return Integer.compare(heatB, heatA);
            }
            double ratingA = a.getRating() == null ? 0.0 : a.getRating();
            double ratingB = b.getRating() == null ? 0.0 : b.getRating();
            return Double.compare(ratingB, ratingA);
        });

        if (offset >= candidates.size())
        {
            return List.of();
        }
        int to = Math.min(offset + s, candidates.size());
        return candidates.subList(offset, to);
    }

    @Override
    public Food detail(Long id)
    {
        Food food = store.findFoodById(id);
        if (food == null)
        {
            throw new IllegalArgumentException("美食不存在");
        }
        return food;
    }

    @Override
    public FoodDetailVO detailView(Long id)
    {
        Food food = detail(id);

        FoodDetailVO vo = new FoodDetailVO();
        vo.setFood(food);

        if (food.getRestaurantId() != null)
        {
            Restaurant restaurant = store.findRestaurantById(food.getRestaurantId());
            vo.setRestaurantName(restaurant == null ? null : restaurant.getName());
        }

        if (food.getAreaId() != null)
        {
            ScenicArea scenicArea = store.findScenicAreaById(food.getAreaId());
            vo.setAreaName(scenicArea == null ? null : scenicArea.getName());
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rate(Long userId, Long foodId, double rating, String comment)
    {
        Food food = store.findFoodById(foodId);
        if (food == null)
        {
            throw new IllegalArgumentException("美食不存在");
        }

        com.travel.model.entity.Comment c = new com.travel.model.entity.Comment();
        c.setUserId(userId);
        c.setTargetId(foodId);
        c.setTargetType("FOOD");
        c.setContent(comment == null ? "" : comment);
        c.setRating(rating);
        LocalDateTime now = LocalDateTime.now();
        c.setCreateTime(now);
        c.setUpdateTime(now);
        store.insertComment(c);

        double avg = store.getAverageRating("FOOD", foodId, rating);
        food.setRating(avg);
        store.updateFood(food);
    }

    private double calcScore(Food food,
                             Double distance,
                             int radiusMeters,
                             int maxHeat,
                             double wHeat,
                             double wRating,
                             double wDist)
    {
        double heat = food.getHeat() == null ? 0.0 : food.getHeat();
        double rating = food.getRating() == null ? 0.0 : food.getRating();

        // 归一化：热度以 maxHeat 为基准；评分以 5 为上限；距离越近得分越高
        double heatScore = heat / maxHeat;
        double ratingScore = Math.min(Math.max(rating / 5.0, 0.0), 1.0);
        double distScore = 0.0;
        if (distance != null)
        {
            double d = Math.min(Math.max(distance, 0.0), radiusMeters);
            distScore = 1.0 - (d / radiusMeters);
        }

        double sum = wHeat + wRating + wDist;
        if (sum <= 0)
        {
            sum = 1.0;
        }
        double wh = wHeat / sum;
        double wr = wRating / sum;
        double wd = wDist / sum;

        return wh * heatScore + wr * ratingScore + wd * distScore;
    }

    private double sanitizeWeight(Double candidate, double defaultValue)
    {
        if (candidate == null)
        {
            return defaultValue;
        }
        return Math.max(0.0, candidate);
    }
}

