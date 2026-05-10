package com.travel.model.dto.diary;

/**
 * 向进行中的 LibTV 会话追加一条用户消息。
 */
public class DiaryAnimationJobMessageRequest
{

    private String message;

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
}
