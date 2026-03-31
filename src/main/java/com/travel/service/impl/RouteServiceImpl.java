package com.travel.service.impl;

import com.travel.algorithm.graph.Dijkstra;
import com.travel.algorithm.graph.Edge;
import com.travel.algorithm.graph.EdgeFilter;
import com.travel.algorithm.graph.EdgeWeightFunc;
import com.travel.algorithm.graph.Graph;
import com.travel.algorithm.graph.PathResult;
import com.travel.storage.InMemoryStore;
import com.travel.model.dto.route.MultiPointRouteRequest;
import com.travel.model.dto.route.RoutePlanRequest;
import com.travel.model.entity.Poi;
import com.travel.model.entity.Road;
import com.travel.model.vo.route.RoutePlanVO;
import com.travel.service.RouteService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路线规划服务实现。
 */
@Service
public class RouteServiceImpl implements RouteService
{

    /**
     * 步行速度（米/秒）：4km/h。
     */
    private static final double WALK_SPEED_MPS = 4.0 * 1000 / 3600;

    /**
     * 自行车速度（米/秒）：12km/h。
     */
    private static final double BIKE_SPEED_MPS = 12.0 * 1000 / 3600;

    /**
     * 电瓶车速度（米/秒）：20km/h。
     */
    private static final double SHUTTLE_SPEED_MPS = 20.0 * 1000 / 3600;

    private final InMemoryStore store;

    private final Dijkstra dijkstra;

    public RouteServiceImpl(InMemoryStore store)
    {
        this.store = store;
        this.dijkstra = new Dijkstra();
    }

    @Override
    public RoutePlanVO plan(RoutePlanRequest request)
    {
        Graph graph = loadGraph(request.getAreaId());
        EdgeFilter edgeFilter = buildVehicleFilter(request.getVehicle());

        String strategy = StringUtils.defaultIfBlank(request.getStrategy(), "distance");
        EdgeWeightFunc weightFunc = "time".equalsIgnoreCase(strategy) ? buildTimeWeightFunc(request.getVehicle()) : Edge::getDistance;

        PathResult result = dijkstra.shortestPath(graph, request.getStartId(), request.getEndId(), weightFunc, edgeFilter);
        if (result.getPath().isEmpty())
        {
            throw new IllegalArgumentException("无法规划到达路径");
        }

        RoutePlanVO vo = new RoutePlanVO();
        vo.setPath(result.getPath());

        if ("time".equalsIgnoreCase(strategy))
        {
            vo.setTime(result.getTotalWeight());
            vo.setDistance(calcDistanceByPath(graph, result.getPath(), edgeFilter));
        }
        else
        {
            vo.setDistance(result.getTotalWeight());
            vo.setTime(calcTimeByPath(graph, result.getPath(), request.getVehicle(), edgeFilter));
        }

        return vo;
    }

    @Override
    public RoutePlanVO planMultiPoint(MultiPointRouteRequest request)
    {
        List<Long> points = request.getPoints();
        if (points.size() < 2)
        {
            throw new IllegalArgumentException("points 至少需要2个点");
        }

        Graph graph = loadGraph(request.getAreaId());
        EdgeFilter edgeFilter = buildVehicleFilter(request.getVehicle());
        String strategy = StringUtils.defaultIfBlank(request.getStrategy(), "distance");
        EdgeWeightFunc weightFunc = "time".equalsIgnoreCase(strategy) ? buildTimeWeightFunc(request.getVehicle()) : Edge::getDistance;

        List<Long> fullPath = new ArrayList<>();
        double totalPrimary = 0.0;

        for (int i = 0; i < points.size() - 1; i++)
        {
            long start = points.get(i);
            long end = points.get(i + 1);
            PathResult segment = dijkstra.shortestPath(graph, start, end, weightFunc, edgeFilter);
            if (segment.getPath().isEmpty())
            {
                throw new IllegalArgumentException("无法规划到达路径");
            }
            List<Long> segmentPath = segment.getPath();
            if (fullPath.isEmpty())
            {
                fullPath.addAll(segmentPath);
            }
            else
            {
                // 拼接时避免重复节点
                fullPath.addAll(segmentPath.subList(1, segmentPath.size()));
            }
            totalPrimary += segment.getTotalWeight();
        }

        RoutePlanVO vo = new RoutePlanVO();
        vo.setPath(fullPath);
        if ("time".equalsIgnoreCase(strategy))
        {
            vo.setTime(totalPrimary);
            vo.setDistance(calcDistanceByPath(graph, fullPath, edgeFilter));
        }
        else
        {
            vo.setDistance(totalPrimary);
            vo.setTime(calcTimeByPath(graph, fullPath, request.getVehicle(), edgeFilter));
        }
        return vo;
    }

