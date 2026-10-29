package me.mss1r.smallshipsheraldry.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ShipPreviewTypeTest {
    @Test
    void stableSmallShipsTypesRoundTripBySerializedName() {
        List<String> expectedNames = List.of(
                "cog",
                "brigg",
                "galley",
                "drakkar",
                "caravel",
                "dhow",
                "galleon"
        );

        assertEquals(expectedNames, List.of(ShipPreviewType.values()).subList(0, 7).stream()
                .map(ShipPreviewType::serializedName)
                .toList());
        for (ShipPreviewType shipType : ShipPreviewType.values()) {
            assertEquals(shipType, ShipPreviewType.byName(shipType.serializedName()));
        }
    }

    @Test
    void upstreamRegistryIdsKeepTheExistingSavedNames() {
        for (ShipPreviewType type : List.of(ShipPreviewType.values()).subList(0, 7)) {
            assertEquals("smallships:" + type.serializedName(), type.registryId().toString());
        }
    }

    @Test
    void addonTypesKeepIndependentRegistryIdsAndSavedNames() {
        assertEquals("smallships_addon:early_cog", ShipPreviewType.EARLY_COG.registryId().toString());
        assertEquals("smallships_addon:early_caravel", ShipPreviewType.EARLY_CARAVEL.registryId().toString());
        assertEquals("smallships_addon:fishing_boat", ShipPreviewType.FISHING_BOAT.registryId().toString());
        assertEquals("smallships_viking_addon:knarr", ShipPreviewType.KNARR.registryId().toString());
        assertEquals("smallships_viking_addon:longship", ShipPreviewType.LONGSHIP.registryId().toString());
        assertEquals("smallships_viking_addon:transport_drakkar", ShipPreviewType.TRANSPORT_DRAKKAR.registryId().toString());
        assertFalse(List.of(ShipPreviewType.values()).stream().anyMatch(type -> type.serializedName().equals("rowing_boat")));
    }

    @Test
    void navigationSkipsMissingAddonsInBothDirections() {
        var base = List.of(ShipPreviewType.values()).subList(0, 7);
        assertEquals(ShipPreviewType.COG, ShipPreviewType.GALLEON.cycle(1, base::contains));
        assertEquals(ShipPreviewType.GALLEON, ShipPreviewType.COG.cycle(-1, base::contains));
        assertEquals(ShipPreviewType.KNARR, ShipPreviewType.GALLEON.cycle(1,
                type -> base.contains(type) || type == ShipPreviewType.KNARR));
        assertEquals(ShipPreviewType.KNARR, ShipPreviewType.COG.cycle(-1,
                type -> base.contains(type) || type == ShipPreviewType.KNARR));
    }
}
