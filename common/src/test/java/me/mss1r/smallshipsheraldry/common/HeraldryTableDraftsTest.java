package me.mss1r.smallshipsheraldry.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeraldryTableDraftsTest {
    @Test
    void keepsIndependentDraftForEveryShipType() {
        int[] cogPixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
        cogPixels[12] = 0xFFAA2200;
        int[] briggPixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
        briggPixels[34] = 0xFF0033BB;

        HeraldryPatternData cog = HeraldryPatternData.fromMeshPixels("one", ShipPreviewType.COG, 0xFFFFFFFF, cogPixels);
        HeraldryPatternData brigg = HeraldryPatternData.fromMeshPixels("two", ShipPreviewType.BRIGG, 0xFFEEEEEE, briggPixels);
        HeraldryTableDrafts drafts = HeraldryTableDrafts.blank().withDraft(cog).withDraft(brigg);

        assertEquals(0xFFAA2200, drafts.get(ShipPreviewType.COG).meshAtlasPixelAt(12, 0));
        assertEquals(0xFF0033BB, drafts.get(ShipPreviewType.BRIGG).meshAtlasPixelAt(34, 0));
        assertEquals(ShipPreviewType.BRIGG, drafts.activeShipType());
    }

    @Test
    void roundTripPreservesDraftsAndActiveShip() {
        int[] pixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
        pixels[HeraldryPatternData.MESH_ATLAS_WIDTH + 5] = 0xCC44AA66;
        HeraldryPatternData galley = HeraldryPatternData.fromMeshPixels("tester", ShipPreviewType.GALLEY, 0xFF112233, pixels);
        HeraldryTableDrafts original = HeraldryTableDrafts.blank()
                .withDraft(galley)
                .withActiveShipType(ShipPreviewType.DRAKKAR);

        HeraldryTableDrafts restored = HeraldryTableDrafts.fromTag(original.toTag());

        assertEquals(ShipPreviewType.DRAKKAR, restored.activeShipType());
        assertEquals(galley.visualHash(), restored.get(ShipPreviewType.GALLEY).visualHash());
        assertEquals(ShipPreviewType.DRAKKAR, restored.get(ShipPreviewType.DRAKKAR).shipType());
    }
}
