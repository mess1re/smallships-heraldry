package me.mss1r.smallshipsheraldry.client.render;

import com.talhanation.smallships.api.ShipRegistry;
import com.talhanation.smallships.api.ShipType;
import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import me.mss1r.smallshipsheraldry.common.HeraldryColorUtil;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import me.mss1r.smallshipsheraldry.registry.HeraldryItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class PreviewShipFactory {
    private PreviewShipFactory() {
    }

    public static Ship create(ShipPreviewType previewType, HeraldryPatternData data) {
        return create(previewType, data, 4);
    }

    public static Ship create(ShipPreviewType previewType, HeraldryPatternData data, int sailState) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        ShipType registeredType = ShipRegistry.get(previewType.registryId());
        if (registeredType == null) {
            return null;
        }
        Ship ship = registeredType.summon(level, 0.0D, 0.0D, 0.0D);

        setSailState(ship, sailState);
        applyPattern(ship, data);
        Entity entity = ship;
        entity.setPos(0.0D, 0.0D, 0.0D);
        entity.setYRot(180.0F);
        entity.yRotO = entity.getYRot();
        entity.tickCount = 1;
        return ship;
    }

    public static void applyPattern(Ship ship, HeraldryPatternData data) {
        applyBaseColor(ship, data.baseColorArgb());
        ItemStack patternStack = new ItemStack(HeraldryItems.HERALDRY_PATTERN.get());
        data.writeToStack(patternStack);
        ((SailBannerAccess) ship).smallshipsheraldry$setSailBanner(patternStack);
    }

    public static void applyBaseColor(Ship ship, int baseColorArgb) {
        if (ship != null) {
            ship.setData(Ship.SAIL_COLOR, HeraldryColorUtil.nearestDyeName(baseColorArgb));
        }
    }

    public static void setSailState(Ship ship, int sailState) {
        if (ship != null) {
            ship.setData(Ship.SAIL_STATE, (byte) Math.max(0, Math.min(4, sailState)));
        }
    }
}
