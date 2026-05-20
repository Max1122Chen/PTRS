package com.travel.indoor;

/**
 * 室内图边（运行期）。
 */
public class IndoorEdgeRecord
{

    private long id;

    private long buildingPoiId;

    private long startNodeId;

    private long endNodeId;

    private String edgeKind;

    private double distance;

    private boolean directed;

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public long getBuildingPoiId()
    {
        return buildingPoiId;
    }

    public void setBuildingPoiId(long buildingPoiId)
    {
        this.buildingPoiId = buildingPoiId;
    }

    public long getStartNodeId()
    {
        return startNodeId;
    }

    public void setStartNodeId(long startNodeId)
    {
        this.startNodeId = startNodeId;
    }

    public long getEndNodeId()
    {
        return endNodeId;
    }

    public void setEndNodeId(long endNodeId)
    {
        this.endNodeId = endNodeId;
    }

    public String getEdgeKind()
    {
        return edgeKind;
    }

    public void setEdgeKind(String edgeKind)
    {
        this.edgeKind = edgeKind;
    }

    public double getDistance()
    {
        return distance;
    }

    public void setDistance(double distance)
    {
        this.distance = distance;
    }

    public boolean isDirected()
    {
        return directed;
    }

    public void setDirected(boolean directed)
    {
        this.directed = directed;
    }
}
