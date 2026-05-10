package com.travel.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.model.enums.TransportMode;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * roads.mode_profile 的编解码工具。
 */
public final class ModeProfileCodec
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ModeProfileCodec()
    {
    }

    public static Map<String, Double> decode(String modeProfile)
    {
        if (StringUtils.isBlank(modeProfile))
        {
            return Collections.emptyMap();
        }
        try
        {
            Map<String, Object> raw = MAPPER.readValue(modeProfile, new TypeReference<Map<String, Object>>()
            {
            });
            if (raw == null || raw.isEmpty())
            {
                return Collections.emptyMap();
            }
            Map<String, Double> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet())
            {
                String key = normalizeMode(entry.getKey());
                if (key == null)
                {
                    continue;
                }
                Double val = toDouble(entry.getValue());
                if (val == null)
                {
                    continue;
                }
                out.put(key, clamp(val));
            }
            return out;
        }
        catch (Exception ex)
        {
            return Collections.emptyMap();
        }
    }

    public static String encode(Map<String, Double> profile)
    {
        if (profile == null || profile.isEmpty())
        {
            return "{}";
        }

        Map<String, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : profile.entrySet())
        {
            String key = normalizeMode(entry.getKey());
            if (key == null || entry.getValue() == null)
            {
                continue;
            }
            normalized.put(key, clamp(entry.getValue()));
        }

        try
        {
            return MAPPER.writeValueAsString(normalized);
        }
        catch (Exception ex)
        {
            return "{}";
        }
    }

    private static String normalizeMode(String raw)
    {
        return TransportMode.fromCode(raw).map(TransportMode::code).orElse(null);
    }

    private static Double toDouble(Object raw)
    {
        if (raw instanceof Number number)
        {
            return number.doubleValue();
        }
        if (raw instanceof String text)
        {
            try
            {
                return Double.parseDouble(text);
            }
            catch (Exception ignored)
            {
                return null;
            }
        }
        return null;
    }

    private static double clamp(double value)
    {
        if (value < 0.0)
        {
            return 0.0;
        }
        if (value > 1.0)
        {
            return 1.0;
        }
        return value;
    }
}
