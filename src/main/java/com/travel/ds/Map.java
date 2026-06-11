package com.travel.ds;

public interface Map<K, V> {
    interface Entry<K, V> {
        K getKey();

        V getValue();

        V setValue(V value);
    }

    int size();

    boolean isEmpty();

    boolean containsKey(Object key);

    V get(Object key);

    V put(K key, V value);

    V remove(Object key);

    void clear();

    Set<K> keySet();

    Collection<V> values();

    Set<Entry<K, V>> entrySet();

    default V getOrDefault(Object key, V defaultValue) {
        V value = get(key);
        return value != null || containsKey(key) ? value : defaultValue;
    }

    default V computeIfAbsent(K key, java.util.function.Function<? super K, ? extends V> mappingFunction) {
        V value = get(key);
        if (value != null || containsKey(key)) {
            return value;
        }
        V newValue = mappingFunction.apply(key);
        if (newValue != null) {
            put(key, newValue);
        }
        return newValue;
    }
}
