package com.travel.algorithm.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图边结构（邻接表边）。
 */
public class Edge
{

    private final long targetId;

    private final double distance;

    private final double speed;

    private final Map<String, Double> modeCongestion;

    public Edge(long targetId, double distance, double speed, Map<String, Double> modeCongestion)
    {
        this.targetId = targetId;
        this.distance = distance;
        this.speed = speed;
        this.modeCongestion = modeCongestion == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(modeCongestion));
    }

    public long getTargetId()
    {
        return targetId;
    }

    public double getDistance()
    {
        return distance;
    }

    public double getSpeed()
    {
        return speed;
    }

    public Map<String, Double> getModeCongestion()
    {
        return modeCongestion;
    }
}

