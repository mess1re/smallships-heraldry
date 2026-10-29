package me.mss1r.smallshipsheraldry.common;

import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import me.mss1r.smallshipsheraldry.registry.HeraldryItems;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;

public final class SailBannerInteractionHandler {
    private SailBannerInteractionHandler() {
    }

    public static InteractionResult tryHandle(Ship ship, Player player, InteractionHand interactionHand) {
        if (ship.isLocked() || ship.isInDockyardWork() || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        Entity entity = ship;
        boolean clientSide = entity.level().isClientSide();
        SailBannerAccess access = (SailBannerAccess) ship;
        ItemStack heldItem = player.getItemInHand(interactionHand);

        if (heldItem.is(HeraldryItems.HERALDRY_PATTERN.get())) {
            HeraldryPatternData patternData = HeraldryPatternData.tryFromStack(heldItem).orElse(null);
            if (patternData == null) {
                if (!clientSide) {
                    player.displayClientMessage(Component.translatable("smallshipsheraldry.message.invalid_pattern"), true);
                }
                return InteractionResult.sidedSuccess(clientSide);
            }
            if (!patternData.shipType().matches(ship)) {
                if (!clientSide) {
                    player.displayClientMessage(Component.translatable("smallshipsheraldry.message.wrong_ship_type", patternData.shipType().getDisplayName()), true);
                }
                return InteractionResult.sidedSuccess(clientSide);
            }

            ItemStack installedPattern = access.smallshipsheraldry$getSailBanner();
            if (ItemStack.isSameItemSameComponents(installedPattern, heldItem)) {
                return InteractionResult.sidedSuccess(clientSide);
            }

            if (!clientSide) {
                access.smallshipsheraldry$setOriginalSailColor(colorWithoutPattern(ship, access));

                ItemStack sailPattern = heldItem.copy();
                sailPattern.setCount(1);
                access.smallshipsheraldry$setSailBanner(sailPattern);
                access.smallshipsheraldry$applyPatternSailColor(HeraldryColorUtil.nearestDyeName(patternData.baseColorArgb()));

                if (!player.isCreative()) {
                    heldItem.shrink(1);
                }

                giveOrDrop(player, installedPattern);

                playClothSound(ship);
            }

            return InteractionResult.sidedSuccess(clientSide);
        }

        if (heldItem.getItem() instanceof ShearsItem && access.smallshipsheraldry$hasSailBanner()) {
            if (!clientSide) {
                String restoredColor = colorWithoutPattern(ship, access);
                access.smallshipsheraldry$setSailBanner(ItemStack.EMPTY);
                ship.setData(Ship.SAIL_COLOR, restoredColor);
                access.smallshipsheraldry$setOriginalSailColor("");
                if (!player.isCreative()) {
                    heldItem.hurtAndBreak(
                            1,
                            player,
                            interactionHand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND
                    );
                }
                playClothSound(ship);
            }

            return InteractionResult.sidedSuccess(clientSide);
        }

        return InteractionResult.PASS;
    }

    private static String colorWithoutPattern(Ship ship, SailBannerAccess access) {
        String currentColor = ship.getData(Ship.SAIL_COLOR);
        HeraldryPatternData installed = HeraldryPatternData.tryFromStack(access.smallshipsheraldry$getSailBanner()).orElse(null);
        // Recover external recolors made before they were tracked in saved ship data.
        if (installed == null || !access.smallshipsheraldry$hasOriginalSailColor()
                || !currentColor.equals(HeraldryColorUtil.nearestDyeName(installed.baseColorArgb()))) {
            return currentColor;
        }
        return access.smallshipsheraldry$getOriginalSailColor();
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
        Entity entity = ship;
        entity.level().playSound(
                null,
                entity.getX(),
                entity.getY() + 1.0D,
                entity.getZ(),
                SoundEvents.WOOL_HIT,
                entity.getSoundSource(),
                1.0F,
                1.0F
        );
    }
}
