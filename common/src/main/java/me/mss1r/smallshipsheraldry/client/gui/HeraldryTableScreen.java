package me.mss1r.smallshipsheraldry.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talhanation.smallships.world.entity.ship.Ship;
import dev.architectury.networking.NetworkManager;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.client.HeraldryClientSettings;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryPaintController.FillMode;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryPaintController.PaintTool;
import me.mss1r.smallshipsheraldry.client.gui.HeraldryPaintController.StrokeMode;
import me.mss1r.smallshipsheraldry.client.render.HeraldryDraftTexture;
import me.mss1r.smallshipsheraldry.client.render.GuiScissor;
import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;
import me.mss1r.smallshipsheraldry.client.render.PreviewShipFactory;
import me.mss1r.smallshipsheraldry.client.render.PreviewShipFraming;
import me.mss1r.smallshipsheraldry.client.render.PreviewShipRenderer;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import me.mss1r.smallshipsheraldry.world.inventory.HeraldryTableMenu;
import me.mss1r.smallshipsheraldry.world.level.block.entity.HeraldryTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static me.mss1r.smallshipsheraldry.client.gui.HeraldryEditorLayout.isInside;

public class HeraldryTableScreen extends AbstractContainerScreen<HeraldryTableMenu> {
    private static final ResourceLocation TOOL_ICONS =
            new ResourceLocation(SmallShipsHeraldry.MOD_ID, "textures/gui/heraldry_tools.png");
    private static final ResourceLocation CRAFTING_TEXTURE = new ResourceLocation("textures/gui/container/crafting_table.png");

    private static final int ELEMENT_X = 38;
    private static final int ELEMENT_Y = HeraldryEditorLayout.PREVIEW_Y;
    private static final int ELEMENT_WIDTH = 116;
    private static final int ELEMENT_ROW_HEIGHT = 13;
    private static final int MODE_TAB_X = 142;
    private static final int MODE_TAB_Y = 7;
    private static final int MODE_TAB_WIDTH = 72;
    private static final int MODE_TAB_HEIGHT = 16;

    private static final int PREVIEW_Y = HeraldryEditorLayout.PREVIEW_Y;

    private static final int RIGHT_WIDTH = HeraldryEditorLayout.RIGHT_WIDTH;
    private static final int HELP_SIZE = 14;
    private static final int HELP_MARGIN = 8;
    private static final int HELP_Y = 7;
    private static final int TOOL_X = 10;
    private static final int TOOL_Y = HeraldryEditorLayout.PREVIEW_Y;
    private static final int TOOL_WIDTH = 20;
    private static final int TOOL_HEIGHT = 20;
    private static final int TOOL_GAP = 2;
    private static final int TOOL_TEXTURE_WIDTH = 144;
    private static final int SIZE_Y = 222;
    private static final int STROKE_Y = 244;
    private static final int SYMMETRY_Y = 270;
    private static final int FILL_MODE_Y = 222;

    private HeraldryEditorLayout layout = new HeraldryEditorLayout(HeraldryEditorLayout.MIN_WIDTH, HeraldryEditorLayout.MIN_HEIGHT, 1);
    private boolean partsListVisible = true;

    private final HeraldryEditorSession session = new HeraldryEditorSession();
    private final HeraldryEditorSurface surface = new EditorSurface();
    private final HeraldryEditorEdits edits = new EditorEdits();
    private final HeraldrySelectionController selection = new HeraldrySelectionController(session, surface, edits);
    private final HeraldryPaintController paint = new HeraldryPaintController(session, surface, edits,
            color -> this.colorPicker.setColor(color));
    private final HeraldryDraftSync draftSync;
    private final HeraldryElementSelection parts = new HeraldryElementSelection();
    private final HeraldryChoicePopup choices = new HeraldryChoicePopup();
    private HeraldryDraftTexture draftTexture;
    private Ship previewShip;
    private PreviewShipFraming framing;
    private ShipPreviewType framedType;
    private HeraldryColorPicker colorPicker;
    private HeraldryButton printButton;
    private HeraldryButton libraryButton;
    private HeraldryLibraryPanel libraryPanel;
    private int previewStage = 4;
    private final HeraldryPreviewCamera camera = new HeraldryPreviewCamera();
    private PaintTool activeTool = PaintTool.BRUSH;
    private EditorMode editorMode = EditorMode.PAINT;
    private StrokeMode strokeMode = StrokeMode.FREEHAND;
    private FillMode fillMode = FillMode.AREA;
    private int brushSize = 1;
    private boolean mirrorVertical;
    private boolean mirrorHorizontal;
    private boolean rotatingPreview;
    private boolean panningPreview;
    private boolean gridVisible = true;
    private boolean helpVisible;
    private int helpScroll;
    private int helpMaxScroll;
    private boolean actionsVisible;
    private float uiScale = 1.0F;
    private double lastMouseX;
    private double lastMouseY;
    private boolean editorSettingsLoaded;
    private Component statusMessage;
    private long statusMessageUntil;
    private long clearConfirmationUntil;

    public HeraldryTableScreen(HeraldryTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        draftSync = new HeraldryDraftSync(menu.getBlockPos(), session, NetworkManager::sendToServer, this::showStatus);
        this.imageWidth = layout.width();
        this.imageHeight = layout.height();
    }

    private int previewX() { return layout.previewX(); }
    private int previewWidth() { return layout.previewWidth(); }
    private int previewHeight() { return layout.previewHeight(); }
    private int rightX() { return layout.rightX(); }
    private int footerY() { return layout.footerY(); }
    private float fittedZoom() { return framing == null ? layout.previewScale() : framing.fitZoom(previewWidth(), previewHeight()); }
    private float previewZoom() { return camera.zoom() * fittedZoom(); }
    private org.joml.Vector3f framingOffset() {
        return framing == null ? new org.joml.Vector3f() : framing.centerOffset(camera.yaw(), camera.pitch(), previewZoom());
    }
    private float previewPanX() { return camera.panX() + framingOffset().x; }
    private float previewPanY() { return camera.panY() + framingOffset().y; }
    private boolean elementListVisible() { return editorMode == EditorMode.PARTS && partsListVisible; }

