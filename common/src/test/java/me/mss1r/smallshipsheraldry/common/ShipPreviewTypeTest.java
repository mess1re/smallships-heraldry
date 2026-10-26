package me.mss1r.smallshipsheraldry.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        assertEquals(expectedNames, List.of(ShipPreviewType.values()).stream()
                .map(ShipPreviewType::serializedName)
                .toList());
        for (ShipPreviewType shipType : ShipPreviewType.values()) {
            assertEquals(shipType, ShipPreviewType.byName(shipType.serializedName()));
        }
    }
}
