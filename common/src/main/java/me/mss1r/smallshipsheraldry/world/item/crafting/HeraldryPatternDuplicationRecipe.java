package me.mss1r.smallshipsheraldry.world.item.crafting;

import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.registry.HeraldryItems;
import me.mss1r.smallshipsheraldry.registry.HeraldryRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Copies a printed pattern while preserving its compact heraldry data. */
public final class HeraldryPatternDuplicationRecipe extends CustomRecipe {
    public HeraldryPatternDuplicationRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput container, Level level) {
        if (container.width() != 3 || container.height() != 3) {
            return false;
        }

        for (int slot = 0; slot < container.size(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (slot == 4) {
                if (!stack.is(HeraldryItems.HERALDRY_PATTERN.get())
                        || HeraldryPatternData.tryFromStack(stack).isEmpty()) {
                    return false;
                }
            } else if (!stack.is(Items.PAPER)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput container, HolderLookup.Provider registries) {
        ItemStack source = container.getItem(4);
        if (!source.is(HeraldryItems.HERALDRY_PATTERN.get())
                || HeraldryPatternData.tryFromStack(source).isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copies = source.copy();
        copies.setCount(2);
        return copies;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(HeraldryItems.HERALDRY_PATTERN.get(), 2);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HeraldryRecipes.PATTERN_DUPLICATION.get();
    }
}
