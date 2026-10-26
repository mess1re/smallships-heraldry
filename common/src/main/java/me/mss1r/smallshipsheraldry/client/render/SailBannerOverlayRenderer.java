package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.talhanation.smallships.client.model.sail.BriggSailModel;
import com.talhanation.smallships.client.model.sail.CaravelSailModel;
import com.talhanation.smallships.client.model.sail.CogSailModel;
import com.talhanation.smallships.client.model.sail.DhowSailModel;
import com.talhanation.smallships.client.model.sail.DrakkarSailModel;
import com.talhanation.smallships.client.model.sail.GalleonSailModel;
import com.talhanation.smallships.client.model.sail.GalleySailModel;
import com.talhanation.smallships.client.model.sail.SailModel;
import com.talhanation.smallships.world.entity.ship.BriggEntity;
import com.talhanation.smallships.world.entity.ship.CaravelEntity;
import com.talhanation.smallships.world.entity.ship.CogEntity;
import com.talhanation.smallships.world.entity.ship.DhowEntity;
import com.talhanation.smallships.world.entity.ship.DrakkarEntity;
import com.talhanation.smallships.world.entity.ship.GalleonEntity;
import com.talhanation.smallships.world.entity.ship.GalleyEntity;
import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Sailable;
import me.mss1r.smallshipsheraldry.access.SailBannerAccess;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class SailBannerOverlayRenderer {
    private static final Map<Class<? extends Ship>, SailModel> SAIL_MODELS = new HashMap<>();
    private static final ThreadLocal<ResourceLocation> EDITOR_DRAFT_TEXTURE = new ThreadLocal<>();

    static {
        SAIL_MODELS.put(CogEntity.class, new CogSailModel());
        SAIL_MODELS.put(BriggEntity.class, new BriggSailModel());
        SAIL_MODELS.put(GalleyEntity.class, new GalleySailModel());
        SAIL_MODELS.put(DrakkarEntity.class, new DrakkarSailModel());
        SAIL_MODELS.put(CaravelEntity.class, new CaravelSailModel());
        SAIL_MODELS.put(DhowEntity.class, new DhowSailModel());
        SAIL_MODELS.put(GalleonEntity.class, new GalleonSailModel());
    }

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

    public static void renderOverlay(Sailable sailable, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Ship ship = sailable.self();

        ItemStack sailBanner = ((SailBannerAccess) ship).smallshipsheraldry$getSailBanner();
        if (sailBanner.isEmpty()) {
            return;
        }
        HeraldryPatternData patternData = SailBannerTextureCache.getPatternData(sailBanner);
        if (patternData == null || !patternData.shipType().matches(ship)) {
            return;
        }

        ResourceLocation overlayTexture = SailBannerTextureCache.getTexture(ship, sailBanner);
        if (overlayTexture == null) {
            return;
        }

        SailModel sailModel = SAIL_MODELS.get(ship.getClass());
        if (sailModel == null) {
            return;
        }

        SailModelBridge.setupAnim(sailModel, ship, partialTicks);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(overlayTexture));
        PaintableSailMesh.render(ship, sailModel, poseStack, vertexConsumer, packedLight, PaintableSailMesh.editorHiddenElements());
    }

    public static void renderEditorPreview(Sailable sailable, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Ship ship = sailable.self();
        SailModel sailModel = SAIL_MODELS.get(ship.getClass());
        if (sailModel == null) {
            return;
        }

        SailModelBridge.setupAnim(sailModel, ship, partialTicks);
        PaintableSailMesh.captureEditorPickFaces(ship, sailModel, poseStack);
        VertexConsumer baseConsumer = bufferSource.getBuffer(
                SailModelBridge.renderType(sailModel, SailModel.getSailColor(ship.getData(Ship.SAIL_COLOR)).location)
        );
        PaintableSailMesh.renderOriginal(ship, sailModel, poseStack, baseConsumer, packedLight, PaintableSailMesh.editorHiddenElements());

        ResourceLocation overlayTexture = EDITOR_DRAFT_TEXTURE.get();
        if (overlayTexture == null) {
            ItemStack sailBanner = ((SailBannerAccess) ship).smallshipsheraldry$getSailBanner();
            if (!sailBanner.isEmpty()) {
                overlayTexture = SailBannerTextureCache.getTexture(ship, sailBanner);
            }
        }
        if (overlayTexture != null) {
            VertexConsumer overlayConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(overlayTexture));
            PaintableSailMesh.render(ship, sailModel, poseStack, overlayConsumer, packedLight, PaintableSailMesh.editorHiddenElements());
        }

        VertexConsumer highlightConsumer = bufferSource.getBuffer(RenderType.lines());
        PaintableSailMesh.renderHighlight(ship, sailModel, poseStack, highlightConsumer, PaintableSailMesh.editorHiddenElements());

        if (PaintableSailMesh.editorGridVisible()) {
            VertexConsumer gridConsumer = bufferSource.getBuffer(RenderType.lines());
            PaintableSailMesh.renderGrid(ship, sailModel, poseStack, gridConsumer, PaintableSailMesh.editorHiddenElements());
        }
    }
}
