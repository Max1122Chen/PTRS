package com.travel.indoor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 室内图完整度判定（与 scripts/indoor_seed.py 规则一致）。
 */
public final class IndoorSeedCompleteness
{

    private IndoorSeedCompleteness()
    {
    }

    public static Result evaluate(IndoorBuildingBundle bundle)
    {
        List<String> failures = new ArrayList<>();
        if (bundle == null)
        {
            failures.add("BUNDLE_NULL");
            return new Result(false, 0.0, failures);
        }

        List<IndoorNodeRecord> nodes = bundle.getNodes();
        List<IndoorEdgeRecord> edges = bundle.getEdges();

        long roomCount = nodes.stream().filter(n -> "room".equalsIgnoreCase(n.getNodeKind())).count();
        long corridorCount = edges.stream().filter(e -> "corridor".equalsIgnoreCase(e.getEdgeKind())).count();

        Set<String> levels = new HashSet<>();
        for (IndoorNodeRecord n : nodes)
        {
            if (n.getLevel() != null && !n.getLevel().isBlank())
            {
                levels.add(n.getLevel().trim());
            }
        }

        if (levels.isEmpty())
        {
            failures.add("LEVELS");
        }
        if (roomCount < 2)
        {
            failures.add("ROOMS");
        }
        if (corridorCount < 3)
        {
            failures.add("CORRIDORS");
        }
        if (!areRoomsConnectedViaCorridors(nodes, edges))
        {
            failures.add("DISCONNECTED");
        }

        boolean pass = failures.isEmpty();
        return new Result(pass, pass ? 1.0 : 0.0, failures);
    }

    /**
     * 要求所有 room 节点落在「可走通」边（corridor / elevator / steps）诱导的同一连通分量内，
     * 以便多层场景下竖向交通可连接各层房间（与 indoor_seed 脚本一致）。
     */
    static boolean areRoomsConnectedViaCorridors(List<IndoorNodeRecord> nodes, List<IndoorEdgeRecord> edges)
    {
        Map<Long, Set<Long>> adj = new HashMap<>();
        Set<Long> roomIds = new HashSet<>();

        for (IndoorEdgeRecord e : edges)
        {
            String k = e.getEdgeKind() == null ? "" : e.getEdgeKind();
            if (!"corridor".equalsIgnoreCase(k)
                && !"elevator".equalsIgnoreCase(k)
                && !"steps".equalsIgnoreCase(k))
            {
                continue;
            }
            long u = e.getStartNodeId();
            long v = e.getEndNodeId();
            adj.computeIfAbsent(u, unused -> new HashSet<>()).add(v);
            adj.computeIfAbsent(v, unused -> new HashSet<>()).add(u);
        }

        for (IndoorNodeRecord n : nodes)
        {
            if ("room".equalsIgnoreCase(n.getNodeKind()))
            {
                roomIds.add(n.getId());
                if (!adj.containsKey(n.getId()))
                {
                    return false;
                }
            }
        }

        if (roomIds.isEmpty())
        {
            return false;
        }

        long start = roomIds.iterator().next();
        Set<Long> visited = new HashSet<>();
        List<Long> stack = new ArrayList<>();
        stack.add(start);
        while (!stack.isEmpty())
        {
            long u = stack.remove(stack.size() - 1);
            if (!visited.add(u))
            {
                continue;
            }
            for (Long v : adj.getOrDefault(u, Set.of()))
            {
                if (!visited.contains(v))
                {
                    stack.add(v);
                }
            }
        }

        for (Long roomId : roomIds)
        {
            if (!visited.contains(roomId))
            {
                return false;
            }
        }
        return true;
    }

    public record Result(boolean pass, double score, List<String> failureCodes)
    {
    }
}
