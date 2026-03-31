package com.travel.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.model.entity.Comment;
import com.travel.model.entity.Diary;
import com.travel.model.entity.DiaryDestination;
import com.travel.model.entity.Facility;
import com.travel.model.entity.Food;
import com.travel.model.entity.Poi;
import com.travel.model.entity.Restaurant;
import com.travel.model.entity.Road;
import com.travel.model.entity.ScenicArea;
import com.travel.model.entity.ScenicAreaTag;
import com.travel.model.entity.Tag;
import com.travel.model.entity.User;
import com.travel.model.entity.UserInterest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 开发环境种子数据加载器。
 *
 * <p>用于数据库不可用时向内存仓库写入可测试数据，保障前端联调与检索演示。</p>
 */
@Component
public class DevSeedDataLoader
{

    private static final Logger log = LoggerFactory.getLogger(DevSeedDataLoader.class);

    private final InMemoryStore store;

    private final ObjectMapper objectMapper;

    private final ResourceLoader resourceLoader;

    private final PasswordEncoder passwordEncoder;

    private volatile boolean loaded;

    @Value("${app.dev-seed.enabled:false}")
    private boolean devSeedEnabled;

    @Value("${app.dev-seed.path:classpath:dev-seed}")
    private String devSeedPath;

    @Value("${app.dev-seed.map-import-config:}")
    private String mapImportConfigPath;

    public DevSeedDataLoader(InMemoryStore store,
                             ObjectMapper objectMapper,
                             ResourceLoader resourceLoader,
                             PasswordEncoder passwordEncoder)
    {
        this.store = store;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.passwordEncoder = passwordEncoder;
    }

    public synchronized void loadSeedIfEnabled(String reason)
    {
        if (!devSeedEnabled)
        {
            log.info("Dev seed data is disabled by config: app.dev-seed.enabled=false");
            return;
        }
        if (loaded)
        {
            log.info("Dev seed data already loaded, skip duplicate load.");
            return;
        }

        SeedBundle bundle = readSeedBundle();

        for (User u : bundle.users)
        {
            encodePasswordIfNeeded(u);
            store.insertUser(u);
        }

        Map<Long, List<UserInterest>> interestsByUserId = new HashMap<>();
        for (UserInterest interest : bundle.userInterests)
        {
            interestsByUserId.computeIfAbsent(interest.getUserId(), k -> new java.util.ArrayList<>()).add(interest);
        }
        for (Map.Entry<Long, List<UserInterest>> entry : interestsByUserId.entrySet())
        {
            store.replaceUserInterests(entry.getKey(), entry.getValue());
        }

        for (ScenicArea s : bundle.scenicAreas)
        {
            store.insertScenicArea(s);
        }
        for (Tag t : bundle.tags)
        {
            store.insertTag(t);
        }
        for (ScenicAreaTag r : bundle.scenicAreaTags)
        {
            store.insertScenicAreaTag(r);
        }
        store.rebuildScenicAreaTagWeights();

        for (Poi p : bundle.pois)
        {
            store.insertPoi(p);
        }
        for (Road r : bundle.roads)
        {
            store.insertRoad(r);
        }
        for (Facility f : bundle.facilities)
        {
            store.insertFacility(f);
        }
        for (Restaurant r : bundle.restaurants)
        {
            store.insertRestaurant(r);
        }
        for (Food f : bundle.foods)
        {
            store.insertFood(f);
        }
        for (Diary d : bundle.diaries)
        {
            store.insertDiary(d);
        }

        store.rebuildSearchIndicesAll();

        Map<Long, List<Long>> diaryDestinationIdsByDiaryId = new HashMap<>();
        for (DiaryDestination dd : bundle.diaryDestinations)
        {
            diaryDestinationIdsByDiaryId.computeIfAbsent(dd.getDiaryId(), k -> new java.util.ArrayList<>()).add(dd.getDestinationId());
        }
        for (Map.Entry<Long, List<Long>> entry : diaryDestinationIdsByDiaryId.entrySet())
        {
            store.replaceDiaryDestinations(entry.getKey(), entry.getValue());
        }

        for (Comment c : bundle.comments)
        {
            store.insertComment(c);
        }

        loaded = true;
        log.info("Dev seed data loaded from JSON path {} successfully (users={}, scenicAreas={}, facilities={}, foods={}, diaries={}, comments={}), reason={}",
                devSeedPath,
                bundle.users.size(),
                bundle.scenicAreas.size(),
                bundle.facilities.size(),
                bundle.foods.size(),
                bundle.diaries.size(),
                bundle.comments.size(),
                reason);
    }

