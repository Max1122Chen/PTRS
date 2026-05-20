package com.travel.model.dto.indoor;

import jakarta.validation.constraints.NotNull;

public class IndoorPlanRequest
{

    @NotNull
    private Long startNodeId;

    @NotNull
    private Long endNodeId;

    public Long getStartNodeId()
    {
        return startNodeId;
    }

    public void setStartNodeId(Long startNodeId)
    {
        this.startNodeId = startNodeId;
    }

    public Long getEndNodeId()
    {
        return endNodeId;
    }

    public void setEndNodeId(Long endNodeId)
    {
        this.endNodeId = endNodeId;
    }
}
