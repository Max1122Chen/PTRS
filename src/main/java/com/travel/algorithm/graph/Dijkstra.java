package com.travel.algorithm.graph;

import com.travel.ds.ArrayList;
import com.travel.ds.Collections;
import com.travel.ds.HashMap;
import com.travel.ds.List;
import com.travel.ds.Map;
import com.travel.ds.PriorityQueue;

import java.util.Comparator;

/**
 * Dijkstra 最短路径算法实现。
 */
public class Dijkstra
{

    /**
     * 计算从 start 到 end 的最短路径。
     *
     * @param graph       图
     * @param startId     起点
     * @param endId       终点
     * @param weightFunc  边权重函数
     * @param edgeFilter  边过滤器（用于交通工具过滤），可为 null
     * @return PathResult（不可达时 path 为空，totalWeight 为 Double.MAX_VALUE）
     */
    public PathResult shortestPath(Graph graph,
                                   long startId,
                                   long endId,
                                   EdgeWeightFunc weightFunc,
                                   EdgeFilter edgeFilter)
    {
        Map<Long, Double> dist = new HashMap<>();
        Map<Long, Long> prev = new HashMap<>();

        PriorityQueue<Node> pq = new PriorityQueue<>(Comparator.comparingDouble(Node::getDistance));
        dist.put(startId, 0.0);
        pq.add(new Node(startId, 0.0));

        while (!pq.isEmpty())
        {
            Node current = pq.poll();
            long u = current.getId();
            double d = current.getDistance();
            if (d > dist.getOrDefault(u, Double.MAX_VALUE))
            {
                continue;
            }
            if (u == endId)
            {
                break;
            }

            for (Edge edge : graph.getEdges(u))
            {
                if (edgeFilter != null && !edgeFilter.allow(edge))
                {
                    continue;
                }
                long v = edge.getTargetId();
                double w = weightFunc.weight(edge);
                double nd = d + w;
                if (nd < dist.getOrDefault(v, Double.MAX_VALUE))
                {
                    dist.put(v, nd);
                    prev.put(v, u);
                    pq.add(new Node(v, nd));
                }
            }
        }

        double total = dist.getOrDefault(endId, Double.MAX_VALUE);
        if (total == Double.MAX_VALUE)
        {
            return new PathResult(Collections.emptyList(), total);
        }
        return new PathResult(reconstruct(prev, startId, endId), total);
    }

    private List<Long> reconstruct(Map<Long, Long> prev, long startId, long endId)
    {
        List<Long> path = new ArrayList<>();
        long cur = endId;
        path.add(cur);
        while (cur != startId)
        {
            Long p = prev.get(cur);
            if (p == null)
            {
                return Collections.emptyList();
            }
            cur = p;
            path.add(0, cur);
        }
        return path;
    }

    private static class Node
    {
        private final long id;
        private final double distance;

        private Node(long id, double distance)
        {
            this.id = id;
            this.distance = distance;
        }

        public long getId()
        {
            return id;
        }

        public double getDistance()
        {
            return distance;
        }
    }
}
