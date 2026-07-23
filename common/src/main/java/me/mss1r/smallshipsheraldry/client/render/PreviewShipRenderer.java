package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.talhanation.smallships.world.entity.ship.Ship;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Set;

public final class PreviewShipRenderer {
    private PreviewShipRenderer() {
    }

    public static void render(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int scale, Ship ship, float rotationDegrees) {
        float zoom = Math.max(0.1F, scale / 12.0F);
        render(guiGraphics, left, top, right, bottom, ship, rotationDegrees, -10.0F, zoom, 0.0F, 0.0F, Set.of());
    }

    public static void render(GuiGraphics guiGraphics, int left, int top, int right, int bottom, Ship ship, float yawDegrees, float pitchDegrees, float zoom, float panX, float panY) {
        render(guiGraphics, left, top, right, bottom, ship, yawDegrees, pitchDegrees, zoom, panX, panY, Set.of());
    }

    public static void render(GuiGraphics guiGraphics, int left, int top, int right, int bottom, Ship ship, float yawDegrees, float pitchDegrees, float zoom, float panX, float panY, Set<String> hiddenElements) {
        render(guiGraphics, left, top, right, bottom, ship, yawDegrees, pitchDegrees, zoom, panX, panY, hiddenElements, null);
    }

    public static void render(GuiGraphics guiGraphics, int left, int top, int right, int bottom, Ship ship, float yawDegrees, float pitchDegrees, float zoom, float panX, float panY, Set<String> hiddenElements, ResourceLocation draftTexture) {
        if (ship == null) {
            return;
        }

        float scale = 12.0F * zoom;
        float centerX = (left + right) * 0.5F + panX;
        float centerY = (top + bottom) * 0.5F + PaintableSailMesh.EDITOR_VERTICAL_OFFSET + panY;
        Quaternionf orientation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateX(pitchDegrees * ((float) Math.PI / 180.0F))
                .rotateY(yawDegrees * ((float) Math.PI / 180.0F));

        guiGraphics.enableScissor(left, top, right, bottom);
        guiGraphics.pose().pushPose();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        try {
            guiGraphics.pose().translate(centerX, centerY, 180.0D);
            guiGraphics.pose().mulPoseMatrix(new Matrix4f().scaling((float) scale, (float) scale, (float) (-scale)));
            guiGraphics.pose().translate(0.0F, 0.05F, 0.0F);
            guiGraphics.pose().mulPose(orientation);
            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            PaintableSailMesh.setEditorEntitySailsSuppressed(true);
            SailBannerOverlayRenderer.setEditorDraftTexture(draftTexture);
            RenderSystem.runAsFancy(() -> dispatcher.render(ship, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, guiGraphics.pose(), guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT));
            guiGraphics.flush();
        } finally {
            SailBannerOverlayRenderer.clearEditorDraftTexture();
            PaintableSailMesh.clearEditorEntitySailsSuppressed();
            dispatcher.setRenderShadow(true);
            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
            Lighting.setupFor3DItems();
        }
    }
}
