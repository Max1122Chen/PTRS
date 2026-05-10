package com.travel.model.dto.diary;

/**
 * 日记动画生成可选参数（未传字段由后端填默认）。
 */
public class AnimationGenerateRequest
{

    /**
     * 画面比例，如 16:9、9:16、1:1。
     */
    private String aspectRatio;

    /**
     * 风格预设键：documentary | cinematic | fresh | anime（或其它文案将原样写入提示词）。
     */
    private String style;

    /**
     * 目标时长（秒），常见 5～15。
     */
    private Integer durationSec;

    /**
     * 附在日记提示后的额外说明。
     */
    private String extraPrompt;

    /**
     * 是否与 LibTV 云端助手多轮对话直至成片（仅 LibTV 备用链生效；即梦无会话）。
     */
    private Boolean interactive;

    public String getAspectRatio()
    {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio)
    {
        this.aspectRatio = aspectRatio;
    }

    public String getStyle()
    {
        return style;
    }

    public void setStyle(String style)
    {
        this.style = style;
    }

    public Integer getDurationSec()
    {
        return durationSec;
    }

    public void setDurationSec(Integer durationSec)
    {
        this.durationSec = durationSec;
    }

    public String getExtraPrompt()
    {
        return extraPrompt;
    }

    public void setExtraPrompt(String extraPrompt)
    {
        this.extraPrompt = extraPrompt;
    }

    public Boolean getInteractive()
    {
        return interactive;
    }

    public void setInteractive(Boolean interactive)
    {
        this.interactive = interactive;
    }
}
