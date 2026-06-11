package com.travel.service.impl;

import com.travel.algorithm.graph.Dijkstra;
import com.travel.algorithm.graph.Edge;
import com.travel.algorithm.graph.Graph;
import com.travel.algorithm.graph.PathResult;
import com.travel.model.entity.Facility;
import com.travel.model.entity.Poi;
import com.travel.model.entity.Road;
import com.travel.model.vo.facility.FacilityNearbyVO;
import com.travel.service.FacilityService;
import com.travel.storage.InMemoryStore;
import com.travel.util.GeoUtil;
import com.travel.util.ModeProfileCodec;
import com.travel.ds.DsConvert;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 设施查询服务实现。
 */
@Service
public class FacilityServiceImpl implements FacilityService
{

    private final InMemoryStore store;

    private final Dijkstra dijkstra;

    public FacilityServiceImpl(InMemoryStore store)
    {
        this.store = store;
        this.dijkstra = new Dijkstra();
    }

    @Override
    public List<FacilityNearbyVO> nearby(double lat, double lng, int radius, String type, Long areaId)
    {
        int r = radius <= 0 ? 500 : radius;
        List<Facility> all = store.findFacilitiesByAreaIdAndType(areaId, type);
        List<FacilityNearbyVO> candidates = filterByGeo(all, lat, lng, r);
        if (candidates.isEmpty())
        {
            return List.of();
        }

        Graph graph = loadGraph(areaId);
        Long startNode = findNearestFacilityNode(lat, lng, candidates);
        applyPathDistances(graph, startNode, candidates);
        sortByPathDistance(candidates);
        return candidates;
    }

    @Override
    public List<FacilityNearbyVO> nearbyByAnchor(Long anchorPoiId, int radius, String type, Long areaId)
    {
        if (anchorPoiId == null)
        {
            throw new IllegalArgumentException("anchorPoiId 不能为空");
        }
        AnchorPoint anchor = resolveAnchor(anchorPoiId);

        int r = radius <= 0 ? 500 : radius;
        List<Facility> all = store.findFacilitiesByAreaIdAndType(areaId, type);
        List<FacilityNearbyVO> candidates = filterByGeo(all, anchor.lat(), anchor.lng(), r);
        if (candidates.isEmpty())
        {
            return List.of();
        }

        Graph graph = loadGraph(areaId);
        applyPathDistances(graph, anchor.nodeId(), candidates);
        sortByPathDistance(candidates);
        return candidates;
    }

    @Override
    public List<Facility> search(String keyword, String type, Long areaId, int limit)
    {
        int l = limit <= 0 ? 50 : Math.min(limit, 200);
        return store.searchFacilities(keyword, type, areaId, l);
    }

    @Override
    public List<FacilityNearbyVO> searchNearAnchor(String keyword, String type, Long areaId, Long anchorPoiId, int radius, int limit)
    {
        if (anchorPoiId == null)
        {
            throw new IllegalArgumentException("anchorPoiId 不能为空");
        }
        AnchorPoint anchor = resolveAnchor(anchorPoiId);

        int r = radius <= 0 ? 500 : radius;
        int l = limit <= 0 ? 50 : Math.min(limit, 200);
        List<Facility> hits = store.searchFacilities(keyword, type, areaId, l);
        List<FacilityNearbyVO> candidates = filterByGeo(hits, anchor.lat(), anchor.lng(), r);
        if (candidates.isEmpty())
        {
            return List.of();
        }

        Graph graph = loadGraph(areaId);
        applyPathDistances(graph, anchor.nodeId(), candidates);
        sortByPathDistance(candidates);
        return candidates;
    }

    @Override
    public Facility detail(Long id)
    {
        Facility facility = store.findFacilityById(id);
        if (facility == null)
        {
            throw new IllegalArgumentException("设施不存在");
        }
        return facility;
    }

    private AnchorPoint resolveAnchor(Long anchorId)
    {
        Poi poi = store.findPoiById(anchorId);
        if (poi != null)
        {
            if (poi.getLatitude() == null || poi.getLongitude() == null)
            {
                throw new IllegalArgumentException("锚点 POI 缺少经纬度");
            }
            return new AnchorPoint(anchorId, poi.getLatitude(), poi.getLongitude());
        }
        Facility facility = store.findFacilityById(anchorId);
        if (facility != null)
        {
            if (facility.getLatitude() == null || facility.getLongitude() == null)
            {
                throw new IllegalArgumentException("锚点设施缺少经纬度");
            }
            return new AnchorPoint(anchorId, facility.getLatitude(), facility.getLongitude());
        }
        throw new IllegalArgumentException("锚点不存在");
    }

    private record AnchorPoint(Long nodeId, double lat, double lng)
    {
    }

    private List<FacilityNearbyVO> filterByGeo(List<Facility> all, double lat, double lng, int radiusMeters)
    {
        List<FacilityNearbyVO> candidates = new ArrayList<>();
        for (Facility f : all)
        {
            if (f.getLatitude() == null || f.getLongitude() == null)
            {
                continue;
            }
            double d = GeoUtil.distanceMeters(lat, lng, f.getLatitude(), f.getLongitude());
            if (d <= radiusMeters)
            {
                FacilityNearbyVO vo = new FacilityNearbyVO();
                vo.setFacility(f);
                vo.setGeoDistance(d);
                candidates.add(vo);
            }
        }
        return candidates;
    }

    private void applyPathDistances(Graph graph, Long startNodeId, List<FacilityNearbyVO> candidates)
    {
        if (startNodeId == null || graph == null)
        {
            return;
        }
        for (FacilityNearbyVO vo : candidates)
        {
            Long endNode = vo.getFacility().getId();
            PathResult path = dijkstra.shortestPath(graph, startNodeId, endNode, Edge::getDistance, null);
            if (!path.getPath().isEmpty())
            {
                vo.setPathDistance(path.getTotalWeight());
            }
        }
    }

    private void sortByPathDistance(List<FacilityNearbyVO> candidates)
    {
        candidates.sort(Comparator.comparingDouble(v ->
        {
            if (v.getPathDistance() != null)
            {
                return v.getPathDistance();
            }
            return v.getGeoDistance() == null ? Double.MAX_VALUE : v.getGeoDistance();
        }));
    }

    private Graph loadGraph(Long areaId)
    {
        List<Road> roads = store.findRoadsByAreaId(areaId);
        Graph graph = new Graph();
        for (Road road : roads)
        {
            double distance = road.getDistance() == null ? 0.0 : road.getDistance();
            double speed = road.getSpeed() == null ? 0.0 : road.getSpeed();
            var modeCongestion = ModeProfileCodec.decode(road.getModeProfile());
            graph.addUndirectedEdge(road.getStartId(), road.getEndId(), distance, speed,
                    DsConvert.copyStringDoubleMap(modeCongestion));
        }
        return graph;
    }

    private Long findNearestFacilityNode(double lat, double lng, List<FacilityNearbyVO> candidates)
    {
        FacilityNearbyVO nearest = null;
        for (FacilityNearbyVO vo : candidates)
        {
            if (nearest == null || (vo.getGeoDistance() != null && vo.getGeoDistance() < nearest.getGeoDistance()))
            {
                nearest = vo;
            }
        }
        return nearest == null ? null : nearest.getFacility().getId();
    }
}
