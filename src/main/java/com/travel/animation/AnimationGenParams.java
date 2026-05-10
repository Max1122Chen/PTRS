package com.travel.animation;

import com.travel.model.dto.diary.AnimationGenerateRequest;

/**
 * 解析后的动画生成参数（下发即梦 / LibTV）。
 */
public final class AnimationGenParams
{

    private final String aspectRatio;

    private final String styleKey;

    /** 写入中文提示的风格描述 */
    private final String styleLabel;

    private final int durationSec;

    private final String extraPrompt;

    private final boolean interactive;

    private AnimationGenParams(String aspectRatio, String styleKey, String styleLabel, int durationSec,
        String extraPrompt, boolean interactive)
    {
        this.aspectRatio = aspectRatio;
        this.styleKey = styleKey;
        this.styleLabel = styleLabel;
        this.durationSec = durationSec;
        this.extraPrompt = extraPrompt == null ? "" : extraPrompt;
        this.interactive = interactive;
    }

    public static AnimationGenParams defaults()
    {
        return new AnimationGenParams("16:9", "documentary", "写实纪实", 8, "", false);
    }

    public static AnimationGenParams fromRequest(AnimationGenerateRequest req)
    {
        if (req == null)
        {
            return defaults();
        }
        String ar = trimOrNull(req.getAspectRatio());
        if (ar == null || ar.isBlank())
        {
            ar = "16:9";
        }
        String styleInput = trimOrNull(req.getStyle());
        if (styleInput == null || styleInput.isBlank())
        {
            return new AnimationGenParams(normalizeAspect(ar), "documentary", "写实纪实",
                clampDur(req.getDurationSec()), safeExtra(req.getExtraPrompt()),
                Boolean.TRUE.equals(req.getInteractive()));
        }
        String label = mapStyleKeyToLabel(styleInput);
        int dur = clampDur(req.getDurationSec());
        boolean inter = Boolean.TRUE.equals(req.getInteractive());
        return new AnimationGenParams(normalizeAspect(ar), styleInput.trim(), label, dur,
            safeExtra(req.getExtraPrompt()), inter);
    }

    private static int clampDur(Integer sec)
    {
        if (sec == null)
        {
            return 8;
        }
        return Math.min(120, Math.max(3, sec));
    }

    private static String safeExtra(String extraPrompt)
    {
        String e = trimOrNull(extraPrompt);
        return e == null ? "" : e;
    }

    private static String normalizeAspect(String ar)
    {
        String t = ar.trim();
        if ("9:16".equals(t) || "16:9".equals(t) || "1:1".equals(t))
        {
            return t;
        }
        return "16:9";
    }

    /**
     * 预设英文/短键映射为中文；否则把用户原文当作风格描述。
     */
    private static String mapStyleKeyToLabel(String styleKey)
    {
        String raw = styleKey.trim();
        String k = raw.toLowerCase();
        return switch (k)
        {
            case "documentary", "real" -> "写实纪实";
            case "cinematic", "film" -> "电影感";
            case "fresh", "healing" -> "清新治愈";
            case "anime", "cartoon" -> "动漫风格";
            default -> raw;
        };
    }

    private static String trimOrNull(String s)
    {
        if (s == null)
        {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public String getAspectRatio()
    {
        return aspectRatio;
    }

    public String getStyleKey()
    {
        return styleKey;
    }

    public String getStyleLabel()
    {
        return styleLabel;
    }

    public int getDurationSec()
    {
        return durationSec;
    }

    public String getExtraPrompt()
    {
        return extraPrompt;
    }

    public boolean isInteractive()
    {
        return interactive;
    }
}
