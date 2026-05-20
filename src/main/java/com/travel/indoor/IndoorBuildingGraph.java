package com.travel.indoor;

import com.travel.algorithm.graph.Graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单栋建筑室内图运行期视图。
 */
public class IndoorBuildingGraph
{

    private final IndoorBuildingBundle bundle;

    private final Graph graph;

    private final Map<Long, IndoorNodeRecord> nodeById;

    private final Map<String, List<IndoorNodeRecord>> nodesByLevel;

    public IndoorBuildingGraph(IndoorBuildingBundle bundle, Graph graph, Map<Long, IndoorNodeRecord> nodeById,
                               Map<String, List<IndoorNodeRecord>> nodesByLevel)
    {
        this.bundle = bundle;
        this.graph = graph;
        this.nodeById = Collections.unmodifiableMap(nodeById);
        this.nodesByLevel = Collections.unmodifiableMap(nodesByLevel);
    }

    public IndoorBuildingBundle getBundle()
    {
        return bundle;
    }

    public Graph getGraph()
    {
        return graph;
    }

    public IndoorNodeRecord findNode(long nodeId)
    {
        return nodeById.get(nodeId);
    }

    public Map<Long, IndoorNodeRecord> getNodeById()
    {
        return nodeById;
    }

    public Map<String, List<IndoorNodeRecord>> getNodesByLevel()
    {
        return nodesByLevel;
    }
}
