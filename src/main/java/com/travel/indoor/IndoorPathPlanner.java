package com.travel.indoor;

import com.travel.algorithm.graph.Dijkstra;
import com.travel.algorithm.graph.Edge;
import com.travel.algorithm.graph.EdgeWeightFunc;
import com.travel.algorithm.graph.PathResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 室内最短路径（距离策略，米）。
 */
@Component
public class IndoorPathPlanner
{

    private static final double WALK_SPEED_MPS = 4.0 * 1000.0 / 3600.0;

    private final Dijkstra dijkstra = new Dijkstra();

    private final EdgeWeightFunc distanceWeight = Edge::getDistance;

    public IndoorPathResult plan(IndoorBuildingGraph buildingGraph, long startNodeId, long endNodeId)
    {
        IndoorPathResult out = new IndoorPathResult();
        if (buildingGraph.findNode(startNodeId) == null || buildingGraph.findNode(endNodeId) == null)
        {
            return out;
        }

        PathResult raw = dijkstra.shortestPath(
            buildingGraph.getGraph(),
            startNodeId,
            endNodeId,
            distanceWeight,
            null
        );

        if (raw.getPath().isEmpty())
        {
            return out;
        }

        out.setPath(raw.getPath());
        out.setDistanceMeters(raw.getTotalWeight());
        out.setTimeSec(raw.getTotalWeight() / WALK_SPEED_MPS);
        out.setSegments(buildSegments(buildingGraph, raw.getPath()));
        out.setInstructions(buildInstructions(buildingGraph, raw.getPath()));
        return out;
    }

    private List<IndoorPathSegment> buildSegments(IndoorBuildingGraph graph, List<Long> path)
    {
        List<IndoorPathSegment> segments = new ArrayList<>();
        if (path.isEmpty())
        {
            return segments;
        }

        String currentLevel = null;
        IndoorPathSegment current = null;
        for (Long nodeId : path)
        {
            IndoorNodeRecord node = graph.findNode(nodeId);
            String level = node == null ? "" : node.getLevel();
            if (current == null || !level.equals(currentLevel))
            {
                current = new IndoorPathSegment();
                current.setLevel(level);
                segments.add(current);
                currentLevel = level;
            }
            current.getNodeIds().add(nodeId);
        }
        return segments;
    }

    private List<String> buildInstructions(IndoorBuildingGraph graph, List<Long> path)
    {
        List<String> lines = new ArrayList<>();
        String lastKind = null;
        for (Long nodeId : path)
        {
            IndoorNodeRecord node = graph.findNode(nodeId);
            if (node == null)
            {
                continue;
            }
            String kind = node.getNodeKind() == null ? "" : node.getNodeKind().toLowerCase();
            if ("corridor_junction".equals(kind) && lines.size() > 0)
            {
                continue;
            }
            if (kind.equals(lastKind) && !"elevator".equals(kind) && !"stairs".equals(kind))
            {
                continue;
            }
            String label = node.getName();
            if (label == null || label.isBlank())
            {
                label = switch (kind)
                {
                    case "door" -> "门";
                    case "elevator" -> "电梯";
                    case "stairs" -> "楼梯";
                    case "room" -> "房间";
                    default -> "节点" + nodeId;
                };
            }
            if ("elevator".equals(kind) || "stairs".equals(kind))
            {
                label = label + "（" + node.getLevel() + "层）";
            }
            lines.add(label);
            lastKind = kind;
        }
        return lines;
    }
}
