package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.smallships.client.model.sail.SailModel;
import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Sailable;
import com.talhanation.smallships.world.entity.ship.sail.SailDamage;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class SailPatternRenderer {
    private static final ThreadLocal<HeraldryDraftTexture> EDITOR_DRAFT_TEXTURE = new ThreadLocal<>();

    private SailPatternRenderer() {
    }

    public static void setEditorDraftTexture(HeraldryDraftTexture texture) {
        if (texture == null) EDITOR_DRAFT_TEXTURE.remove();
        else EDITOR_DRAFT_TEXTURE.set(texture);
    }

    public static void clearEditorDraftTexture() {
        EDITOR_DRAFT_TEXTURE.remove();
    }

    public static boolean renderWorldSail(Sailable sailable, float partialTicks, PoseStack poseStack,
                                           MultiBufferSource buffers, int packedLight) {
        Ship ship = sailable.self();
        if (SailDamage.getState(ship) == SailDamage.State.DESTROYED) return false;
        ItemStack sailPattern = ((SailBannerAccess) ship).smallshipsheraldry$getSailBanner();
        HeraldryPatternData pattern = SailBannerTextureCache.getPatternData(sailPattern);
        if (pattern == null || !pattern.shipType().matches(ship)) return false;
        SailModel model = SailModelBridge.model(ship);
        if (model == null) return false;
        SailModelBridge.setupAnim(model, ship, partialTicks);

        ResourceLocation base = baseTexture(ship);
        boolean rendered = SailBannerTextureCache.render(pattern, base, buffers,
                texture -> SailModelBridge.renderType(model, texture),
                consumer -> PaintableSailMesh.render(ship, poseStack, consumer, packedLight, Set.of()));
        if (rendered) {
            PaintableSailMesh.renderUnpaintable(ship, poseStack,
                    buffers.getBuffer(SailModelBridge.renderType(model, base)), packedLight, Set.of());
        }
        return rendered;
    }

    public static void renderEditorPreview(Sailable sailable, PoseStack poseStack,
                                           MultiBufferSource buffers, int packedLight) {
        Ship ship = sailable.self();
        SailModel model = SailModelBridge.model(ship);
        if (model == null) return;
        PaintableSailMesh.captureEditorPickFaces(ship, model, poseStack);
        ResourceLocation base = baseTexture(ship);
        Set<String> hidden = PaintableSailMesh.editorHiddenElements();
        HeraldryDraftTexture draft = EDITOR_DRAFT_TEXTURE.get();
        boolean rendered = false;
        if (draft != null) {
            ResourceLocation atlas = draft.prepare(ShipPreviewType.fromShip(ship), base);
            if (atlas != null) {
                PaintableSailMesh.render(ship, poseStack,
                        buffers.getBuffer(SailModelBridge.renderType(model, atlas)), packedLight, hidden);
                rendered = true;
            }
        } else {
            ItemStack stack = ((SailBannerAccess) ship).smallshipsheraldry$getSailBanner();
            rendered = SailBannerTextureCache.render(SailBannerTextureCache.getPatternData(stack), base, buffers,
                    texture -> SailModelBridge.renderType(model, texture),
                    consumer -> PaintableSailMesh.render(ship, poseStack, consumer, packedLight, hidden));
        }
        var baseConsumer = buffers.getBuffer(SailModelBridge.renderType(model, base));
        if (rendered) PaintableSailMesh.renderUnpaintable(ship, poseStack, baseConsumer, packedLight, hidden);
        else PaintableSailMesh.renderOriginal(ship, poseStack, baseConsumer, packedLight, hidden);

        PaintableSailMesh.renderHighlight(ship, poseStack, buffers.getBuffer(RenderType.lines()), hidden);
        if (PaintableSailMesh.editorGridVisible()) {
            PaintableSailMesh.renderGrid(ship, poseStack, buffers.getBuffer(RenderType.lines()), hidden);
        }
    }

    private static ResourceLocation baseTexture(Ship ship) {
        var color = SailModel.getSailColor(ship.getData(Ship.SAIL_COLOR));
        return SailDamage.getState(ship) == SailDamage.State.TORN ? color.damagedLocation : color.location;
    }
}
