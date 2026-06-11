package com.travel.storage.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIndexTest
{

    @Test
    void prefixTrieShouldMatchPrefix()
    {
        PrefixTrieIdIndex index = new PrefixTrieIdIndex();
        index.insert("图书馆", 11L);
        index.insert("食堂", 22L);

        assertEquals(1, index.search("图").size());
        assertEquals(11L, index.search("图").get(0));
        assertTrue(index.search("xyz").isEmpty());
    }

    @Test
    void nGramIndexShouldRankByOverlap()
    {
        NGramInvertedIndex index = new NGramInvertedIndex();
        index.insert("麻辣火锅", 1L);
        index.insert("清汤面", 2L);

        assertEquals(1L, index.search("麻辣", 5).get(0));
    }
}
