package com.travel.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.PageData;
import com.travel.storage.InMemoryStore;
import com.travel.model.entity.Food;
import com.travel.model.entity.Poi;
import com.travel.model.entity.Road;
import com.travel.model.entity.ScenicArea;
import com.travel.service.AdminService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 管理端服务实现。
 */
@Service
public class AdminServiceImpl implements AdminService
{
    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private static final String NOMINATIM_BASE = "https://nominatim.openstreetmap.org/search";

    private final InMemoryStore store;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AdminServiceImpl(InMemoryStore store, ObjectMapper objectMapper)
    {
        this.store = store;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public ScenicArea addScenicArea(ScenicArea scenicArea)
    {
        LocalDateTime now = LocalDateTime.now();
        scenicArea.setCreateTime(now);
        scenicArea.setUpdateTime(now);
        store.insertScenicArea(scenicArea);
        return scenicArea;
    }

    @Override
    public PageData<ScenicArea> listScenicAreas(Integer page, Integer size, String type)
    {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size <= 0 ? 10 : Math.min(size, 50);
        int offset = (p - 1) * s;
        List<ScenicArea> list = type != null && !type.isBlank()
            ? store.findScenicAreasByType(type)
            : store.findAllScenicAreas();

        list.sort((a, b) ->
        {
            LocalDateTime ta = a.getCreateTime();
            LocalDateTime tb = b.getCreateTime();
            if (ta == null && tb == null)
            {
                return 0;
            }
            if (ta == null)
            {
                return 1;
            }
            if (tb == null)
            {
                return -1;
            }
            return tb.compareTo(ta);
        });

        int total = list.size();
        if (offset >= total)
        {
            return new PageData<>(List.of(), (long) total);
        }
        int to = Math.min(offset + s, total);
        return new PageData<>(list.subList(offset, to), (long) total);
    }

    @Override
    public Poi addPoi(Poi poi)
    {
        LocalDateTime now = LocalDateTime.now();
        poi.setCreateTime(now);
        poi.setUpdateTime(now);
        store.insertPoi(poi);
        return poi;
    }

    @Override
    public Road addRoad(Road road)
    {
        LocalDateTime now = LocalDateTime.now();
        road.setCreateTime(now);
        road.setUpdateTime(now);
        store.insertRoad(road);
        return road;
    }

    @Override
    public Food addFood(Food food)
    {
        LocalDateTime now = LocalDateTime.now();
        food.setCreateTime(now);
        food.setUpdateTime(now);
        store.insertFood(food);
        return food;
    }

    @Override
    public Map<String, Object> runPlaceSeedTask(String placeName, boolean force)
    {
        return generateFromSelectedOsm(placeName, placeName, null, null, null, force, true);
    }

    @Override
    public List<Map<String, Object>> searchLocalPlaceMatches(String keyword)
    {
        String k = keyword == null ? "" : keyword.trim();
        if (k.isBlank())
        {
            return List.of();
        }

        String lower = k.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScenicArea sa : store.findAllScenicAreas())
        {
            String name = sa.getName() == null ? "" : sa.getName();
            String location = sa.getLocation() == null ? "" : sa.getLocation();
            if (!name.toLowerCase(Locale.ROOT).contains(lower) && !location.toLowerCase(Locale.ROOT).contains(lower))
            {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", sa.getId());
            item.put("name", sa.getName());
            item.put("location", sa.getLocation());
            item.put("type", sa.getType());
            item.put("longitude", sa.getLongitude());
            item.put("latitude", sa.getLatitude());
            out.add(item);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> searchOsmCandidates(String keyword)
    {
        String k = keyword == null ? "" : keyword.trim();
        if (k.isBlank())
        {
            return List.of();
        }
        try
        {
            List<String> queryVariants = buildQueryVariants(k);
            List<Map<String, Object>> out = new ArrayList<>();
            Set<String> dedup = new HashSet<>();
            List<String> failureReasons = new ArrayList<>();

            for (String query : queryVariants)
            {
                try
                {
                    String url = NOMINATIM_BASE + "?format=jsonv2&limit=8&addressdetails=1&q=" +
                        URLEncoder.encode(query, StandardCharsets.UTF_8);
                    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "BUPT-TravelSystem-Admin/1.0")
                        .GET()
                        .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() < 200 || response.statusCode() >= 300)
                    {
                        String reason = "query=\"" + query + "\" HTTP " + response.statusCode();
                        failureReasons.add(reason);
                        log.warn("OSM nominatim non-2xx: {}", reason);
                        continue;
                    }
                    List<Map<String, Object>> raw = objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>()
                    {
                    });

                    for (Map<String, Object> row : raw)
                    {
                        String dedupKey = String.valueOf(row.get("osm_type")) + ":" + String.valueOf(row.get("osm_id"));
                        if (dedup.contains(dedupKey))
                        {
                            continue;
                        }
                        dedup.add(dedupKey);

                        Map<String, Object> item = new HashMap<>();
                        item.put("placeId", row.get("place_id"));
                        item.put("osmType", row.get("osm_type"));
                        item.put("osmId", row.get("osm_id"));
                        item.put("name", row.get("name"));
                        item.put("displayName", row.get("display_name"));
                        item.put("lat", row.get("lat"));
                        item.put("lon", row.get("lon"));
                        item.put("category", row.get("category"));
                        item.put("type", row.get("type"));
                        out.add(item);

                        if (out.size() >= 20)
                        {
                            return out;
                        }
                    }
                }
                catch (Exception innerEx)
                {
                    String reason = "query=\"" + query + "\" exception=" + innerEx.getClass().getSimpleName()
                        + ": " + (innerEx.getMessage() == null ? "null" : innerEx.getMessage());
                    failureReasons.add(reason);
                    log.warn("OSM nominatim query failed: {}", reason);
                }
            }

            if (out.isEmpty() && !failureReasons.isEmpty())
            {
                String reasonMessage = String.join(" | ", failureReasons);
                throw new IllegalStateException("OSM 查询失败或被限流，未返回候选结果。原因：" + reasonMessage);
            }
            if (out.isEmpty())
            {
                log.info("OSM nominatim returns empty with no explicit error. keyword={}", k);
            }
            return out;
        }
        catch (IllegalStateException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            throw new IllegalStateException("OSM 查询异常：" + detail, ex);
        }
    }

