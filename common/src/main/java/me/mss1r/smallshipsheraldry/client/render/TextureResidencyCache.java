package me.mss1r.smallshipsheraldry.client.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded residency; requests that cannot be admitted are drawn through the caller's fallback. */
final class TextureResidencyCache<K, V> {
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final int capacity;
    private final int uploadsPerTick;
    private final Consumer<V> release;
    private long tick;
    private int uploads;

    TextureResidencyCache(int capacity, int uploadsPerTick, Consumer<V> release) {
        if (capacity < 1 || uploadsPerTick < 1) throw new IllegalArgumentException("Invalid texture cache limits");
        this.capacity = capacity;
        this.uploadsPerTick = uploadsPerTick;
        this.release = release;
    }

    void use(K key, Supplier<V> create, Consumer<V> draw, Runnable fallback) {
        Entry<V> cached = entries.get(key);
        if (cached != null) {
            cached.lastUse = tick;
            if (cached.value != null) draw.accept(cached.value);
            return;
        }
        if (uploads >= uploadsPerTick) {
            fallback.run();
            return;
        }
        if (entries.size() >= capacity) {
            var iterator = entries.entrySet().iterator();
            var oldest = iterator.next().getValue();
            // Protect both this tick and the previous one, regardless of entity render order.
            if (tick - oldest.lastUse <= 1) {
                fallback.run();
                return;
            }
            iterator.remove();
            release.accept(oldest.value);
        }
        uploads++;
        V value = create.get();
        entries.put(key, new Entry<>(value, tick));
        if (value != null) draw.accept(value);
    }

    void advanceTick() {
        tick++;
        uploads = 0;
    }

    void clear() {
        entries.values().forEach(entry -> release.accept(entry.value));
        entries.clear();
        uploads = 0;
    }

    private static final class Entry<V> {
        private final V value;
        private long lastUse;

        private Entry(V value, long tick) {
            this.value = value;
            lastUse = tick;
        }
    }
}
