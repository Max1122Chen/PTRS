package com.travel.model.enums;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * 路线规划支持的交通工具枚举。
 */
public enum TransportMode
{
    WALK("walk"),
    BIKE("bike"),
    SHUTTLE("shuttle");

    private final String code;

    TransportMode(String code)
    {
        this.code = code;
    }

    public String code()
    {
        return code;
    }

    public static Optional<TransportMode> fromCode(String raw)
    {
        if (StringUtils.isBlank(raw))
        {
            return Optional.empty();
        }
        String norm = raw.trim().toLowerCase();
        return Arrays.stream(values()).filter(mode -> mode.code.equals(norm)).findFirst();
    }
}
