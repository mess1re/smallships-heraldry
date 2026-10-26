package me.mss1r.smallshipsheraldry.client.render;

import com.talhanation.smallships.world.entity.ship.BriggEntity;
import com.talhanation.smallships.world.entity.ship.CaravelEntity;
import com.talhanation.smallships.world.entity.ship.CogEntity;
import com.talhanation.smallships.world.entity.ship.DhowEntity;
import com.talhanation.smallships.world.entity.ship.DrakkarEntity;
import com.talhanation.smallships.world.entity.ship.GalleonEntity;
import com.talhanation.smallships.world.entity.ship.GalleyEntity;
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

        Ship ship = switch (previewType) {
            // Each helper deliberately returns Ship. Architectury Transformer 5
            // otherwise loads the concrete Small Ships classes to merge stack
            // frames before the loader has applied Small Ships' access widener.
            case COG -> createCog(level);
            case BRIGG -> createBrigg(level);
            case GALLEY -> createGalley(level);
            case DRAKKAR -> createDrakkar(level);
            case CARAVEL -> createCaravel(level);
            case DHOW -> createDhow(level);
            case GALLEON -> createGalleon(level);
        };

        setSailState(ship, sailState);
        ship.setData(Ship.SAIL_COLOR, HeraldryColorUtil.nearestDyeName(data.baseColorArgb()));
        applyPattern(ship, data);
        Entity entity = ship;
        entity.setPos(0.0D, 0.0D, 0.0D);
        entity.setYRot(180.0F);
        entity.yRotO = entity.getYRot();
        entity.tickCount = 1;
        return ship;
    }

    private static Ship createCog(ClientLevel level) {
        return CogEntity.summon(level, 0.0D, 0.0D, 0.0D);
    }

    private static Ship createBrigg(ClientLevel level) {
        return BriggEntity.summon(level, 0.0D, 0.0D, 0.0D);
    }

    private static Ship createGalley(ClientLevel level) {
        return GalleyEntity.summon(level, 0.0D, 0.0D, 0.0D);
    }

    private static Ship createDrakkar(ClientLevel level) {
        return DrakkarEntity.summon(level, 0.0D, 0.0D, 0.0D);
    }

    private static Ship createCaravel(ClientLevel level) {
        return CaravelEntity.summon(level, 0.0D, 0.0D, 0.0D);
    }

    private static Ship createDhow(ClientLevel level) {
        return DhowEntity.summon(level, 0.0D, 0.0D, 0.0D);
    }

    private static Ship createGalleon(ClientLevel level) {
        return GalleonEntity.summon(level, 0.0D, 0.0D, 0.0D);
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
