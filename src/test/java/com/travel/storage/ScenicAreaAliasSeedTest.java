package com.travel.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 校验 scenic-area-aliases.json 可被 Jackson 完整反序列化（name/description 等字段非空）。
 */
class ScenicAreaAliasSeedTest
{

    @Test
    void aliasJsonDeserializesDisplayFields() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        try (InputStream in = new ClassPathResource("dev-seed/scenic-area-aliases.json").getInputStream())
        {
            List<Map<String, Object>> rows = mapper.readValue(in, new TypeReference<>()
            {
            });
            assertFalse(rows.isEmpty());
            Map<String, Object> first = rows.get(0);
            assertEquals(10001, ((Number) first.get("id")).intValue());
            assertNotNull(first.get("name"));
            assertFalse(String.valueOf(first.get("name")).isBlank());
            assertNotNull(first.get("description"));
            assertNotNull(first.get("location"));
        }
    }
}