    private SeedBundle readSeedBundle()
    {
        try
        {
            List<User> users = readList("users.json", new TypeReference<List<User>>()
            {
            });
            List<UserInterest> interests = readList("user_interests.json", new TypeReference<List<UserInterest>>()
            {
            });
            List<ScenicArea> scenicAreas = readList("scenic_areas.json", new TypeReference<List<ScenicArea>>()
            {
            });
            List<Tag> tags = readList("tags.json", new TypeReference<List<Tag>>()
            {
            });
            List<ScenicAreaTag> scenicAreaTags = readList("scenic_area_tags.json", new TypeReference<List<ScenicAreaTag>>()
            {
            });
            List<Poi> pois = readList("buildings.json", new TypeReference<List<Poi>>()
            {
            });
            List<Road> roads = readList("roads.json", new TypeReference<List<Road>>()
            {
            });
            List<Facility> facilities = readList("facilities.json", new TypeReference<List<Facility>>()
            {
            });

            MapImportConfig mapImportConfig = readMapImportConfig();
            if (mapImportConfig != null)
            {
                List<ScenicArea> importedScenicAreas = readOptionalMultiList(
                    mapImportConfig.scenicAreas(),
                    new TypeReference<List<ScenicArea>>()
                    {
                    }
                );
                scenicAreas = mergeById(scenicAreas, importedScenicAreas, ScenicArea::getId);

                List<Poi> importedPois = readOptionalMultiList(
                    mapImportConfig.pois(),
                    new TypeReference<List<Poi>>()
                    {
                    }
                );
                // 兼容旧命名 buildings。
                importedPois.addAll(readOptionalMultiList(
                    mapImportConfig.buildings(),
                    new TypeReference<List<Poi>>()
                    {
                    }
                ));
                pois = mergeById(pois, importedPois, Poi::getId);

                List<Road> importedRoads = readOptionalMultiList(
                    mapImportConfig.roads(),
                    new TypeReference<List<Road>>()
                    {
                    }
                );
                roads = mergeById(roads, importedRoads, Road::getId);

                List<Facility> importedFacilities = readOptionalMultiList(
                    mapImportConfig.facilities(),
                    new TypeReference<List<Facility>>()
                    {
                    }
                );
                facilities = mergeById(facilities, importedFacilities, Facility::getId);

                log.info("Dev seed map imports loaded via config {} (scenicAreas={}, pois={}, roads={}, facilities={})",
                    mapImportConfigPath,
                    importedScenicAreas.size(),
                    importedPois.size(),
                    importedRoads.size(),
                    importedFacilities.size());
            }

            List<Restaurant> restaurants = readList("restaurants.json", new TypeReference<List<Restaurant>>()
            {
            });
            List<Food> foods = readList("foods.json", new TypeReference<List<Food>>()
            {
            });
            List<Diary> diaries = readList("diaries.json", new TypeReference<List<Diary>>()
            {
            });
            List<DiaryDestination> diaryDestinations = readList("diary_destinations.json", new TypeReference<List<DiaryDestination>>()
            {
            });
            List<Comment> comments = readList("comments.json", new TypeReference<List<Comment>>()
            {
            });

            return new SeedBundle(
                users,
                interests,
                scenicAreas,
                tags,
                scenicAreaTags,
                pois,
                roads,
                facilities,
                restaurants,
                foods,
                diaries,
                diaryDestinations,
                comments
            );
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Failed to load dev seed json files from path: " + devSeedPath, ex);
        }
    }

