package com.travel.indoor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Indoor900020327CompletenessTest
{

    @Test
    void auditoriumBundleShouldPassCompleteness() throws Exception
    {
        IndoorBuildingBundle bundle;
        try (InputStream in = new ClassPathResource(
            "osm-data/北京邮电大学-沙河校区-南丰路-沙河镇-昌平区-北京市-102206-中国/latest/indoor/900020599.json")
            .getInputStream())
        {
            bundle = new ObjectMapper().readValue(in, IndoorBuildingBundle.class);
        }
        IndoorSeedCompleteness.Result check = IndoorSeedCompleteness.evaluate(bundle);
        assertTrue(check.pass(), () -> "failures=" + check.failureCodes());
    }
}
