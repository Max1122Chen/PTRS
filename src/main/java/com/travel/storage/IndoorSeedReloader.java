package com.travel.storage;

import com.travel.indoor.IndoorGraphRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 从 classpath 地图包与文件系统目录重载室内图（采集后无需整进程重启）。
 */
@Component
public class IndoorSeedReloader
{

    private static final Logger log = LoggerFactory.getLogger(IndoorSeedReloader.class);

    private final InMemoryStore store;

    private final IndoorDevSeedLoader indoorDevSeedLoader;

    private final IndoorGraphRegistry indoorGraphRegistry;

    @Value("${app.dev-seed.map-import-config:}")
    private String mapImportConfigPath;

    public IndoorSeedReloader(InMemoryStore store,
                              IndoorDevSeedLoader indoorDevSeedLoader,
                              IndoorGraphRegistry indoorGraphRegistry)
    {
        this.store = store;
        this.indoorDevSeedLoader = indoorDevSeedLoader;
        this.indoorGraphRegistry = indoorGraphRegistry;
    }

    public synchronized int reloadAll()
    {
        store.clearIndoorState();
        int loaded = 0;
        loaded += indoorDevSeedLoader.loadLegacyDevSeedIndoor();
        loaded += indoorDevSeedLoader.loadFromMapImportConfig(mapImportConfigPath);
        store.reconcileIndoorAvailableFromBundles();
        indoorGraphRegistry.reloadFromStore(store);
        log.info("Indoor seeds reloaded from classpath: {} building(s)", loaded);
        return loaded;
    }

    /**
     * 管理端采集完成后：先读本次 scenicRoot/latest/indoor，再合并 classpath 地图包与 legacy。
     */
    public synchronized int reloadAfterCollect(String scenicRootPath)
    {
        store.clearIndoorState();
        int loaded = 0;
        loaded += indoorDevSeedLoader.loadLegacyDevSeedIndoor();
        loaded += indoorDevSeedLoader.loadFromMapImportConfig(mapImportConfigPath);
        if (scenicRootPath != null && !scenicRootPath.isBlank())
        {
            Path indoorDir = Path.of(scenicRootPath.trim()).resolve("latest").resolve("indoor");
            try
            {
                loaded += indoorDevSeedLoader.loadFromFileSystemDirectory(indoorDir);
            }
            catch (IOException ex)
            {
                log.warn("Load indoor from filesystem failed dir={}: {}", indoorDir, ex.getMessage());
            }
        }
        store.reconcileIndoorAvailableFromBundles();
        indoorGraphRegistry.reloadFromStore(store);
        log.info("Indoor seeds reloaded after collect: {} building(s)", loaded);
        return loaded;
    }
}
