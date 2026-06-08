package com.travel.indoor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.config.IndoorProperties;
import com.travel.storage.InMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收沙河 osm-data 图书馆室内 bundle 的完整度与路径规划。
 */
class IndoorDevSeedBundleTest
{

    private static final String SHAHE_LIBRARY_INDOOR =
        "osm-data/北京邮电大学-沙河校区-鸿雁路-沙河镇-昌平区-北京市-102206-中国/latest/indoor/900022224.json";

    @Test
    void shaheLibraryBundleShouldPassCompletenessAndPlanPath() throws Exception
    {
        IndoorBuildingBundle bundle;
        try (InputStream in = new ClassPathResource(SHAHE_LIBRARY_INDOOR).getInputStream())
        {
            bundle = new ObjectMapper().readValue(in, IndoorBuildingBundle.class);
        }

        IndoorSeedCompleteness.Result check = IndoorSeedCompleteness.evaluate(bundle);
        assertTrue(check.pass(), () -> "failures=" + check.failureCodes());

        InMemoryStore store = new InMemoryStore();
        store.putIndoorBundle(bundle);

        IndoorGraphRegistry registry = new IndoorGraphRegistry(new IndoorProperties());
        registry.reloadFromStore(store);

        long buildingPoiId = bundle.getBuildingPoiId();
        IndoorBuildingGraph graph = registry.find(buildingPoiId).orElseThrow();

        long entrance = bundle.getEntranceNodeId();
        long targetRoom = bundle.getNodes().stream()
            .filter(n -> "room".equalsIgnoreCase(n.getNodeKind()))
            .mapToLong(IndoorNodeRecord::getId)
            .filter(id -> id != entrance)
            .findFirst()
            .orElseThrow();

        IndoorPathResult result = new IndoorPathPlanner().plan(graph, entrance, targetRoom);

        assertTrue(result.getPath().size() >= 2);
        assertEquals(entrance, result.getPath().get(0));
        assertEquals(targetRoom, result.getPath().get(result.getPath().size() - 1));
        assertTrue(result.getDistanceMeters() > 0.0);
    }
}
