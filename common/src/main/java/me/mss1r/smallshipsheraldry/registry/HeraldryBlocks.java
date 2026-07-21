package me.mss1r.smallshipsheraldry.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.world.level.block.HeraldryTableBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class HeraldryBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(SmallShipsHeraldry.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> HERALDRY_TABLE = BLOCKS.register(
            "heraldry_table",
            () -> new HeraldryTableBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion())
    );

    private HeraldryBlocks() {
    }

    public static void register() {
        BLOCKS.register();
    }
}
