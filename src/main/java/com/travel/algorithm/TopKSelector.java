package com.travel.algorithm;

import com.travel.ds.ArrayList;
import com.travel.ds.Collections;
import com.travel.ds.List;
import com.travel.ds.PriorityQueue;

import java.util.Comparator;

/**
 * Top-K 选择器。
 *
 * <p>
 * 用于在不进行完全排序的情况下，从候选集中选出分数最高的前 K 个元素。
 * </p>
 */
public class TopKSelector<T>
{

    /**
     * 选出 Top-K。
     *
     * @param items      候选列表
     * @param k          K 值
     * @param comparator 比较器（分数越大越靠前）
     * @return Top-K（按 comparator 从高到低排序后的结果）
     */
    public List<T> selectTopK(Iterable<T> items, int k, Comparator<T> comparator)
    {
        if (items == null || k <= 0)
        {
            return Collections.emptyList();
        }

        int total = 0;
        for (T ignored : items)
        {
            total++;
        }
        if (total == 0)
        {
            return Collections.emptyList();
        }
        int kk = Math.min(k, total);

        PriorityQueue<T> pq = new PriorityQueue<>(comparator);
        for (T item : items)
        {
            if (pq.size() < kk)
            {
                pq.offer(item);
            }
            else if (comparator.compare(item, pq.peek()) > 0)
            {
                pq.poll();
                pq.offer(item);
            }
        }

        List<T> result = new ArrayList<>(pq.size());
        while (!pq.isEmpty())
        {
            result.add(pq.poll());
        }

        Collections.sort(result, comparator.reversed());
        return result;
    }
}
