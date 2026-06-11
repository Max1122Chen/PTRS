package com.travel.algorithm.graph;

import com.travel.ds.HashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DijkstraTest
{

    @Test
    void shortestPathShouldReachTargetOnConnectedGraph()
    {
        Graph graph = new Graph();
        graph.addUndirectedEdge(1L, 2L, 10.0, 4.0, walkOnly());
        graph.addUndirectedEdge(2L, 3L, 20.0, 4.0, walkOnly());

        PathResult result = new Dijkstra().shortestPath(graph, 1L, 3L, Edge::getDistance, null);

        assertEquals(3, result.getPath().size());
        assertEquals(1L, result.getPath().get(0));
        assertEquals(3L, result.getPath().get(2));
        assertEquals(30.0, result.getTotalWeight(), 0.001);
    }

    @Test
    void shortestPathShouldReturnEmptyWhenUnreachable()
    {
        Graph graph = new Graph();
        graph.addUndirectedEdge(1L, 2L, 10.0, 4.0, walkOnly());

        PathResult result = new Dijkstra().shortestPath(graph, 1L, 9L, Edge::getDistance, null);

        assertTrue(result.getPath().isEmpty());
    }

    private static HashMap<String, Double> walkOnly()
    {
        HashMap<String, Double> profile = new HashMap<>();
        profile.put("walk", 1.0);
        return profile;
    }
}
