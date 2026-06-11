package com.travel.algorithm;

import com.travel.ds.ArrayList;
import com.travel.ds.List;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopKSelectorTest
{

    @Test
    void selectTopKShouldReturnHighestScoresFirst()
    {
        ArrayList<Integer> items = new ArrayList<>();
        items.add(3);
        items.add(1);
        items.add(4);
        items.add(2);

        TopKSelector<Integer> selector = new TopKSelector<>();
        List<Integer> top2 = selector.selectTopK(items, 2, Comparator.naturalOrder());

        assertEquals(2, top2.size());
        assertEquals(4, top2.get(0));
        assertEquals(3, top2.get(1));
    }
}
