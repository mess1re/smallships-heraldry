package me.mss1r.smallshipsheraldry.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.world.item.crafting.HeraldryPatternDuplicationRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

public final class HeraldryRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(SmallShipsHeraldry.MOD_ID, Registries.RECIPE_SERIALIZER);

    public static final RegistrySupplier<RecipeSerializer<?>> PATTERN_DUPLICATION = RECIPE_SERIALIZERS.register(
            "heraldry_pattern_duplication",
            () -> new SimpleCraftingRecipeSerializer<>(HeraldryPatternDuplicationRecipe::new)
    );

    private HeraldryRecipes() {
    }

    public static void register() {
        RECIPE_SERIALIZERS.register();
    }
}
