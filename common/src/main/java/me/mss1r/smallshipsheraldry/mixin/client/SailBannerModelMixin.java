package me.mss1r.smallshipsheraldry.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.smallships.client.model.sail.banner.MastBannerModel;
import com.talhanation.smallships.client.model.sail.banner.SailBannerModel;
import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import me.mss1r.smallshipsheraldry.client.render.SailBannerTextureCache;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(SailBannerModel.class)
abstract class SailBannerModelMixin {
    @Unique
    private boolean smallshipsheraldry$paintedSail;

    @WrapMethod(method = "render")
    private void smallshipsheraldry$renderAbovePaint(Ship ship, ItemStack banner, PoseStack poseStack,
                                                    MultiBufferSource buffers, int light, Operation<Void> original) {
        HeraldryPatternData pattern = (Object) this instanceof MastBannerModel ? null
                : SailBannerTextureCache.getPatternData(((SailBannerAccess) ship).smallshipsheraldry$getSailBanner());
        boolean previous = smallshipsheraldry$paintedSail;
        smallshipsheraldry$paintedSail = pattern != null && pattern.shipType().matches(ship)
                && pattern.paintedPixelCount() > 0;
        try {
            original.call(ship, banner, poseStack, buffers, light);
        } finally {
            smallshipsheraldry$paintedSail = previous;
        }
    }

    @ModifyConstant(method = "emitSegment", constant = @Constant(floatValue = 0.05F), require = 2)
    private float smallshipsheraldry$bannerSurfaceOffset(float original) {
        // Model pixels: both banner faces must clear the paint's 0.003 model-unit offset.
        return smallshipsheraldry$paintedSail ? 0.16F : original;
    }
}
