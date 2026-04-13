package com.travel.model.dto.route;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 多点路径规划请求。
 */
public class MultiPointRouteRequest
{

    @NotEmpty(message = "points 不能为空")
    private List<Long> points;

    private String strategy;

    private String vehicle;

    private Long areaId;

    /**
     * 是否回到起点（第一个点）。
     */
    private Boolean returnToStart;

    public List<Long> getPoints()
    {
        return points;
    }

    public void setPoints(List<Long> points)
    {
        this.points = points;
    }

    public String getStrategy()
    {
        return strategy;
    }

    public void setStrategy(String strategy)
    {
        this.strategy = strategy;
    }

    public String getVehicle()
    {
        return vehicle;
    }

    public void setVehicle(String vehicle)
    {
        this.vehicle = vehicle;
    }

    public Long getAreaId()
    {
        return areaId;
    }

    public void setAreaId(Long areaId)
    {
        this.areaId = areaId;
    }

    public Boolean getReturnToStart()
    {
        return returnToStart;
    }

    public void setReturnToStart(Boolean returnToStart)
    {
        this.returnToStart = returnToStart;
    }
}

