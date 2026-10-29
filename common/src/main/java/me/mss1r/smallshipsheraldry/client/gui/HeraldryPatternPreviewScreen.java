package me.mss1r.smallshipsheraldry.client.gui;

import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;
import me.mss1r.smallshipsheraldry.client.render.PreviewShipFactory;
import me.mss1r.smallshipsheraldry.client.render.PreviewShipRenderer;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class HeraldryPatternPreviewScreen extends Screen {
    private static final float DEFAULT_PREVIEW_YAW = 180.0F;
    private static final float DEFAULT_PREVIEW_PITCH = -12.0F;
    private static final float DEFAULT_PREVIEW_ZOOM = 2.25F;

    private final HeraldryPatternData patternData;
    private final ShipPreviewType previewType;
    private Ship previewShip;
    private float previewYaw = DEFAULT_PREVIEW_YAW;
    private float previewPitch = DEFAULT_PREVIEW_PITCH;

    public HeraldryPatternPreviewScreen(ItemStack patternStack) {
        super(Component.translatable("item.smallshipsheraldry.heraldry_pattern"));
        this.patternData = HeraldryPatternData.fromStack(patternStack);
        this.previewType = patternData.shipType();
    }

    @Override
    protected void init() {
        super.init();
        this.previewShip = PreviewShipFactory.create(previewType, patternData);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (previewShip == null) {
            guiGraphics.drawCenteredString(font, Component.translatable("smallshipsheraldry.message.ship_unavailable"),
                    width / 2, height / 2, 0xFFFFFFFF);
            return;
        }
        PreviewShipRenderer.render(
                guiGraphics,
                0,
                0,
                width,
                height,
                previewShip,
                previewYaw,
                previewPitch,
                DEFAULT_PREVIEW_ZOOM,
                0.0F,
                62.0F
        );
    }

    @Override
    public void removed() {
        PaintableSailMesh.clearEditorPickCache();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return button == 0 || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            previewYaw = (previewYaw + (float) dragX * 1.8F) % 360.0F;
            previewPitch = Mth.clamp(previewPitch + (float) dragY * 1.2F, -65.0F, 65.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
