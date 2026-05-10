package com.travel.animation;

import com.alibaba.fastjson.JSONObject;
import com.travel.config.AnimationProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JimengVideoSubmitBuilderTest
{

    @Test
    void framesForDuration_mapsToDiscrete121Or241()
    {
        assertThat(JimengVideoSubmitBuilder.framesForDurationSeconds(3)).isEqualTo(121);
        assertThat(JimengVideoSubmitBuilder.framesForDurationSeconds(7)).isEqualTo(121);
        assertThat(JimengVideoSubmitBuilder.framesForDurationSeconds(8)).isEqualTo(241);
        assertThat(JimengVideoSubmitBuilder.framesForDurationSeconds(120)).isEqualTo(241);
    }

    @Test
    void clampPrompt_truncatesLongText()
    {
        String longText = "x".repeat(500);
        assertThat(JimengVideoSubmitBuilder.clampPrompt(longText).length())
            .isEqualTo(JimengVideoSubmitBuilder.PROMPT_MAX_CHARS);
    }

    @Test
    void preferHttpImageUrls_rejectsLocalhost()
    {
        assertThat(JimengVideoSubmitBuilder.preferHttpImageUrls(List.of("http://localhost:8080/media/a.jpg")))
            .isFalse();
        assertThat(JimengVideoSubmitBuilder.preferHttpImageUrls(List.of("https://cdn.example.com/a.jpg")))
            .isTrue();
    }

    @Test
    void buildSubmitPayload_textModeUsesTextReqKey()
    {
        AnimationProperties.Jimeng j = new AnimationProperties.Jimeng();
        j.setReqKeyTextToVideo("jimeng_ti2v_v30_pro");
        j.setReqKeyImageToVideo("jimeng_i2v_first_v30");
        AnimationGenParams gp = AnimationGenParams.defaults();
        JSONObject body = JimengVideoSubmitBuilder.buildSubmitPayload("测试提示词", List.of(), List.of(), j, gp);
        assertThat(body.getString("req_key")).isEqualTo("jimeng_ti2v_v30_pro");
        assertThat(body.getInteger("seed")).isEqualTo(-1);
        assertThat(body.getInteger("frames")).isEqualTo(241); // defaults durationSec=8 → 241 帧
        assertThat(body.containsKey("binary_data_base64")).isFalse();
        assertThat(body.containsKey("image_urls")).isFalse();
    }

    @Test
    void buildSubmitPayload_imageBytesUsesImageReqKey()
    {
        AnimationProperties.Jimeng j = new AnimationProperties.Jimeng();
        j.setReqKeyTextToVideo("t2v");
        j.setReqKeyImageToVideo("i2v");
        AnimationGenParams gp = AnimationGenParams.defaults();
        JSONObject body = JimengVideoSubmitBuilder.buildSubmitPayload("p",
            List.of(new byte[] {1, 2, 3}), List.of(), j, gp);
        assertThat(body.getString("req_key")).isEqualTo("i2v");
        assertThat(body.getJSONArray("binary_data_base64")).isNotEmpty();
    }

    @Test
    void buildPollPayload_includesReqJsonWhenAigcConfigured()
    {
        AnimationProperties.Jimeng j = new AnimationProperties.Jimeng();
        j.setAigcMetaJson("{\"producer_id\":\"p1\",\"propagate_id\":\"x\"}");
        JSONObject poll = JimengVideoSubmitBuilder.buildPollPayload("tid-1", "rk", j);
        assertThat(poll.getString("task_id")).isEqualTo("tid-1");
        assertThat(poll.getString("req_key")).isEqualTo("rk");
        assertThat(poll.getString("req_json")).contains("aigc_meta");
    }
}
