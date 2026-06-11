package com.travel.model.vo.diary;

import com.travel.model.entity.Diary;
import com.travel.model.vo.comment.CommentVO;

import java.util.List;

/**
 * 日记详情返回对象（包含目的地列表）。
 */
public class DiaryDetailVO
{

    private Diary diary;

    private List<Long> destinations;

    /**
     * 日记创建者昵称（用于列表展示）。
     */
    private String creatorNickname;

    private List<CommentVO> comments;

    public Diary getDiary()
    {
        return diary;
    }

    public void setDiary(Diary diary)
    {
        this.diary = diary;
    }

    public List<Long> getDestinations()
    {
        return destinations;
    }

    public void setDestinations(List<Long> destinations)
    {
        this.destinations = destinations;
    }

    public String getCreatorNickname()
    {
        return creatorNickname;
    }

    public void setCreatorNickname(String creatorNickname)
    {
        this.creatorNickname = creatorNickname;
    }

    public List<CommentVO> getComments()
    {
        return comments;
    }

    public void setComments(List<CommentVO> comments)
    {
        this.comments = comments;
    }
}

