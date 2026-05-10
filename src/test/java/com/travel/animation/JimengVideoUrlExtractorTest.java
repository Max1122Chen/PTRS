package com.travel.animation;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JimengVideoUrlExtractorTest
{

    @Test
    void extractsFromVideoResultArray()
    {
        String json = "{\"code\":10000,\"data\":{\"status\":\"done\",\"video_result\":[{\"url\":\"https://cdn.example.com/a/b/video.mp4?x=1\"}]}}";
        String u = JimengVideoUrlExtractor.extractFirstVideoUrl(JSON.parseObject(json));
        assertThat(u).contains("video.mp4");
    }

    @Test
    void extractsFromNestedRespJsonString()
    {
        com.alibaba.fastjson.JSONObject inner = new com.alibaba.fastjson.JSONObject();
        inner.put("video_url", "https://tos.volces.com/bucket/out.mp4");
        com.alibaba.fastjson.JSONObject data = new com.alibaba.fastjson.JSONObject();
        data.put("status", "done");
        data.put("resp_json", inner.toJSONString());
        com.alibaba.fastjson.JSONObject root = new com.alibaba.fastjson.JSONObject();
        root.put("data", data);
        String u = JimengVideoUrlExtractor.extractFirstVideoUrl(root);
        assertThat(u).contains("out.mp4");
    }

    @Test
    void acceptsVolcCdnUrlWithoutFileSuffix()
    {
        String json = "{\"data\":{\"status\":\"done\",\"url\":\"https://demo.tos-cn-beijing.volces.com/obj/abc123?X-Tos-Algorithm=aws4\"}}";
        String u = JimengVideoUrlExtractor.extractFirstVideoUrl(JSON.parseObject(json));
        assertThat(u).startsWith("https://");
    }
}
