package me.mss1r.smallshipsheraldry.access;

import net.minecraft.world.item.ItemStack;

public interface SailBannerAccess {
    ItemStack smallshipsheraldry$getSailBanner();

    void smallshipsheraldry$setSailBanner(ItemStack stack);

    String smallshipsheraldry$getOriginalSailColor();

    void smallshipsheraldry$setOriginalSailColor(String color);

    void smallshipsheraldry$applyPatternSailColor(String color);

    default boolean smallshipsheraldry$hasSailBanner() {
        return !smallshipsheraldry$getSailBanner().isEmpty();
    }

    default boolean smallshipsheraldry$hasOriginalSailColor() {
        return !smallshipsheraldry$getOriginalSailColor().isBlank();
    }
}
