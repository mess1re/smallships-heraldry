package me.mss1r.smallshipsheraldry.registry;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.world.inventory.HeraldryTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class HeraldryMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(SmallShipsHeraldry.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<MenuType<HeraldryTableMenu>> HERALDRY_TABLE = MENUS.register(
            "heraldry_table",
            () -> MenuRegistry.ofExtended(HeraldryTableMenu::fromNetwork)
    );

    private HeraldryMenus() {
    }

    public static void register() {
        MENUS.register();
    }
}
