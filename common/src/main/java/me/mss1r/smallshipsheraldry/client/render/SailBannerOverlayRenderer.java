package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.talhanation.smallships.client.model.sail.SailModel;
import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Sailable;
import com.talhanation.smallships.world.entity.ship.sail.SailDamage;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class SailBannerOverlayRenderer {
    private static final ThreadLocal<ResourceLocation> EDITOR_DRAFT_TEXTURE = new ThreadLocal<>();

    private SailBannerOverlayRenderer() {
    }

    public static void setEditorDraftTexture(ResourceLocation texture) {
        if (texture == null) {
            EDITOR_DRAFT_TEXTURE.remove();
        } else {
            EDITOR_DRAFT_TEXTURE.set(texture);
        }
    }

    public static void clearEditorDraftTexture() {
        EDITOR_DRAFT_TEXTURE.remove();
    }

    public static boolean renderWorldSail(Sailable sailable, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Ship ship = sailable.self();
        SailDamage.State damageState = SailDamage.getState(ship);
        if (damageState == SailDamage.State.DESTROYED) {
            return false;
        }

        ItemStack sailBanner = ((SailBannerAccess) ship).smallshipsheraldry$getSailBanner();
        if (sailBanner.isEmpty()) {
            return false;
        }
        HeraldryPatternData patternData = SailBannerTextureCache.getPatternData(sailBanner);
        if (patternData == null || !patternData.shipType().matches(ship)) {
            return false;
        }
        SailModel model = SailModelBridge.model(ship);
        if (model == null) return false;
        SailModelBridge.setupAnim(model, ship, partialTicks);

        ResourceLocation damageTexture = damageState == SailDamage.State.TORN
                ? SailModel.getSailColor(ship.getData(Ship.SAIL_COLOR)).damagedLocation : null;
        ResourceLocation baseTexture = damageTexture != null ? damageTexture
                : SailModel.getSailColor(ship.getData(Ship.SAIL_COLOR)).location;
        // Both passes must emit identical positions for the decal depth test.
        PaintableSailMesh.renderOriginal(ship, poseStack,
                bufferSource.getBuffer(RenderType.entityCutoutNoCull(baseTexture)), packedLight, java.util.Set.of());
        SailBannerTextureCache.render(patternData, damageTexture, bufferSource,
                consumer -> PaintableSailMesh.render(ship, poseStack, consumer, packedLight, PaintableSailMesh.editorHiddenElements()));
        return true;
    }

    public static void renderEditorPreview(Sailable sailable, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Ship ship = sailable.self();
        SailModel sailModel = SailModelBridge.model(ship);
        if (sailModel == null) {
            return;
        }

        PaintableSailMesh.captureEditorPickFaces(ship, sailModel, poseStack);
        VertexConsumer baseConsumer = bufferSource.getBuffer(
                SailModelBridge.renderType(sailModel, SailModel.getSailColor(ship.getData(Ship.SAIL_COLOR)).location)
        );
        PaintableSailMesh.renderOriginal(ship, poseStack, baseConsumer, packedLight, PaintableSailMesh.editorHiddenElements());

        ResourceLocation overlayTexture = EDITOR_DRAFT_TEXTURE.get();
        if (overlayTexture == null) {
            ItemStack sailBanner = ((SailBannerAccess) ship).smallshipsheraldry$getSailBanner();
            if (!sailBanner.isEmpty()) {
                SailBannerTextureCache.render(SailBannerTextureCache.getPatternData(sailBanner), null, bufferSource,
                        consumer -> PaintableSailMesh.render(ship, poseStack, consumer, packedLight, PaintableSailMesh.editorHiddenElements()));
            }
        }
        if (overlayTexture != null) {
            VertexConsumer overlayConsumer = bufferSource.getBuffer(RenderType.entityDecal(overlayTexture));
            PaintableSailMesh.render(ship, poseStack, overlayConsumer, packedLight, PaintableSailMesh.editorHiddenElements());
        }

        VertexConsumer highlightConsumer = bufferSource.getBuffer(RenderType.lines());
        PaintableSailMesh.renderHighlight(ship, poseStack, highlightConsumer, PaintableSailMesh.editorHiddenElements());

        if (PaintableSailMesh.editorGridVisible()) {
            VertexConsumer gridConsumer = bufferSource.getBuffer(RenderType.lines());
            PaintableSailMesh.renderGrid(ship, poseStack, gridConsumer, PaintableSailMesh.editorHiddenElements());
        }
    }
}
