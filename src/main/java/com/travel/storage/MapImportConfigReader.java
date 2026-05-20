package com.travel.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Component
public class MapImportConfigReader
{

    private static final Logger log = LoggerFactory.getLogger(MapImportConfigReader.class);

    private final ObjectMapper objectMapper;

    private final ResourceLoader resourceLoader;

    public MapImportConfigReader(ObjectMapper objectMapper, ResourceLoader resourceLoader)
    {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public MapImportConfig read(String mapImportConfigPath)
    {
        if (mapImportConfigPath == null || mapImportConfigPath.isBlank())
        {
            return null;
        }
        Resource resource = resourceLoader.getResource(resolveConfigResourcePath(mapImportConfigPath));
        if (!resource.exists())
        {
            log.warn("Map import config not found: {}", mapImportConfigPath);
            return null;
        }
        try (InputStream inputStream = resource.getInputStream())
        {
            return objectMapper.readValue(inputStream, MapImportConfig.class);
        }
        catch (IOException ex)
        {
            log.warn("Failed to read map import config {}: {}", mapImportConfigPath, ex.getMessage());
            return null;
        }
    }

    private String resolveConfigResourcePath(String path)
    {
        String trimmed = path.trim();
        if (trimmed.startsWith("classpath:") || trimmed.startsWith("file:"))
        {
            return trimmed;
        }
        return "classpath:" + trimmed;
    }

    public record MapImportConfig(
        List<String> scenicAreas,
        List<String> pois,
        List<String> buildings,
        List<String> roads,
        List<String> facilities
    )
    {
        public MapImportConfig
        {
            scenicAreas = scenicAreas == null ? Collections.emptyList() : scenicAreas;
            pois = pois == null ? Collections.emptyList() : pois;
            buildings = buildings == null ? Collections.emptyList() : buildings;
            roads = roads == null ? Collections.emptyList() : roads;
            facilities = facilities == null ? Collections.emptyList() : facilities;
        }
    }
}
