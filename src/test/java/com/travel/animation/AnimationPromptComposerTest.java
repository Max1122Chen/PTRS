package com.travel.animation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnimationPromptComposerTest
{

    @Test
    void sharedBlock_containsAspectStyleAndDuration()
    {
        AnimationGenParams gp = AnimationGenParams.fromRequest(null);
        String block = AnimationPromptComposer.sharedGenerationParamsBlock(gp);
        assertThat(block).contains("画面比例").contains("16:9").contains("写实纪实").contains("目标时长");
    }

    @Test
    void buildJimengBody_appendsDiaryDirective()
    {
        AnimationGenParams gp = AnimationGenParams.defaults();
        String body = AnimationPromptComposer.buildJimengPromptBody("标题：x\n正文摘录：y", gp);
        assertThat(body).contains("旅游短视频").contains("标题：x");
    }
}
