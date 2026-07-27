package me.mss1r.smallshipsheraldry.mixin;

import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Ship.class)
public abstract class ShipMixin implements SailBannerAccess {
    @Unique
    private static final String SMALLSHIPSHERALDRY$SAIL_BANNER_TAG = "HeraldrySailBanner";
    @Unique
    private static final String SMALLSHIPSHERALDRY$ORIGINAL_SAIL_COLOR_TAG = "HeraldryOriginalSailColor";

    @Unique
    private String smallshipsheraldry$originalSailColor = "";

    @Unique
    private static final EntityDataAccessor<ItemStack> SMALLSHIPSHERALDRY$SAIL_BANNER =
            SynchedEntityData.defineId(Ship.class, EntityDataSerializers.ITEM_STACK);

    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void smallshipsheraldry$defineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(SMALLSHIPSHERALDRY$SAIL_BANNER, ItemStack.EMPTY);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void smallshipsheraldry$readAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        Ship ship = (Ship) (Object) this;
        Entity entity = (Entity) (Object) this;
        if (tag.contains(SMALLSHIPSHERALDRY$SAIL_BANNER_TAG, Tag.TAG_COMPOUND)) {
            ship.setData(
                    SMALLSHIPSHERALDRY$SAIL_BANNER,
                    ItemStack.parseOptional(entity.registryAccess(), tag.getCompound(SMALLSHIPSHERALDRY$SAIL_BANNER_TAG))
            );
            smallshipsheraldry$originalSailColor = tag.getString(SMALLSHIPSHERALDRY$ORIGINAL_SAIL_COLOR_TAG);
        } else {
            ship.setData(SMALLSHIPSHERALDRY$SAIL_BANNER, ItemStack.EMPTY);
            smallshipsheraldry$originalSailColor = "";
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void smallshipsheraldry$addAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        Ship ship = (Ship) (Object) this;
        Entity entity = (Entity) (Object) this;
        ItemStack sailBanner = ship.getData(SMALLSHIPSHERALDRY$SAIL_BANNER);
        if (!sailBanner.isEmpty()) {
            tag.put(SMALLSHIPSHERALDRY$SAIL_BANNER_TAG, sailBanner.save(entity.registryAccess()));
            if (!smallshipsheraldry$originalSailColor.isBlank()) {
                tag.putString(SMALLSHIPSHERALDRY$ORIGINAL_SAIL_COLOR_TAG, smallshipsheraldry$originalSailColor);
            }
        }
    }

    @Override
    public ItemStack smallshipsheraldry$getSailBanner() {
        return ((Ship) (Object) this).getData(SMALLSHIPSHERALDRY$SAIL_BANNER);
    }

    @Override
    public void smallshipsheraldry$setSailBanner(ItemStack stack) {
        ((Ship) (Object) this).setData(SMALLSHIPSHERALDRY$SAIL_BANNER, stack);
    }

    @Override
    public String smallshipsheraldry$getOriginalSailColor() {
        return smallshipsheraldry$originalSailColor;
    }

    @Override
    public void smallshipsheraldry$setOriginalSailColor(String color) {
        smallshipsheraldry$originalSailColor = color == null ? "" : color;
    }
}
