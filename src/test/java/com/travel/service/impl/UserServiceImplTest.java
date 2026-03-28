package com.travel.service.impl;

import com.travel.model.dto.auth.InterestItemRequest;
import com.travel.model.dto.auth.UpdateInterestRequest;
import com.travel.model.entity.Food;
import com.travel.model.entity.ScenicArea;
import com.travel.model.entity.UserBehavior;
import com.travel.model.entity.UserInterest;
import com.travel.security.JwtUtil;
import com.travel.storage.InMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest
{

    @Mock
    private InMemoryStore store;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Test
    void updateInterestsShouldUseDefaultWeightWhenWeightIsNull()
    {
        UserServiceImpl service = new UserServiceImpl(store, passwordEncoder, jwtUtil);

        InterestItemRequest item = new InterestItemRequest();
        item.setType("  美食  ");
        item.setWeight(null);

        UpdateInterestRequest request = new UpdateInterestRequest();
        request.setInterests(List.of(item));

        service.updateInterests(101L, request);

        ArgumentCaptor<List<UserInterest>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).replaceUserInterests(org.mockito.ArgumentMatchers.eq(101L), captor.capture());

        List<UserInterest> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("food", saved.get(0).getInterestType());
        assertEquals(1.0, saved.get(0).getWeight());
        assertNotNull(saved.get(0).getCreateTime());
    }

    @Test
    void updateInterestsShouldPersistProvidedWeight()
    {
        UserServiceImpl service = new UserServiceImpl(store, passwordEncoder, jwtUtil);

        InterestItemRequest item = new InterestItemRequest();
        item.setType("历史");
        item.setWeight(2.3);

        UpdateInterestRequest request = new UpdateInterestRequest();
        request.setInterests(List.of(item));

        service.updateInterests(202L, request);

        ArgumentCaptor<List<UserInterest>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).replaceUserInterests(org.mockito.ArgumentMatchers.eq(202L), captor.capture());

        List<UserInterest> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("history", saved.get(0).getInterestType());
        assertEquals(2.3, saved.get(0).getWeight());
    }

    @Test
    void recordEngagementShouldIncreaseScenicTagWeights()
    {
        UserServiceImpl service = new UserServiceImpl(store, passwordEncoder, jwtUtil);

        ScenicArea scenicArea = new ScenicArea();
        scenicArea.setId(201L);

        when(store.findScenicAreaById(201L)).thenReturn(scenicArea);
        when(store.getScenicAreaTagWeights(201L)).thenReturn(Map.of("nature", 1.0, "photo", 0.5));
        when(store.getUserInterests(101L)).thenReturn(Map.of("nature", 1.0));

        service.recordEngagement(101L, "SCENIC", 201L, "LIKE");

        verify(store).insertUserBehavior(any(UserBehavior.class));

        ArgumentCaptor<List<UserInterest>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).replaceUserInterests(org.mockito.ArgumentMatchers.eq(101L), captor.capture());

        Map<String, Double> result = captor.getValue().stream()
            .collect(java.util.stream.Collectors.toMap(UserInterest::getInterestType, UserInterest::getWeight));

        assertEquals(1.2, result.get("nature"), 1e-8);
        assertEquals(0.1, result.get("photo"), 1e-8);
    }

    @Test
    void recordEngagementShouldAddFoodAndCuisineSignals()
    {
        UserServiceImpl service = new UserServiceImpl(store, passwordEncoder, jwtUtil);

        Food food = new Food();
        food.setId(901L);
        food.setCuisine("Sichuan");
        food.setAreaId(201L);

        when(store.findFoodById(901L)).thenReturn(food);
        when(store.getScenicAreaTagWeights(201L)).thenReturn(Map.of("culture", 0.6));
        when(store.getUserInterests(102L)).thenReturn(Map.of());

        service.recordEngagement(102L, "FOOD", 901L, "FAVORITE");

        ArgumentCaptor<List<UserInterest>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).replaceUserInterests(org.mockito.ArgumentMatchers.eq(102L), captor.capture());
        Map<String, Double> result = captor.getValue().stream()
            .collect(java.util.stream.Collectors.toMap(UserInterest::getInterestType, UserInterest::getWeight));

        assertEquals(0.28, result.get("sichuan"), 1e-8);
        assertEquals(0.11, result.get("culture"), 1e-8);
        assertEquals(0.35, result.get("food"), 1e-8);
    }

    @Test
    void recordEngagementShouldMergeChineseInterestAndBoostAllScenicTags()
    {
        UserServiceImpl service = new UserServiceImpl(store, passwordEncoder, jwtUtil);

        ScenicArea scenicArea = new ScenicArea();
        scenicArea.setId(301L);

        when(store.findScenicAreaById(301L)).thenReturn(scenicArea);
        when(store.getScenicAreaTagWeights(301L)).thenReturn(Map.of("nature", 1.0, "lake", 0.8));
        when(store.getUserInterests(501L)).thenReturn(Map.of("自然", 1.0));

        service.recordEngagement(501L, "SCENIC", 301L, "LIKE");

        ArgumentCaptor<List<UserInterest>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).replaceUserInterests(org.mockito.ArgumentMatchers.eq(501L), captor.capture());

        Map<String, Double> result = captor.getValue().stream()
            .collect(java.util.stream.Collectors.toMap(UserInterest::getInterestType, UserInterest::getWeight));

        assertEquals(1.2, result.get("nature"), 1e-8);
        assertEquals(0.16, result.get("lake"), 1e-8);
    }
}
