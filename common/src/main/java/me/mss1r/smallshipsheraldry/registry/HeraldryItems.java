package me.mss1r.smallshipsheraldry.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.world.item.HeraldryPatternItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class HeraldryItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(SmallShipsHeraldry.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> HERALDRY_TABLE = ITEMS.register(
            "heraldry_table",
            () -> new BlockItem(HeraldryBlocks.HERALDRY_TABLE.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Item> HERALDRY_PATTERN = ITEMS.register(
            "heraldry_pattern",
            () -> new HeraldryPatternItem(new Item.Properties())
    );

    private HeraldryItems() {
    }

    @SuppressWarnings("unchecked") // Architectury's creative-tab supplier API exposes a generic varargs signature.
    public static void register() {
        ITEMS.register();
        CreativeTabRegistry.append(CreativeModeTabs.FUNCTIONAL_BLOCKS, HERALDRY_TABLE);
        CreativeTabRegistry.append(CreativeModeTabs.INGREDIENTS, HERALDRY_PATTERN);
    }
}