    @Override
    public Map<String, Object> getMapData(Long areaId)
    {
        Graph graph = loadGraph(areaId);
        List<Map<String, Object>> edges = new ArrayList<>();
        List<Map<String, Object>> nodeDetails = listRoutePoiCandidates(areaId);
        List<Map<String, Object>> nodeGeo = new ArrayList<>();

        for (Long nodeId : graph.getNodes())
        {
            Poi poi = store.findPoiById(nodeId);
            if (poi == null)
            {
                continue;
            }
            if (poi.getLongitude() == null || poi.getLatitude() == null)
            {
                continue;
            }
            Map<String, Object> geo = new HashMap<>();
            geo.put("nodeId", nodeId);
            geo.put("type", poi.getType());
            geo.put("longitude", poi.getLongitude());
            geo.put("latitude", poi.getLatitude());
            nodeGeo.add(geo);
        }

        for (Long start : graph.getNodes())
        {
            for (Edge edge : graph.getEdges(start))
            {
                Map<String, Object> e = new HashMap<>();
                e.put("startId", start);
                e.put("endId", edge.getTargetId());
                e.put("distance", edge.getDistance());
                e.put("speed", edge.getSpeed());
                e.put("congestion", edge.getCongestion());
                e.put("vehicleType", edge.getVehicleType());
                edges.add(e);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", new ArrayList<>(graph.getNodes()));
        result.put("nodeDetails", nodeDetails);
        result.put("nodeGeo", nodeGeo);
        result.put("edges", edges);
        return result;
    }

    @Override
    public List<Map<String, Object>> listRoutePoiCandidates(Long areaId)
    {
        List<Poi> pois = store.findPoisByAreaId(areaId);
        List<Map<String, Object>> nodeDetails = new ArrayList<>();
        for (Poi poi : pois)
        {
            if (poi == null)
            {
                continue;
            }
            String type = poi.getType();
            if (type != null && "virtual_node".equalsIgnoreCase(type.trim()))
            {
                continue;
            }

            Map<String, Object> node = new HashMap<>();
            node.put("nodeId", poi.getId());
            node.put("name", poi.getName());
            node.put("type", poi.getType());
            node.put("location", poi.getLocation());
            node.put("longitude", poi.getLongitude());
            node.put("latitude", poi.getLatitude());
            node.put("areaId", poi.getAreaId());
            nodeDetails.add(node);
        }
        nodeDetails.sort(Comparator.comparing(o -> String.valueOf(o.get("name")), String.CASE_INSENSITIVE_ORDER));
        return nodeDetails;
    }

    private Graph loadGraph(Long areaId)
    {
        List<Road> roads = store.findRoadsByAreaId(areaId);
        Graph graph = new Graph();
        for (Road road : roads)
        {
            double distance = road.getDistance() == null ? 0.0 : road.getDistance();
            double speed = road.getSpeed() == null ? 0.0 : road.getSpeed();
            double congestion = road.getCongestion() == null ? 1.0 : road.getCongestion();
            graph.addUndirectedEdge(road.getStartId(), road.getEndId(), distance, speed, congestion, road.getVehicleType());
        }
        return graph;
    }

    private EdgeFilter buildVehicleFilter(String vehicle)
    {
        if (StringUtils.isBlank(vehicle))
        {
            return null;
        }
        String v = vehicle.trim().toLowerCase();
        return edge ->
        {
            if (StringUtils.isBlank(edge.getVehicleType()))
            {
                return true;
            }
            // 数据允许保存多种类型，用逗号分隔
            String[] parts = edge.getVehicleType().toLowerCase().split(",");
            for (String p : parts)
            {
                if (v.equals(p.trim()))
                {
                    return true;
                }
            }
            return false;
        };
    }

    private EdgeWeightFunc buildTimeWeightFunc(String vehicle)
    {
        double vehicleSpeed = vehicleSpeedMps(vehicle);
        return edge ->
        {
            double congestion = edge.getCongestion() == 0 ? 1.0 : edge.getCongestion();
            double ideal = edge.getSpeed() <= 0 ? vehicleSpeed : edge.getSpeed();
            // 取交通工具速度与道路理想速度的较小值
            double speed = Math.min(vehicleSpeed, ideal) * Math.max(0.1, congestion);
            return edge.getDistance() / speed;
        };
    }

    private double vehicleSpeedMps(String vehicle)
    {
        if (StringUtils.isBlank(vehicle))
        {
            return WALK_SPEED_MPS;
        }
        return switch (vehicle.trim().toLowerCase())
        {
            case "bike" -> BIKE_SPEED_MPS;
            case "shuttle" -> SHUTTLE_SPEED_MPS;
            default -> WALK_SPEED_MPS;
        };
    }

    private double calcDistanceByPath(Graph graph, List<Long> path, EdgeFilter edgeFilter)
    {
        double sum = 0.0;
        for (int i = 0; i < path.size() - 1; i++)
        {
            sum += findEdgeDistance(graph, path.get(i), path.get(i + 1), edgeFilter);
        }
        return sum;
    }

    private double calcTimeByPath(Graph graph, List<Long> path, String vehicle, EdgeFilter edgeFilter)
    {
        EdgeWeightFunc timeWeight = buildTimeWeightFunc(vehicle);
        double sum = 0.0;
        for (int i = 0; i < path.size() - 1; i++)
        {
            Edge e = findEdge(graph, path.get(i), path.get(i + 1), edgeFilter);
            if (e != null)
            {
                sum += timeWeight.weight(e);
            }
        }
        return sum;
    }

    private double findEdgeDistance(Graph graph, long start, long end, EdgeFilter edgeFilter)
    {
        Edge e = findEdge(graph, start, end, edgeFilter);
        return e == null ? 0.0 : e.getDistance();
    }

    private Edge findEdge(Graph graph, long start, long end, EdgeFilter edgeFilter)
    {
        for (Edge edge : graph.getEdges(start))
        {
            if (edge.getTargetId() == end && (edgeFilter == null || edgeFilter.allow(edge)))
            {
                return edge;
            }
        }
        return null;
    }
}

