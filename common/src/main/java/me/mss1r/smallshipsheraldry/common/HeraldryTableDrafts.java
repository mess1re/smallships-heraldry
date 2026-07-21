package me.mss1r.smallshipsheraldry.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

public final class HeraldryTableDrafts {
    private static final String ACTIVE_SHIP_TAG = "ActiveShip";
    private static final String DRAFTS_TAG = "Drafts";

    private final ShipPreviewType activeShipType;
    private final Map<ShipPreviewType, HeraldryPatternData> drafts;

    private HeraldryTableDrafts(ShipPreviewType activeShipType, Map<ShipPreviewType, HeraldryPatternData> drafts) {
        this.activeShipType = activeShipType == null ? ShipPreviewType.COG : activeShipType;
        this.drafts = Map.copyOf(drafts);
    }

    public static HeraldryTableDrafts blank() {
        return new HeraldryTableDrafts(ShipPreviewType.COG, Map.of());
    }

    public ShipPreviewType activeShipType() {
        return activeShipType;
    }

    public HeraldryPatternData get(ShipPreviewType shipType) {
        ShipPreviewType resolvedType = shipType == null ? ShipPreviewType.COG : shipType;
        HeraldryPatternData draft = drafts.get(resolvedType);
        return draft == null ? HeraldryPatternData.blank().withShipType(resolvedType) : draft;
    }

    public HeraldryTableDrafts withDraft(HeraldryPatternData draft) {
        if (draft == null) {
            return this;
        }
        Map<ShipPreviewType, HeraldryPatternData> updated = new HashMap<>(drafts);
        updated.put(draft.shipType(), draft);
        return new HeraldryTableDrafts(draft.shipType(), updated);
    }

    public HeraldryTableDrafts withActiveShipType(ShipPreviewType shipType) {
        return new HeraldryTableDrafts(shipType, drafts);
    }

    public HeraldryTableDrafts withAuthor(String author) {
        Map<ShipPreviewType, HeraldryPatternData> updated = new HashMap<>();
        for (Map.Entry<ShipPreviewType, HeraldryPatternData> entry : drafts.entrySet()) {
            updated.put(entry.getKey(), entry.getValue().withAuthor(author));
        }
        return new HeraldryTableDrafts(activeShipType, updated);
    }

    public CompoundTag toTag() {
        CompoundTag root = new CompoundTag();
        root.putString(ACTIVE_SHIP_TAG, activeShipType.serializedName());
        CompoundTag draftsTag = new CompoundTag();
        for (Map.Entry<ShipPreviewType, HeraldryPatternData> entry : drafts.entrySet()) {
            draftsTag.put(entry.getKey().serializedName(), entry.getValue().toTag());
        }
        root.put(DRAFTS_TAG, draftsTag);
        return root;
    }

    public static HeraldryTableDrafts fromTag(CompoundTag root) {
        if (root == null) {
            return blank();
        }

        ShipPreviewType activeType = ShipPreviewType.byName(root.getString(ACTIVE_SHIP_TAG));
        Map<ShipPreviewType, HeraldryPatternData> drafts = new HashMap<>();
        if (root.contains(DRAFTS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag draftsTag = root.getCompound(DRAFTS_TAG);
            for (ShipPreviewType shipType : ShipPreviewType.values()) {
                if (!draftsTag.contains(shipType.serializedName(), Tag.TAG_COMPOUND)) {
                    continue;
                }
                HeraldryPatternData.tryFromRootTag(draftsTag.getCompound(shipType.serializedName()))
                        .filter(data -> data.shipType() == shipType)
                        .ifPresent(data -> drafts.put(shipType, data));
            }
        }
        return new HeraldryTableDrafts(activeType, drafts);
    }
}