    @Override
    protected void init() {
        int viewportWidth = minecraft.getWindow().getGuiScaledWidth();
        int viewportHeight = minecraft.getWindow().getGuiScaledHeight();
        float previousPreviewScale = fittedZoom();
        layout = HeraldryEditorLayout.create(viewportWidth, viewportHeight, minecraft.getWindow().getGuiScale());
        camera.rescalePan(fittedZoom() / previousPreviewScale);
        uiScale = layout.scale();
        imageWidth = layout.width();
        imageHeight = layout.height();
        width = (int) (viewportWidth / uiScale);
        height = (int) (viewportHeight / uiScale);
        super.init();
        choices.close();
        actionsVisible = false;
        paint.cancel();
        rotatingPreview = false;
        panningPreview = false;
        selection.release();
        if (colorPicker == null) {
            colorPicker = new HeraldryColorPicker(font, rightX(), RIGHT_WIDTH);
        }
        colorPicker.setPanelX(rightX());
        if (!editorSettingsLoaded) {
            HeraldryClientSettings.EditorSettings settings = HeraldryClientSettings.loadEditorSettings();
            colorPicker.setColor(settings.brushColor());
            activeTool = PaintTool.byName(settings.tool());
            editorMode = EditorMode.byName(settings.editorMode());
            strokeMode = StrokeMode.byName(settings.strokeMode());
            fillMode = FillMode.byName(settings.fillMode());
            brushSize = Mth.clamp(settings.brushSize(), 1, 8);
            mirrorVertical = settings.mirrorVertical();
            mirrorHorizontal = settings.mirrorHorizontal();
            gridVisible = settings.gridVisible();
            previewStage = settings.previewStage();
            camera.restore(settings.previewYaw(), settings.previewPitch(), settings.previewZoom());
            editorSettingsLoaded = true;
        }
        session.initialize(menu.getInitialDrafts());
        parts.switchContext(session.shipType(), previewStage);
        if (draftTexture == null) {
            try {
                draftTexture = new HeraldryDraftTexture("table", session.pixels());
            } catch (RuntimeException exception) {
                SmallShipsHeraldry.LOGGER.error("Unable to allocate the heraldry editor texture", exception);
                showStatus(Component.translatable("smallshipsheraldry.message.texture_failed"));
            }
        }
        rebuildPreviewShip();
        draftSync.request();

        int x0 = leftPos;
        int y0 = topPos;
        HeraldryEditBox colorInput = colorPicker.init(x0, y0 - 16);
        colorPicker.setVisible(editorMode == EditorMode.PAINT && activeTool != PaintTool.SELECT && !libraryPanelVisible());
        addRenderableWidget(colorInput);

        int controlY = y0 + layout.navigationY();
        addRenderableWidget(new HeraldryButton(x0 + imageWidth - HELP_SIZE - HELP_MARGIN, y0 + HELP_Y, HELP_SIZE, HELP_SIZE, Component.literal("?"), button -> helpVisible = !helpVisible));
        addRenderableWidget(new HeraldryButton(x0 + layout.shipPreviousX(), controlY, 20, 18, Component.literal("<"), button -> changePreviewType(session.shipType().previous())));
        addRenderableWidget(new HeraldryButton(x0 + layout.shipNextX(), controlY, 20, 18, Component.literal(">"), button -> changePreviewType(session.shipType().next())));
        addRenderableWidget(new HeraldryButton(x0 + layout.stagePreviousX(), controlY, 22, 18, Component.literal("S<"), button -> changePreviewStage(previewStage - 1)));
        addRenderableWidget(new HeraldryButton(x0 + layout.stageNextX(), controlY, 22, 18, Component.literal("S>"), button -> changePreviewStage(previewStage + 1)));
        if (libraryPanel == null) {
            libraryPanel = new HeraldryLibraryPanel(font, new LibraryController());
        }
        libraryPanel.init(x0, y0, imageWidth, imageHeight);
        libraryButton = addRenderableWidget(new HeraldryButton(x0 + 10, y0 + footerY(), 86, 18,
                Component.translatable("smallshipsheraldry.button.library"), button -> openLibrary()));
        addRenderableWidget(new HeraldryButton(x0 + 102, y0 + footerY(), 104, 18,
                Component.translatable("smallshipsheraldry.button.actions"), button -> {
                    actionsVisible = !actionsVisible;
                    colorPicker.closePalette();
                }));
        printButton = addRenderableWidget(new HeraldryButton(x0 + rightX() + 4, y0 + footerY(), 88, 18, Component.translatable("smallshipsheraldry.button.export"), button -> exportPattern()));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        HeraldryGuiTheme.drawWindow(guiGraphics, x0, y0, imageWidth, imageHeight);

        drawLeftPanel(guiGraphics, x0, y0);
        if (!libraryPanelVisible() && !helpVisible) {
            drawPreview(guiGraphics, x0, y0);
        } else {
            HeraldryGuiTheme.drawPreviewInset(guiGraphics, x0 + previewX() - 1, y0 + PREVIEW_Y - 1, previewWidth() + 2, previewHeight() + 2);
        }
        if (elementListVisible()) {
            HeraldryGuiTheme.drawInset(guiGraphics, x0 + ELEMENT_X - 1, y0 + ELEMENT_Y - 1, ELEMENT_WIDTH + 2, previewHeight() + 2);
            drawElementRows(guiGraphics, x0, y0);
        }
        drawRightPanel(guiGraphics, x0, y0);
        drawTableSlots(guiGraphics, x0, y0);

        guiGraphics.drawString(font, font.plainSubstrByWidth(title.getString(), MODE_TAB_X - 20), x0 + 10, y0 + 10, HeraldryGuiTheme.INK, false);
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.screen.stage_short", previewStage + 1),
                x0 + layout.stageCenterX(), y0 + layout.navigationY() + 5, HeraldryGuiTheme.MUTED_INK);
        Component shipName = Component.literal(font.plainSubstrByWidth(session.shipType().getDisplayName().getString(), layout.shipNameWidth()));
        drawCenteredNoShadow(guiGraphics, shipName, x0 + layout.shipNameX() + layout.shipNameWidth() / 2,
                y0 + layout.navigationY() + 5, HeraldryGuiTheme.SELECTED);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (printButton != null) {
            printButton.active = !draftSync.isLoading() && !menu.getPaperStack().isEmpty() && menu.getResultStack().isEmpty();
        }
        if (libraryButton != null) {
            libraryButton.visible = !libraryPanelVisible() && !helpVisible;
        }
        if (session.saveDue(System.currentTimeMillis())) {
            draftSync.save();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int scaledX = (int) (mouseX / uiScale);
        int scaledY = (int) (mouseY / uiScale);
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1.0F);
        try {
            renderEditor(graphics, scaledX, scaledY, partialTick);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderEditor(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        colorPicker.setVisible(editorMode == EditorMode.PAINT && activeTool != PaintTool.SELECT && !libraryPanelVisible() && !helpVisible && !actionsVisible);
        if (draftTexture != null) {
            draftTexture.uploadIfDirty();
        }
        renderBackground(guiGraphics);
        // Slots use the editor layout; inventory actions are sent explicitly, not through vanilla dragging.
        renderBg(guiGraphics, partialTick, mouseX, mouseY);
        for (var child : children()) {
            if (child instanceof Renderable widget) widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (libraryPanelVisible()) {
            libraryPanel.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        if (helpVisible) {
            drawHelpPopupAboveEverything(guiGraphics);
            return;
        }
        if (actionsVisible) {
            drawActionsPopup(guiGraphics);
            return;
        }
        if (colorPicker.isPaletteVisible()) {
            colorPicker.renderPalette(guiGraphics);
            return;
        }
        if (choices.isOpen()) {
            choices.render(guiGraphics, font);
            return;
        }
        if (statusMessage != null && System.currentTimeMillis() < statusMessageUntil) {
            Component clipped = Component.literal(font.plainSubstrByWidth(statusMessage.getString(), previewWidth() - 16));
            int messageY = topPos + PREVIEW_Y + previewHeight() - 14;
            guiGraphics.fill(leftPos + previewX() + 4, messageY - 2, leftPos + layout.previewRight() - 4, messageY + 10, 0xCC151A20);
            drawCenteredNoShadow(guiGraphics, clipped, leftPos + previewX() + previewWidth() / 2, messageY, 0xFFFF7777);
        }
        renderTableSlotTooltip(guiGraphics, mouseX, mouseY);
        renderEditorTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        paint.release();
        // The server accepts draft updates only while this menu is still open.
        draftSync.saveBeforeClose(super::onClose);
    }

    @Override
    public void removed() {
        HeraldryClientSettings.saveEditorSettings(new HeraldryClientSettings.EditorSettings(
                colorPicker.color(),
                activeTool.label(),
                editorMode.label(),
                strokeMode.label(),
                fillMode.label(),
                brushSize,
                mirrorVertical,
                mirrorHorizontal,
                gridVisible,
                previewStage,
                camera.yaw(),
                camera.pitch(),
                camera.zoom()
        ));
        selection.closeClipboard();
        if (draftTexture != null) {
            draftTexture.close();
            draftTexture = null;
        }
        if (libraryPanel != null) {
            libraryPanel.close();
        }
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (draftSync.isLoading()) return true;
        mouseX /= uiScale;
        mouseY /= uiScale;
        if (libraryPanelVisible()) {
            return libraryPanel.mouseClicked(mouseX, mouseY, button, hasShiftDown());
        }
        if (choices.isOpen()) {
            choices.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (helpVisible && !isInside(mouseX, mouseY, leftPos + imageWidth - HELP_SIZE - HELP_MARGIN, topPos + HELP_Y, HELP_SIZE, HELP_SIZE)) {
            helpVisible = false;
            return true;
        }
        if (actionsVisible) {
            handleActionsClick(mouseX, mouseY, button);
            return true;
        }
        if (colorPicker.isPaletteVisible()) {
            colorPicker.paletteClicked(mouseX, mouseY, button, hasShiftDown());
            return true;
        }
        if (getFocused() != null && getChildAt(mouseX, mouseY).orElse(null) != getFocused()) {
            setFocused(null);
        }
        if (handleModeTabClick(mouseX, mouseY, button)) {
            return true;
        }
        if (editorMode == EditorMode.PARTS && button == 0
                && isInside(mouseX, mouseY, leftPos + TOOL_X, topPos + TOOL_Y, TOOL_WIDTH, TOOL_HEIGHT)) {
            partsListVisible = !partsListVisible;
            return true;
        }
        if (handleTableSlotClick(mouseX, mouseY, button)) {
            return true;
        }
        if (editorMode == EditorMode.PAINT) {
            if (handleToolClick(mouseX, mouseY, button)
                    || handlePaintControlClick(mouseX, mouseY, button)
                    || handleColorClick(mouseX, mouseY, button)) {
                return true;
            }
        } else if (editorMode == EditorMode.PARTS) {
            if (handleElementClick(mouseX, mouseY, button) || handlePartsActionClick(mouseX, mouseY, button)) {
                return true;
            }
        } else if (handleViewControlClick(mouseX, mouseY, button)) {
            return true;
        }
        if (isInsidePreview(mouseX, mouseY) && (button == 0 || button == 1 || button == 2)) {
            if (button == 1) {
                selection.release();
                if (hasShiftDown()) {
                    panningPreview = true;
                } else {
                    rotatingPreview = true;
                }
                return true;
            }
            if (button == 2) {
                selection.release();
                panningPreview = true;
                return true;
            }
            if (editorMode == EditorMode.PARTS) {
                return selectElementAtPreview(mouseX, mouseY);
            }
            if (editorMode != EditorMode.PAINT) {
                return true;
            }
            if (activeTool == PaintTool.SELECT) {
                return selection.beginPixelSelection(mouseX, mouseY);
            }
            return paint.begin(mouseX, mouseY, new HeraldryPaintController.Settings(
                    activeTool, strokeMode, fillMode, brushSize, mirrorVertical, mirrorHorizontal, colorPicker.color()), hasAltDown());
        }
        var child = getChildAt(mouseX, mouseY);
        if (child.isPresent() && child.get().mouseClicked(mouseX, mouseY, button)) {
            setFocused(child.get());
            if (button == 0) setDragging(true);
            return true;
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseX /= uiScale;
        mouseY /= uiScale;
        dragX /= uiScale;
        dragY /= uiScale;
        if (libraryPanelVisible()) {
            return libraryPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (helpVisible || actionsVisible || colorPicker.isPaletteVisible() || choices.isOpen()) return true;
        if (colorPicker.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && selection.drag(mouseX, mouseY)) {
            return true;
        }
        if (rotatingPreview && button == 1) {
            camera.rotate(dragX, dragY);
            return true;
        }
        if (panningPreview && (button == 1 || button == 2)) {
            camera.pan(dragX, dragY);
            return true;
        }
        if (button == 0 && paint.drag(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX /= uiScale;
        mouseY /= uiScale;
        if (libraryPanelVisible()) {
            return libraryPanel.mouseReleased(button);
        }
        if (button == 0) paint.release();
        if (button == 0 || button == 1 || button == 2) {
            paint.cancel();
            rotatingPreview = false;
            panningPreview = false;
            selection.release();
            colorPicker.mouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (draftSync.isLoading()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(keyCode, scanCode)) onClose();
            return true;
        }
        if (libraryPanelVisible()) {
            return libraryPanel.keyPressed(keyCode, scanCode, modifiers);
        }
        if (choices.isOpen()) {
            choices.keyPressed(keyCode);
            return true;
        }
        if (helpVisible) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_F1) helpVisible = false;
            return true;
        }
        if (actionsVisible || colorPicker.isPaletteVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                actionsVisible = false;
                colorPicker.closePalette();
            }
            return true;
        }
        if (colorPicker.isInputFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            selection.copyPixelSelection(false);
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_X) {
            selection.copyPixelSelection(true);
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            setActiveTool(PaintTool.SELECT);
            editorMode = EditorMode.PAINT;
            selection.beginPaste();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            if (hasShiftDown()) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (selection.isPasting() && keyCode == GLFW.GLFW_KEY_ENTER) {
            selection.commitPaste();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && selection.cancelSelection()) {
            return true;
        }
        if (selection.isPasting() && (keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_DOWN)) {
            int step = hasShiftDown() ? 8 : 1;
            int deltaX = keyCode == GLFW.GLFW_KEY_LEFT ? -step : keyCode == GLFW.GLFW_KEY_RIGHT ? step : 0;
            int deltaY = keyCode == GLFW.GLFW_KEY_UP ? -step : keyCode == GLFW.GLFW_KEY_DOWN ? step : 0;
            selection.movePasteBy(deltaX, deltaY);
            return true;
        }
        if (activeTool == PaintTool.SELECT && keyCode == GLFW.GLFW_KEY_DELETE) {
            selection.clearPixelSelection();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
            changePreviewStage(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT_BRACKET) {
            changePreviewStage(previewStage - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            changePreviewStage(previewStage + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_H) {
            if (!parts.selected().isEmpty()) {
                parts.hideSelected();
                return true;
            }
            if (isInsidePreview(lastMouseX, lastMouseY)) {
                return selectElementAtPreview(lastMouseX, lastMouseY);
            }
        }
        if (keyCode == GLFW.GLFW_KEY_U) {
            parts.showAll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G) {
            gridVisible = !gridVisible;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F1) {
            helpVisible = !helpVisible;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_B) {
            setActiveTool(PaintTool.BRUSH);
            editorMode = EditorMode.PAINT;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E) {
            setActiveTool(PaintTool.ERASER);
            editorMode = EditorMode.PAINT;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_I) {
            setActiveTool(PaintTool.PICKER);
            editorMode = EditorMode.PAINT;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F) {
            setActiveTool(PaintTool.FILL);
            editorMode = EditorMode.PAINT;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_M) {
            setActiveTool(PaintTool.SELECT);
            editorMode = EditorMode.PAINT;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_L) {
            strokeMode = strokeMode == StrokeMode.FREEHAND ? StrokeMode.LINE : StrokeMode.FREEHAND;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        mouseX /= uiScale;
        mouseY /= uiScale;
        if (libraryPanelVisible()) {
            return libraryPanel.mouseScrolled(mouseX, mouseY, scrollY);
        }
        if (choices.isOpen()) {
            choices.mouseScrolled(scrollY);
            return true;
        }
        if (helpVisible) {
            helpScroll = Mth.clamp(helpScroll - (int) Math.signum(scrollY) * 20, 0, helpMaxScroll);
            return true;
        }
        if (actionsVisible || colorPicker.isPaletteVisible()) return true;
        if (isInsidePreview(mouseX, mouseY)) {
            zoomPreviewAt(mouseX, mouseY, scrollY);
            return true;
        }
        if (editorMode == EditorMode.PAINT
                && (activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER)
                && isInside(mouseX, mouseY, leftPos + rightX() + 4, topPos + SIZE_Y, 88, 18)) {
            brushSize = Mth.clamp(brushSize + (int) Math.signum(scrollY), 1, 8);
            return true;
        }
        if (elementListVisible() && isInside(mouseX, mouseY, leftPos + ELEMENT_X, topPos + ELEMENT_Y, ELEMENT_WIDTH, previewHeight())) {
            parts.scrollBy(-(int) Math.signum(scrollY), visibleElementRows());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (choices.isOpen()) {
            choices.mouseMoved(mouseX / uiScale, mouseY / uiScale);
            return;
        }
        super.mouseMoved(mouseX / uiScale, mouseY / uiScale);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (draftSync.isLoading()) return true;
        if (choices.isOpen()) return true;
        if (libraryPanelVisible()) {
            return libraryPanel.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (draftSync.isLoading()) return;
        choices.close();
        if (libraryPanel == null) {
            super.onFilesDrop(paths);
            return;
        }
        if (!libraryPanel.isVisible()) {
            libraryPanel.open();
        }
        libraryPanel.importFiles(paths);
    }

    private void drawLeftPanel(GuiGraphics guiGraphics, int x0, int y0) {
        drawModeTabs(guiGraphics, x0, y0);
        if (editorMode == EditorMode.PAINT) {
            drawToolButtons(guiGraphics, x0, y0);
        } else if (editorMode == EditorMode.PARTS) {
            drawControl(guiGraphics, x0 + TOOL_X, y0 + TOOL_Y, TOOL_WIDTH, TOOL_HEIGHT, false,
                    Component.literal(partsListVisible ? "<" : ">"));
        }
    }

    private void drawModeTabs(GuiGraphics guiGraphics, int x0, int y0) {
        EditorMode[] modes = EditorMode.values();
        for (int index = 0; index < modes.length; index++) {
            int x = x0 + MODE_TAB_X + index * MODE_TAB_WIDTH;
            int y = y0 + MODE_TAB_Y;
            boolean selected = modes[index] == editorMode;
            drawToggleControl(guiGraphics, x, y, MODE_TAB_WIDTH - 1, MODE_TAB_HEIGHT, selected, modes[index].displayName());
        }
    }

    private void drawPaintPanel(GuiGraphics guiGraphics, int x0, int y0) {
        int x = x0 + rightX() + 4;
        if (activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER) {
            guiGraphics.drawString(font, activeTool.displayName(), x, y0 + 210, HeraldryGuiTheme.INK, false);
            drawControl(guiGraphics, x, y0 + SIZE_Y, 18, 18, false, brushSize > 1, Component.literal("-"));
            drawCenteredNoShadow(guiGraphics, Component.literal(brushSize + " px"), x + 44, y0 + SIZE_Y + 5, HeraldryGuiTheme.INK);
            drawControl(guiGraphics, x + 70, y0 + SIZE_Y, 18, 18, false, brushSize < 8, Component.literal("+"));
            drawChoiceControl(guiGraphics, x, y0 + STROKE_Y, 88,
                    Component.translatable(strokeMode == StrokeMode.LINE ? "smallshipsheraldry.stroke.line_short" : "smallshipsheraldry.stroke.freehand"));
            guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.control.symmetry"), x, y0 + SYMMETRY_Y + 5, HeraldryGuiTheme.INK, false);
            drawIconControl(guiGraphics, x + 46, y0 + SYMMETRY_Y, 18, 18, mirrorVertical, 64);
            drawIconControl(guiGraphics, x + 70, y0 + SYMMETRY_Y, 18, 18, mirrorHorizontal, 80);
        } else if (activeTool == PaintTool.FILL) {
            guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.control.mode"), x, y0 + 210, HeraldryGuiTheme.INK, false);
            drawChoiceControl(guiGraphics, x, y0 + FILL_MODE_Y, 88,
                    Component.translatable("smallshipsheraldry.fill." + fillMode.label() + "_short"));
            drawWrapped(guiGraphics, Component.translatable("smallshipsheraldry.fill." + fillMode.label() + "_hint"), x, y0 + 248, 88);
        }
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, HeraldryGuiTheme.MUTED_INK, false);
            y += 10;
        }
    }

    private void drawActionsPopup(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        int x = leftPos + 102;
        int y = topPos + footerY() - 54;
        HeraldryGuiTheme.drawWindow(graphics, x, y, 184, 48);
        Component scope = parts.selected().isEmpty()
                ? Component.translatable("smallshipsheraldry.scope.stage")
                : Component.translatable("smallshipsheraldry.scope.selected", parts.selected().size());
        drawCenteredNoShadow(graphics, scope, x + 92, y + 8, HeraldryGuiTheme.INK);
        drawControl(graphics, x + 8, y + 24, 80, 18, false, Component.translatable("smallshipsheraldry.button.fill"));
        drawControl(graphics, x + 96, y + 24, 80, 18, false, Component.translatable(
                parts.selected().isEmpty() && System.currentTimeMillis() < clearConfirmationUntil
                        ? "smallshipsheraldry.button.confirm" : "smallshipsheraldry.button.clear"));
        graphics.flush();
        graphics.pose().popPose();
    }

    private void handleActionsClick(double mouseX, double mouseY, int button) {
        int x = leftPos + 102;
        int y = topPos + footerY() - 54;
        if (button == 0 && isInside(mouseX, mouseY, x + 8, y + 24, 80, 18)) {
            fillScope(colorPicker.color(), false);
            actionsVisible = false;
        } else if (button == 0 && isInside(mouseX, mouseY, x + 96, y + 24, 80, 18)) {
            fillScope(0, true);
            if (clearConfirmationUntil == 0) actionsVisible = false;
        } else if (!isInside(mouseX, mouseY, x, y, 184, 48)) {
            actionsVisible = false;
        }
    }

    private void drawElementRows(GuiGraphics guiGraphics, int x0, int y0) {
        int left = x0 + ELEMENT_X;
        int top = y0 + ELEMENT_Y;

        List<PaintableSailMesh.Element> elements = parts.elements();
        int rows = visibleElementRows();
        for (int row = 0; row < rows; row++) {
            int index = parts.scroll() + row;
            int rowTop = top + row * ELEMENT_ROW_HEIGHT;
            if (index >= elements.size()) {
                break;
            }

            PaintableSailMesh.Element element = elements.get(index);
            boolean hidden = parts.hidden().contains(element.id());
            boolean selected = parts.selected().contains(element.id());
            HeraldryGuiTheme.drawRow(guiGraphics, left, rowTop, ELEMENT_WIDTH, 11, selected, hidden);
            guiGraphics.fill(left + 2, rowTop + 2, left + 9, rowTop + 9, hidden ? HeraldryGuiTheme.SURFACE_ALT : selected ? HeraldryGuiTheme.SURFACE : HeraldryGuiTheme.SELECTED);
            if (hidden) {
                guiGraphics.fill(left + 2, rowTop + 5, left + 9, rowTop + 6, HeraldryGuiTheme.MUTED_INK);
            }
            String label = font.plainSubstrByWidth(parts.friendlyName(index).getString(), ELEMENT_WIDTH - 16);
            guiGraphics.drawString(font, label, left + 13, rowTop + 2, hidden ? HeraldryGuiTheme.MUTED_INK : HeraldryGuiTheme.INK, false);
        }
    }

    private void drawViewPanel(GuiGraphics guiGraphics, int x0, int y0) {
        int x = x0 + rightX() + 4;
        int y = y0 + 32;
        drawToggleControl(guiGraphics, x, y, 88, 18, gridVisible, gridButtonText());
        String[] presets = {"front", "back", "left", "right"};
        for (int index = 0; index < presets.length; index++) {
            drawControl(guiGraphics, x, y + 26 + index * 22, 88, 18, false,
                    Component.translatable("smallshipsheraldry.view." + presets[index]));
        }
        drawControl(guiGraphics, x, y + 120, 88, 18, false, Component.translatable("smallshipsheraldry.view.reset"));
    }

    private void drawPreview(GuiGraphics guiGraphics, int x0, int y0) {
        HeraldryGuiTheme.drawPreviewInset(guiGraphics, x0 + previewX() - 1, y0 + PREVIEW_Y - 1, previewWidth() + 2, previewHeight() + 2);
        if (draftSync.isLoading()) {
            drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.screen.loading"),
                    x0 + previewX() + previewWidth() / 2, y0 + PREVIEW_Y + previewHeight() / 2, 0xFFFFFFFF);
            return;
        }
        Optional<Integer> hoveredFaceIndex = gridFaceIndexUnderMouse();
        PaintableSailMesh.setEditorHiddenElements(parts.hidden());
        PaintableSailMesh.setEditorGridVisible(gridVisible);
        PaintableSailMesh.setEditorHighlightFaceIndex(hoveredFaceIndex.orElse(null));
        PaintableSailMesh.setEditorGridFaceIndex(gridVisible ? hoveredFaceIndex.orElse(null) : null);
        try {
            PreviewShipRenderer.render(
                    guiGraphics,
                    x0 + previewX(),
                    y0 + PREVIEW_Y,
                    x0 + previewX() + previewWidth(),
                    y0 + PREVIEW_Y + previewHeight(),
                    previewShip,
                    camera.yaw(),
                    camera.pitch(),
                    previewZoom(),
                    previewPanX(),
                    previewPanY(),
                    parts.hidden(),
                    draftTexture == null ? null : draftTexture.textureLocation()
            );
        } finally {
            PaintableSailMesh.clearEditorHiddenElements();
            PaintableSailMesh.clearEditorGridVisible();
            PaintableSailMesh.clearEditorHighlightFaceIndex();
            PaintableSailMesh.clearEditorGridFaceIndex();
        }
        if (editorMode == EditorMode.PAINT && activeTool == PaintTool.SELECT) {
            selection.drawPixelSelection(guiGraphics);
        }
        if (!parts.selected().isEmpty()) {
            String selectionLabel;
            if (parts.selected().size() == 1) {
                String selectedId = parts.selected().iterator().next();
                List<PaintableSailMesh.Element> elements = parts.elements();
                int selectedIndex = -1;
                for (int index = 0; index < elements.size(); index++) {
                    if (elements.get(index).id().equals(selectedId)) {
                        selectedIndex = index;
                        break;
                    }
                }
                selectionLabel = selectedIndex >= 0 ? parts.friendlyName(selectedIndex).getString() : shortElementName(selectedId);
            } else {
                selectionLabel = Integer.toString(parts.selected().size());
            }
            Component selected = Component.translatable("smallshipsheraldry.screen.selected", selectionLabel);
            guiGraphics.drawString(font, font.plainSubstrByWidth(selected.getString(), previewWidth() - 16), x0 + previewX() + 8, y0 + PREVIEW_Y + 8, HeraldryGuiTheme.HIGHLIGHT, false);
        }
        if (paint.isLine()) {
            paint.drawLinePreview(guiGraphics);
        }
    }

    private void drawRightPanel(GuiGraphics guiGraphics, int x0, int y0) {
        switch (editorMode) {
            case PAINT -> {
                if (activeTool == PaintTool.SELECT) {
                    drawSelectionActions(guiGraphics, x0, y0);
                    drawWrapped(guiGraphics, Component.translatable(selection.isPasting()
                            ? "smallshipsheraldry.selection.move_hint" : "smallshipsheraldry.selection.drag_hint"),
                            x0 + rightX() + 4, y0 + 180, 88);
                } else {
                    guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.screen.color"), x0 + rightX() + 4, y0 + 32, HeraldryGuiTheme.INK, false);
                    colorPicker.render(guiGraphics);
                    drawControl(guiGraphics, x0 + rightX() + 4, y0 + 166, 88, 17, false, Component.translatable("smallshipsheraldry.button.base"));
                    drawControl(guiGraphics, x0 + rightX() + 4, y0 + 187, 88, 17, false, Component.translatable("smallshipsheraldry.button.palette"));
                    drawPaintPanel(guiGraphics, x0, y0);
                }
            }
            case PARTS -> drawPartsActions(guiGraphics, x0, y0);
            case VIEW -> {
                drawViewPanel(guiGraphics, x0, y0);
                drawViewDetails(guiGraphics, x0, y0);
            }
        }
    }

    private void drawSelectionActions(GuiGraphics guiGraphics, int x0, int y0) {
        int x = x0 + rightX() + 4;
        int y = y0 + 32;
        boolean hasSelection = selection.hasSelection();
        drawControl(guiGraphics, x, y, 42, 18, false, hasSelection, Component.translatable("smallshipsheraldry.selection.copy"));
        drawControl(guiGraphics, x + 46, y, 42, 18, false, hasSelection, Component.translatable("smallshipsheraldry.selection.cut"));
        drawControl(guiGraphics, x, y + 22, 88, 18, selection.isPasting(), selection.hasClipboard(), Component.translatable("smallshipsheraldry.selection.paste"));
        drawControl(guiGraphics, x, y + 48, 42, 18, false, selection.hasPasteTarget(), Component.translatable("smallshipsheraldry.selection.apply"));
        drawControl(guiGraphics, x + 46, y + 48, 42, 18, false, selection.isPasting() || hasSelection, Component.translatable("smallshipsheraldry.selection.cancel"));
        drawControl(guiGraphics, x, y + 70, 88, 18, false, selection.isPasting() || hasSelection, Component.translatable("smallshipsheraldry.selection.delete"));

        drawCenteredNoShadow(guiGraphics, selection.summary(), x + 44, y + 96, HeraldryGuiTheme.MUTED_INK);
        drawWrapped(guiGraphics, Component.translatable("smallshipsheraldry.selection.shortcuts"), x, y + 120, 88);
    }

    private void drawPartsActions(GuiGraphics guiGraphics, int x0, int y0) {
        int x = x0 + rightX() + 4;
        int y = y0 + 32;
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.parts.selected", parts.selected().size()), x + 44, y, HeraldryGuiTheme.INK);
        drawControl(guiGraphics, x, y + 16, 88, 17, false, Component.translatable("smallshipsheraldry.parts.select_all"));
        drawControl(guiGraphics, x, y + 36, 88, 17, false, Component.translatable("smallshipsheraldry.parts.deselect"));
        drawControl(guiGraphics, x, y + 60, 42, 18, false, Component.translatable("smallshipsheraldry.parts.hide"));
        drawIconControl(guiGraphics, x + 46, y + 60, 42, 18, parts.isIsolated(), 112);
        drawControl(guiGraphics, x, y + 82, 88, 17, false, Component.translatable("smallshipsheraldry.parts.show_all"));
        drawControl(guiGraphics, x, y + 108, 88, 18, false, Component.translatable("smallshipsheraldry.button.fill"));
        drawControl(guiGraphics, x, y + 130, 88, 18, false, Component.translatable("smallshipsheraldry.button.clear"));
    }

    private void drawViewDetails(GuiGraphics guiGraphics, int x0, int y0) {
        int x = x0 + rightX() + 4;
        int y = y0 + 188;
        for (String key : List.of("rotate_hint", "pan_hint", "zoom_hint")) {
            for (FormattedCharSequence line : font.split(Component.translatable("smallshipsheraldry.view." + key), 88)) {
                guiGraphics.drawString(font, line, x, y, HeraldryGuiTheme.MUTED_INK, false);
                y += 10;
            }
            y += 8;
        }
    }

    private void drawToolButtons(GuiGraphics guiGraphics, int x0, int y0) {
        PaintTool[] tools = PaintTool.values();
        for (int index = 0; index < tools.length; index++) {
            drawIconControl(guiGraphics, x0 + TOOL_X, y0 + TOOL_Y + index * (TOOL_HEIGHT + TOOL_GAP),
                    TOOL_WIDTH, TOOL_HEIGHT, tools[index] == activeTool, tools[index].iconU());
        }
    }

    private boolean libraryPanelVisible() {
        return libraryPanel != null && libraryPanel.isVisible();
    }

    private void openLibrary() {
        choices.close();
        helpVisible = false;
        actionsVisible = false;
        colorPicker.closePalette();
        selection.clearPixelSelectionOverlay();
        if (libraryButton != null) {
            libraryButton.visible = false;
        }
        colorPicker.setVisible(false);
        libraryPanel.open();
    }

    private boolean hasDraftWork(ShipPreviewType shipType) {
        return session.hasDraftWork(shipType);
    }

    private void loadLibraryPattern(HeraldryPatternData imported) {
        selection.clearPixelSelectionOverlay();
        session.importPattern(imported);
        if (draftTexture != null) {
            draftTexture.replacePixels(session.pixels());
            draftTexture.uploadIfDirty();
        }
        parts.switchContext(session.shipType(), previewStage);
        rebuildPreviewShip();
    }

    private final class LibraryController implements HeraldryLibraryPanel.Controller {
        @Override
        public HeraldryPatternData currentData() {
            return HeraldryTableScreen.this.currentData();
        }

        @Override
        public int previewStage() {
            return previewStage;
        }

        @Override
        public boolean hasDraftWork(ShipPreviewType shipType) {
            return HeraldryTableScreen.this.hasDraftWork(shipType);
        }

        @Override
        public void load(HeraldryPatternData data) {
            loadLibraryPattern(data);
        }

        @Override
        public void showStatus(Component message) {
            HeraldryTableScreen.this.showStatus(message);
        }
    }

    private void drawHelpPopupAboveEverything(GuiGraphics guiGraphics) {
        guiGraphics.flush();
        RenderSystem.disableDepthTest();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
        try {
            drawHelpPopup(guiGraphics);
        } finally {
            guiGraphics.pose().popPose();
            guiGraphics.flush();
            RenderSystem.enableDepthTest();
        }
    }

    private void drawHelpPopup(GuiGraphics guiGraphics) {
        int x = leftPos + 20;
        int y = topPos + 30;
        int width = imageWidth - 40;
        int height = imageHeight - 60;
        HeraldryGuiTheme.drawWindow(guiGraphics, x - 2, y - 2, width + 4, height + 4);
        guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.help.title"), x + 8, y + 8, HeraldryGuiTheme.INK, false);

        String[] paragraphs = {
                "section.modes", "layout", "paint", "parts", "view", "navigation",
                "section.controls", "controls", "selection",
                "section.patterns", "library", "workflow", "apply"
        };
        List<HelpLine> lines = new java.util.ArrayList<>();
        for (String paragraph : paragraphs) {
            int color = paragraph.startsWith("section.") ? HeraldryGuiTheme.INK : HeraldryGuiTheme.MUTED_INK;
            for (FormattedCharSequence line : font.split(Component.translatable("smallshipsheraldry.help." + paragraph), width - 24)) {
                lines.add(new HelpLine(line, color));
            }
            lines.add(new HelpLine(FormattedCharSequence.EMPTY, color));
        }
        int contentTop = y + 24;
        int contentBottom = y + height - 20;
        int visibleHeight = contentBottom - contentTop;
        helpMaxScroll = Math.max(0, lines.size() * 10 - visibleHeight);
        helpScroll = Mth.clamp(helpScroll, 0, helpMaxScroll);
        GuiScissor.enable(guiGraphics, x + 8, contentTop, x + width - 14, contentBottom);
        try {
            int lineY = contentTop - helpScroll;
            for (HelpLine line : lines) {
                if (lineY + 9 > contentTop && lineY < contentBottom) {
                    guiGraphics.drawString(font, line.text(), x + 8, lineY, line.color(), false);
                }
                lineY += 10;
            }
        } finally {
            guiGraphics.disableScissor();
        }
        if (helpMaxScroll > 0) {
            int trackX = x + width - 9;
            int thumbHeight = Math.max(12, visibleHeight * visibleHeight / (lines.size() * 10));
            int thumbY = contentTop + helpScroll * (visibleHeight - thumbHeight) / helpMaxScroll;
            guiGraphics.fill(trackX, contentTop, trackX + 3, contentBottom, HeraldryGuiTheme.MUTED_INK);
            guiGraphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, HeraldryGuiTheme.INK);
        }
        guiGraphics.drawString(font, Component.translatable(helpMaxScroll > 0
                ? "smallshipsheraldry.help.scroll_hint" : "smallshipsheraldry.help.close_hint"),
                x + 8, y + height - 12, HeraldryGuiTheme.MUTED_INK, false);
    }

    private record HelpLine(FormattedCharSequence text, int color) {}

    private void drawTableSlots(GuiGraphics guiGraphics, int x0, int y0) {
        drawTableSlot(guiGraphics, x0 + layout.paperSlotX(), y0 + layout.slotY(), menu.getPaperStack());
        drawTableSlot(guiGraphics, x0 + layout.resultSlotX(), y0 + layout.slotY(), menu.getResultStack());
        guiGraphics.blit(CRAFTING_TEXTURE,
                x0 + rightX() - 50, y0 + footerY(), 89, 35, 22, 17);
    }

    private void drawTableSlot(GuiGraphics graphics, int x, int y, ItemStack stack) {
        HeraldryGuiTheme.drawInset(graphics, x - 1, y - 1, 18, 18);
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        if (isInside(lastMouseX, lastMouseY, x - 1, y - 1, 18, 18)) renderSlotHighlight(graphics, x, y, 200);
    }

    private boolean handleModeTabClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        EditorMode[] modes = EditorMode.values();
        for (int index = 0; index < modes.length; index++) {
            int x = leftPos + MODE_TAB_X + index * MODE_TAB_WIDTH;
            int y = topPos + MODE_TAB_Y;
            if (isInside(mouseX, mouseY, x, y, MODE_TAB_WIDTH - 1, MODE_TAB_HEIGHT)) {
                if (modes[index] == EditorMode.PARTS) {
                    selection.clearPixelSelectionOverlay();
                }
                editorMode = modes[index];
                colorPicker.closePalette();
                colorPicker.setVisible(editorMode == EditorMode.PAINT && activeTool != PaintTool.SELECT);
                return true;
            }
        }
        return false;
    }

    private boolean handlePaintControlClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (activeTool == PaintTool.SELECT) {
            int x = leftPos + rightX() + 4;
            int y = topPos + 32;
            if (isInside(mouseX, mouseY, x, y, 42, 18)) {
                if (selection.hasSelection()) selection.copyPixelSelection(false);
                return true;
            }
            if (isInside(mouseX, mouseY, x + 46, y, 42, 18)) {
                if (selection.hasSelection()) selection.copyPixelSelection(true);
                return true;
            }
            if (isInside(mouseX, mouseY, x, y + 22, 88, 18)) {
                if (selection.hasClipboard()) selection.beginPaste();
                return true;
            }
            if (isInside(mouseX, mouseY, x, y + 48, 42, 18)) {
                if (selection.hasPasteTarget()) selection.commitPaste();
                return true;
            }
            if (isInside(mouseX, mouseY, x + 46, y + 48, 42, 18)) {
                if (selection.isPasting()) {
                    selection.cancelPaste();
                } else {
                    selection.clearPixelSelectionOverlay();
                }
                return true;
            }
            if (isInside(mouseX, mouseY, x, y + 70, 88, 18)) {
                selection.clearPixelSelection();
                return true;
            }
            return false;
        }
        int x = leftPos + rightX() + 4;
        if (activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER) {
            if (isInside(mouseX, mouseY, x, topPos + SIZE_Y, 18, 18)) {
                brushSize = Math.max(1, brushSize - 1);
                return true;
            }
            if (isInside(mouseX, mouseY, x + 70, topPos + SIZE_Y, 18, 18)) {
                brushSize = Math.min(8, brushSize + 1);
                return true;
            }
            if (isInside(mouseX, mouseY, x, topPos + STROKE_Y, 88, 18)) {
                choices.open(font, x, topPos + STROKE_Y, 88, leftPos + imageWidth - 8,
                        List.of(Component.translatable("smallshipsheraldry.stroke.freehand"),
                                Component.translatable("smallshipsheraldry.stroke.line_short")),
                        strokeMode.ordinal(), index -> strokeMode = StrokeMode.values()[index]);
                colorPicker.setVisible(false);
                return true;
            }
            if (isInside(mouseX, mouseY, x + 46, topPos + SYMMETRY_Y, 18, 18)) {
                mirrorVertical = !mirrorVertical;
                return true;
            }
            if (isInside(mouseX, mouseY, x + 70, topPos + SYMMETRY_Y, 18, 18)) {
                mirrorHorizontal = !mirrorHorizontal;
                return true;
            }
        } else if (activeTool == PaintTool.FILL && isInside(mouseX, mouseY, x, topPos + FILL_MODE_Y, 88, 18)) {
            choices.open(font, x, topPos + FILL_MODE_Y, 88, leftPos + imageWidth - 8,
                    java.util.Arrays.stream(FillMode.values()).map(FillMode::displayName).toList(),
                    fillMode.ordinal(), index -> fillMode = FillMode.values()[index]);
            colorPicker.setVisible(false);
            return true;
        }
        if (isInside(mouseX, mouseY, x, topPos + 187, 88, 17)) {
            colorPicker.togglePalette();
            return true;
        }
        if (isInside(mouseX, mouseY, x, topPos + 166, 88, 17)) {
            if (pushUndo()) {
                session.setBaseColor(colorPicker.color());
                markDirty();
                updatePreview();
            }
            return true;
        }
        return false;
    }

    private boolean handlePartsActionClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int x = leftPos + rightX() + 4;
        int y = topPos + 32;
        if (isInside(mouseX, mouseY, x, y + 16, 88, 17)) {
            parts.selectAll();
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 36, 88, 17)) {
            parts.deselect();
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 60, 42, 18)) {
            parts.hideSelected();
            return true;
        }
        if (isInside(mouseX, mouseY, x + 46, y + 60, 42, 18)) {
            parts.toggleIsolate();
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 82, 88, 17)) {
            parts.showAll();
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 108, 88, 18)) {
            if (!parts.selected().isEmpty()) {
                fillScope(colorPicker.color(), false);
            }
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 130, 88, 18)) {
            if (!parts.selected().isEmpty()) {
                fillScope(0, false);
            }
            return true;
        }
        return false;
    }

