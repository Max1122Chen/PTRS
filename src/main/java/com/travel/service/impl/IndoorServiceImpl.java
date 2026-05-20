package com.travel.service.impl;

import com.travel.indoor.IndoorBuildingGraph;
import com.travel.indoor.IndoorLevelLabel;
import com.travel.indoor.IndoorEdgeRecord;
import com.travel.indoor.IndoorLevelMeta;
import com.travel.indoor.IndoorNodeRecord;
import com.travel.indoor.IndoorPathPlanner;
import com.travel.indoor.IndoorPathResult;
import com.travel.indoor.IndoorGraphRegistry;
import com.travel.model.dto.indoor.IndoorPlanRequest;
import com.travel.model.entity.Poi;
import com.travel.service.IndoorService;
import com.travel.storage.InMemoryStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndoorServiceImpl implements IndoorService
{

    private final IndoorGraphRegistry registry;

    private final IndoorPathPlanner pathPlanner;

    private final InMemoryStore store;

    public IndoorServiceImpl(IndoorGraphRegistry registry, IndoorPathPlanner pathPlanner, InMemoryStore store)
    {
        this.registry = registry;
        this.pathPlanner = pathPlanner;
        this.store = store;
    }

    @Override
    public List<Map<String, Object>> listBuildings(Long areaId)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        if (areaId == null)
        {
            for (var bundle : store.findAllIndoorBundles())
            {
                registry.find(bundle.getBuildingPoiId()).ifPresent(g -> out.add(toBuildingSummary(g)));
            }
            return out;
        }
        for (IndoorBuildingGraph graph : registry.findByAreaId(areaId))
        {
            out.add(toBuildingSummary(graph));
        }
        return out;
    }

    @Override
    public Map<String, Object> getMeta(long buildingPoiId)
    {
        IndoorBuildingGraph graph = requireGraph(buildingPoiId);
        Map<String, Object> data = new HashMap<>();
        data.put("buildingPoiId", buildingPoiId);
        Poi poi = store.findPoiById(buildingPoiId);
        if (poi != null)
        {
            data.put("name", poi.getName());
            data.put("areaId", poi.getAreaId());
        }
        List<Map<String, Object>> levelViews = new ArrayList<>();
        for (IndoorLevelMeta lv : graph.getBundle().getLevels())
        {
            Map<String, Object> row = new HashMap<>();
            row.put("level", lv.getLevel());
            row.put("label", IndoorLevelLabel.displayLabel(lv.getLevel(), lv.getLabel()));
            row.put("order", lv.getOrder());
            levelViews.add(row);
        }
        data.put("levels", levelViews);
        data.put("entranceNodeId", graph.getBundle().getEntranceNodeId());
        data.put("completenessScore", graph.getBundle().getCompletenessScore());
        data.put("source", graph.getBundle().getSource());
        return data;
    }

    @Override
    public Map<String, Object> getFloorGraph(long buildingPoiId, String level)
    {
        IndoorBuildingGraph graph = requireGraph(buildingPoiId);
        String levelKey = level == null ? "" : level.trim();
        List<IndoorNodeRecord> nodes = graph.getNodesByLevel().getOrDefault(levelKey, List.of());

        List<Map<String, Object>> nodeDtos = new ArrayList<>();
        for (IndoorNodeRecord n : nodes)
        {
            nodeDtos.add(toNodeDto(n));
        }

        List<Map<String, Object>> edgeDtos = new ArrayList<>();
        for (IndoorEdgeRecord e : graph.getBundle().getEdges())
        {
            IndoorNodeRecord a = graph.findNode(e.getStartNodeId());
            IndoorNodeRecord b = graph.findNode(e.getEndNodeId());
            if (a == null || b == null)
            {
                continue;
            }
            if (!edgeVisibleOnFloor(e, a, b, levelKey))
            {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getId());
            m.put("startNodeId", e.getStartNodeId());
            m.put("endNodeId", e.getEndNodeId());
            m.put("edgeKind", e.getEdgeKind());
            m.put("distance", e.getDistance());
            edgeDtos.add(m);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("level", levelKey);
        data.put("nodes", nodeDtos);
        data.put("edges", edgeDtos);
        return data;
    }

    private static boolean edgeVisibleOnFloor(IndoorEdgeRecord e, IndoorNodeRecord a, IndoorNodeRecord b, String levelKey)
    {
        String kind = e.getEdgeKind() == null ? "" : e.getEdgeKind();
        if ("corridor".equalsIgnoreCase(kind))
        {
            return levelKey.equals(trimLevel(a.getLevel())) && levelKey.equals(trimLevel(b.getLevel()));
        }
        if ("elevator".equalsIgnoreCase(kind) || "steps".equalsIgnoreCase(kind))
        {
            return levelKey.equals(trimLevel(a.getLevel())) || levelKey.equals(trimLevel(b.getLevel()));
        }
        return false;
    }

    private static String trimLevel(String level)
    {
        return level == null ? "" : level.trim();
    }

    private Map<String, Object> toNodeDto(IndoorNodeRecord n)
    {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("name", n.getName());
        m.put("level", n.getLevel());
        m.put("nodeKind", n.getNodeKind());
        if (n.getLongitude() != null && n.getLatitude() != null)
        {
            m.put("longitude", n.getLongitude());
            m.put("latitude", n.getLatitude());
        }
        m.put("x", n.getX());
        m.put("y", n.getY());
        return m;
    }

    @Override
    public IndoorPathResult plan(long buildingPoiId, IndoorPlanRequest request)
    {
        IndoorBuildingGraph graph = requireGraph(buildingPoiId);
        long start = request.getStartNodeId();
        long end = request.getEndNodeId();
        if (graph.findNode(start) == null || graph.findNode(end) == null)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "起点或终点不属于该建筑室内图");
        }
        return pathPlanner.plan(graph, start, end);
    }

    private IndoorBuildingGraph requireGraph(long buildingPoiId)
    {
        return registry.find(buildingPoiId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该 POI 无可用室内图"));
    }

    private Map<String, Object> toBuildingSummary(IndoorBuildingGraph graph)
    {
        Map<String, Object> m = new HashMap<>();
        long id = graph.getBundle().getBuildingPoiId();
        m.put("buildingPoiId", id);
        Poi poi = store.findPoiById(id);
        m.put("name", poi == null ? ("建筑" + id) : poi.getName());
        m.put("areaId", graph.getBundle().getAreaId());
        m.put("levels", graph.getBundle().getLevels());
        m.put("completenessScore", graph.getBundle().getCompletenessScore());
        return m;
    }
}
