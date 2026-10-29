package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.Set;

public final class PreviewShipRenderer {
    private PreviewShipRenderer() {
    }

    public static PreviewShipFraming measure(Ship ship) {
        BoundsCollector bounds = new BoundsCollector();
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        byte previousStage = ship.getData(Ship.SAIL_STATE);
        SailBannerAccess access = (SailBannerAccess) ship;
        ItemStack previousPattern = access.smallshipsheraldry$getSailBanner();
        boolean editorSails = PaintableSailMesh.editorEntitySailsSuppressed();
        try {
            PreviewShipFactory.setSailState(ship, 4);
            access.smallshipsheraldry$setSailBanner(ItemStack.EMPTY);
            dispatcher.setRenderShadow(false);
            PaintableSailMesh.setEditorEntitySailsSuppressed(false);
            dispatcher.render(ship, 0, 0, 0, 0, 1, new PoseStack(), type -> bounds, LightTexture.FULL_BRIGHT);
            return bounds.result();
        } finally {
            ship.setData(Ship.SAIL_STATE, previousStage);
            access.smallshipsheraldry$setSailBanner(previousPattern);
            if (editorSails) PaintableSailMesh.setEditorEntitySailsSuppressed(true);
            else PaintableSailMesh.clearEditorEntitySailsSuppressed();
            dispatcher.setRenderShadow(true);
        }
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

        PaintableSailMesh.setEditorGuiScale(guiGraphics.pose().last().pose().m00());
        GuiScissor.enable(guiGraphics, left, top, right, bottom);
        guiGraphics.pose().pushPose();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        try {
            guiGraphics.pose().translate(centerX, centerY, 180.0D);
            guiGraphics.pose().scale(scale, scale, -scale);
            guiGraphics.pose().translate(0.0F, 0.05F, 0.0F);
            guiGraphics.pose().mulPose(orientation);
            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            PaintableSailMesh.setEditorEntitySailsSuppressed(true);
            SailBannerOverlayRenderer.setEditorDraftTexture(draftTexture);
            dispatcher.render(
                    ship,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0F,
                    1.0F,
                    guiGraphics.pose(),
                    guiGraphics.bufferSource(),
                    LightTexture.FULL_BRIGHT
            );
            guiGraphics.flush();
        } finally {
            PaintableSailMesh.clearEditorGuiScale();
            SailBannerOverlayRenderer.clearEditorDraftTexture();
            PaintableSailMesh.clearEditorEntitySailsSuppressed();
            dispatcher.setRenderShadow(true);
            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
            Lighting.setupFor3DItems();
        }
    }
    private static final class BoundsCollector implements VertexConsumer {
        private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        @Override public VertexConsumer vertex(double x, double y, double z) {
            minX = Math.min(minX, (float) x); minY = Math.min(minY, (float) y); minZ = Math.min(minZ, (float) z);
            maxX = Math.max(maxX, (float) x); maxY = Math.max(maxY, (float) y); maxZ = Math.max(maxZ, (float) z);
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}

        PreviewShipFraming result() {
            if (!Float.isFinite(minX) || !Float.isFinite(maxX)) return new PreviewShipFraming(-1, 0, -1, 1, 2, 1);
            return new PreviewShipFraming(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
