package com.travel.service.impl;

import com.travel.model.entity.Food;
import com.travel.model.entity.Restaurant;
import com.travel.storage.InMemoryStore;
import com.travel.model.vo.food.FoodRecommendVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodServiceImplRecommendationTest
{

    @Mock
    private InMemoryStore store;

    @Test
    void recommendShouldOrderByScoreAndPaginate()
    {
        FoodServiceImpl service = new FoodServiceImpl(store);

        Food top = food(1L, 801L, 100, 5.0);
        Food middle = food(2L, 802L, 50, 4.0);
        Food low = food(3L, 803L, 10, 1.0);

        when(store.findFoodsByAreaId(201L)).thenReturn(List.of(top, middle, low));
        when(store.findRestaurantById(801L)).thenReturn(restaurant(801L, 39.998, 116.310));
        when(store.findRestaurantById(802L)).thenReturn(restaurant(802L, 39.998, 116.310));
        when(store.findRestaurantById(803L)).thenReturn(restaurant(803L, 39.998, 116.310));

        List<FoodRecommendVO> page1 = service.recommend(201L, null, null, 1000, null, null, null, 1, 2);
        List<FoodRecommendVO> page2 = service.recommend(201L, null, null, 1000, null, null, null, 2, 2);

        assertEquals(2, page1.size());
        assertEquals(1L, page1.get(0).getFood().getId());
        assertEquals(2L, page1.get(1).getFood().getId());

        assertEquals(1, page2.size());
        assertEquals(3L, page2.get(0).getFood().getId());
    }

    @Test
    void recommendShouldFilterByRadiusAndSortByDistanceWhenConfigured()
    {
        FoodServiceImpl service = new FoodServiceImpl(store);

        Food nearFood = food(1L, 801L, 10, 1.0);
        Food farFood = food(2L, 802L, 10, 1.0);

        when(store.findFoodsByAreaId(201L)).thenReturn(List.of(nearFood, farFood));
        when(store.findRestaurantById(801L)).thenReturn(restaurant(801L, 39.9980, 116.3100));
        when(store.findRestaurantById(802L)).thenReturn(restaurant(802L, 40.0200, 116.3300));

        List<FoodRecommendVO> result = service.recommend(201L, 39.9980, 116.3100, 500, 0.0, 0.0, 1.0, 1, 10);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getFood().getId());
        assertTrue(result.get(0).getDistance() != null && result.get(0).getDistance() < 1.0);
    }

    @Test
    void recommendShouldFallbackToDefaultWeightsWhenAllWeightsAreNonPositive()
    {
        FoodServiceImpl service = new FoodServiceImpl(store);

        Food top = food(1L, 801L, 100, 5.0);
        Food low = food(2L, 802L, 10, 1.0);

        when(store.findFoodsByAreaId(201L)).thenReturn(List.of(low, top));
        when(store.findRestaurantById(801L)).thenReturn(restaurant(801L, 39.998, 116.310));
        when(store.findRestaurantById(802L)).thenReturn(restaurant(802L, 39.998, 116.310));

        List<FoodRecommendVO> result = service.recommend(201L, 39.998, 116.310, 1000, -1.0, -2.0, -3.0, 1, 10);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getFood().getId());
    }

    private static Food food(Long id, Long restaurantId, Integer heat, Double rating)
    {
        Food food = new Food();
        food.setId(id);
        food.setRestaurantId(restaurantId);
        food.setAreaId(201L);
        food.setHeat(heat);
        food.setRating(rating);
        return food;
    }

    private static Restaurant restaurant(Long id, Double lat, Double lng)
    {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(id);
        restaurant.setLatitude(lat);
        restaurant.setLongitude(lng);
        return restaurant;
    }
}
