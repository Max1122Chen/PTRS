package com.travel.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 旅游日记实体，对应 diaries 表。
 */
@TableName("diaries")
public class Diary implements Serializable
{

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String title;

    private String content;

    /**
     * 图片 URL 列表（JSON 字符串存储）。
     */
    private String images;

    /**
     * 视频 URL 列表（JSON 字符串存储）。
     */
    private String videos;

    /**
     * AIGC 旅游动画成片地址（本站 /media 路径，落盘持久化）。
     */
    @TableField("animation_url")
    private String animationUrl;

    private Integer heat;

    private Double rating;

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

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getImages()
    {
        return images;
    }

    public void setImages(String images)
    {
        this.images = images;
    }

    public String getVideos()
    {
        return videos;
    }

    public void setVideos(String videos)
    {
        this.videos = videos;
    }

    public String getAnimationUrl()
    {
        return animationUrl;
    }

    public void setAnimationUrl(String animationUrl)
    {
        this.animationUrl = animationUrl;
    }

    public Integer getHeat()
    {
        return heat;
    }

    public void setHeat(Integer heat)
    {
        this.heat = heat;
    }

    public Double getRating()
    {
        return rating;
    }

    public void setRating(Double rating)
    {
        this.rating = rating;
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

