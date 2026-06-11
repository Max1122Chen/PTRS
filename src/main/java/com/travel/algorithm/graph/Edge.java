package com.travel.algorithm.graph;

import com.travel.ds.Collections;
import com.travel.ds.HashMap;
import com.travel.ds.Map;

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
        if (modeCongestion == null || modeCongestion.isEmpty())
        {
            this.modeCongestion = Collections.emptyMap();
        }
        else
        {
            HashMap<String, Double> copy = new HashMap<>();
            for (Map.Entry<String, Double> entry : modeCongestion.entrySet())
            {
                copy.put(entry.getKey(), entry.getValue());
            }
            this.modeCongestion = Collections.unmodifiableMap(copy);
        }
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
