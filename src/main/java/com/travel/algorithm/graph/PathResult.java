package com.travel.algorithm.graph;

import com.travel.ds.List;

/**
 * 路径结果封装。
 */
public class PathResult
{

    private final List<Long> path;

    private final double totalWeight;

    public PathResult(List<Long> path, double totalWeight)
    {
        this.path = path;
        this.totalWeight = totalWeight;
    }

    public List<Long> getPath()
    {
        return path;
    }

    public double getTotalWeight()
    {
        return totalWeight;
    }
}
