package com.travel.ds;

/**
 * {@code com.travel.ds} 与 JDK 集合在服务/持久层边界的转换工具。
 * 核心算法模块内部应直接使用 ds 结构，仅在 Controller/VO/JSON 边界按需转换。
 */
public final class DsConvert
{
    private DsConvert()
    {
    }

    public static HashMap<String, Double> copyStringDoubleMap(java.util.Map<String, Double> source)
    {
        HashMap<String, Double> map = new HashMap<>();
        if (source == null)
        {
            return map;
        }
        for (java.util.Map.Entry<String, Double> entry : source.entrySet())
        {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    public static <E> java.util.List<E> toJavaList(List<E> source)
    {
        if (source == null || source.isEmpty())
        {
            return java.util.List.of();
        }
        java.util.ArrayList<E> out = new java.util.ArrayList<>(source.size());
        for (E item : source)
        {
            out.add(item);
        }
        return out;
    }

    public static java.util.List<String> modeKeysToJavaList(Map<String, Double> profile)
    {
        java.util.ArrayList<String> modes = new java.util.ArrayList<>();
        if (profile == null)
        {
            return modes;
        }
        for (String key : profile.keySet())
        {
            modes.add(key);
        }
        return modes;
    }
}