    @Override
    public Map<String, Object> generateFromSelectedOsm(String placeName,
                                                       String query,
                                                       String selectedPlaceId,
                                                       String selectedOsmType,
                                                       String selectedOsmId,
                                                       boolean force,
                                                       boolean buildFrontend)
    {
        String keyword = placeName == null ? "" : placeName.trim();
        String queryText = query == null ? keyword : query.trim();
        if (keyword.isBlank())
        {
            throw new IllegalArgumentException("地名不能为空");
        }
        if (queryText.isBlank())
        {
            queryText = keyword;
        }

        Map<String, Object> result = new HashMap<>();
        List<String> recycled = cleanupIncompleteMatchedArtifacts(selectedPlaceId, selectedOsmType, selectedOsmId);
        List<Map<String, Object>> fuzzyMatches = searchLocalPlaceMatches(keyword);
        boolean fuzzyExists = !fuzzyMatches.isEmpty();
        boolean exactDuplicate = isExactOsmDuplicate(selectedPlaceId, selectedOsmType, selectedOsmId);

        result.put("placeName", keyword);
        result.put("query", queryText);
        result.put("exists", exactDuplicate);
        result.put("exactDuplicate", exactDuplicate);
        result.put("selectedPlaceId", safeTrim(selectedPlaceId));
        result.put("selectedOsmType", safeTrim(selectedOsmType));
        result.put("selectedOsmId", safeTrim(selectedOsmId));
        result.put("fuzzyLocalMatchesCount", fuzzyMatches.size());
        result.put("fuzzyExists", fuzzyExists);
        result.put("recycledIncompletePaths", recycled);
        result.put("force", force);
        result.put("buildFrontend", buildFrontend);

        if (exactDuplicate && !force)
        {
            result.put("status", "skipped");
            result.put("message", "已存在相同 OSM 实体数据，跳过采集。可使用 force=true 强制重抓。");
            return result;
        }

        String pythonCmd = isWindows() ? "python" : "python3";
        List<String> seedCmd = List.of(
            pythonCmd,
            "scripts/osm_seed.py",
            "--skip-config",
            "--target-name", keyword,
            "--query", queryText,
            "--output-dir", "src/main/resources/osm-data",
            "--run-name", "latest",
            "--map-imports", "src/main/resources/dev-seed/map-imports.json"
        );
        String projectRoot = resolveProjectRoot();
        ExecResult seedRes = exec(seedCmd, projectRoot, 900);
        result.put("seedExitCode", seedRes.exitCode());
        result.put("seedOutput", seedRes.output());
        if (seedRes.exitCode() != 0)
        {
            result.put("recycledIncompletePaths", cleanupIncompleteMatchedArtifacts(selectedPlaceId, selectedOsmType, selectedOsmId));
            result.put("status", "seed_failed");
            result.put("message", "采集脚本执行失败");
            return result;
        }

        if (buildFrontend)
        {
            String npmCmd = isWindows() ? "npm.cmd" : "npm";
            ExecResult buildRes = exec(List.of(npmCmd, "run", "build"), projectRoot + File.separator + "frontend", 900);
            result.put("buildExitCode", buildRes.exitCode());
            result.put("buildOutput", buildRes.output());
            if (buildRes.exitCode() != 0)
            {
                result.put("status", "build_failed");
                result.put("message", "前端构建失败");
                return result;
            }

            result.put("status", "success");
            result.put("message", "采集与前端构建完成");
            return result;
        }

        result.put("status", "success");
        result.put("message", "采集完成（已跳过前端构建）");
        return result;
    }

