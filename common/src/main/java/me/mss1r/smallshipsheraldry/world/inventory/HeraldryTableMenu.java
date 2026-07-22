package me.mss1r.smallshipsheraldry.world.inventory;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.HeraldryTableDrafts;
import me.mss1r.smallshipsheraldry.network.HeraldryPackets;
import me.mss1r.smallshipsheraldry.registry.HeraldryMenus;
import me.mss1r.smallshipsheraldry.world.level.block.entity.HeraldryTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class HeraldryTableMenu extends AbstractContainerMenu {
    public static final int PAPER_SLOT_X = 382;
    public static final int PAPER_SLOT_Y = 206;
    public static final int RESULT_SLOT_X = 440;
    public static final int RESULT_SLOT_Y = 206;
    public static final int SLOT_ACTION_INSERT = 0;
    public static final int SLOT_ACTION_EXTRACT = 1;
    public static final int SLOT_ACTION_INSERT_ALL = 2;
    public static final int SLOT_ACTION_EXTRACT_ONE = 3;

    private final HeraldryTableBlockEntity blockEntity;
    private final Level level;
    private final BlockPos blockPos;
    private final HeraldryTableDrafts initialDrafts;

    public HeraldryTableMenu(int containerId, Inventory inventory, HeraldryTableBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity, blockEntity.getDrafts());
    }

    public HeraldryTableMenu(int containerId, Inventory inventory, HeraldryTableBlockEntity blockEntity, HeraldryTableDrafts initialDrafts) {
        super(HeraldryMenus.HERALDRY_TABLE.get(), containerId);
        this.blockEntity = blockEntity;
        this.level = inventory.player.level();
        this.blockPos = blockEntity.getBlockPos();
        this.initialDrafts = initialDrafts == null ? HeraldryTableDrafts.blank() : initialDrafts;

        addSlot(new Slot(blockEntity, HeraldryTableBlockEntity.PAPER_SLOT, PAPER_SLOT_X, PAPER_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.PAPER);
            }
        });

        addSlot(new Slot(blockEntity, HeraldryTableBlockEntity.RESULT_SLOT, RESULT_SLOT_X, RESULT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
    }

    public static HeraldryTableMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        BlockPos blockPos = buf.readBlockPos();
        HeraldryTableDrafts initialDrafts = HeraldryTableDrafts.fromTag(buf.readNbt());
        Container container = inventory.player.level().getBlockEntity(blockPos) instanceof HeraldryTableBlockEntity blockEntity
                ? blockEntity
                : null;
        if (container instanceof HeraldryTableBlockEntity blockEntity) {
            return new HeraldryTableMenu(containerId, inventory, blockEntity, initialDrafts);
        }
        throw new IllegalStateException("Missing heraldry table block entity at " + blockPos);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public ItemStack getPaperStack() {
        return blockEntity.getItem(HeraldryTableBlockEntity.PAPER_SLOT);
    }

    public ItemStack getResultStack() {
        return blockEntity.getItem(HeraldryTableBlockEntity.RESULT_SLOT);
    }

    public HeraldryTableDrafts getInitialDrafts() {
        return initialDrafts;
    }

    public void handleExportRequest(ServerPlayer serverPlayer, BlockPos expectedBlockPos, CompoundTag patternTag) {
        if (!isValidRequest(serverPlayer, expectedBlockPos) || patternTag == null) {
            return;
        }

        HeraldryPatternData data = HeraldryPatternData.tryFromRootTag(patternTag).orElse(null);
        if (data != null && blockEntity.exportPattern(serverPlayer, data)) {
            serverPlayer.level().playSound(
                    null,
                    blockPos,
                    SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
            );
            broadcastChanges();
        }
    }

    public void handleDraftUpdate(ServerPlayer serverPlayer, BlockPos expectedBlockPos, CompoundTag patternTag) {
        if (!isValidRequest(serverPlayer, expectedBlockPos) || patternTag == null) {
            return;
        }

        HeraldryTableDrafts drafts = HeraldryTableDrafts.fromTag(patternTag)
                .withAuthor(serverPlayer.getGameProfile().getName());
        blockEntity.setDrafts(drafts);
    }

    public void handleDraftRequest(ServerPlayer serverPlayer, BlockPos expectedBlockPos) {
        if (!isValidRequest(serverPlayer, expectedBlockPos)) {
            return;
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(blockPos);
        buf.writeNbt(blockEntity.getDrafts().toTag());
        NetworkManager.sendToPlayer(serverPlayer, HeraldryPackets.TABLE_DRAFT_SYNC, buf);
    }

    public void handleSlotAction(ServerPlayer serverPlayer, BlockPos expectedBlockPos, int slot, int action) {
        if (!isValidRequest(serverPlayer, expectedBlockPos)) {
            return;
        }

        boolean changed = switch (action) {
            case SLOT_ACTION_INSERT -> insertOneFromInventory(serverPlayer, slot);
            case SLOT_ACTION_EXTRACT -> extractToInventory(serverPlayer, slot);
            case SLOT_ACTION_INSERT_ALL -> insertAllFromInventory(serverPlayer, slot);
            case SLOT_ACTION_EXTRACT_ONE -> extractOneToInventory(serverPlayer, slot);
            default -> false;
        };

        if (changed) {
            broadcastChanges();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }

    private boolean isValidRequest(ServerPlayer serverPlayer, BlockPos expectedBlockPos) {
        return expectedBlockPos.equals(blockPos)
                && serverPlayer.containerMenu == this
                && blockEntity.stillValid(serverPlayer);
    }

    private boolean insertOneFromInventory(ServerPlayer serverPlayer, int slot) {
        if (slot != HeraldryTableBlockEntity.PAPER_SLOT) {
            return false;
        }

        ItemStack current = blockEntity.getItem(slot);
        if (!current.isEmpty() && (!current.is(Items.PAPER) || current.getCount() >= current.getMaxStackSize())) {
            return false;
        }

        Inventory inventory = serverPlayer.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack candidate = inventory.getItem(index);
            if (!candidate.is(Items.PAPER)) {
                continue;
            }

            if (current.isEmpty()) {
                ItemStack inserted = candidate.copy();
                inserted.setCount(1);
                blockEntity.setItem(slot, inserted);
            } else {
                current.grow(1);
                blockEntity.setChanged();
            }

            candidate.shrink(1);
            if (candidate.isEmpty()) {
                inventory.setItem(index, ItemStack.EMPTY);
            }
            inventory.setChanged();
            return true;
        }

        return false;
    }

    private boolean extractToInventory(ServerPlayer serverPlayer, int slot) {
        if (slot != HeraldryTableBlockEntity.PAPER_SLOT && slot != HeraldryTableBlockEntity.RESULT_SLOT) {
            return false;
        }

        ItemStack stack = blockEntity.removeItemNoUpdate(slot);
        if (stack.isEmpty()) {
            return false;
        }

        if (!serverPlayer.getInventory().add(stack)) {
            serverPlayer.drop(stack, false);
        }
        serverPlayer.getInventory().setChanged();
        blockEntity.setChanged();
        return true;
    }

    private boolean insertAllFromInventory(ServerPlayer serverPlayer, int slot) {
        boolean changed = false;
        while (insertOneFromInventory(serverPlayer, slot)) {
            changed = true;
        }
        return changed;
    }

    private boolean extractOneToInventory(ServerPlayer serverPlayer, int slot) {
        if (slot != HeraldryTableBlockEntity.PAPER_SLOT) {
            return false;
        }
        ItemStack current = blockEntity.getItem(slot);
        if (current.isEmpty()) {
            return false;
        }
        ItemStack extracted = current.copy();
        extracted.setCount(1);
        current.shrink(1);
        if (current.isEmpty()) {
            blockEntity.setItem(slot, ItemStack.EMPTY);
        } else {
            blockEntity.setChanged();
        }
        if (!serverPlayer.getInventory().add(extracted)) {
            serverPlayer.drop(extracted, false);
        }
        serverPlayer.getInventory().setChanged();
        return true;
    }
}
