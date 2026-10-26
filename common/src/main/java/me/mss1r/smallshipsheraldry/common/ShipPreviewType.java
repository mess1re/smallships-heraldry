package me.mss1r.smallshipsheraldry.common;

import com.talhanation.smallships.world.entity.ship.BriggEntity;
import com.talhanation.smallships.world.entity.ship.CaravelEntity;
import com.talhanation.smallships.world.entity.ship.CogEntity;
import com.talhanation.smallships.world.entity.ship.DhowEntity;
import com.talhanation.smallships.world.entity.ship.DrakkarEntity;
import com.talhanation.smallships.world.entity.ship.GalleonEntity;
import com.talhanation.smallships.world.entity.ship.GalleyEntity;
import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.network.chat.Component;

public enum ShipPreviewType {
    COG("cog"),
    BRIGG("brigg"),
    GALLEY("galley"),
    DRAKKAR("drakkar"),
    CARAVEL("caravel"),
    DHOW("dhow"),
    GALLEON("galleon");

    private final String serializedName;

    ShipPreviewType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public Component getDisplayName() {
        return Component.translatable("smallshipsheraldry.preview." + serializedName);
    }

    public boolean matches(Ship ship) {
        return switch (this) {
            case COG -> ship instanceof CogEntity;
            case BRIGG -> ship instanceof BriggEntity;
            case GALLEY -> ship instanceof GalleyEntity;
            case DRAKKAR -> ship instanceof DrakkarEntity;
            case CARAVEL -> ship instanceof CaravelEntity;
            case DHOW -> ship instanceof DhowEntity;
            case GALLEON -> ship instanceof GalleonEntity;
        };
    }

    public ShipPreviewType next() {
        ShipPreviewType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public ShipPreviewType previous() {
        ShipPreviewType[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }

    public static ShipPreviewType byName(String serializedName) {
        for (ShipPreviewType previewType : values()) {
            if (previewType.serializedName.equals(serializedName)) {
                return previewType;
            }
        }
        return COG;
    }

    public static ShipPreviewType fromShip(Ship ship) {
        if (ship instanceof BriggEntity) {
            return BRIGG;
        }
        if (ship instanceof GalleyEntity) {
            return GALLEY;
        }
        if (ship instanceof DrakkarEntity) {
            return DRAKKAR;
        }
        if (ship instanceof CaravelEntity) {
            return CARAVEL;
        }
        if (ship instanceof DhowEntity) {
            return DHOW;
        }
        if (ship instanceof GalleonEntity) {
            return GALLEON;
        }
        return COG;
    }
}
