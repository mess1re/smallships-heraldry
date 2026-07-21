package me.mss1r.smallshipsheraldry.common;

import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import me.mss1r.smallshipsheraldry.registry.HeraldryItems;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;

public final class SailBannerInteractionHandler {
    private SailBannerInteractionHandler() {
    }

    public static InteractionResult tryHandle(Ship ship, Player player, InteractionHand interactionHand) {
        if (ship.isLocked() || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        SailBannerAccess access = (SailBannerAccess) ship;
        ItemStack heldItem = player.getItemInHand(interactionHand);

        if (heldItem.is(HeraldryItems.HERALDRY_PATTERN.get())) {
            HeraldryPatternData patternData = HeraldryPatternData.tryFromStack(heldItem).orElse(null);
            if (patternData == null) {
                if (!ship.level().isClientSide()) {
                    player.displayClientMessage(Component.translatable("smallshipsheraldry.message.invalid_pattern"), true);
                }
                return InteractionResult.sidedSuccess(ship.level().isClientSide());
            }
            if (!patternData.shipType().matches(ship)) {
                if (!ship.level().isClientSide()) {
                    player.displayClientMessage(Component.translatable("smallshipsheraldry.message.wrong_ship_type", patternData.shipType().getDisplayName()), true);
                }
                return InteractionResult.sidedSuccess(ship.level().isClientSide());
            }

            ItemStack installedPattern = access.smallshipsheraldry$getSailBanner();
            if (ItemStack.isSameItemSameTags(installedPattern, heldItem)) {
                return InteractionResult.sidedSuccess(ship.level().isClientSide());
            }

            if (!ship.level().isClientSide()) {
                if (!access.smallshipsheraldry$hasSailBanner()) {
                    access.smallshipsheraldry$setOriginalSailColor(ship.getData(Ship.SAIL_COLOR));
                }

                ItemStack sailPattern = heldItem.copy();
                sailPattern.setCount(1);
                access.smallshipsheraldry$setSailBanner(sailPattern);
                ship.setData(Ship.SAIL_COLOR, HeraldryColorUtil.nearestDyeName(patternData.baseColorArgb()));

                if (!player.isCreative()) {
                    heldItem.shrink(1);
                }

                giveOrDrop(player, installedPattern);

                playClothSound(ship);
            }

            return InteractionResult.sidedSuccess(ship.level().isClientSide());
        }

        if (heldItem.getItem() instanceof ShearsItem && access.smallshipsheraldry$hasSailBanner()) {
            if (!ship.level().isClientSide()) {
                access.smallshipsheraldry$setSailBanner(ItemStack.EMPTY);
                if (access.smallshipsheraldry$hasOriginalSailColor()) {
                    ship.setData(Ship.SAIL_COLOR, access.smallshipsheraldry$getOriginalSailColor());
                }
                access.smallshipsheraldry$setOriginalSailColor("");
                if (!player.isCreative()) {
                    heldItem.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(interactionHand));
                }
                playClothSound(ship);
            }

            return InteractionResult.sidedSuccess(ship.level().isClientSide());
        }

        return InteractionResult.PASS;
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        stack.setCount(1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void playClothSound(Ship ship) {
        ship.level().playSound(
                null,
                ship.getX(),
                ship.getY() + 1.0D,
                ship.getZ(),
                SoundEvents.WOOL_HIT,
                ship.getSoundSource(),
                1.0F,
                1.0F
        );
    }
}
