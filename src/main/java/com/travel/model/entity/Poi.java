package com.travel.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * POI 实体，对应 buildings 表。
 */
@TableName("buildings")
public class Poi implements Serializable
{

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String type;

    private String description;

    private String location;

    private Double longitude;

    private Double latitude;

    @TableField("parent_id")
    private Long parentId;

    @TableField("indoor_available")
    private Boolean indoorAvailable;

    @TableField("osm_indoor_ref")
    private String osmIndoorRef;

    @TableField("area_id")
    private Long areaId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getLocation()
    {
        return location;
    }

    public void setLocation(String location)
    {
        this.location = location;
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

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public Boolean getIndoorAvailable()
    {
        return indoorAvailable;
    }

    public void setIndoorAvailable(Boolean indoorAvailable)
    {
        this.indoorAvailable = indoorAvailable;
    }

    public String getOsmIndoorRef()
    {
        return osmIndoorRef;
    }

    public void setOsmIndoorRef(String osmIndoorRef)
    {
        this.osmIndoorRef = osmIndoorRef;
    }

    public boolean isIndoorAvailable()
    {
        return Boolean.TRUE.equals(indoorAvailable);
    }

    public Long getAreaId()
    {
        return areaId;
    }

    public void setAreaId(Long areaId)
    {
        this.areaId = areaId;
    }

    public LocalDateTime getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime)
    {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime)
    {
        this.updateTime = updateTime;
    }
}

