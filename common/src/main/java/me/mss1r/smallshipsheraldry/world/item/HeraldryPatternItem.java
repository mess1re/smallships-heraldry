package me.mss1r.smallshipsheraldry.world.item;

import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldryClient;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class HeraldryPatternItem extends Item {
    public HeraldryPatternItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            EnvExecutor.runInEnv(Env.CLIENT, () -> () -> SmallShipsHeraldryClient.openPatternPreview(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        HeraldryPatternData data = HeraldryPatternData.fromStack(stack);
        if (!data.author().isBlank()) {
            tooltipComponents.add(Component.translatable("smallshipsheraldry.tooltip.author", data.author()).withStyle(ChatFormatting.GRAY));
        }
        tooltipComponents.add(Component.translatable("smallshipsheraldry.tooltip.ship_type", data.shipType().getDisplayName()).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("smallshipsheraldry.tooltip.base_color", String.format("#%06X", data.baseColorArgb() & 0xFFFFFF)).withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("smallshipsheraldry.tooltip.preview").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(Component.translatable("smallshipsheraldry.tooltip.duplicate").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("smallshipsheraldry.tooltip.install").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("smallshipsheraldry.tooltip.remove").withStyle(ChatFormatting.DARK_GRAY));
    }
}
