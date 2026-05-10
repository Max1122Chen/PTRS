package com.travel.algorithm.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图结构（邻接表实现）。
 */
public class Graph
{

    private final Map<Long, List<Edge>> adjList;

    public Graph()
    {
        this.adjList = new HashMap<>();
    }

    /**
     * 添加无向边（道路默认双向）。
     */
    public void addUndirectedEdge(long startId, long endId, double distance, double speed, Map<String, Double> modeCongestion)
    {
        addDirectedEdge(startId, endId, distance, speed, modeCongestion);
        addDirectedEdge(endId, startId, distance, speed, modeCongestion);
    }

    /**
     * 添加有向边。
     */
    public void addDirectedEdge(long startId, long endId, double distance, double speed, Map<String, Double> modeCongestion)
    {
        adjList.computeIfAbsent(startId, k -> new ArrayList<>())
            .add(new Edge(endId, distance, speed, modeCongestion));
    }

    public List<Edge> getEdges(long nodeId)
    {
        return adjList.getOrDefault(nodeId, Collections.emptyList());
    }

    public Set<Long> getNodes()
    {
        return adjList.keySet();
    }
}

