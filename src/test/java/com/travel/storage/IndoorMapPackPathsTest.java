package com.travel.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IndoorMapPackPathsTest
{

    @Test
    void shouldDeriveIndoorGlobFromPoisAppendPath()
    {
        String pois = "classpath:osm-data/campus/latest/pois.append.json";
        String glob = IndoorMapPackPaths.indoorGlobFromMapAsset(pois);
        assertNotNull(glob);
        assertEquals("classpath:osm-data/campus/latest/indoor/*.json", glob);
    }
}