    private boolean isExactOsmDuplicate(String placeId, String osmType, String osmId)
    {
        String place = safeTrim(placeId);
        String type = safeTrim(osmType);
        String id = safeTrim(osmId);
        if ((place == null || place.isBlank()) && (type == null || type.isBlank() || id == null || id.isBlank()))
        {
            return false;
        }

        try
        {
            Path resourcesRoot = Path.of(resolveProjectRoot(), "src", "main", "resources", "osm-data");
            if (!Files.exists(resourcesRoot))
            {
                return false;
            }

            return findMatchedScenicRoots(resourcesRoot, place, type, id)
                .stream()
                .anyMatch(this::hasValidLatestData);
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    private List<String> cleanupIncompleteMatchedArtifacts(String placeId, String osmType, String osmId)
    {
        String place = safeTrim(placeId);
        String type = safeTrim(osmType);
        String id = safeTrim(osmId);
        if ((place == null || place.isBlank()) && (type == null || type.isBlank() || id == null || id.isBlank()))
        {
            return List.of();
        }

        try
        {
            Path resourcesRoot = Path.of(resolveProjectRoot(), "src", "main", "resources", "osm-data");
            if (!Files.exists(resourcesRoot))
            {
                return List.of();
            }

            List<String> removed = new ArrayList<>();
            for (Path root : findMatchedScenicRoots(resourcesRoot, place, type, id))
            {
                if (hasValidLatestData(root))
                {
                    continue;
                }
                deleteDirectory(root);
                removed.add(root.toString());
            }
            return removed;
        }
        catch (Exception ex)
        {
            return List.of();
        }
    }

    private Set<Path> findMatchedScenicRoots(Path resourcesRoot, String placeId, String osmType, String osmId) throws Exception
    {
        try (var paths = Files.walk(resourcesRoot))
        {
            Set<Path> roots = new HashSet<>();
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals("_context.json") || p.toString().endsWith("latest/raw/nominatim_top.json"))
                .forEach(p ->
                {
                    if (!matchesOsmIdentity(p, placeId, osmType, osmId))
                    {
                        return;
                    }
                    Path root = resolveScenicRootFromContextPath(p);
                    if (root != null)
                    {
                        roots.add(root);
                    }
                });
            return roots;
        }
    }

    private Path resolveScenicRootFromContextPath(Path contextPath)
    {
        String name = contextPath.getFileName().toString();
        if ("_context.json".equals(name))
        {
            return contextPath.getParent();
        }
        if (contextPath.toString().endsWith("latest" + File.separator + "raw" + File.separator + "nominatim_top.json"))
        {
            Path raw = contextPath.getParent();
            if (raw == null)
            {
                return null;
            }
            Path latest = raw.getParent();
            if (latest == null)
            {
                return null;
            }
            return latest.getParent();
        }
        return null;
    }

    private boolean hasValidLatestData(Path scenicRoot)
    {
        if (scenicRoot == null)
        {
            return false;
        }
        Path latest = scenicRoot.resolve("latest");
        Path scenic = latest.resolve("scenic_areas.append.json");
        Path pois = latest.resolve("pois.append.json");
        Path roads = latest.resolve("roads.append.json");
        return Files.exists(scenic)
            && Files.exists(pois)
            && Files.exists(roads)
            && fileSizeGreaterThan(scenic, 2)
            && fileSizeGreaterThan(pois, 2)
            && fileSizeGreaterThan(roads, 2);
    }

    private boolean fileSizeGreaterThan(Path file, long size)
    {
        try
        {
            return Files.size(file) > size;
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    private void deleteDirectory(Path dir) throws Exception
    {
        if (dir == null || !Files.exists(dir))
        {
            return;
        }
        try (var walk = Files.walk(dir))
        {
            walk.sorted(Comparator.reverseOrder()).forEach(path ->
            {
                try
                {
                    Files.deleteIfExists(path);
                }
                catch (Exception ignored)
                {
                }
            });
        }
    }

    private boolean matchesOsmIdentity(Path filePath, String placeId, String osmType, String osmId)
    {
        try
        {
            Map<String, Object> raw = objectMapper.readValue(filePath.toFile(), new TypeReference<Map<String, Object>>()
            {
            });
            String currentPlaceId = safeTrim(raw.get("place_id"));
            String currentOsmType = safeTrim(raw.get("osm_type"));
            String currentOsmId = safeTrim(raw.get("osm_id"));

            if (placeId != null && !placeId.isBlank() && placeId.equals(currentPlaceId))
            {
                return true;
            }
            return osmType != null && !osmType.isBlank() && osmId != null && !osmId.isBlank()
                && osmType.equalsIgnoreCase(currentOsmType)
                && osmId.equals(currentOsmId);
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    private String safeTrim(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private List<String> buildQueryVariants(String keyword)
    {
        String k = keyword.trim();
        List<String> variants = new ArrayList<>();
        variants.add(k);

        String noParen = k.replaceAll("[（(].*?[）)]", "").trim();
        if (!noParen.isBlank() && !noParen.equalsIgnoreCase(k))
        {
            variants.add(noParen);
        }

        if (!k.contains("校区"))
        {
            variants.add(k + " 校区");
        }

        return variants;
    }

    private boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String resolveProjectRoot()
    {
        File current = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        File cursor = current;
        while (cursor != null)
        {
            File pom = new File(cursor, "pom.xml");
            File frontend = new File(cursor, "frontend");
            if (pom.exists() && frontend.exists())
            {
                return cursor.getAbsolutePath();
            }
            cursor = cursor.getParentFile();
        }
        return current.getAbsolutePath();
    }

    private ExecResult exec(List<String> command, String workingDir, long timeoutSec)
    {
        try
        {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null && !workingDir.isBlank())
            {
                pb.directory(new java.io.File(workingDir));
            }
            Map<String, String> env = pb.environment();
            env.put("PYTHONIOENCODING", "utf-8");
            env.put("PYTHONUTF8", "1");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (out.length() < 12000)
                    {
                        out.append(line).append('\n');
                    }
                }
            }

            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished)
            {
                process.destroyForcibly();
                return new ExecResult(-2, "process timeout");
            }
            return new ExecResult(process.exitValue(), out.toString());
        }
        catch (Exception ex)
        {
            return new ExecResult(-1, ex.getMessage());
        }
    }

    private record ExecResult(int exitCode, String output)
    {
    }
}

