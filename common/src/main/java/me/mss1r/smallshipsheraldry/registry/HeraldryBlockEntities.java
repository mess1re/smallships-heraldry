package me.mss1r.smallshipsheraldry.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.world.level.block.entity.HeraldryTableBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class HeraldryBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(SmallShipsHeraldry.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<BlockEntityType<HeraldryTableBlockEntity>> HERALDRY_TABLE = BLOCK_ENTITY_TYPES.register(
            "heraldry_table",
            () -> BlockEntityType.Builder.of(HeraldryTableBlockEntity::new, HeraldryBlocks.HERALDRY_TABLE.get()).build(null)
    );

    private HeraldryBlockEntities() {
    }

    public static void register() {
        BLOCK_ENTITY_TYPES.register();
    }
}
