package com.travel.indoor;

import com.travel.algorithm.graph.Graph;
import com.travel.config.IndoorProperties;
import com.travel.model.entity.Poi;
import com.travel.storage.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 室内图注册表：从 {@link InMemoryStore} 构建多层合并图。
 */
@Component
public class IndoorGraphRegistry
{

    private static final Logger log = LoggerFactory.getLogger(IndoorGraphRegistry.class);

    private static final com.travel.ds.Map<String, Double> EMPTY_MODE = com.travel.ds.Collections.emptyMap();

    private final IndoorProperties properties;

    private volatile Map<Long, IndoorBuildingGraph> byBuildingPoiId = Map.of();

    public IndoorGraphRegistry(IndoorProperties properties)
    {
        this.properties = properties;
    }

    public void reloadFromStore(InMemoryStore store)
    {
        Map<Long, IndoorBuildingGraph> next = new HashMap<>();
        for (IndoorBuildingBundle bundle : store.findAllIndoorBundles())
        {
            IndoorSeedCompleteness.Result check = IndoorSeedCompleteness.evaluate(bundle);
            if (!check.pass())
            {
                log.warn("Skip indoor graph for buildingPoiId={} failures={}", bundle.getBuildingPoiId(),
                    check.failureCodes());
                continue;
            }
            next.put(bundle.getBuildingPoiId(), buildGraph(bundle));
        }
        byBuildingPoiId = Collections.unmodifiableMap(next);
        log.info("Indoor graphs loaded: {}", byBuildingPoiId.size());
    }

    public Optional<IndoorBuildingGraph> find(long buildingPoiId)
    {
        return Optional.ofNullable(byBuildingPoiId.get(buildingPoiId));
    }

    public List<IndoorBuildingGraph> findByAreaId(long areaId)
    {
        List<IndoorBuildingGraph> out = new ArrayList<>();
        for (IndoorBuildingGraph g : byBuildingPoiId.values())
        {
            Long bundleArea = g.getBundle().getAreaId();
            if (bundleArea != null && bundleArea == areaId)
            {
                out.add(g);
            }
        }
        out.sort((a, b) -> Long.compare(a.getBundle().getBuildingPoiId(), b.getBundle().getBuildingPoiId()));
        return out;
    }

    private IndoorBuildingGraph buildGraph(IndoorBuildingBundle bundle)
    {
        Graph graph = new Graph();
        Map<Long, IndoorNodeRecord> nodeById = new LinkedHashMap<>();
        Map<String, List<IndoorNodeRecord>> nodesByLevel = new LinkedHashMap<>();

        for (IndoorNodeRecord n : bundle.getNodes())
        {
            n.setBuildingPoiId(bundle.getBuildingPoiId());
            nodeById.put(n.getId(), n);
            nodesByLevel.computeIfAbsent(n.getLevel(), k -> new ArrayList<>()).add(n);
        }

        double verticalDist = properties.getVerticalEdgeDistanceMeters();
        for (IndoorEdgeRecord e : bundle.getEdges())
        {
            e.setBuildingPoiId(bundle.getBuildingPoiId());
            IndoorNodeRecord na = nodeById.get(e.getStartNodeId());
            IndoorNodeRecord nb = nodeById.get(e.getEndNodeId());
            double dist = e.getDistance();
            if ("elevator".equalsIgnoreCase(e.getEdgeKind()) || "stairs".equalsIgnoreCase(e.getEdgeKind()))
            {
                dist = verticalDist;
            }
            else if ("corridor".equalsIgnoreCase(e.getEdgeKind()) && IndoorGeo.hasCoordinates(na) && IndoorGeo.hasCoordinates(nb))
            {
                double geo = IndoorGeo.edgeLengthMeters(na, nb);
                if (geo > 0.05)
                {
                    dist = geo;
                }
            }
            if (e.isDirected())
            {
                graph.addDirectedEdge(e.getStartNodeId(), e.getEndNodeId(), dist, 1.0, EMPTY_MODE);
            }
            else
            {
                graph.addUndirectedEdge(e.getStartNodeId(), e.getEndNodeId(), dist, 1.0, EMPTY_MODE);
            }
        }

        return new IndoorBuildingGraph(bundle, graph, nodeById, nodesByLevel);
    }
}
