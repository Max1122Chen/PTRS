package com.travel.indoor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单栋建筑的室内图数据包（dev-seed / 内存）。
 */
public class IndoorBuildingBundle
{

    private long buildingPoiId;

    private Long areaId;

    private String source;

    private double completenessScore;

    private List<IndoorLevelMeta> levels = new ArrayList<>();

    private Long entranceNodeId;

    private List<IndoorNodeRecord> nodes = new ArrayList<>();

    private List<IndoorEdgeRecord> edges = new ArrayList<>();

    public long getBuildingPoiId()
    {
        return buildingPoiId;
    }

    public void setBuildingPoiId(long buildingPoiId)
    {
        this.buildingPoiId = buildingPoiId;
    }

    public Long getAreaId()
    {
        return areaId;
    }

    public void setAreaId(Long areaId)
    {
        this.areaId = areaId;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public double getCompletenessScore()
    {
        return completenessScore;
    }

    public void setCompletenessScore(double completenessScore)
    {
        this.completenessScore = completenessScore;
    }

    public List<IndoorLevelMeta> getLevels()
    {
        return levels;
    }

    public void setLevels(List<IndoorLevelMeta> levels)
    {
        this.levels = levels == null ? new ArrayList<>() : levels;
    }

    public Long getEntranceNodeId()
    {
        return entranceNodeId;
    }

    public void setEntranceNodeId(Long entranceNodeId)
    {
        this.entranceNodeId = entranceNodeId;
    }

    public List<IndoorNodeRecord> getNodes()
    {
        return nodes;
    }

    public void setNodes(List<IndoorNodeRecord> nodes)
    {
        this.nodes = nodes == null ? new ArrayList<>() : nodes;
    }

    public List<IndoorEdgeRecord> getEdges()
    {
        return edges;
    }

    public void setEdges(List<IndoorEdgeRecord> edges)
    {
        this.edges = edges == null ? new ArrayList<>() : edges;
    }
}
