package com.travel.indoor;

import com.travel.config.IndoorProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IndoorPathPlannerTest
{

    @Test
    void shouldPlanAlongCorridorChain()
    {
        IndoorBuildingGraph graph = buildRegistryGraph(singleFloorChain());
        IndoorPathPlanner planner = new IndoorPathPlanner();
        IndoorPathResult result = planner.plan(graph, 1, 4);
        assertEquals(List.of(1L, 2L, 3L, 4L), result.getPath());
        assertEquals(30.0, result.getDistanceMeters(), 0.01);
        assertFalse(result.getInstructions().isEmpty());
    }

    @Test
    void shouldUseVerticalEdgeWeightForElevator()
    {
        IndoorBuildingBundle bundle = multiFloorBundle();
        IndoorProperties props = new IndoorProperties();
        props.setVerticalEdgeDistanceMeters(10.0);
        IndoorGraphRegistry registry = new IndoorGraphRegistry(props);
        InMemoryStoreHelper.store(registry, bundle);

        IndoorBuildingGraph graph = registry.find(bundle.getBuildingPoiId()).orElseThrow();
        IndoorPathResult result = new IndoorPathPlanner().plan(graph, 1, 5);
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), result.getPath());
        assertEquals(35.0, result.getDistanceMeters(), 0.01);
    }

    private static IndoorBuildingBundle singleFloorChain()
    {
        IndoorBuildingBundle b = new IndoorBuildingBundle();
        b.setBuildingPoiId(1);
        b.setAreaId(201L);
        b.setNodes(List.of(
            node(1, "0", "door"),
            node(2, "0", "corridor_junction"),
            node(3, "0", "room"),
            node(4, "0", "room")
        ));
        b.setEdges(List.of(
            corridor(1, 1, 2, 10),
            corridor(2, 2, 3, 10),
            corridor(3, 3, 4, 10)
        ));
        IndoorLevelMeta l = new IndoorLevelMeta();
        l.setLevel("0");
        b.setLevels(List.of(l));
        return b;
    }

    private static IndoorBuildingBundle multiFloorBundle()
    {
        IndoorBuildingBundle b = new IndoorBuildingBundle();
        b.setBuildingPoiId(99);
        b.setAreaId(201L);
        b.setNodes(List.of(
            node(1, "0", "door"),
            node(2, "0", "elevator"),
            node(3, "1", "elevator"),
            node(4, "1", "room"),
            node(5, "1", "room")
        ));
        b.setEdges(List.of(
            corridor(1, 1, 2, 5),
            vertical(2, 2, 3),
            corridor(3, 3, 4, 10),
            corridor(4, 4, 5, 10)
        ));
        IndoorLevelMeta l0 = new IndoorLevelMeta();
        l0.setLevel("0");
        IndoorLevelMeta l1 = new IndoorLevelMeta();
        l1.setLevel("1");
        b.setLevels(List.of(l0, l1));
        return b;
    }

    private static IndoorBuildingGraph buildRegistryGraph(IndoorBuildingBundle bundle)
    {
        IndoorProperties props = new IndoorProperties();
        IndoorGraphRegistry registry = new IndoorGraphRegistry(props);
        InMemoryStoreHelper.store(registry, bundle);
        return registry.find(bundle.getBuildingPoiId()).orElseThrow();
    }

    private static IndoorNodeRecord node(long id, String level, String kind)
    {
        IndoorNodeRecord n = new IndoorNodeRecord();
        n.setId(id);
        n.setLevel(level);
        n.setNodeKind(kind);
        n.setName("n" + id);
        return n;
    }

    private static IndoorEdgeRecord corridor(long id, long s, long e, double d)
    {
        IndoorEdgeRecord edge = new IndoorEdgeRecord();
        edge.setId(id);
        edge.setStartNodeId(s);
        edge.setEndNodeId(e);
        edge.setEdgeKind("corridor");
        edge.setDistance(d);
        return edge;
    }

    private static IndoorEdgeRecord vertical(long id, long s, long e)
    {
        IndoorEdgeRecord edge = new IndoorEdgeRecord();
        edge.setId(id);
        edge.setStartNodeId(s);
        edge.setEndNodeId(e);
        edge.setEdgeKind("elevator");
        edge.setDistance(999);
        return edge;
    }

    /**
     * 测试辅助：直接向 registry 灌 bundle。
     */
    private static final class InMemoryStoreHelper
    {
        static void store(IndoorGraphRegistry registry, IndoorBuildingBundle bundle)
        {
            com.travel.storage.InMemoryStore store = new com.travel.storage.InMemoryStore();
            store.putIndoorBundle(bundle);
            registry.reloadFromStore(store);
        }
    }
}
