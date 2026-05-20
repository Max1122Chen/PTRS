package com.travel.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从地图增量包路径推导同目录下的室内图 classpath 通配符。
 */
public final class IndoorMapPackPaths
{

    private IndoorMapPackPaths()
    {
    }

    public static String indoorGlobFromMapAsset(String mapAssetPath)
    {
        if (mapAssetPath == null || mapAssetPath.isBlank())
        {
            return null;
        }
        String trimmed = mapAssetPath.trim();
        if (trimmed.endsWith("pois.append.json"))
        {
            return trimmed.substring(0, trimmed.length() - "pois.append.json".length()) + "indoor/*.json";
        }
        if (trimmed.endsWith("buildings.json"))
        {
            return trimmed.substring(0, trimmed.length() - "buildings.json".length()) + "indoor/*.json";
        }
        return null;
    }

    public static List<String> indoorGlobsFromPoiImports(List<String> poiImportPaths)
    {
        Set<String> globs = new LinkedHashSet<>();
        if (poiImportPaths == null)
        {
            return List.of();
        }
        for (String path : poiImportPaths)
        {
            String glob = indoorGlobFromMapAsset(path);
            if (glob != null)
            {
                globs.add(glob);
            }
        }
        return new ArrayList<>(globs);
    }
}
