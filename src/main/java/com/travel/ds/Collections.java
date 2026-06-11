package com.travel.ds;

import java.util.Comparator;

public final class Collections {
    private static final ArrayList<Object> EMPTY_LIST = new ArrayList<>();

    private Collections() {
    }

    @SuppressWarnings("unchecked")
    public static <E> List<E> emptyList() {
        return (List<E>) EMPTY_LIST;
    }

    public static <K, V> Map<K, V> emptyMap() {
        return new HashMap<>();
    }

    public static <K, V> Map<K, V> unmodifiableMap(Map<K, V> source) {
        return new UnmodifiableMap<>(source);
    }

    public static <E> List<E> copyRange(List<E> list, int fromInclusive, int toExclusive) {
        ArrayList<E> out = new ArrayList<>(Math.max(0, toExclusive - fromInclusive));
        for (int i = fromInclusive; i < toExclusive; i++) {
            out.add(list.get(i));
        }
        return out;
    }

    private static final class UnmodifiableMap<K, V> implements Map<K, V> {
        private final Map<K, V> delegate;

        private UnmodifiableMap(Map<K, V> delegate) {
            this.delegate = delegate;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return delegate.containsKey(key);
        }

        @Override
        public V get(Object key) {
            return delegate.get(key);
        }

        @Override
        public V put(K key, V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V remove(Object key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<K> keySet() {
            return delegate.keySet();
        }

        @Override
        public Collection<V> values() {
            return delegate.values();
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return delegate.entrySet();
        }
    }

    public static <T extends Comparable<? super T>> void sort(List<T> list) {
        sort(list, Comparator.naturalOrder());
    }

    public static <T> void sort(List<T> list, Comparator<? super T> comparator) {
        // In-place insertion sort keeps implementation simple and dependency-free.
        for (int i = 1; i < list.size(); i++) {
            T value = list.get(i);
            int j = i - 1;
            while (j >= 0 && comparator.compare(list.get(j), value) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, value);
        }
    }

    public static void reverse(List<?> list) {
        int left = 0;
        int right = list.size() - 1;
        while (left < right) {
            swap(list, left, right);
            left++;
            right--;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void swap(List<?> list, int i, int j) {
        List raw = list;
        Object tmp = raw.get(i);
        raw.set(i, raw.get(j));
        raw.set(j, tmp);
    }
}
