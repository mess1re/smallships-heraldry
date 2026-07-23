package me.mss1r.smallshipsheraldry.world.level.block.entity;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.HeraldryTableDrafts;
import me.mss1r.smallshipsheraldry.registry.HeraldryBlockEntities;
import me.mss1r.smallshipsheraldry.registry.HeraldryItems;
import me.mss1r.smallshipsheraldry.registry.HeraldryMenus;
import me.mss1r.smallshipsheraldry.world.inventory.HeraldryTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HeraldryTableBlockEntity extends BlockEntity implements Container, Clearable, ExtendedMenuProvider {
    public static final int PAPER_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    private static final String DRAFT_TAG = "Draft";
    private static final String DRAFTS_TAG = "EditorDrafts";

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private HeraldryTableDrafts drafts = HeraldryTableDrafts.blank();

    public HeraldryTableBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(HeraldryBlockEntities.HERALDRY_TABLE.get(), blockPos, blockState);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : items) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack itemStack) {
        items.set(slot, itemStack);
        if (itemStack.getCount() > itemStack.getMaxStackSize()) {
            itemStack.setCount(itemStack.getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        return switch (slot) {
            case PAPER_SLOT -> stack.is(Items.PAPER);
            default -> false;
        };
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.smallshipsheraldry.heraldry_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, @NotNull Inventory inventory, @NotNull Player player) {
        return new HeraldryTableMenu(syncId, inventory, this);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(getBlockPos());
    }

    public boolean exportPattern(Player player, HeraldryPatternData data) {
        ItemStack paper = getItem(PAPER_SLOT);
        ItemStack result = getItem(RESULT_SLOT);
        if (!paper.is(Items.PAPER) || paper.isEmpty() || !result.isEmpty()) {
            return false;
        }

        ItemStack outputStack = new ItemStack(HeraldryItems.HERALDRY_PATTERN.get());
        HeraldryPatternData finalData = data.withAuthor(player.getGameProfile().getName());
        setDraftData(finalData);
        finalData.writeToStack(outputStack);
        paper.shrink(1);
        if (paper.isEmpty()) {
            setItem(PAPER_SLOT, ItemStack.EMPTY);
        } else {
            setChanged();
        }
        setItem(RESULT_SLOT, outputStack);
        return true;
    }

    public HeraldryPatternData getDraftData() {
        return drafts.get(drafts.activeShipType());
    }

    public HeraldryTableDrafts getDrafts() {
        return drafts;
    }

    public void setDraftData(HeraldryPatternData draftData) {
        this.drafts = drafts.withDraft(draftData);
        setChanged();
    }

    public void setDrafts(HeraldryTableDrafts drafts) {
        this.drafts = drafts == null ? HeraldryTableDrafts.blank() : drafts;
        setChanged();
    }

    public void dropContents() {
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            Containers.dropContents(level, worldPosition, this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.put(DRAFTS_TAG, drafts.toTag());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, items);
        if (tag.contains(DRAFTS_TAG, Tag.TAG_COMPOUND)) {
            drafts = HeraldryTableDrafts.fromTag(tag.getCompound(DRAFTS_TAG));
        } else if (tag.contains(DRAFT_TAG, Tag.TAG_COMPOUND)) {
            drafts = HeraldryTableDrafts.blank().withDraft(HeraldryPatternData.fromRootTag(tag.getCompound(DRAFT_TAG)));
        } else {
            drafts = HeraldryTableDrafts.blank();
        }
    }
}
