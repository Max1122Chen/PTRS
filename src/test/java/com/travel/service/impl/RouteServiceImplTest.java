package com.travel.service.impl;

import com.travel.model.dto.route.MultiPointRouteRequest;
import com.travel.model.dto.route.RoutePlanRequest;
import com.travel.model.entity.Poi;
import com.travel.model.entity.Road;
import com.travel.storage.InMemoryStore;
import com.travel.util.ModeProfileCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceImplTest
{

    private static final Long AREA_ID = 252L;

    @Mock
    private InMemoryStore store;

    @Test
    void planShouldDescribeUnreachableSegmentWithPoiNames()
    {
        RouteServiceImpl service = new RouteServiceImpl(store);
        when(store.findRoadsByAreaId(AREA_ID)).thenReturn(List.of(
                road(1L, 2L, walkBikeProfile()),
                road(2L, 3L, walkBikeProfile())
        ));
        when(store.findPoiById(1L)).thenReturn(poi(1L, "东门"));
        when(store.findPoiById(4L)).thenReturn(poi(4L, "孤立馆"));

        RoutePlanRequest request = new RoutePlanRequest();
        request.setAreaId(AREA_ID);
        request.setStartId(1L);
        request.setEndId(4L);
        request.setVehicle("walk");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.plan(request));
        assertTrue(ex.getMessage().contains("无法规划到达路径"));
        assertTrue(ex.getMessage().contains("孤立馆(4)"));
        assertTrue(ex.getMessage().contains("未接入路网"));
    }

    @Test
    void planShouldDescribeVehicleFilteredSegment()
    {
        RouteServiceImpl service = new RouteServiceImpl(store);
        when(store.findRoadsByAreaId(AREA_ID)).thenReturn(List.of(
                road(1L, 2L, Map.of("walk", 1.0)),
                road(2L, 3L, Map.of("walk", 1.0))
        ));
        when(store.findPoiById(1L)).thenReturn(poi(1L, "东门"));
        when(store.findPoiById(3L)).thenReturn(poi(3L, "西门"));

        RoutePlanRequest request = new RoutePlanRequest();
        request.setAreaId(AREA_ID);
        request.setStartId(1L);
        request.setEndId(3L);
        request.setVehicle("bike");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.plan(request));
        assertTrue(ex.getMessage().contains("东门(1)"));
        assertTrue(ex.getMessage().contains("西门(3)"));
        assertTrue(ex.getMessage().contains("自行车"));
    }

    @Test
    void planMultiPointShouldListAllUnreachableSegments()
    {
        RouteServiceImpl service = new RouteServiceImpl(store);
        when(store.findRoadsByAreaId(AREA_ID)).thenReturn(List.of(
                road(1L, 2L, walkBikeProfile()),
                road(2L, 3L, walkBikeProfile())
        ));
        when(store.findPoiById(2L)).thenReturn(poi(2L, "中间点"));
        when(store.findPoiById(99L)).thenReturn(poi(99L, "孤岛"));

        MultiPointRouteRequest request = new MultiPointRouteRequest();
        request.setAreaId(AREA_ID);
        request.setPoints(List.of(1L, 2L, 99L));
        request.setReturnToStart(false);
        request.setVehicle("walk");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.planMultiPoint(request));
        assertTrue(ex.getMessage().contains("无法规划到达路径"));
        assertTrue(ex.getMessage().contains("孤岛(99)"));
    }

    @Test
    void planMultiPointShouldSucceedOnConnectedGraph()
    {
        RouteServiceImpl service = new RouteServiceImpl(store);
        when(store.findRoadsByAreaId(AREA_ID)).thenReturn(List.of(
                road(1L, 2L, walkBikeProfile()),
                road(2L, 3L, walkBikeProfile()),
                road(3L, 4L, walkBikeProfile())
        ));

        MultiPointRouteRequest request = new MultiPointRouteRequest();
        request.setAreaId(AREA_ID);
        request.setPoints(List.of(1L, 3L, 4L));
        request.setReturnToStart(false);
        request.setVehicle("walk");

        assertEquals(4, service.planMultiPoint(request).getPath().size());
    }

    private static Poi poi(long id, String name)
    {
        Poi poi = new Poi();
        poi.setId(id);
        poi.setName(name);
        poi.setAreaId(AREA_ID);
        return poi;
    }

    private static Road road(long start, long end, Map<String, Double> profile)
    {
        Road road = new Road();
        road.setStartId(start);
        road.setEndId(end);
        road.setDistance(100.0);
        road.setSpeed(4.0);
        road.setAreaId(AREA_ID);
        road.setModeProfile(ModeProfileCodec.encode(profile));
        return road;
    }

    private static Map<String, Double> walkBikeProfile()
    {
        return Map.of("walk", 1.0, "bike", 1.0);
    }
}
