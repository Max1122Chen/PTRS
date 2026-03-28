package com.travel.model.vo.recommendation;

import com.travel.model.entity.ScenicArea;

/**
 * 景区推荐返回对象（包含推荐得分）。
 */
public class ScenicAreaRecommendVO
{

    private ScenicArea scenicArea;

    private Double score;

    private String reason;

    public ScenicArea getScenicArea()
    {
        return scenicArea;
    }

    public void setScenicArea(ScenicArea scenicArea)
    {
        this.scenicArea = scenicArea;
    }

    public Double getScore()
    {
        return score;
    }

    public void setScore(Double score)
    {
        this.score = score;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    // ---- backward-compatible flattened getters for frontend ----

    public Long getId()
    {
        return scenicArea == null ? null : scenicArea.getId();
    }

    public String getName()
    {
        return scenicArea == null ? null : scenicArea.getName();
    }

    public String getTitle()
    {
        return scenicArea == null ? null : scenicArea.getName();
    }

    public String getDescription()
    {
        return scenicArea == null ? null : scenicArea.getDescription();
    }

    public String getLocation()
    {
        return scenicArea == null ? null : scenicArea.getLocation();
    }

    public String getType()
    {
        return scenicArea == null ? null : scenicArea.getType();
    }

    public Double getRating()
    {
        return scenicArea == null ? null : scenicArea.getRating();
    }

    public Integer getHeat()
    {
        return scenicArea == null ? null : scenicArea.getHeat();
    }

    public Double getLongitude()
    {
        return scenicArea == null ? null : scenicArea.getLongitude();
    }

    public Double getLatitude()
    {
        return scenicArea == null ? null : scenicArea.getLatitude();
    }

    public String getOpenTime()
    {
        return scenicArea == null ? null : scenicArea.getOpenTime();
    }

    public String getTicketPrice()
    {
        return scenicArea == null ? null : scenicArea.getTicketPrice();
    }
}

