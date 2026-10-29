package me.mss1r.smallshipsheraldry.client.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TextureResidencyCacheTest {
    @Test
    void allThirteenShipsAndTheirDamagedVariantsFitTheProductionBudget() {
        AtomicInteger builds = new AtomicInteger();
        var cache = new TextureResidencyCache<Integer, Integer>(SailBannerTextureCache.MAX_RESIDENT_TEXTURES, 2,
                ignored -> fail("Visible fleet texture evicted"));
        for (int tick = 0; tick < 100; tick++) {
            cache.advanceTick();
            for (int key = 0; key < 26; key++) {
                int id = key;
                List<Integer> draws = new ArrayList<>();
                cache.use(key, () -> { builds.incrementAndGet(); return id; }, draws::add, () -> draws.add(id));
                assertEquals(List.of(id), draws, "Each ship must render even during cache warmup");
            }
        }
        assertEquals(26, builds.get());
    }

    @Test
    void nineVisiblePatternsDoNotRebuildEachOther() {
        AtomicInteger builds = new AtomicInteger();
        List<Integer> released = new ArrayList<>();
        var cache = new TextureResidencyCache<Integer, Integer>(20, 2, released::add);
        for (int tick = 0; tick < 100; tick++) {
            cache.advanceTick();
            for (int frame = 0; frame < 4; frame++) {
                for (int key = 0; key < 9; key++) {
                    int id = key;
                    cache.use(key, () -> { builds.incrementAndGet(); return id; }, ignored -> {}, () -> {});
                }
            }
        }
        assertEquals(9, builds.get());
        assertTrue(released.isEmpty());
    }

    @Test
    void everyPatternBeyondTheBudgetIsDrawnWithoutEvictingTheVisibleWorkingSet() {
        AtomicInteger builds = new AtomicInteger();
        AtomicInteger fallbacks = new AtomicInteger();
        List<Integer> released = new ArrayList<>();
        var cache = new TextureResidencyCache<Integer, Integer>(3, 2, released::add);
        for (int tick = 0; tick < 100; tick++) {
            cache.advanceTick();
            for (int frame = 0; frame < 4; frame++) {
                for (int index = 0; index < 60; index++) {
                    int id = Math.floorMod(index + tick + frame, 60);
                    List<Integer> draws = new ArrayList<>();
                    cache.use(id, () -> { builds.incrementAndGet(); return id; }, draws::add, () -> {
                        fallbacks.incrementAndGet();
                        draws.add(id);
                    });
                    assertEquals(List.of(id), draws);
                }
            }
        }
        assertEquals(3, builds.get());
        assertTrue(released.isEmpty());
        assertTrue(fallbacks.get() >= 100 * 4 * 57);
    }

    @Test
    void coldTexturesAreReleasedBeforeAnUploadButNotBeforeTheirLastDraw() {
        List<Integer> released = new ArrayList<>();
        List<Integer> draws = new ArrayList<>();
        var cache = new TextureResidencyCache<Integer, Integer>(1, 2, released::add);
        cache.use(1, () -> 10, draws::add, () -> fail("Empty cache"));
        cache.advanceTick();
        cache.use(2, () -> fail("Previous frame's texture evicted"), draws::add, () -> draws.add(20));
        assertTrue(released.isEmpty());
        cache.advanceTick();
        cache.use(2, () -> {
            assertEquals(List.of(10), released);
            return 20;
        }, draws::add, () -> fail("Cold entry should be reusable"));
        assertEquals(List.of(10, 20, 20), draws);
    }

    @Test
    void uploadBudgetUsesFallbackInsteadOfSkippingDraws() {
        var cache = new TextureResidencyCache<Integer, Integer>(20, 2, ignored -> {});
        List<Integer> draws = new ArrayList<>();
        cache.use(1, () -> 1, draws::add, () -> fail());
        cache.use(2, () -> 2, draws::add, () -> fail());
        cache.use(3, () -> fail("Upload budget exceeded"), draws::add, () -> draws.add(3));
        assertEquals(List.of(1, 2, 3), draws);
        cache.advanceTick();
        cache.use(3, () -> 3, draws::add, () -> fail("Budget did not reset"));
        assertEquals(List.of(1, 2, 3, 3), draws);
    }

    @Test
    void failuresAreCachedAndClearReleasesAllEntriesOnce() {
        AtomicInteger builds = new AtomicInteger();
        List<Integer> released = new ArrayList<>();
        var cache = new TextureResidencyCache<Integer, Integer>(3, 2, released::add);
        cache.use(1, () -> { builds.incrementAndGet(); return null; }, ignored -> fail(), () -> fail());
        for (int tick = 0; tick < 10; tick++) {
            cache.advanceTick();
            cache.use(1, () -> fail("Retried a failed resource"), ignored -> fail(), () -> fail());
        }
        assertEquals(1, builds.get());
        cache.use(2, () -> 20, ignored -> {}, () -> fail());
        cache.clear();
        assertEquals(2, released.size());
        assertTrue(released.contains(null));
        assertTrue(released.contains(20));
        cache.clear();
        assertEquals(2, released.size());
    }

    @Test
    void failedFallbackDoesNotPoisonLaterAdmission() {
        var cache = new TextureResidencyCache<Integer, Integer>(1, 1, ignored -> {});
        cache.use(1, () -> 1, ignored -> {}, () -> fail());
        assertThrows(IllegalStateException.class, () ->
                cache.use(2, () -> fail(), ignored -> fail(), () -> { throw new IllegalStateException(); }));
        cache.advanceTick();
        cache.advanceTick();
        List<Integer> draws = new ArrayList<>();
        cache.use(2, () -> 2, draws::add, () -> fail());
        assertEquals(List.of(2), draws);
    }
}
