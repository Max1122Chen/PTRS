package com.travel.indoor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndoorSeedCompletenessTest
{

    @Test
    void demoBundleShouldPass()
    {
        IndoorSeedCompleteness.Result r = IndoorSeedCompleteness.evaluate(demoBundle());
        assertTrue(r.pass());
        assertEquals(1.0, r.score());
        assertTrue(r.failureCodes().isEmpty());
    }

    @Test
    void shouldFailWhenOnlyOneRoom()
    {
        IndoorBuildingBundle b = demoBundle();
        List<IndoorNodeRecord> kept = new java.util.ArrayList<>();
        for (IndoorNodeRecord n : b.getNodes())
        {
            if (!"room".equals(n.getNodeKind()))
            {
                kept.add(n);
            }
        }
        IndoorNodeRecord room = new IndoorNodeRecord();
        room.setId(1);
        room.setLevel("0");
        room.setNodeKind("room");
        kept.add(room);
        b.setNodes(kept);
        assertFalse(IndoorSeedCompleteness.evaluate(b).pass());
    }

    @Test
    void shouldFailWhenDisconnectedRoom()
    {
        IndoorBuildingBundle b = demoBundle();
        IndoorNodeRecord isolated = new IndoorNodeRecord();
        isolated.setId(9999);
        isolated.setLevel("0");
        isolated.setNodeKind("room");
        isolated.setX(100);
        isolated.setY(100);
        List<IndoorNodeRecord> nodes = new java.util.ArrayList<>(b.getNodes());
        nodes.add(isolated);
        b.setNodes(nodes);
        assertFalse(IndoorSeedCompleteness.evaluate(b).pass());
    }

    private static IndoorBuildingBundle demoBundle()
    {
        IndoorBuildingBundle b = new IndoorBuildingBundle();
        b.setBuildingPoiId(502);
        IndoorNodeRecord n1 = node(1, "door");
        IndoorNodeRecord n2 = node(2, "corridor_junction");
        IndoorNodeRecord n3 = node(3, "room");
        IndoorNodeRecord n4 = node(4, "room");
        b.setNodes(List.of(n1, n2, n3, n4));
        b.setEdges(List.of(
            edge(1, 1, 2),
            edge(2, 2, 3),
            edge(3, 3, 4)
        ));
        IndoorLevelMeta level = new IndoorLevelMeta();
        level.setLevel("0");
        b.setLevels(List.of(level));
        return b;
    }

    private static IndoorNodeRecord node(long id, String kind)
    {
        IndoorNodeRecord n = new IndoorNodeRecord();
        n.setId(id);
        n.setLevel("0");
        n.setNodeKind(kind);
        return n;
    }

    private static IndoorEdgeRecord edge(long id, long s, long e)
    {
        IndoorEdgeRecord edge = new IndoorEdgeRecord();
        edge.setId(id);
        edge.setStartNodeId(s);
        edge.setEndNodeId(e);
        edge.setEdgeKind("corridor");
        edge.setDistance(10);
        return edge;
    }
}
