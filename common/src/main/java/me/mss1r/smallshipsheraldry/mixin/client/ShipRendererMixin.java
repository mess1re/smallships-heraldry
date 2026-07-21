package me.mss1r.smallshipsheraldry.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.smallships.client.renderer.entity.ShipRenderer;
import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Sailable;
import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;
import me.mss1r.smallshipsheraldry.client.render.SailBannerOverlayRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShipRenderer.class)
public abstract class ShipRendererMixin<T extends Ship> {
    @Inject(method = "renderSail", at = @At("HEAD"), cancellable = true)
    private void smallshipsheraldry$suppressEditorSails(Sailable sailable, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, CallbackInfo ci) {
        if (PaintableSailMesh.editorEntitySailsSuppressed()) {
            SailBannerOverlayRenderer.renderEditorPreview(sailable, partialTicks, poseStack, multiBufferSource, packedLight);
            ci.cancel();
        }
    }

    @Inject(method = "renderSail", at = @At("TAIL"))
    private void smallshipsheraldry$renderHeraldryPaintLayer(Sailable sailable, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, CallbackInfo ci) {
        SailBannerOverlayRenderer.renderOverlay(sailable, partialTicks, poseStack, multiBufferSource, packedLight);
    }
}
