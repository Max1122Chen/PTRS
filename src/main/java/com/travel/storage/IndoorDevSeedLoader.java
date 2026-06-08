package com.travel.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.config.IndoorProperties;
import com.travel.indoor.IndoorBuildingBundle;
import com.travel.indoor.IndoorEdgeRecord;
import com.travel.indoor.IndoorLevelMeta;
import com.travel.indoor.IndoorNodeRecord;
import com.travel.indoor.IndoorSeedCompleteness;
import com.travel.model.entity.Poi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 室内图加载：优先地图包 {@code osm-data/.../latest/indoor/}，兼容 {@code dev-seed/indoor/}（演示 502 等）。
 */
@Component
public class IndoorDevSeedLoader
{

    private static final Logger log = LoggerFactory.getLogger(IndoorDevSeedLoader.class);

    private final InMemoryStore store;

    private final ObjectMapper objectMapper;

    private final ResourcePatternResolver resourcePatternResolver;

    private final IndoorProperties indoorProperties;

    private final MapImportConfigReader mapImportConfigReader;

    @Value("${app.dev-seed.path:classpath:dev-seed}")
    private String devSeedPath;

    @Value("${app.dev-seed.map-import-config:}")
    private String defaultMapImportConfigPath;

    public IndoorDevSeedLoader(InMemoryStore store,
                               ObjectMapper objectMapper,
                               ResourcePatternResolver resourcePatternResolver,
                               IndoorProperties indoorProperties,
                               MapImportConfigReader mapImportConfigReader)
    {
        this.store = store;
        this.objectMapper = objectMapper;
        this.resourcePatternResolver = resourcePatternResolver;
        this.indoorProperties = indoorProperties;
        this.mapImportConfigReader = mapImportConfigReader;
    }

    /** 启动时：地图包 + legacy dev-seed。 */
    public int loadIndoorSeeds()
    {
        int loaded = 0;
        loaded += loadLegacyDevSeedIndoor();
        loaded += loadFromMapImportConfig(null);
        store.reconcileIndoorAvailableFromBundles();
        log.info("Indoor seeds loaded at startup: {} building(s)", loaded);
        return loaded;
    }

    public int loadFromMapImportConfig(String mapImportConfigPath)
    {
        String path = mapImportConfigPath;
        if (path == null || path.isBlank())
        {
            path = defaultMapImportConfigPath;
        }
        MapImportConfigReader.MapImportConfig config = mapImportConfigReader.read(path);
        if (config == null)
        {
            return 0;
        }
        List<String> globs = IndoorMapPackPaths.indoorGlobsFromPoiImports(config.pois());
        globs.addAll(IndoorMapPackPaths.indoorGlobsFromPoiImports(config.buildings()));
        int loaded = 0;
        for (String glob : globs)
        {
            loaded += loadFromClasspathPattern(glob);
        }
        return loaded;
    }

    public int loadLegacyDevSeedIndoor()
    {
        String pattern = resolveBase() + "/" + indoorProperties.getSeedSubdir() + "/*.json";
        return loadFromClasspathPattern(pattern);
    }

    public int loadFromFileSystemDirectory(Path indoorDir) throws IOException
    {
        if (indoorDir == null || !Files.isDirectory(indoorDir))
        {
            return 0;
        }
        int loaded = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(indoorDir, "*.json"))
        {
            for (Path file : stream)
            {
                String name = file.getFileName().toString();
                if ("manifest.json".equalsIgnoreCase(name))
                {
                    continue;
                }
                try (InputStream in = Files.newInputStream(file))
                {
                    if (ingestBundle(in, name))
                    {
                        loaded++;
                    }
                }
            }
        }
        return loaded;
    }

    private int loadFromClasspathPattern(String pattern)
    {
        int loaded = 0;
        try
        {
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            for (Resource resource : resources)
            {
                loaded += ingestResource(resource);
            }
        }
        catch (IOException ex)
        {
            log.warn("Indoor classpath load failed pattern={}: {}", pattern, ex.getMessage());
        }
        return loaded;
    }

    private int ingestResource(Resource resource) throws IOException
    {
        String filename = resource.getFilename();
        if (filename == null || "manifest.json".equalsIgnoreCase(filename))
        {
            return 0;
        }
        try (InputStream in = resource.getInputStream())
        {
            return ingestBundle(in, filename) ? 1 : 0;
        }
    }

    private boolean ingestBundle(InputStream in, String filename) throws IOException
    {
        IndoorBuildingBundle bundle = objectMapper.readValue(in, IndoorBuildingBundle.class);
        normalizeBundle(bundle);
        IndoorSeedCompleteness.Result check = IndoorSeedCompleteness.evaluate(bundle);
        if (!check.pass())
        {
            log.warn("Reject indoor seed {} failures={}", filename, check.failureCodes());
            return false;
        }
        if (bundle.getAreaId() == null)
        {
            Poi poi = store.findPoiById(bundle.getBuildingPoiId());
            if (poi != null)
            {
                bundle.setAreaId(poi.getAreaId());
            }
        }
        store.putIndoorBundle(bundle);
        return true;
    }

    private void normalizeBundle(IndoorBuildingBundle bundle)
    {
        if (bundle.getNodes() != null)
        {
            for (IndoorNodeRecord n : bundle.getNodes())
            {
                n.setBuildingPoiId(bundle.getBuildingPoiId());
                if (n.getParentId() == null)
                {
                    n.setParentId(bundle.getBuildingPoiId());
                }
            }
        }
        if (bundle.getEdges() != null)
        {
            for (IndoorEdgeRecord e : bundle.getEdges())
            {
                e.setBuildingPoiId(bundle.getBuildingPoiId());
            }
        }
        if (bundle.getLevels() == null || bundle.getLevels().isEmpty())
        {
            List<IndoorLevelMeta> levels = new ArrayList<>();
            IndoorLevelMeta meta = new IndoorLevelMeta();
            meta.setLevel("0");
            meta.setLabel("1层");
            meta.setOrder(0);
            levels.add(meta);
            bundle.setLevels(levels);
        }
    }

    private String resolveBase()
    {
        String base = devSeedPath == null ? "classpath:dev-seed" : devSeedPath.trim();
        if (!base.endsWith("/"))
        {
            base = base + "/";
        }
        return base;
    }
}
