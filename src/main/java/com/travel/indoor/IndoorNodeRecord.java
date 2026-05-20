package com.travel.indoor;

/**
 * 室内图节点（运行期）。
 */
public class IndoorNodeRecord
{

    private long id;

    private long buildingPoiId;

    private String level;

    private String name;

    private String nodeKind;

    /**
     * WGS84，米制距离规划优先使用经纬度；与室外路网一致。
     */
    private Double longitude;

    private Double latitude;

    /**
     * 历史种子局部平面坐标（米）；新数据可省略，由经纬度替代。
     */
    private double x;

    private double y;

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

    public String getLevel()
    {
        return level;
    }

    public void setLevel(String level)
    {
        this.level = level;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getNodeKind()
    {
        return nodeKind;
    }

    public void setNodeKind(String nodeKind)
    {
        this.nodeKind = nodeKind;
    }

    public Double getLongitude()
    {
        return longitude;
    }

    public void setLongitude(Double longitude)
    {
        this.longitude = longitude;
    }

    public Double getLatitude()
    {
        return latitude;
    }

    public void setLatitude(Double latitude)
    {
        this.latitude = latitude;
    }

    public double getX()
    {
        return x;
    }

    public void setX(double x)
    {
        this.x = x;
    }

    public double getY()
    {
        return y;
    }

    public void setY(double y)
    {
        this.y = y;
    }
}
