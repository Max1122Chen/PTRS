package com.travel.service.impl;

import com.travel.common.PageData;
import com.travel.model.entity.ScenicArea;
import com.travel.model.vo.recommendation.ScenicAreaRecommendVO;
import com.travel.storage.InMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest
{

    @Mock
    private InMemoryStore store;

    @Test
    void personalizedShouldRankMatchedTagsHigher()
    {
        RecommendationServiceImpl service = new RecommendationServiceImpl(store);

        ScenicArea natureArea = scenic(201L, "未名湖", 600, 4.2);
        ScenicArea historyArea = scenic(202L, "校史馆", 900, 4.7);

        when(store.findAllScenicAreas()).thenReturn(List.of(natureArea, historyArea));
        when(store.getUserInterests(101L)).thenReturn(Map.of("nature", 1.0));

        when(store.getScenicAreaTagWeights(201L)).thenReturn(mapOf("nature", 1.0));
        when(store.getScenicAreaTagWeights(202L)).thenReturn(mapOf("history", 1.0));

        when(store.getScenicAreaTagNames(201L)).thenReturn(List.of("nature"));
        when(store.getScenicAreaTagNames(202L)).thenReturn(List.of("history"));

        PageData<ScenicAreaRecommendVO> result = service.personalized(101L, 1, 10, null, null);

        assertEquals(2L, result.getTotal());
        assertEquals(201L, result.getList().get(0).getId());
        assertTrue(result.getList().get(0).getScore() > result.getList().get(1).getScore());
    }

    @Test
    void personalizedShouldFilterByTagKeyword()
    {
        RecommendationServiceImpl service = new RecommendationServiceImpl(store);

        ScenicArea natureArea = scenic(201L, "未名湖", 600, 4.2);
        ScenicArea scienceArea = scenic(210L, "科技馆", 700, 4.7);

        when(store.findAllScenicAreas()).thenReturn(List.of(natureArea, scienceArea));
        when(store.getUserInterests(101L)).thenReturn(Map.of("nature", 1.0));

        when(store.getScenicAreaTagWeights(201L)).thenReturn(mapOf("nature", 1.0));
        when(store.getScenicAreaTagWeights(210L)).thenReturn(mapOf("science", 1.0));

        when(store.getScenicAreaTagNames(210L)).thenReturn(List.of("science"));

        PageData<ScenicAreaRecommendVO> result = service.personalized(101L, 1, 10, null, "sci");

        assertEquals(1L, result.getTotal());
        assertEquals(210L, result.getList().get(0).getId());
    }

    @Test
    void personalizedShouldMatchChineseInterestAliasAndProvideReason()
    {
        RecommendationServiceImpl service = new RecommendationServiceImpl(store);

        ScenicArea natureArea = scenic(201L, "未名湖", 600, 4.2);
        ScenicArea historyArea = scenic(202L, "校史馆", 900, 4.7);

        when(store.findAllScenicAreas()).thenReturn(List.of(natureArea, historyArea));
        when(store.getUserInterests(101L)).thenReturn(Map.of("自然", 1.0));

        when(store.getScenicAreaTagWeights(201L)).thenReturn(mapOf("nature", 1.0));
        when(store.getScenicAreaTagWeights(202L)).thenReturn(mapOf("history", 1.0));

        when(store.getScenicAreaTagNames(201L)).thenReturn(List.of("nature"));
        when(store.getScenicAreaTagNames(202L)).thenReturn(List.of("history"));

        PageData<ScenicAreaRecommendVO> result = service.personalized(101L, 1, 10, null, null);

        assertEquals(2L, result.getTotal());
        assertEquals(201L, result.getList().get(0).getId());
        assertTrue(result.getList().get(0).getReason() != null && !result.getList().get(0).getReason().isBlank());
    }

    @Test
    void listShouldSortByHeatDescending()
    {
        RecommendationServiceImpl service = new RecommendationServiceImpl(store);

        ScenicArea lowHeat = scenic(1L, "A", 100, 4.8);
        ScenicArea highHeat = scenic(2L, "B", 900, 3.9);

        when(store.findAllScenicAreas()).thenReturn(List.of(lowHeat, highHeat));
        when(store.getScenicAreaTagNames(1L)).thenReturn(List.of());
        when(store.getScenicAreaTagNames(2L)).thenReturn(List.of());

        PageData<ScenicArea> result = service.list(1, 10, "heat", null);

        assertEquals(2L, result.getTotal());
        assertEquals(2L, result.getList().get(0).getId());
        assertEquals(1L, result.getList().get(1).getId());
    }

    private static ScenicArea scenic(Long id, String name, Integer heat, Double rating)
    {
        ScenicArea scenicArea = new ScenicArea();
        scenicArea.setId(id);
        scenicArea.setName(name);
        scenicArea.setHeat(heat);
        scenicArea.setRating(rating);
        scenicArea.setType("test");
        return scenicArea;
    }

    private static Map<String, Double> mapOf(String key, Double value)
    {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
