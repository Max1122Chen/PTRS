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
 * 验收 dev-seed 演示建筑（图书馆 POI 502）的完整度与路径规划。
 */
class IndoorDevSeedBundleTest
{

    @Test
    void library502SeedShouldPassCompletenessAndPlanEntranceToFarRoom() throws Exception
    {
        IndoorBuildingBundle bundle;
        try (InputStream in = new ClassPathResource("dev-seed/indoor/502.json").getInputStream())
        {
            bundle = new ObjectMapper().readValue(in, IndoorBuildingBundle.class);
        }

        IndoorSeedCompleteness.Result check = IndoorSeedCompleteness.evaluate(bundle);
        assertTrue(check.pass(), () -> "failures=" + check.failureCodes());

        InMemoryStore store = new InMemoryStore();
        store.putIndoorBundle(bundle);

        IndoorGraphRegistry registry = new IndoorGraphRegistry(new IndoorProperties());
        registry.reloadFromStore(store);

        IndoorBuildingGraph graph = registry.find(502L).orElseThrow();
        IndoorPathResult result = new IndoorPathPlanner().plan(graph, 9001L, 9004L);

        assertEquals(java.util.List.of(9001L, 9002L, 9003L, 9004L), result.getPath());
        assertEquals(35.96, result.getDistanceMeters(), 0.05);
    }
}
