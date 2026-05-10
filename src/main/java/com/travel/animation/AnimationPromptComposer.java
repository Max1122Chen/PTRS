package com.travel.animation;

/**
 * 即梦与 LibTV 共用的「生成参数」文案拼装，保证前端同一套 {@link AnimationGenParams} 语义一致落地。
 */
public final class AnimationPromptComposer
{

    private AnimationPromptComposer()
    {
    }

    /**
     * LibTV 首轮正文、即梦 {@code prompt} 共用段落（不含日记正文与厂商专属前缀）。
     */
    public static String sharedGenerationParamsBlock(AnimationGenParams gp)
    {
        if (gp == null)
        {
            gp = AnimationGenParams.defaults();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【本次生成参数】画面比例 ").append(gp.getAspectRatio())
            .append("；风格 ").append(gp.getStyleLabel())
            .append("；目标时长约 ").append(gp.getDurationSec()).append(" 秒。");
        if (gp.getExtraPrompt() != null && !gp.getExtraPrompt().isBlank())
        {
            sb.append("\n【用户额外要求】").append(gp.getExtraPrompt().trim());
        }
        return sb.toString();
    }

    /**
     * 即梦完整提示词：共用参数块 + 日记摘录指引。
     */
    public static String buildJimengPromptBody(String diaryPart, AnimationGenParams gp)
    {
        return sharedGenerationParamsBlock(gp) + "\n请根据以下旅行日记生成一段符合上述参数的旅游短视频。\n" + diaryPart;
    }
}