    private <T> List<T> readList(String fileName, TypeReference<List<T>> typeReference) throws IOException
    {
        Resource resource = resourceLoader.getResource(resolveResourcePath(fileName));
        if (!resource.exists())
        {
            throw new IllegalStateException("Seed file not found: " + resolveResourcePath(fileName));
        }
        try (InputStream inputStream = resource.getInputStream())
        {
            return objectMapper.readValue(inputStream, typeReference);
        }
    }

    private MapImportConfig readMapImportConfig() throws IOException
    {
        if (mapImportConfigPath == null || mapImportConfigPath.isBlank())
        {
            return null;
        }

        Resource resource = resourceLoader.getResource(resolveConfigResourcePath(mapImportConfigPath));
        if (!resource.exists())
        {
            log.warn("Dev seed map import config not found, skip map imports: {}", mapImportConfigPath);
            return null;
        }

        try (InputStream inputStream = resource.getInputStream())
        {
            return objectMapper.readValue(inputStream, MapImportConfig.class);
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

    private <T> List<T> readOptionalMultiList(List<String> resourcePaths, TypeReference<List<T>> typeReference) throws IOException
    {
        if (resourcePaths == null || resourcePaths.isEmpty())
        {
            return new java.util.ArrayList<>();
        }
        List<T> out = new java.util.ArrayList<>();
        for (String path : resourcePaths)
        {
            if (path == null || path.isBlank())
            {
                continue;
            }
            Resource resource = resourceLoader.getResource(resolveConfigResourcePath(path));
            if (!resource.exists())
            {
                log.warn("Map import file not found, skip: {}", path);
                continue;
            }
            try (InputStream inputStream = resource.getInputStream())
            {
                List<T> list = objectMapper.readValue(inputStream, typeReference);
                if (list != null && !list.isEmpty())
                {
                    out.addAll(list);
                }
            }
        }
        return out;
    }

    private <T> List<T> mergeById(List<T> base, List<T> imports, java.util.function.Function<T, Long> idGetter)
    {
        if (imports == null || imports.isEmpty())
        {
            return base;
        }
        Map<Long, T> merged = new java.util.LinkedHashMap<>();
        for (T row : base)
        {
            Long id = idGetter.apply(row);
            if (id != null)
            {
                merged.put(id, row);
            }
        }
        for (T row : imports)
        {
            Long id = idGetter.apply(row);
            if (id != null)
            {
                merged.put(id, row);
            }
        }
        return new java.util.ArrayList<>(merged.values());
    }

    private String resolveResourcePath(String fileName)
    {
        if (devSeedPath.endsWith("/"))
        {
            return devSeedPath + fileName;
        }
        return devSeedPath + "/" + fileName;
    }

    private void encodePasswordIfNeeded(User user)
    {
        String pwd = user.getPassword();
        if (pwd == null || pwd.isBlank())
        {
            return;
        }
        if (isBcryptEncoded(pwd))
        {
            return;
        }
        user.setPassword(passwordEncoder.encode(pwd));
    }

    private boolean isBcryptEncoded(String value)
    {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private record SeedBundle(
        List<User> users,
        List<UserInterest> userInterests,
        List<ScenicArea> scenicAreas,
        List<Tag> tags,
        List<ScenicAreaTag> scenicAreaTags,
        List<Poi> pois,
        List<Road> roads,
        List<Facility> facilities,
        List<Restaurant> restaurants,
        List<Food> foods,
        List<Diary> diaries,
        List<DiaryDestination> diaryDestinations,
        List<Comment> comments
    )
    {
    }

    private record MapImportConfig(
        List<String> scenicAreas,
        List<String> pois,
        List<String> buildings,
        List<String> roads,
        List<String> facilities
    )
    {
        private MapImportConfig
        {
            scenicAreas = scenicAreas == null ? Collections.emptyList() : scenicAreas;
            pois = pois == null ? Collections.emptyList() : pois;
            buildings = buildings == null ? Collections.emptyList() : buildings;
            roads = roads == null ? Collections.emptyList() : roads;
            facilities = facilities == null ? Collections.emptyList() : facilities;
        }
    }
}
