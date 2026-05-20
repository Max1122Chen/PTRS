package com.travel.indoor;

import java.util.ArrayList;
import java.util.List;

public class IndoorPathSegment
{

    private String level;

    private List<Long> nodeIds = new ArrayList<>();

    public String getLevel()
    {
        return level;
    }

    public void setLevel(String level)
    {
        this.level = level;
    }

    public List<Long> getNodeIds()
    {
        return nodeIds;
    }

    public void setNodeIds(List<Long> nodeIds)
    {
        this.nodeIds = nodeIds == null ? new ArrayList<>() : nodeIds;
    }
}