    private boolean handleViewControlClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x = leftPos + rightX() + 4;
        int y = topPos + 32;
        if (isInside(mouseX, mouseY, x, y, 88, 18)) {
            gridVisible = !gridVisible;
            return true;
        }
        float[] angles = {180, 0, 90, 270};
        for (int index = 0; index < angles.length; index++) {
            if (isInside(mouseX, mouseY, x, y + 26 + index * 22, 88, 18)) {
                setCamera(angles[index], 0);
                return true;
            }
        }
        if (!isInside(mouseX, mouseY, x, y + 120, 88, 18)) return false;
        camera.reset();
        return true;
    }

    private void setCamera(float yaw, float pitch) {
        camera.orient(yaw, pitch);
    }

    private void fillScope(int color, boolean confirmFullClear) {
        if (confirmFullClear && parts.selected().isEmpty() && System.currentTimeMillis() >= clearConfirmationUntil) {
            clearConfirmationUntil = System.currentTimeMillis() + 2_500L;
            return;
        }
        clearConfirmationUntil = 0L;
        if (!pushUndo()) {
            return;
        }
        if (!PaintableSailMesh.fillScopedPixels(previewShip, previewStage, session.pixels(), color, parts.selected(), parts.hidden())) {
            discardLatestUndo();
            return;
        }
        replaceDraftTexturePixels();
        commitPaintBuffer();
    }

    private boolean handleElementClick(double mouseX, double mouseY, int button) {
        if (!elementListVisible() || (button != 0 && button != 1) || !isInside(mouseX, mouseY, leftPos + ELEMENT_X, topPos + ELEMENT_Y, ELEMENT_WIDTH, previewHeight())) {
            return false;
        }
        int row = (int) ((mouseY - topPos - ELEMENT_Y) / ELEMENT_ROW_HEIGHT);
        int index = parts.scroll() + row;
        if (index >= 0 && index < parts.elements().size()) {
            if (button == 1) parts.toggleHidden(parts.elements().get(index).id());
            else parts.selectRow(index, hasShiftDown(), hasControlDown());
        }
        return true;
    }

    private boolean handleTableSlotClick(double mouseX, double mouseY, int button) {
        int slot = tableSlotAt(mouseX, mouseY);
        if (slot < 0) {
            return false;
        }

        if (slot == HeraldryTableBlockEntity.PAPER_SLOT) {
            if (button == 0) {
                sendSlotAction(slot, hasShiftDown() ? HeraldryTableMenu.SLOT_ACTION_INSERT_ALL : HeraldryTableMenu.SLOT_ACTION_INSERT);
            } else if (button == 1) {
                sendSlotAction(slot, hasShiftDown() ? HeraldryTableMenu.SLOT_ACTION_EXTRACT : HeraldryTableMenu.SLOT_ACTION_EXTRACT_ONE);
            }
        } else if (button == 0) {
            sendSlotAction(slot, HeraldryTableMenu.SLOT_ACTION_EXTRACT);
        }

        return true;
    }

    private int tableSlotAt(double mouseX, double mouseY) {
        if (isInside(mouseX, mouseY, leftPos + layout.paperSlotX() - 1, topPos + layout.slotY() - 1, 18, 18)) {
            return HeraldryTableBlockEntity.PAPER_SLOT;
        }
        if (isInside(mouseX, mouseY, leftPos + layout.resultSlotX() - 1, topPos + layout.slotY() - 1, 18, 18)) {
            return HeraldryTableBlockEntity.RESULT_SLOT;
        }
        return -1;
    }

    private boolean handleToolClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        PaintTool[] tools = PaintTool.values();
        for (int index = 0; index < tools.length; index++) {
            int x = leftPos + TOOL_X;
            int y = topPos + TOOL_Y + index * (TOOL_HEIGHT + TOOL_GAP);
            if (isInside(mouseX, mouseY, x, y, TOOL_WIDTH, TOOL_HEIGHT)) {
                setActiveTool(tools[index]);
                return true;
            }
        }
        return false;
    }

    private boolean handleColorClick(double mouseX, double mouseY, int button) {
        return colorPicker.mouseClicked(mouseX, mouseY, button);
    }

    private Optional<PaintableSailMesh.Hit> pickAtPreview(double mouseX, double mouseY, Set<String> hidden) {
        return PaintableSailMesh.pick(
                previewShip,
                leftPos + previewX(),
                topPos + PREVIEW_Y,
                leftPos + previewX() + previewWidth(),
                topPos + PREVIEW_Y + previewHeight(),
                camera.yaw(),
                camera.pitch(),
                previewZoom(),
                previewPanX(),
                previewPanY(),
                mouseX,
                mouseY,
                hidden
        );
    }

    private void replaceDraftTexturePixels() {
        if (draftTexture != null) {
            draftTexture.replacePixels(session.pixels());
        }
    }

    private void setActiveTool(PaintTool tool) {
        if (activeTool != tool && tool != PaintTool.SELECT) {
            selection.clearPixelSelectionOverlay();
        }
        activeTool = tool;
        colorPicker.setVisible(editorMode == EditorMode.PAINT && activeTool != PaintTool.SELECT);
    }

    private Optional<Integer> gridFaceIndexUnderMouse() {
        if (activeTool == PaintTool.SELECT || !isInsidePreview(lastMouseX, lastMouseY)) {
            return Optional.empty();
        }

        return surface.pick(lastMouseX, lastMouseY).filter(surface::accepts).map(PaintableSailMesh.Hit::faceIndex);
    }

    private boolean selectElementAtPreview(double mouseX, double mouseY) {
        Optional<PaintableSailMesh.Hit> hit = pickAtPreview(mouseX, mouseY, Set.of());
        hit.ifPresent(value -> parts.selectHit(value.elementId(), hasControlDown(), hasShiftDown(), visibleElementRows()));
        return true;
    }

    private void changePreviewType(ShipPreviewType newPreviewType) {
        if (newPreviewType == session.shipType()) {
            return;
        }
        HeraldryPatternData current = currentData();
        if (current == null || !draftSync.transfer(false, current)) {
            return;
        }
        selection.clearPixelSelectionOverlay();
        session.switchShip(newPreviewType);
        if (draftTexture != null) {
            draftTexture.replacePixels(session.pixels());
        }
        parts.switchContext(session.shipType(), previewStage);
        camera.center();
        rebuildPreviewShip();
    }

    private void changePreviewStage(int newStage) {
        int clampedStage = Mth.clamp(newStage, 0, 4);
        if (clampedStage == previewStage) {
            return;
        }
        selection.clearPixelSelectionOverlay();
        previewStage = clampedStage;
        parts.switchContext(session.shipType(), previewStage);
        PreviewShipFactory.setSailState(previewShip, previewStage);
    }

    private void exportPattern() {
        HeraldryPatternData data = currentData();
        if (data == null) {
            return;
        }
        draftSync.transfer(true, data);
    }

    public static void handleDraftSync(
            BlockPos blockPos,
            ShipPreviewType activeType,
            ShipPreviewType draftType,
            CompoundTag patternTag,
            boolean first,
            boolean last
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof HeraldryTableScreen screen) {
            screen.applyDraftSync(blockPos, activeType, draftType, patternTag, first, last);
        }
    }

    private void applyDraftSync(
            BlockPos blockPos,
            ShipPreviewType activeType,
            ShipPreviewType draftType,
            CompoundTag patternTag,
            boolean first,
            boolean last
    ) {
        if (!draftSync.receive(blockPos, activeType, draftType, patternTag, first, last)) {
            return;
        }
        selection.clearPixelSelectionOverlay();
        if (draftTexture != null) {
            draftTexture.replacePixels(session.pixels());
        }
        parts.clearContexts();
        parts.switchContext(session.shipType(), previewStage);
        rebuildPreviewShip();
    }

    private void sendSlotAction(int slot, int action) {
        draftSync.slotAction(slot, action);
    }

    private void rebuildPreviewShip() {
        previewShip = PreviewShipFactory.create(session.shipType(), session.pattern().withShipType(session.shipType()), previewStage);
        if (previewShip != null && (framing == null || framedType != session.shipType())) {
            framing = PreviewShipRenderer.measure(previewShip);
            framedType = session.shipType();
        }
    }

    private void updatePreview() {
        if (previewShip == null) {
            rebuildPreviewShip();
        } else {
            PreviewShipFactory.applyBaseColor(previewShip, session.pattern().baseColorArgb());
            PreviewShipFactory.setSailState(previewShip, previewStage);
        }
    }

    private HeraldryPatternData currentData() {
        try {
            return session.currentPattern();
        } catch (HeraldryPatternData.PatternTooComplexException exception) {
            showStatus(Component.translatable("smallshipsheraldry.message.pattern_too_complex"));
            return null;
        }
    }

    private void commitPaintBuffer() {
        if (currentData() != null) {
            markDirty();
            updatePreview();
            return;
        }
        session.restoreFailedEdit().ifPresent(this::applyRestore);
    }

    private void applyRestore(HeraldryEditorSession.Restore restore) {
        if (draftTexture != null) {
            draftTexture.replacePixels(session.pixels());
        }
        if (restore.shipChanged()) {
            parts.switchContext(session.shipType(), previewStage);
            rebuildPreviewShip();
        } else {
            updatePreview();
        }
    }

    private void discardLatestUndo() {
        session.discardLatestUndo();
    }

    private void showStatus(Component message) {
        statusMessage = message;
        statusMessageUntil = System.currentTimeMillis() + 4_000L;
    }

    private void markDirty() {
        session.markDirty();
    }

    private boolean pushUndo() {
        try {
            return session.pushUndo();
        } catch (HeraldryPatternData.PatternTooComplexException exception) {
            showStatus(Component.translatable("smallshipsheraldry.message.pattern_too_complex"));
            return false;
        }
    }

    private void undo() {
        try {
            session.undo().ifPresent(this::applyRestore);
        } catch (HeraldryPatternData.PatternTooComplexException exception) {
            showStatus(Component.translatable("smallshipsheraldry.message.pattern_too_complex"));
        }
    }

    private void redo() {
        try {
            session.redo().ifPresent(this::applyRestore);
        } catch (HeraldryPatternData.PatternTooComplexException exception) {
            showStatus(Component.translatable("smallshipsheraldry.message.pattern_too_complex"));
        }
    }

    private Component gridButtonText() {
        return Component.translatable(gridVisible
                ? "smallshipsheraldry.button.grid_on"
                : "smallshipsheraldry.button.grid_off");
    }

    private void renderEditorTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (editorMode == EditorMode.PARTS && isInside(mouseX, mouseY, leftPos + TOOL_X, topPos + TOOL_Y, TOOL_WIDTH, TOOL_HEIGHT)) {
            drawTooltip(guiGraphics, Component.translatable(partsListVisible
                    ? "smallshipsheraldry.parts.collapse" : "smallshipsheraldry.parts.expand"), mouseX, mouseY);
            return;
        }
        if (isInside(mouseX, mouseY, leftPos + layout.shipNameX(), topPos + layout.navigationY(), layout.shipNameWidth(), 18)) {
            drawTooltip(guiGraphics, session.shipType().getDisplayName(), mouseX, mouseY);
            return;
        }
        if (editorMode == EditorMode.PARTS
                && isInside(mouseX, mouseY, leftPos + rightX() + 50, topPos + 92, 42, 18)) {
            drawTooltip(guiGraphics, Component.translatable("smallshipsheraldry.tooltip.isolate"), mouseX, mouseY);
            return;
        }
        if (elementListVisible() && isInside(mouseX, mouseY, leftPos + ELEMENT_X, topPos + ELEMENT_Y, ELEMENT_WIDTH, previewHeight())) {
            int row = (mouseY - (topPos + ELEMENT_Y)) / ELEMENT_ROW_HEIGHT;
            int index = parts.scroll() + row;
            List<PaintableSailMesh.Element> elements = parts.elements();
            if (index >= 0 && index < elements.size()) {
                drawTooltip(guiGraphics, Component.literal(elements.get(index).id()), mouseX, mouseY);
            }
            return;
        }
        if (editorMode != EditorMode.PAINT) {
            return;
        }
        if ((activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER)
                && isInside(mouseX, mouseY, leftPos + rightX() + 4, topPos + STROKE_Y, 88, 18)) {
            drawTooltip(guiGraphics, font.split(Component.translatable("smallshipsheraldry.tooltip.stroke_mode"), 180), mouseX, mouseY);
            return;
        }
        if ((activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER)
                && isInside(mouseX, mouseY, leftPos + rightX() + 50, topPos + SYMMETRY_Y, 18, 18)) {
            drawTooltip(guiGraphics, Component.translatable("smallshipsheraldry.tooltip.mirror_vertical"), mouseX, mouseY);
            return;
        }
        if ((activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER)
                && isInside(mouseX, mouseY, leftPos + rightX() + 74, topPos + SYMMETRY_Y, 18, 18)) {
            drawTooltip(guiGraphics, Component.translatable("smallshipsheraldry.tooltip.mirror_horizontal"), mouseX, mouseY);
            return;
        }
        PaintTool[] tools = PaintTool.values();
        for (int index = 0; index < tools.length; index++) {
            int x = leftPos + TOOL_X;
            int y = topPos + TOOL_Y + index * (TOOL_HEIGHT + TOOL_GAP);
            if (isInside(mouseX, mouseY, x, y, TOOL_WIDTH, TOOL_HEIGHT)) {
                drawTooltip(guiGraphics, tools[index].displayName(), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderTableSlotTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int slot = tableSlotAt(mouseX, mouseY);
        if (slot == HeraldryTableBlockEntity.PAPER_SLOT) {
            drawTooltip(guiGraphics, Component.translatable("smallshipsheraldry.tooltip.paper_slot"), mouseX, mouseY);
        } else if (slot == HeraldryTableBlockEntity.RESULT_SLOT && !menu.getResultStack().isEmpty()) {
            drawTooltip(guiGraphics, menu.getResultStack(), mouseX, mouseY);
        } else if (slot == HeraldryTableBlockEntity.RESULT_SLOT && menu.getResultStack().isEmpty()) {
            drawTooltip(guiGraphics, Component.translatable("smallshipsheraldry.tooltip.result_slot"), mouseX, mouseY);
        }
    }

    private void drawTooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
        drawTooltip(graphics, font.split(text, Math.min(220, width - 16)), mouseX, mouseY);
    }

    private void drawTooltip(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, stack).stream()
                .flatMap(line -> font.split(line, Math.min(220, width - 16)).stream()).toList();
        drawTooltip(graphics, lines, mouseX, mouseY);
    }

    private void drawTooltip(GuiGraphics graphics, List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        graphics.renderTooltip(font, lines, (screenWidth, screenHeight, x, y, tooltipWidth, tooltipHeight) ->
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE.positionTooltip(
                        width, height, x, y, tooltipWidth, tooltipHeight), mouseX, mouseY);
    }

    private void zoomPreviewAt(double mouseX, double mouseY, double amount) {
        camera.zoomAt(mouseX, mouseY, amount,
                leftPos + previewX() + previewWidth() * 0.5F,
                topPos + PREVIEW_Y + previewHeight() * 0.5F);
    }

    private static String shortElementName(String elementId) {
        int slash = elementId.lastIndexOf('/');
        return slash >= 0 ? elementId.substring(slash + 1) : elementId;
    }

    private int visibleElementRows() {
        return previewHeight() / ELEMENT_ROW_HEIGHT;
    }

    private void drawControl(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean selected, Component text) {
        drawControl(guiGraphics, x, y, width, height, selected, true, text);
    }

    private void drawControl(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean selected, boolean enabled, Component text) {
        boolean hovered = isInside(lastMouseX, lastMouseY, x, y, width, height);
        HeraldryGuiTheme.drawButton(guiGraphics, font, x, y, width, height, selected, hovered, enabled, text, 2);
    }

    private void drawToggleControl(GuiGraphics graphics, int x, int y, int width, int height, boolean selected, Component text) {
        HeraldryGuiTheme.drawToggle(graphics, x, y, width, height, selected,
                isInside(lastMouseX, lastMouseY, x, y, width, height));
        Component clipped = Component.literal(font.plainSubstrByWidth(text.getString(), width - 6));
        drawCenteredNoShadow(graphics, clipped, x + width / 2, y + (height - 8) / 2,
                selected ? HeraldryGuiTheme.BUTTON_TEXT : HeraldryGuiTheme.INK);
    }

    private void drawChoiceControl(GuiGraphics graphics, int x, int y, int width, Component text) {
        boolean hovered = isInside(lastMouseX, lastMouseY, x, y, width, 18);
        HeraldryGuiTheme.drawButton(graphics, x, y, width, 18, choices.isOpen(), hovered, true);
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), width - 26), x + 5, y + 5,
                HeraldryGuiTheme.BUTTON_TEXT, false);
        int center = x + width - 9;
        for (int row = 0; row < 3; row++) {
            int arrowY = y + (choices.isOpen() ? 9 - row : 7 + row);
            graphics.fill(center - 2 + row, arrowY, center + 3 - row, arrowY + 1, HeraldryGuiTheme.BUTTON_TEXT);
        }
    }

    private void drawIconControl(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean selected, int iconU) {
        boolean hovered = isInside(lastMouseX, lastMouseY, x, y, width, height);
        HeraldryGuiTheme.drawToggle(guiGraphics, x, y, width, height, selected, hovered);
        guiGraphics.setColor(selected ? 1 : 0.22F, selected ? 1 : 0.22F, selected ? 1 : 0.22F, 1);
        guiGraphics.blit(TOOL_ICONS, x + (width - 16) / 2, y + (height - 16) / 2, iconU, 0, 16, 16, TOOL_TEXTURE_WIDTH, 16);
        guiGraphics.setColor(1, 1, 1, 1);
    }

    private void drawCenteredNoShadow(GuiGraphics guiGraphics, Component text, int centerX, int y, int color) {
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        if (elementListVisible() && isInside(mouseX, mouseY, leftPos + ELEMENT_X - 1, topPos + ELEMENT_Y - 1,
                ELEMENT_WIDTH + 2, previewHeight() + 2)) return false;
        return isInside(mouseX, mouseY, leftPos + previewX(), topPos + PREVIEW_Y, previewWidth(), previewHeight());
    }

    private final class EditorSurface implements HeraldryEditorSurface {
        public Ship ship() { return previewShip; }
        public int stage() { return previewStage; }
        public Bounds bounds() { return new Bounds(leftPos + previewX(), topPos + PREVIEW_Y, previewWidth(), previewHeight()); }
        public Set<String> selectedElements() { return parts.selected(); }
        public Set<String> hiddenElements() { return parts.hidden(); }
        public Optional<PaintableSailMesh.Hit> pick(double x, double y) { return pickAtPreview(x, y, parts.hidden()); }
        public HeraldryDraftTexture texture() { return draftTexture; }
    }

    private final class EditorEdits implements HeraldryEditorEdits {
        public boolean pushUndo() { return HeraldryTableScreen.this.pushUndo(); }
        public void commit() { commitPaintBuffer(); }
        public void status(Component message) { showStatus(message); }
    }

    private enum EditorMode {
        PAINT("paint"),
        PARTS("parts"),
        VIEW("view");

        private final String label;

        EditorMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private Component displayName() {
            return Component.translatable("smallshipsheraldry.mode." + label);
        }

        private static EditorMode byName(String name) {
            for (EditorMode mode : values()) {
                if (mode.label.equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return PAINT;
        }
    }

}
