package com.travel.service;

import com.travel.indoor.IndoorPathResult;
import com.travel.model.dto.indoor.IndoorPlanRequest;

import java.util.List;
import java.util.Map;

public interface IndoorService
{

    List<Map<String, Object>> listBuildings(Long areaId);

    Map<String, Object> getMeta(long buildingPoiId);

    Map<String, Object> getFloorGraph(long buildingPoiId, String level);

    IndoorPathResult plan(long buildingPoiId, IndoorPlanRequest request);
}
