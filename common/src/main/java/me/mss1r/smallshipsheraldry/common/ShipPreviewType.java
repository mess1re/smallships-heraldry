package me.mss1r.smallshipsheraldry.common;

import com.talhanation.smallships.api.ShipRegistry;
import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.function.Predicate;

public enum ShipPreviewType {
    COG("cog"),
    BRIGG("brigg"),
    GALLEY("galley"),
    DRAKKAR("drakkar"),
    CARAVEL("caravel"),
    DHOW("dhow"),
    GALLEON("galleon"),
    FISHING_BOAT("smallships_addon", "fishing_boat"),
    EARLY_COG("smallships_addon", "early_cog"),
    EARLY_CARAVEL("smallships_addon", "early_caravel"),
    KNARR("smallships_viking_addon", "knarr"),
    LONGSHIP("smallships_viking_addon", "longship"),
    TRANSPORT_DRAKKAR("smallships_viking_addon", "transport_drakkar");

    private static final ShipPreviewType[] TYPES = values();
    private final String serializedName;
    private final ResourceLocation registryId;

    ShipPreviewType(String serializedName) {
        this("smallships", serializedName);
    }

    ShipPreviewType(String namespace, String serializedName) {
        this.serializedName = serializedName;
        this.registryId = ResourceLocation.fromNamespaceAndPath(namespace, serializedName);
    }

    public String serializedName() {
        return serializedName;
    }

    public ResourceLocation registryId() {
        return registryId;
    }

    public Component getDisplayName() {
        return Component.translatable("smallshipsheraldry.preview." + serializedName);
    }

    public boolean matches(Ship ship) {
        return ship != null && registryId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(ship.getType()));
    }

    public boolean isAvailable() {
        return ShipRegistry.get(registryId) != null;
    }

    public static ShipPreviewType[] availableTypes() {
        return Arrays.stream(TYPES).filter(ShipPreviewType::isAvailable).toArray(ShipPreviewType[]::new);
    }

    public ShipPreviewType next() {
        return cycle(1, ShipPreviewType::isAvailable);
    }

    public ShipPreviewType previous() {
        return cycle(-1, ShipPreviewType::isAvailable);
    }

    ShipPreviewType cycle(int direction, Predicate<ShipPreviewType> available) {
        for (int offset = 1; offset <= TYPES.length; offset++) {
            ShipPreviewType candidate = TYPES[Math.floorMod(ordinal() + direction * offset, TYPES.length)];
            if (available.test(candidate)) return candidate;
        }
        return COG;
    }

    public static ShipPreviewType byName(String serializedName) {
        for (ShipPreviewType previewType : TYPES) {
            if (previewType.serializedName.equals(serializedName)) {
                return previewType;
            }
        }
        return COG;
    }

    public static ShipPreviewType fromShip(Ship ship) {
        for (ShipPreviewType type : TYPES) {
            if (type.matches(ship)) {
                return type;
            }
        }
        return COG;
    }
}
