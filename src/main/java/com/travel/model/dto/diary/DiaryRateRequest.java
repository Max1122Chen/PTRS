package com.travel.model.dto.diary;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 日记评分请求。
 */
public class DiaryRateRequest
{

    @NotNull(message = "diaryId 不能为空")
    private Long diaryId;

    @NotNull(message = "rating 不能为空")
    @Min(value = 1, message = "rating 最小为 1")
    @Max(value = 5, message = "rating 最大为 5")
    private Double rating;

    /**
     * 可选评论内容。
     */
    private String comment;

    public Long getDiaryId()
    {
        return diaryId;
    }

    public void setDiaryId(Long diaryId)
    {
        this.diaryId = diaryId;
    }

    public Double getRating()
    {
        return rating;
    }

    public void setRating(Double rating)
    {
        this.rating = rating;
    }

    public String getComment()
    {
        return comment;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }
}

