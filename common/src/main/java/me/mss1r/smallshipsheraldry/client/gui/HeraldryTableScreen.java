package me.mss1r.smallshipsheraldry.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.talhanation.smallships.world.entity.ship.Ship;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import me.mss1r.smallshipsheraldry.SmallShipsHeraldry;
import me.mss1r.smallshipsheraldry.client.HeraldryClientSettings;
import me.mss1r.smallshipsheraldry.client.render.HeraldryDraftTexture;
import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;
import me.mss1r.smallshipsheraldry.client.render.PreviewShipFactory;
import me.mss1r.smallshipsheraldry.client.render.PreviewShipRenderer;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.HeraldryTableDrafts;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import me.mss1r.smallshipsheraldry.network.HeraldryPackets;
import me.mss1r.smallshipsheraldry.network.HeraldryPatternTransfer;
import me.mss1r.smallshipsheraldry.world.inventory.HeraldryTableMenu;
import me.mss1r.smallshipsheraldry.world.level.block.entity.HeraldryTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class HeraldryTableScreen extends AbstractContainerScreen<HeraldryTableMenu> {
    private static final ResourceLocation TOOL_ICONS = new ResourceLocation(SmallShipsHeraldry.MOD_ID, "textures/gui/heraldry_tools.png");

    private static final int ELEMENT_X = 10;
    private static final int ELEMENT_Y = 36;
    private static final int ELEMENT_WIDTH = 116;
    private static final int ELEMENT_HEIGHT = 210;
    private static final int ELEMENT_ROW_HEIGHT = 13;
    private static final int MODE_TAB_X = ELEMENT_X - 2;
    private static final int MODE_TAB_Y = 20;
    private static final int MODE_TAB_WIDTH = 40;
    private static final int MODE_TAB_HEIGHT = 13;

    private static final int PREVIEW_X = 136;
    private static final int PREVIEW_Y = 36;
    private static final int PREVIEW_WIDTH = 226;
    private static final int PREVIEW_HEIGHT = 160;

    private static final int RIGHT_X = 374;
    private static final int RIGHT_WIDTH = 88;
    private static final int HELP_SIZE = 12;
    private static final int HELP_MARGIN = 5;
    private static final int HELP_Y = 5;
    private static final int TOOL_X = ELEMENT_X;
    private static final int TOOL_Y = 42;
    private static final int TOOL_WIDTH = 18;
    private static final int TOOL_HEIGHT = 18;
    private static final int TOOL_GAP = 4;
    private static final int TOOL_TEXTURE_WIDTH = 144;
    private static final int SETTINGS_LABEL_X = ELEMENT_X;
    private static final int SIZE_Y = 68;
    private static final int STROKE_Y = 88;
    private static final int SYMMETRY_Y = 108;
    private static final int FILL_MODE_Y = 68;
    private static final int SCOPE_ACTION_Y = 164;
    private static final int LIBRARY_BUTTON_Y = 188;

    private final HeraldryEditorSession session = new HeraldryEditorSession();
    private HeraldryDraftTexture draftTexture;
    private Ship previewShip;
    private final Map<String, Set<String>> hiddenElementsByContext = new HashMap<>();
    private final Map<String, Set<String>> selectedElementsByContext = new HashMap<>();
    private Set<String> hiddenElements = new LinkedHashSet<>();
    private Set<String> selectedElementIds = new LinkedHashSet<>();
    private HeraldryColorPicker colorPicker;
    private PaperButton printButton;
    private PaperButton libraryButton;
    private HeraldryLibraryPanel libraryPanel;
    private int previewStage = 4;
    private float previewYaw = 180.0F;
    private float previewPitch = -10.0F;
    private float previewZoom = 1.0F;
    private float previewPanX = 0.0F;
    private float previewPanY = 0.0F;
    private PaintTool activeTool = PaintTool.BRUSH;
    private EditorMode editorMode = EditorMode.PAINT;
    private StrokeMode strokeMode = StrokeMode.FREEHAND;
    private FillMode fillMode = FillMode.AREA;
    private int brushSize = 1;
    private boolean mirrorVertical;
    private boolean mirrorHorizontal;
    private int elementScroll;
    private int selectionAnchorIndex = -1;
    private boolean paintingPreview;
    private boolean temporaryPicker;
    private boolean rotatingPreview;
    private boolean panningPreview;
    private boolean strokeUndoCaptured;
    private PaintableSailMesh.Hit strokeStartHit;
    private PaintableSailMesh.Hit lastStrokeHit;
    private double strokeStartScreenX;
    private double strokeStartScreenY;
    private double strokeEndScreenX;
    private double strokeEndScreenY;
    private boolean selectingPixels;
    private boolean pixelSelectionActive;
    private PaintableSailMesh.Hit selectionStartHit;
    private PaintableSailMesh.Hit selectionEndHit;
    private List<PaintableSailMesh.ScreenPixel> surfaceSelection = List.of();
    private SelectionClipboard clipboard;
    private boolean pasteActive;
    private boolean movingPaste;
    private int pasteX;
    private int pasteY;
    private PaintableSailMesh.Hit pasteTargetHit;
    private Map<Integer, Integer> pasteReplacements = Map.of();
    private final Set<Integer> pastePreviewIndices = new LinkedHashSet<>();
    private boolean gridVisible = true;
    private boolean helpVisible;
    private double lastMouseX;
    private double lastMouseY;
    private boolean editorSettingsLoaded;
    private boolean draftSyncRequested;
    private int nextTransferId;
    private HeraldryTableDrafts pendingDraftSync = HeraldryTableDrafts.blank();
    private Component statusMessage;
    private long statusMessageUntil;
    private long clearConfirmationUntil;

    public HeraldryTableScreen(HeraldryTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 470;
        this.imageHeight = 260;
        this.titleLabelX = ELEMENT_X;
        this.titleLabelY = 10;
        this.inventoryLabelY = 30000;
    }

    @Override
    protected void init() {
        super.init();
        paintingPreview = false;
        temporaryPicker = false;
        rotatingPreview = false;
        panningPreview = false;
        selectingPixels = false;
        movingPaste = false;
        strokeUndoCaptured = false;
        strokeStartHit = null;
        lastStrokeHit = null;
        if (colorPicker == null) {
            colorPicker = new HeraldryColorPicker(font, RIGHT_X, RIGHT_WIDTH);
        }
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
            previewYaw = settings.previewYaw();
            previewPitch = settings.previewPitch();
            previewZoom = settings.previewZoom();
            editorSettingsLoaded = true;
        }
        session.initialize(menu.getInitialDrafts());
        switchElementContext();
        if (draftTexture == null) {
            try {
                draftTexture = new HeraldryDraftTexture("table", session.pixels());
            } catch (RuntimeException exception) {
                SmallShipsHeraldry.LOGGER.error("Unable to allocate the heraldry editor texture", exception);
                showStatus(Component.translatable("smallshipsheraldry.message.texture_failed"));
            }
        }
        rebuildPreviewShip();
        requestDraftSync();

        int x0 = leftPos;
        int y0 = topPos;
        PaperEditBox colorInput = colorPicker.init(x0, y0);
        colorPicker.setVisible(editorMode == EditorMode.PAINT && activeTool != PaintTool.SELECT && !libraryPanelVisible());
        addRenderableWidget(colorInput);

        int previewCenter = x0 + PREVIEW_X + PREVIEW_WIDTH / 2;
        int controlY = y0 + 206;
        addRenderableWidget(new PaperButton(x0 + imageWidth - HELP_SIZE - HELP_MARGIN, y0 + HELP_Y, HELP_SIZE, HELP_SIZE, Component.literal("?"), button -> helpVisible = !helpVisible));
        addRenderableWidget(new PaperButton(x0 + PREVIEW_X + 12, controlY, 20, 18, Component.literal("<"), button -> changePreviewType(session.shipType().previous())));
        addRenderableWidget(new PaperButton(x0 + PREVIEW_X + PREVIEW_WIDTH - 32, controlY, 20, 18, Component.literal(">"), button -> changePreviewType(session.shipType().next())));
        addRenderableWidget(new PaperButton(previewCenter - 62, controlY, 24, 18, Component.literal("S<"), button -> changePreviewStage(previewStage - 1)));
        addRenderableWidget(new PaperButton(previewCenter + 38, controlY, 24, 18, Component.literal("S>"), button -> changePreviewStage(previewStage + 1)));
        if (libraryPanel == null) {
            libraryPanel = new HeraldryLibraryPanel(font, new LibraryController());
        }
        libraryPanel.init(x0, y0);
        libraryButton = addRenderableWidget(new PaperButton(x0 + ELEMENT_X, y0 + LIBRARY_BUTTON_Y, ELEMENT_WIDTH, 17,
                Component.translatable("smallshipsheraldry.button.library"), button -> openLibrary()));
        printButton = addRenderableWidget(new PaperButton(x0 + RIGHT_X + 4, y0 + 230, 82, 18, Component.translatable("smallshipsheraldry.button.export"), button -> exportPattern()));
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
            HeraldryGuiTheme.drawPreviewInset(guiGraphics, x0 + PREVIEW_X - 4, y0 + PREVIEW_Y - 4, PREVIEW_WIDTH + 8, PREVIEW_HEIGHT + 8);
        }
        drawRightPanel(guiGraphics, x0, y0);
        drawTableSlots(guiGraphics, x0, y0);

        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.screen.preview"), x0 + PREVIEW_X + PREVIEW_WIDTH / 2, y0 + 18, HeraldryGuiTheme.INK);
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.screen.stage", previewStage + 1), x0 + PREVIEW_X + PREVIEW_WIDTH / 2, y0 + 211, HeraldryGuiTheme.MUTED_INK);
        drawCenteredNoShadow(guiGraphics, session.shipType().getDisplayName(), x0 + PREVIEW_X + PREVIEW_WIDTH / 2, y0 + 230, HeraldryGuiTheme.SELECTED);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (printButton != null) {
            printButton.active = !menu.getPaperStack().isEmpty() && menu.getResultStack().isEmpty();
        }
        if (libraryButton != null) {
            libraryButton.visible = editorMode == EditorMode.PAINT && !libraryPanelVisible() && !helpVisible;
        }
        if (libraryPanel != null) {
            libraryPanel.tick();
        }
        if (session.saveDue(System.currentTimeMillis())) {
            sendDraftUpdate();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        colorPicker.setVisible(editorMode == EditorMode.PAINT && activeTool != PaintTool.SELECT && !libraryPanelVisible() && !helpVisible);
        if (draftTexture != null) {
            draftTexture.uploadIfDirty();
        }
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (libraryPanelVisible()) {
            libraryPanel.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        if (helpVisible) {
            drawHelpPopupAboveEverything(guiGraphics);
            return;
        }
        if (statusMessage != null && System.currentTimeMillis() < statusMessageUntil) {
            drawCenteredNoShadow(guiGraphics, statusMessage, width / 2, topPos + imageHeight + 6, 0xFFFF7777);
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
        renderTableSlotTooltip(guiGraphics, mouseX, mouseY);
        renderEditorTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void removed() {
        sendDraftUpdate();
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
                previewYaw,
                previewPitch,
                previewZoom
        ));
        closeClipboard();
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
        if (libraryPanelVisible()) {
            return libraryPanel.mouseClicked(mouseX, mouseY, button, hasShiftDown());
        }
        if (helpVisible && !isInside(mouseX, mouseY, leftPos + imageWidth - HELP_SIZE - HELP_MARGIN, topPos + HELP_Y, HELP_SIZE, HELP_SIZE)) {
            helpVisible = false;
            return true;
        }
        if (handleModeTabClick(mouseX, mouseY, button)) {
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
                clearPixelSelectionOverlay();
                if (hasShiftDown()) {
                    panningPreview = true;
                } else {
                    rotatingPreview = true;
                }
                return true;
            }
            if (button == 2) {
                clearPixelSelectionOverlay();
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
                return beginPixelSelection(mouseX, mouseY);
            }
            temporaryPicker = hasAltDown();
            if ((activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER) && strokeMode == StrokeMode.LINE && !temporaryPicker) {
                Optional<PaintableSailMesh.Hit> hit = pickAtPreview(mouseX, mouseY, hiddenElements);
                if (hit.isEmpty() || !isHitInSelection(hit.get())) {
                    return true;
                }
                paintingPreview = true;
                strokeUndoCaptured = false;
                strokeStartHit = hit.get();
                lastStrokeHit = hit.get();
                strokeStartScreenX = mouseX;
                strokeStartScreenY = mouseY;
                strokeEndScreenX = mouseX;
                strokeEndScreenY = mouseY;
                return true;
            }
            paintingPreview = activeTool.drags() && !temporaryPicker;
            strokeUndoCaptured = false;
            return useToolOnPreview(mouseX, mouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (libraryPanelVisible()) {
            return libraryPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (colorPicker.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        if (selectingPixels && button == 0) {
            updatePixelSelection(mouseX, mouseY);
            return true;
        }
        if (movingPaste && button == 0) {
            movePaste(mouseX, mouseY);
            return true;
        }
        if (rotatingPreview && button == 1) {
            previewYaw = (previewYaw + (float) dragX * 1.4F) % 360.0F;
            previewPitch = Mth.clamp(previewPitch + (float) dragY * 1.2F, -80.0F, 80.0F);
            return true;
        }
        if (panningPreview && (button == 1 || button == 2)) {
            previewPanX += (float) dragX;
            previewPanY += (float) dragY;
            return true;
        }
        if (paintingPreview && button == 0) {
            if (strokeMode == StrokeMode.LINE && !temporaryPicker) {
                strokeEndScreenX = mouseX;
                strokeEndScreenY = mouseY;
                Optional<PaintableSailMesh.Hit> hit = pickAtPreview(mouseX, mouseY, hiddenElements);
                if (hit.isPresent() && isHitInSelection(hit.get())) {
                    lastStrokeHit = hit.get();
                }
                return true;
            }
            useToolOnPreview(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (libraryPanelVisible()) {
            return libraryPanel.mouseReleased(button);
        }
        boolean completedPaintStroke = paintingPreview && button == 0;
        boolean completedLine = completedPaintStroke && strokeMode == StrokeMode.LINE && strokeStartHit != null && !temporaryPicker;
        if (completedLine) {
            applyLineStroke();
        }
        if (button == 0 || button == 1 || button == 2) {
            paintingPreview = false;
            rotatingPreview = false;
            panningPreview = false;
            selectingPixels = false;
            movingPaste = false;
            colorPicker.mouseReleased();
            strokeUndoCaptured = false;
            temporaryPicker = false;
            strokeStartHit = null;
            lastStrokeHit = null;
        }
        if (completedPaintStroke && !completedLine) {
            commitPaintBuffer();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (libraryPanelVisible()) {
            return libraryPanel.keyPressed(keyCode, scanCode, modifiers);
        }
        if (colorPicker.isInputFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (helpVisible && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            helpVisible = false;
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            copyPixelSelection(false);
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_X) {
            copyPixelSelection(true);
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            setActiveTool(PaintTool.SELECT);
            editorMode = EditorMode.PAINT;
            beginPaste();
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
        if (pasteActive && keyCode == GLFW.GLFW_KEY_ENTER) {
            commitPaste();
            return true;
        }
        if (pasteActive && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelPaste();
            return true;
        }
        if (pasteActive && (keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_DOWN)) {
            int step = hasShiftDown() ? 8 : 1;
            int deltaX = keyCode == GLFW.GLFW_KEY_LEFT ? -step : keyCode == GLFW.GLFW_KEY_RIGHT ? step : 0;
            int deltaY = keyCode == GLFW.GLFW_KEY_UP ? -step : keyCode == GLFW.GLFW_KEY_DOWN ? step : 0;
            movePasteBy(deltaX, deltaY);
            return true;
        }
        if (activeTool == PaintTool.SELECT && keyCode == GLFW.GLFW_KEY_DELETE) {
            clearPixelSelection();
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
            if (!selectedElementIds.isEmpty()) {
                for (String elementId : selectedElementIds) {
                    hiddenElements.add(elementId);
                }
                return true;
            }
            if (isInsidePreview(lastMouseX, lastMouseY)) {
                return selectElementAtPreview(lastMouseX, lastMouseY);
            }
        }
        if (keyCode == GLFW.GLFW_KEY_U) {
            hiddenElements.clear();
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
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (libraryPanelVisible()) {
            return libraryPanel.mouseScrolled(mouseX, mouseY, amount);
        }
        if (isInsidePreview(mouseX, mouseY)) {
            clearPixelSelectionOverlay();
            zoomPreviewAt(mouseX, mouseY, amount);
            return true;
        }
        if (editorMode == EditorMode.PAINT
                && (activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER)
                && isInside(mouseX, mouseY, leftPos + 60, topPos + SIZE_Y, 58, 16)) {
            brushSize = Mth.clamp(brushSize + (int) Math.signum(amount), 1, 8);
            return true;
        }
        if (editorMode == EditorMode.PARTS && isInside(mouseX, mouseY, leftPos + ELEMENT_X, topPos + ELEMENT_Y, ELEMENT_WIDTH, ELEMENT_HEIGHT)) {
            List<PaintableSailMesh.Element> elements = elements();
            int maxScroll = Math.max(0, elements.size() - visibleElementRows());
            elementScroll = Mth.clamp(elementScroll - (int) Math.signum(amount), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (libraryPanelVisible()) {
            return libraryPanel.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
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
        int left = x0 + ELEMENT_X;
        int top = y0 + ELEMENT_Y;
        HeraldryGuiTheme.drawPanel(guiGraphics, left - 4, top - 18, ELEMENT_WIDTH + 8, ELEMENT_HEIGHT + 22);
        drawModeTabs(guiGraphics, x0, y0);

        switch (editorMode) {
            case PAINT -> drawPaintPanel(guiGraphics, x0, y0);
            case PARTS -> drawElementRows(guiGraphics, x0, y0);
            case VIEW -> drawViewPanel(guiGraphics, x0, y0);
        }
    }

    private void drawModeTabs(GuiGraphics guiGraphics, int x0, int y0) {
        EditorMode[] modes = EditorMode.values();
        for (int index = 0; index < modes.length; index++) {
            int x = x0 + MODE_TAB_X + index * MODE_TAB_WIDTH;
            int y = y0 + MODE_TAB_Y;
            boolean selected = modes[index] == editorMode;
            drawControl(guiGraphics, x, y, MODE_TAB_WIDTH - 1, MODE_TAB_HEIGHT, selected, modes[index].displayName());
        }
    }

    private void drawPaintPanel(GuiGraphics guiGraphics, int x0, int y0) {
        drawToolButtons(guiGraphics, x0, y0);
        if (activeTool == PaintTool.SELECT) {
            Component hint = Component.translatable(pasteActive
                    ? "smallshipsheraldry.selection.move_hint"
                    : "smallshipsheraldry.selection.drag_hint");
            int lineY = y0 + 72;
            for (FormattedCharSequence line : font.split(hint, ELEMENT_WIDTH)) {
                guiGraphics.drawString(font, line, x0 + ELEMENT_X, lineY, HeraldryGuiTheme.MUTED_INK, false);
                lineY += 10;
            }
            drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.selection.shortcuts"),
                    x0 + ELEMENT_X + ELEMENT_WIDTH / 2, y0 + 132, HeraldryGuiTheme.MUTED_INK);
            return;
        }
        if (activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER) {
            guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.control.size"), x0 + SETTINGS_LABEL_X, y0 + SIZE_Y + 4, HeraldryGuiTheme.INK, false);
            drawControl(guiGraphics, x0 + 62, y0 + SIZE_Y, 15, 16, false, Component.literal("-"));
            drawControl(guiGraphics, x0 + 79, y0 + SIZE_Y, 20, 16, true, Component.literal(Integer.toString(brushSize)));
            drawControl(guiGraphics, x0 + 101, y0 + SIZE_Y, 15, 16, false, Component.literal("+"));

            guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.control.stroke"), x0 + SETTINGS_LABEL_X, y0 + STROKE_Y + 4, HeraldryGuiTheme.INK, false);
            drawControl(guiGraphics, x0 + 50, y0 + STROKE_Y, 32, 16, strokeMode == StrokeMode.FREEHAND, Component.translatable("smallshipsheraldry.stroke.freehand_short"));
            drawControl(guiGraphics, x0 + 84, y0 + STROKE_Y, 32, 16, strokeMode == StrokeMode.LINE, Component.translatable("smallshipsheraldry.stroke.line_short"));

            guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.control.symmetry"), x0 + SETTINGS_LABEL_X, y0 + SYMMETRY_Y + 4, HeraldryGuiTheme.INK, false);
            drawIconControl(guiGraphics, x0 + 76, y0 + SYMMETRY_Y, 18, 18, mirrorVertical, 64);
            drawIconControl(guiGraphics, x0 + 96, y0 + SYMMETRY_Y, 18, 18, mirrorHorizontal, 80);
        } else if (activeTool == PaintTool.FILL) {
            guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.control.mode"), x0 + SETTINGS_LABEL_X, y0 + FILL_MODE_Y + 4, HeraldryGuiTheme.INK, false);
            drawControl(guiGraphics, x0 + 42, y0 + FILL_MODE_Y, 16, 16, false, Component.literal("<"));
            int iconU = fillMode == FillMode.REPLACE ? 96 : 48;
            drawIconControl(guiGraphics, x0 + 60, y0 + FILL_MODE_Y, 18, 18, true, iconU);
            drawControl(guiGraphics, x0 + 80, y0 + FILL_MODE_Y, 16, 16, false, Component.literal(">"));
            drawCenteredNoShadow(guiGraphics, fillMode.displayName(), x0 + ELEMENT_X + ELEMENT_WIDTH / 2, y0 + FILL_MODE_Y + 21, HeraldryGuiTheme.MUTED_INK);
        }

        Component scope = selectedElementIds.isEmpty()
                ? Component.translatable("smallshipsheraldry.scope.stage")
                : Component.translatable("smallshipsheraldry.scope.selected", selectedElementIds.size());
        drawCenteredNoShadow(guiGraphics, scope, x0 + ELEMENT_X + ELEMENT_WIDTH / 2, y0 + SCOPE_ACTION_Y - 12, HeraldryGuiTheme.MUTED_INK);
        drawControl(guiGraphics, x0 + ELEMENT_X, y0 + SCOPE_ACTION_Y, 56, 17, false, Component.translatable("smallshipsheraldry.button.fill"));
        Component clearText = selectedElementIds.isEmpty() && System.currentTimeMillis() < clearConfirmationUntil
                ? Component.translatable("smallshipsheraldry.button.confirm")
                : Component.translatable("smallshipsheraldry.button.clear");
        drawControl(guiGraphics, x0 + ELEMENT_X + 60, y0 + SCOPE_ACTION_Y, 56, 17, false, clearText);
    }

    private void drawElementRows(GuiGraphics guiGraphics, int x0, int y0) {
        int left = x0 + ELEMENT_X;
        int top = y0 + ELEMENT_Y;

        List<PaintableSailMesh.Element> elements = elements();
        int rows = visibleElementRows();
        for (int row = 0; row < rows; row++) {
            int index = elementScroll + row;
            int rowTop = top + row * ELEMENT_ROW_HEIGHT;
            if (index >= elements.size()) {
                guiGraphics.fill(left, rowTop, left + ELEMENT_WIDTH, rowTop + 11, HeraldryGuiTheme.PAPER_ALT);
                continue;
            }

            PaintableSailMesh.Element element = elements.get(index);
            boolean hidden = hiddenElements.contains(element.id());
            boolean selected = selectedElementIds.contains(element.id());
            guiGraphics.fill(left, rowTop, left + ELEMENT_WIDTH, rowTop + 11, selected ? HeraldryGuiTheme.GOLD : hidden ? HeraldryGuiTheme.INSET : HeraldryGuiTheme.PAPER_ALT);
            guiGraphics.fill(left + 2, rowTop + 2, left + 9, rowTop + 9, hidden ? HeraldryGuiTheme.PAPER_ALT : selected ? HeraldryGuiTheme.PAPER : HeraldryGuiTheme.SELECTED);
            if (hidden) {
                guiGraphics.fill(left + 2, rowTop + 5, left + 9, rowTop + 6, HeraldryGuiTheme.MUTED_INK);
            }
            String label = font.plainSubstrByWidth(friendlyElementName(elements, index).getString(), ELEMENT_WIDTH - 16);
            guiGraphics.drawString(font, label, left + 13, rowTop + 2, hidden ? HeraldryGuiTheme.MUTED_INK : HeraldryGuiTheme.INK, false);
        }
    }

    private void drawViewPanel(GuiGraphics guiGraphics, int x0, int y0) {
        int x = x0 + ELEMENT_X;
        int y = y0 + ELEMENT_Y + 4;
        drawControl(guiGraphics, x, y, ELEMENT_WIDTH, 17, gridVisible, gridButtonText());
        drawControl(guiGraphics, x, y + 22, 56, 17, false, Component.translatable("smallshipsheraldry.view.front"));
        drawControl(guiGraphics, x + 60, y + 22, 56, 17, false, Component.translatable("smallshipsheraldry.view.back"));
        drawControl(guiGraphics, x, y + 42, 56, 17, false, Component.translatable("smallshipsheraldry.view.left"));
        drawControl(guiGraphics, x + 60, y + 42, 56, 17, false, Component.translatable("smallshipsheraldry.view.right"));
        drawControl(guiGraphics, x, y + 66, ELEMENT_WIDTH, 17, false, Component.translatable("smallshipsheraldry.view.reset"));
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.view.controls"), x + ELEMENT_WIDTH / 2, y + 94, HeraldryGuiTheme.MUTED_INK);
    }

    private void drawPreview(GuiGraphics guiGraphics, int x0, int y0) {
        HeraldryGuiTheme.drawPreviewInset(guiGraphics, x0 + PREVIEW_X - 4, y0 + PREVIEW_Y - 4, PREVIEW_WIDTH + 8, PREVIEW_HEIGHT + 8);
        Optional<Integer> hoveredFaceIndex = gridFaceIndexUnderMouse();
        PaintableSailMesh.setEditorHiddenElements(hiddenElements);
        PaintableSailMesh.setEditorGridVisible(gridVisible);
        PaintableSailMesh.setEditorHighlightFaceIndex(hoveredFaceIndex.orElse(null));
        PaintableSailMesh.setEditorGridFaceIndex(gridVisible ? hoveredFaceIndex.orElse(null) : null);
        try {
            PreviewShipRenderer.render(
                    guiGraphics,
                    x0 + PREVIEW_X,
                    y0 + PREVIEW_Y,
                    x0 + PREVIEW_X + PREVIEW_WIDTH,
                    y0 + PREVIEW_Y + PREVIEW_HEIGHT,
                    previewShip,
                    previewYaw,
                    previewPitch,
                    previewZoom,
                    previewPanX,
                    previewPanY,
                    hiddenElements,
                    draftTexture == null ? null : draftTexture.textureLocation()
            );
        } finally {
            PaintableSailMesh.clearEditorHiddenElements();
            PaintableSailMesh.clearEditorGridVisible();
            PaintableSailMesh.clearEditorHighlightFaceIndex();
            PaintableSailMesh.clearEditorGridFaceIndex();
        }
        drawPixelSelection(guiGraphics);
        if (!selectedElementIds.isEmpty()) {
            String selectionLabel;
            if (selectedElementIds.size() == 1) {
                String selectedId = selectedElementIds.iterator().next();
                List<PaintableSailMesh.Element> elements = elements();
                int selectedIndex = -1;
                for (int index = 0; index < elements.size(); index++) {
                    if (elements.get(index).id().equals(selectedId)) {
                        selectedIndex = index;
                        break;
                    }
                }
                selectionLabel = selectedIndex >= 0 ? friendlyElementName(elements, selectedIndex).getString() : shortElementName(selectedId);
            } else {
                selectionLabel = Integer.toString(selectedElementIds.size());
            }
            Component selected = Component.translatable("smallshipsheraldry.screen.selected", selectionLabel);
            guiGraphics.drawString(font, font.plainSubstrByWidth(selected.getString(), PREVIEW_WIDTH - 16), x0 + PREVIEW_X + 8, y0 + PREVIEW_Y + 8, HeraldryGuiTheme.GOLD_LIGHT, false);
        }
        if (paintingPreview && strokeMode == StrokeMode.LINE && strokeStartHit != null) {
            drawLinePreview(guiGraphics);
        }
    }

    private void drawRightPanel(GuiGraphics guiGraphics, int x0, int y0) {
        HeraldryGuiTheme.drawPanel(guiGraphics, x0 + RIGHT_X - 4, y0 + 18, RIGHT_WIDTH + 8, ELEMENT_HEIGHT + 22);
        switch (editorMode) {
            case PAINT -> {
                if (activeTool == PaintTool.SELECT) {
                    drawSelectionActions(guiGraphics, x0, y0);
                } else {
                    colorPicker.render(guiGraphics);
                    drawControl(guiGraphics, x0 + RIGHT_X + 4, y0 + 142, 80, 17, false, Component.translatable("smallshipsheraldry.button.base"));
                }
            }
            case PARTS -> drawPartsActions(guiGraphics, x0, y0);
            case VIEW -> drawViewDetails(guiGraphics, x0, y0);
        }
    }

    private void drawSelectionActions(GuiGraphics guiGraphics, int x0, int y0) {
        int x = x0 + RIGHT_X + 4;
        int y = y0 + 28;
        drawControl(guiGraphics, x, y, 38, 18, false, Component.translatable("smallshipsheraldry.selection.copy"));
        drawControl(guiGraphics, x + 42, y, 38, 18, false, Component.translatable("smallshipsheraldry.selection.cut"));
        drawControl(guiGraphics, x, y + 22, 80, 18, pasteActive, Component.translatable("smallshipsheraldry.selection.paste"));
        drawControl(guiGraphics, x, y + 48, 38, 18, pasteActive, Component.translatable("smallshipsheraldry.selection.apply"));
        drawControl(guiGraphics, x + 42, y + 48, 38, 18, false, Component.translatable("smallshipsheraldry.selection.cancel"));
        drawControl(guiGraphics, x, y + 70, 80, 18, false, Component.translatable("smallshipsheraldry.selection.delete"));

        SelectionRect rect = pixelSelection();
        Component summary = pasteActive && clipboard != null
                ? Component.translatable("smallshipsheraldry.selection.size", clipboard.width(), clipboard.height())
                : rect == null
                ? Component.translatable("smallshipsheraldry.selection.none")
                : Component.translatable("smallshipsheraldry.selection.size", rect.width(), rect.height());
        drawCenteredNoShadow(guiGraphics, summary, x + 40, y + 96, HeraldryGuiTheme.MUTED_INK);
    }

    private void drawPartsActions(GuiGraphics guiGraphics, int x0, int y0) {
        int x = x0 + RIGHT_X + 4;
        int y = y0 + 28;
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.parts.selected", selectedElementIds.size()), x + 40, y, HeraldryGuiTheme.INK);
        drawControl(guiGraphics, x, y + 16, 80, 17, false, Component.translatable("smallshipsheraldry.parts.select_all"));
        drawControl(guiGraphics, x, y + 36, 80, 17, false, Component.translatable("smallshipsheraldry.parts.deselect"));
        drawControl(guiGraphics, x, y + 60, 38, 18, false, Component.translatable("smallshipsheraldry.parts.hide"));
        drawIconControl(guiGraphics, x + 42, y + 60, 38, 18, selectionIsIsolated(), 112);
        drawControl(guiGraphics, x, y + 82, 80, 17, false, Component.translatable("smallshipsheraldry.parts.show_all"));
        drawControl(guiGraphics, x, y + 108, 38, 18, false, Component.translatable("smallshipsheraldry.button.fill"));
        drawControl(guiGraphics, x + 42, y + 108, 38, 18, false, Component.translatable("smallshipsheraldry.button.clear"));
    }

    private void drawViewDetails(GuiGraphics guiGraphics, int x0, int y0) {
        int center = x0 + RIGHT_X + RIGHT_WIDTH / 2;
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.view.camera"), center, y0 + 30, HeraldryGuiTheme.INK);
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.view.rotate_hint"), center, y0 + 50, HeraldryGuiTheme.MUTED_INK);
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.view.pan_hint"), center, y0 + 68, HeraldryGuiTheme.MUTED_INK);
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.view.zoom_hint"), center, y0 + 86, HeraldryGuiTheme.MUTED_INK);
    }

    private void drawToolButtons(GuiGraphics guiGraphics, int x0, int y0) {
        PaintTool[] tools = PaintTool.values();
        for (int index = 0; index < tools.length; index++) {
            PaintTool tool = tools[index];
            int x = x0 + TOOL_X + index * (TOOL_WIDTH + TOOL_GAP);
            int y = y0 + TOOL_Y;
            boolean selected = tool == activeTool;
            boolean hovered = isInside(lastMouseX, lastMouseY, x, y, TOOL_WIDTH, TOOL_HEIGHT);
            guiGraphics.fill(x - 1, y - 1, x + TOOL_WIDTH + 1, y + TOOL_HEIGHT + 1, selected || hovered ? HeraldryGuiTheme.GOLD_LIGHT : HeraldryGuiTheme.FRAME_DARK);
            guiGraphics.fill(x, y, x + TOOL_WIDTH, y + TOOL_HEIGHT, selected ? HeraldryGuiTheme.SELECTED : hovered ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.PAPER_ALT);
            guiGraphics.blit(TOOL_ICONS, x + 1, y + 1, tool.iconU(), 0, 16, 16, TOOL_TEXTURE_WIDTH, 16);
        }
    }

    private boolean libraryPanelVisible() {
        return libraryPanel != null && libraryPanel.isVisible();
    }

    private void openLibrary() {
        helpVisible = false;
        clearPixelSelectionOverlay();
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
        clearPixelSelectionOverlay();
        session.importPattern(imported);
        if (draftTexture != null) {
            draftTexture.replacePixels(session.pixels());
            draftTexture.uploadIfDirty();
        }
        switchElementContext();
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
        int y = topPos + 28;
        int width = imageWidth - 40;
        int height = 226;
        HeraldryGuiTheme.drawWindow(guiGraphics, x - 2, y - 2, width + 4, height + 4);
        guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.help.title"), x + 8, y + 8, HeraldryGuiTheme.INK, false);

        Component[] lines = {
                Component.translatable("smallshipsheraldry.help.layout"),
                Component.translatable("smallshipsheraldry.help.paint"),
                Component.translatable("smallshipsheraldry.help.parts"),
                Component.translatable("smallshipsheraldry.help.view"),
                Component.translatable("smallshipsheraldry.help.navigation"),
                Component.translatable("smallshipsheraldry.help.library"),
                Component.translatable("smallshipsheraldry.help.workflow")
        };
        int lineY = y + 24;
        for (Component line : lines) {
            for (FormattedCharSequence wrappedLine : font.split(line, width - 16)) {
                guiGraphics.drawString(font, wrappedLine, x + 8, lineY, HeraldryGuiTheme.MUTED_INK, false);
                lineY += 10;
            }
            lineY += 2;
        }
    }

    private void drawTableSlots(GuiGraphics guiGraphics, int x0, int y0) {
        drawSlot(guiGraphics, x0 + HeraldryTableMenu.PAPER_SLOT_X, y0 + HeraldryTableMenu.PAPER_SLOT_Y, HeraldryGuiTheme.PAPER_ALT);
        drawSlot(guiGraphics, x0 + HeraldryTableMenu.RESULT_SLOT_X, y0 + HeraldryTableMenu.RESULT_SLOT_Y, HeraldryGuiTheme.GOLD);
        int arrowCenter = x0 + (HeraldryTableMenu.PAPER_SLOT_X + 16 + HeraldryTableMenu.RESULT_SLOT_X) / 2;
        drawCenteredNoShadow(guiGraphics, Component.literal("→"), arrowCenter, y0 + HeraldryTableMenu.RESULT_SLOT_Y + 4, HeraldryGuiTheme.MUTED_INK);
    }

    private void drawSlot(GuiGraphics guiGraphics, int x, int y, int fillColor) {
        guiGraphics.fill(x - 2, y - 2, x + 18, y + 18, HeraldryGuiTheme.FRAME_DARK);
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, fillColor);
        guiGraphics.fill(x, y, x + 16, y + 16, HeraldryGuiTheme.INSET);
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
                if (modes[index] != EditorMode.PAINT) {
                    clearPixelSelectionOverlay();
                }
                editorMode = modes[index];
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
            int x = leftPos + RIGHT_X + 4;
            int y = topPos + 28;
            if (isInside(mouseX, mouseY, x, y, 38, 18)) {
                copyPixelSelection(false);
                return true;
            }
            if (isInside(mouseX, mouseY, x + 42, y, 38, 18)) {
                copyPixelSelection(true);
                return true;
            }
            if (isInside(mouseX, mouseY, x, y + 22, 80, 18)) {
                beginPaste();
                return true;
            }
            if (isInside(mouseX, mouseY, x, y + 48, 38, 18)) {
                commitPaste();
                return true;
            }
            if (isInside(mouseX, mouseY, x + 42, y + 48, 38, 18)) {
                if (pasteActive) {
                    cancelPaste();
                } else {
                    clearPixelSelectionOverlay();
                }
                return true;
            }
            if (isInside(mouseX, mouseY, x, y + 70, 80, 18)) {
                clearPixelSelection();
                return true;
            }
            return false;
        }
        if (activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER) {
            if (isInside(mouseX, mouseY, leftPos + 62, topPos + SIZE_Y, 15, 16)) {
                brushSize = Math.max(1, brushSize - 1);
                return true;
            }
            if (isInside(mouseX, mouseY, leftPos + 101, topPos + SIZE_Y, 15, 16)) {
                brushSize = Math.min(8, brushSize + 1);
                return true;
            }
            if (isInside(mouseX, mouseY, leftPos + 50, topPos + STROKE_Y, 32, 16)) {
                strokeMode = StrokeMode.FREEHAND;
                return true;
            }
            if (isInside(mouseX, mouseY, leftPos + 84, topPos + STROKE_Y, 32, 16)) {
                strokeMode = StrokeMode.LINE;
                return true;
            }
            if (isInside(mouseX, mouseY, leftPos + 76, topPos + SYMMETRY_Y, 18, 18)) {
                mirrorVertical = !mirrorVertical;
                return true;
            }
            if (isInside(mouseX, mouseY, leftPos + 96, topPos + SYMMETRY_Y, 18, 18)) {
                mirrorHorizontal = !mirrorHorizontal;
                return true;
            }
        } else if (activeTool == PaintTool.FILL) {
            if (isInside(mouseX, mouseY, leftPos + 42, topPos + FILL_MODE_Y, 16, 16)) {
                fillMode = fillMode.previous();
                return true;
            }
            if (isInside(mouseX, mouseY, leftPos + 80, topPos + FILL_MODE_Y, 16, 16)) {
                fillMode = fillMode.next();
                return true;
            }
        }
        if (isInside(mouseX, mouseY, leftPos + ELEMENT_X, topPos + SCOPE_ACTION_Y, 56, 17)) {
            fillScope(colorPicker.color(), false);
            return true;
        }
        if (isInside(mouseX, mouseY, leftPos + ELEMENT_X + 60, topPos + SCOPE_ACTION_Y, 56, 17)) {
            fillScope(0, true);
            return true;
        }
        if (isInside(mouseX, mouseY, leftPos + RIGHT_X + 4, topPos + 142, 80, 17)) {
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
        int x = leftPos + RIGHT_X + 4;
        int y = topPos + 28;
        List<PaintableSailMesh.Element> elements = elements();
        if (isInside(mouseX, mouseY, x, y + 16, 80, 17)) {
            selectedElementIds.clear();
            for (PaintableSailMesh.Element element : elements) {
                selectedElementIds.add(element.id());
            }
            selectionAnchorIndex = elements.isEmpty() ? -1 : 0;
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 36, 80, 17)) {
            selectedElementIds.clear();
            selectionAnchorIndex = -1;
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 60, 38, 18)) {
            hiddenElements.addAll(selectedElementIds);
            return true;
        }
        if (isInside(mouseX, mouseY, x + 42, y + 60, 38, 18)) {
            if (!selectedElementIds.isEmpty()) {
                if (selectionIsIsolated()) {
                    hiddenElements.clear();
                    return true;
                }
                hiddenElements.clear();
                for (PaintableSailMesh.Element element : elements) {
                    if (!selectedElementIds.contains(element.id())) {
                        hiddenElements.add(element.id());
                    }
                }
            }
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 82, 80, 17)) {
            hiddenElements.clear();
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 108, 38, 18)) {
            if (!selectedElementIds.isEmpty()) {
                fillScope(colorPicker.color(), false);
            }
            return true;
        }
        if (isInside(mouseX, mouseY, x + 42, y + 108, 38, 18)) {
            if (!selectedElementIds.isEmpty()) {
                fillScope(0, false);
            }
            return true;
        }
        return false;
    }

    private boolean handleViewControlClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int x = leftPos + ELEMENT_X;
        int y = topPos + ELEMENT_Y + 4;
        if (isInside(mouseX, mouseY, x, y, ELEMENT_WIDTH, 17)) {
            gridVisible = !gridVisible;
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 22, 56, 17)) {
            setCamera(180.0F, 0.0F);
            return true;
        }
        if (isInside(mouseX, mouseY, x + 60, y + 22, 56, 17)) {
            setCamera(0.0F, 0.0F);
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 42, 56, 17)) {
            setCamera(90.0F, 0.0F);
            return true;
        }
        if (isInside(mouseX, mouseY, x + 60, y + 42, 56, 17)) {
            setCamera(270.0F, 0.0F);
            return true;
        }
        if (isInside(mouseX, mouseY, x, y + 66, ELEMENT_WIDTH, 17)) {
            clearPixelSelectionOverlay();
            previewYaw = 180.0F;
            previewPitch = -10.0F;
            previewZoom = 1.0F;
            previewPanX = 0.0F;
            previewPanY = 0.0F;
            return true;
        }
        return false;
    }

    private void setCamera(float yaw, float pitch) {
        clearPixelSelectionOverlay();
        previewYaw = yaw;
        previewPitch = pitch;
        previewPanX = 0.0F;
        previewPanY = 0.0F;
    }

    private boolean selectionIsIsolated() {
        if (selectedElementIds.isEmpty()) {
            return false;
        }
        for (PaintableSailMesh.Element element : elements()) {
            if (hiddenElements.contains(element.id()) == selectedElementIds.contains(element.id())) {
                return false;
            }
        }
        return true;
    }

    private void fillScope(int color, boolean confirmFullClear) {
        if (confirmFullClear && selectedElementIds.isEmpty() && System.currentTimeMillis() >= clearConfirmationUntil) {
            clearConfirmationUntil = System.currentTimeMillis() + 2_500L;
            return;
        }
        clearConfirmationUntil = 0L;
        if (!pushUndo()) {
            return;
        }
        if (!PaintableSailMesh.fillScopedPixels(previewShip, previewStage, session.pixels(), color, selectedElementIds, hiddenElements)) {
            discardLatestUndo();
            return;
        }
        replaceDraftTexturePixels();
        commitPaintBuffer();
    }

    private boolean handleElementClick(double mouseX, double mouseY, int button) {
        if ((button != 0 && button != 1) || !isInside(mouseX, mouseY, leftPos + ELEMENT_X, topPos + ELEMENT_Y, ELEMENT_WIDTH, ELEMENT_HEIGHT)) {
            return false;
        }

        int row = (int) ((mouseY - (topPos + ELEMENT_Y)) / ELEMENT_ROW_HEIGHT);
        int index = elementScroll + row;
        List<PaintableSailMesh.Element> elements = elements();
        if (index < 0 || index >= elements.size()) {
            return true;
        }

        String elementId = elements.get(index).id();
        if (button == 1) {
            toggleElementHidden(elementId);
            return true;
        }
        if (hasShiftDown() && selectionAnchorIndex >= 0) {
            if (!hasControlDown()) {
                selectedElementIds.clear();
            }
            int first = Math.min(selectionAnchorIndex, index);
            int last = Math.max(selectionAnchorIndex, index);
            for (int selectedIndex = first; selectedIndex <= last; selectedIndex++) {
                selectedElementIds.add(elements.get(selectedIndex).id());
            }
        } else if (hasControlDown()) {
            if (!selectedElementIds.add(elementId)) {
                selectedElementIds.remove(elementId);
            }
            selectionAnchorIndex = index;
        } else {
            selectedElementIds.clear();
            selectedElementIds.add(elementId);
            selectionAnchorIndex = index;
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
        if (isInside(mouseX, mouseY, leftPos + HeraldryTableMenu.PAPER_SLOT_X, topPos + HeraldryTableMenu.PAPER_SLOT_Y, 18, 18)) {
            return HeraldryTableBlockEntity.PAPER_SLOT;
        }
        if (isInside(mouseX, mouseY, leftPos + HeraldryTableMenu.RESULT_SLOT_X, topPos + HeraldryTableMenu.RESULT_SLOT_Y, 18, 18)) {
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
            int x = leftPos + TOOL_X + index * (TOOL_WIDTH + TOOL_GAP);
            int y = topPos + TOOL_Y;
            if (isInside(mouseX, mouseY, x, y, TOOL_WIDTH, TOOL_HEIGHT)) {
                setActiveTool(tools[index]);
                return true;
            }
        }
        return false;
    }

    private boolean handleColorClick(double mouseX, double mouseY, int button) {
        return colorPicker.mouseClicked(mouseX, mouseY, button, hasShiftDown());
    }

    private boolean useToolOnPreview(double mouseX, double mouseY) {
        Optional<PaintableSailMesh.Hit> hit = pickAtPreview(mouseX, mouseY, hiddenElements);
        if (hit.isEmpty()) {
            return true;
        }

        PaintableSailMesh.Hit paintHit = hit.get();
        if (!temporaryPicker && activeTool != PaintTool.PICKER && !isHitInSelection(paintHit)) {
            return true;
        }

        if (temporaryPicker || activeTool == PaintTool.PICKER) {
            int sampledColor = session.pixels()[paintHit.pixelY() * HeraldryPatternData.MESH_ATLAS_WIDTH + paintHit.pixelX()];
            colorPicker.setColor((sampledColor >>> 24) == 0 ? session.pattern().baseColorArgb() : sampledColor);
            return true;
        }

        if (activeTool == PaintTool.FILL) {
            if (!pushUndo()) {
                return true;
            }
            boolean changed = switch (fillMode) {
                case AREA -> PaintableSailMesh.floodFillPixels(previewShip, paintHit, previewStage, session.pixels(), colorPicker.color());
                case FACE -> PaintableSailMesh.fillFacePixels(previewShip, paintHit.faceIndex(), previewStage, session.pixels(), colorPicker.color());
                case REPLACE -> {
                    int source = session.pixels()[paintHit.pixelY() * HeraldryPatternData.MESH_ATLAS_WIDTH + paintHit.pixelX()];
                    yield PaintableSailMesh.replaceColorPixels(previewShip, previewStage, session.pixels(), source, colorPicker.color(), selectedElementIds, hiddenElements);
                }
            };
            if (!changed) {
                discardLatestUndo();
                return true;
            }
            if (fillMode == FillMode.REPLACE) {
                replaceDraftTexturePixels();
            } else {
                syncHitFace(paintHit);
            }
            commitPaintBuffer();
            return true;
        }

        int color = activeTool == PaintTool.ERASER ? 0 : colorPicker.color();
        if (!strokeUndoCaptured) {
            if (!pushUndo()) {
                return true;
            }
            strokeUndoCaptured = true;
        }
        boolean changed;
        if (lastStrokeHit != null && lastStrokeHit.faceIndex() == paintHit.faceIndex()) {
            changed = PaintableSailMesh.paintLinePixels(previewShip, lastStrokeHit, paintHit, previewStage, session.pixels(), color,
                    brushSize, mirrorVertical, mirrorHorizontal);
        } else {
            changed = PaintableSailMesh.paintBrushPixels(previewShip, paintHit, previewStage, session.pixels(), color,
                    brushSize, mirrorVertical, mirrorHorizontal);
        }
        lastStrokeHit = paintHit;
        if (changed) {
            syncHitFace(paintHit);
            session.markLocallyModified();
        }
        return true;
    }

    private Optional<PaintableSailMesh.Hit> pickAtPreview(double mouseX, double mouseY, Set<String> hidden) {
        return PaintableSailMesh.pick(
                previewShip,
                leftPos + PREVIEW_X,
                topPos + PREVIEW_Y,
                leftPos + PREVIEW_X + PREVIEW_WIDTH,
                topPos + PREVIEW_Y + PREVIEW_HEIGHT,
                previewYaw,
                previewPitch,
                previewZoom,
                previewPanX,
                previewPanY,
                mouseX,
                mouseY,
                hidden
        );
    }

    private boolean isHitInSelection(PaintableSailMesh.Hit hit) {
        return selectedElementIds.isEmpty() || selectedElementIds.contains(hit.elementId());
    }

    private void syncHitFace(PaintableSailMesh.Hit hit) {
        if (draftTexture != null) {
            draftTexture.updateRegion(session.pixels(), hit.slotX() - 1, hit.slotY() - 1, hit.slotWidth() + 2, hit.slotHeight() + 2);
        }
    }

    private void replaceDraftTexturePixels() {
        if (draftTexture != null) {
            draftTexture.replacePixels(session.pixels());
        }
    }

    private boolean beginPixelSelection(double mouseX, double mouseY) {
        if (pasteActive && clipboard != null) {
            movingPaste = true;
            updatePasteTarget(mouseX, mouseY);
            return true;
        }

        Optional<PaintableSailMesh.Hit> sampled = pickAtPreview(mouseX, mouseY, hiddenElements);
        if (sampled.isEmpty() || !isHitInSelection(sampled.get())) {
            clearPixelSelectionOverlay();
            return true;
        }
        pixelSelectionActive = true;
        selectingPixels = true;
        selectionStartHit = sampled.get();
        selectionEndHit = sampled.get();
        refreshSurfaceSelection();
        return true;
    }

    private void updatePixelSelection(double mouseX, double mouseY) {
        Optional<PaintableSailMesh.Hit> sampled = pickAtPreview(mouseX, mouseY, hiddenElements);
        if (sampled.isEmpty() || !isHitInSelection(sampled.get())) {
            return;
        }
        Optional<PaintableSailMesh.SurfacePoint> startPoint =
                PaintableSailMesh.surfacePoint(previewShip, previewStage, selectionStartHit);
        Optional<PaintableSailMesh.SurfacePoint> endPoint =
                PaintableSailMesh.surfacePoint(previewShip, previewStage, sampled.get());
        if (startPoint.isEmpty() || endPoint.isEmpty()
                || startPoint.get().componentId() != endPoint.get().componentId()) {
            return;
        }
        selectionEndHit = sampled.get();
        refreshSurfaceSelection();
    }

    private void refreshSurfaceSelection() {
        if (!pixelSelectionActive || selectionStartHit == null || selectionEndHit == null) {
            surfaceSelection = List.of();
            return;
        }
        surfaceSelection = PaintableSailMesh.pixelsInSurfaceRect(
                previewShip,
                previewStage,
                selectionStartHit,
                selectionEndHit,
                selectedElementIds,
                hiddenElements
        );
    }

    private void copyPixelSelection(boolean cut) {
        if (!pixelSelectionActive || surfaceSelection.isEmpty()) {
            showStatus(Component.translatable("smallshipsheraldry.selection.select_first"));
            return;
        }

        Map<Integer, CapturedPixel> capturedPixels = new HashMap<>();
        Set<Integer> clearIndices = new LinkedHashSet<>();
        int atlasWidth = HeraldryPatternData.MESH_ATLAS_WIDTH;
        for (PaintableSailMesh.ScreenPixel selectedPixel : surfaceSelection) {
            PaintableSailMesh.Hit hit = selectedPixel.hit();
            int atlasIndex = hit.pixelY() * atlasWidth + hit.pixelX();
            int color = session.pixels()[atlasIndex];
            capturedPixels.putIfAbsent(atlasIndex, new CapturedPixel(
                    hit,
                    selectedPixel.screenX(),
                    selectedPixel.screenY(),
                    color
            ));
            if (cut && color != 0) {
                clearIndices.add(atlasIndex);
            }
        }
        if (capturedPixels.isEmpty()) {
            showStatus(Component.translatable("smallshipsheraldry.selection.empty"));
            return;
        }

        double selectionCenterX = surfaceSelection.stream()
                .mapToDouble(PaintableSailMesh.ScreenPixel::screenX)
                .average()
                .orElse(leftPos + PREVIEW_X + PREVIEW_WIDTH * 0.5D);
        double selectionCenterY = surfaceSelection.stream()
                .mapToDouble(PaintableSailMesh.ScreenPixel::screenY)
                .average()
                .orElse(topPos + PREVIEW_Y + PREVIEW_HEIGHT * 0.5D);
        CapturedPixel anchor = capturedPixels.values().stream()
                .min((first, second) -> Double.compare(
                        distanceSquared(first.screenX(), first.screenY(), selectionCenterX, selectionCenterY),
                        distanceSquared(second.screenX(), second.screenY(), selectionCenterX, selectionCenterY)
                ))
                .orElseThrow();
        Optional<PaintableSailMesh.SurfacePoint> anchorPoint = PaintableSailMesh.surfacePoint(previewShip, previewStage, anchor.hit());
        if (anchorPoint.isEmpty()) {
            showStatus(Component.translatable("smallshipsheraldry.selection.empty"));
            return;
        }

        List<PaintableSailMesh.ClipboardPixel> geometryPixels = new ArrayList<>(capturedPixels.size());
        float minU = Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (CapturedPixel captured : capturedPixels.values()) {
            Optional<PaintableSailMesh.SurfacePoint> point = PaintableSailMesh.surfacePoint(previewShip, previewStage, captured.hit());
            if (point.isEmpty() || point.get().componentId() != anchorPoint.get().componentId()) {
                continue;
            }
            float offsetU = point.get().u() - anchorPoint.get().u();
            float offsetV = point.get().v() - anchorPoint.get().v();
            geometryPixels.add(new PaintableSailMesh.ClipboardPixel(
                    offsetU,
                    offsetV,
                    captured.color()
            ));
            minU = Math.min(minU, offsetU);
            minV = Math.min(minV, offsetV);
            maxU = Math.max(maxU, offsetU);
            maxV = Math.max(maxV, offsetV);
        }
        if (geometryPixels.isEmpty()) {
            showStatus(Component.translatable("smallshipsheraldry.selection.empty"));
            return;
        }

        int geometryWidth = Math.max(1, Mth.ceil((maxU - minU) * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT) + 1);
        int geometryHeight = Math.max(1, Mth.ceil((maxV - minV) * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT) + 1);
        closeClipboard();
        clipboard = new SelectionClipboard(
                geometryWidth,
                geometryHeight,
                List.copyOf(geometryPixels),
                Mth.clamp(Mth.floor(selectionCenterX - leftPos - PREVIEW_X), 0, PREVIEW_WIDTH - 1),
                Mth.clamp(Mth.floor(selectionCenterY - topPos - PREVIEW_Y), 0, PREVIEW_HEIGHT - 1)
        );

        if (cut && !clearIndices.isEmpty() && pushUndo()) {
            for (int atlasIndex : clearIndices) {
                session.pixels()[atlasIndex] = 0;
            }
            replaceDraftTexturePixels();
            commitPaintBuffer();
        }
        showStatus(Component.translatable("smallshipsheraldry.selection.copied", geometryWidth, geometryHeight));
    }

    private void beginPaste() {
        if (clipboard == null) {
            showStatus(Component.translatable("smallshipsheraldry.selection.clipboard_empty"));
            return;
        }
        pixelSelectionActive = false;
        selectingPixels = false;
        movingPaste = false;
        pasteActive = true;
        pasteX = clipboard.originX();
        pasteY = clipboard.originY();
        updatePasteTarget(leftPos + PREVIEW_X + pasteX + 0.5D, topPos + PREVIEW_Y + pasteY + 0.5D);
    }

    private void movePaste(double mouseX, double mouseY) {
        if (!pasteActive || clipboard == null) {
            movingPaste = false;
            return;
        }
        updatePasteTarget(mouseX, mouseY);
    }

    private void movePasteBy(int deltaX, int deltaY) {
        if (!pasteActive || clipboard == null || pasteTargetHit == null) {
            return;
        }
        float pixelScale = PaintableSailMesh.projectedPixelScale(previewShip, pasteTargetHit);
        updatePasteTarget(
                leftPos + PREVIEW_X + pasteX + 0.5D + deltaX * pixelScale,
                topPos + PREVIEW_Y + pasteY + 0.5D + deltaY * pixelScale
        );
    }

    private boolean updatePasteTarget(double mouseX, double mouseY) {
        Optional<PaintableSailMesh.Hit> sampled = pickAtPreview(mouseX, mouseY, hiddenElements);
        if (sampled.isEmpty() || !isHitInSelection(sampled.get()) || clipboard == null) {
            return false;
        }
        pasteTargetHit = sampled.get();
        pasteX = previewRelativeX(mouseX);
        pasteY = previewRelativeY(mouseY);
        updatePastePreview(PaintableSailMesh.mapClipboardPixels(
                previewShip,
                previewStage,
                pasteTargetHit,
                clipboard.pixels(),
                selectedElementIds,
                hiddenElements
        ));
        return true;
    }

    private void updatePastePreview(Map<Integer, Integer> replacements) {
        restorePastePreview();
        pasteReplacements = Map.copyOf(replacements);
        if (draftTexture == null) {
            return;
        }
        int atlasWidth = HeraldryPatternData.MESH_ATLAS_WIDTH;
        for (Map.Entry<Integer, Integer> replacement : pasteReplacements.entrySet()) {
            int atlasIndex = replacement.getKey();
            draftTexture.setPixel(atlasIndex % atlasWidth, atlasIndex / atlasWidth, replacement.getValue());
            pastePreviewIndices.add(atlasIndex);
        }
    }

    private void restorePastePreview() {
        if (draftTexture != null && !pastePreviewIndices.isEmpty()) {
            int atlasWidth = HeraldryPatternData.MESH_ATLAS_WIDTH;
            for (int atlasIndex : pastePreviewIndices) {
                draftTexture.setPixel(atlasIndex % atlasWidth, atlasIndex / atlasWidth, session.pixels()[atlasIndex]);
            }
        }
        pastePreviewIndices.clear();
    }

    private void commitPaste() {
        if (!pasteActive || clipboard == null) {
            return;
        }

        Map<Integer, Integer> replacements = pasteReplacements;
        boolean changed = replacements.entrySet().stream()
                .anyMatch(entry -> session.pixels()[entry.getKey()] != entry.getValue());
        cancelPaste();
        if (!changed) {
            showStatus(Component.translatable(replacements.isEmpty()
                    ? "smallshipsheraldry.selection.no_target"
                    : "smallshipsheraldry.selection.unchanged"));
            return;
        }
        if (!pushUndo()) {
            return;
        }
        replacements.forEach((index, color) -> session.pixels()[index] = color);
        replaceDraftTexturePixels();
        commitPaintBuffer();
        pixelSelectionActive = false;
        selectionStartHit = null;
        selectionEndHit = null;
        surfaceSelection = List.of();
    }

    private void clearPixelSelection() {
        if (pasteActive) {
            cancelPaste();
            return;
        }
        if (!pixelSelectionActive || surfaceSelection.isEmpty()) {
            return;
        }

        Set<Integer> clearIndices = new LinkedHashSet<>();
        int atlasWidth = HeraldryPatternData.MESH_ATLAS_WIDTH;
        for (PaintableSailMesh.ScreenPixel selectedPixel : surfaceSelection) {
            PaintableSailMesh.Hit hit = selectedPixel.hit();
            int atlasIndex = hit.pixelY() * atlasWidth + hit.pixelX();
            if (session.pixels()[atlasIndex] != 0) {
                clearIndices.add(atlasIndex);
            }
        }
        if (clearIndices.isEmpty() || !pushUndo()) {
            return;
        }
        for (int atlasIndex : clearIndices) {
            session.pixels()[atlasIndex] = 0;
        }
        replaceDraftTexturePixels();
        commitPaintBuffer();
    }

    private SelectionRect pixelSelection() {
        if (!pixelSelectionActive || surfaceSelection.isEmpty()
                || selectionStartHit == null || selectionEndHit == null) {
            return null;
        }
        Optional<PaintableSailMesh.SurfacePoint> start =
                PaintableSailMesh.surfacePoint(previewShip, previewStage, selectionStartHit);
        Optional<PaintableSailMesh.SurfacePoint> end =
                PaintableSailMesh.surfacePoint(previewShip, previewStage, selectionEndHit);
        if (start.isEmpty() || end.isEmpty()
                || start.get().componentId() != end.get().componentId()) {
            return null;
        }
        int width = Math.max(1, Mth.floor(
                Math.abs(end.get().u() - start.get().u())
                        * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT + 0.5F
        ) + 1);
        int height = Math.max(1, Mth.floor(
                Math.abs(end.get().v() - start.get().v())
                        * PaintableSailMesh.PAINT_PIXELS_PER_MODEL_UNIT + 0.5F
        ) + 1);
        return new SelectionRect(0, 0, width, height);
    }

    private int previewRelativeX(double mouseX) {
        return Mth.clamp(Mth.floor(mouseX - leftPos - PREVIEW_X), 0, PREVIEW_WIDTH - 1);
    }

    private int previewRelativeY(double mouseY) {
        return Mth.clamp(Mth.floor(mouseY - topPos - PREVIEW_Y), 0, PREVIEW_HEIGHT - 1);
    }

    private void cancelPaste() {
        restorePastePreview();
        pasteActive = false;
        movingPaste = false;
        pasteTargetHit = null;
        pasteReplacements = Map.of();
    }

    private void clearPixelSelectionOverlay() {
        selectingPixels = false;
        pixelSelectionActive = false;
        selectionStartHit = null;
        selectionEndHit = null;
        surfaceSelection = List.of();
        cancelPaste();
    }

    private void closeClipboard() {
        cancelPaste();
        clipboard = null;
    }

    private void setActiveTool(PaintTool tool) {
        if (activeTool != tool && tool != PaintTool.SELECT) {
            clearPixelSelectionOverlay();
        }
        activeTool = tool;
        colorPicker.setVisible(editorMode == EditorMode.PAINT && activeTool != PaintTool.SELECT);
    }

    private void applyLineStroke() {
        if (strokeStartHit == null || !pushUndo()) {
            return;
        }
        int color = activeTool == PaintTool.ERASER ? 0 : colorPicker.color();
        double deltaX = strokeEndScreenX - strokeStartScreenX;
        double deltaY = strokeEndScreenY - strokeStartScreenY;
        double lengthSquared = deltaX * deltaX + deltaY * deltaY;
        double length = Math.sqrt(lengthSquared);
        double thickness = lineScreenThickness();
        double halfThickness = thickness * 0.5D;
        int previewLeft = leftPos + PREVIEW_X;
        int previewTop = topPos + PREVIEW_Y;
        int previewRight = previewLeft + PREVIEW_WIDTH - 1;
        int previewBottom = previewTop + PREVIEW_HEIGHT - 1;
        int minX = Math.max(previewLeft, Mth.floor(Math.min(strokeStartScreenX, strokeEndScreenX) - halfThickness));
        int maxX = Math.min(previewRight, Mth.ceil(Math.max(strokeStartScreenX, strokeEndScreenX) + halfThickness));
        int minY = Math.max(previewTop, Mth.floor(Math.min(strokeStartScreenY, strokeEndScreenY) - halfThickness));
        int maxY = Math.min(previewBottom, Mth.ceil(Math.max(strokeStartScreenY, strokeEndScreenY) + halfThickness));
        Map<Integer, PaintableSailMesh.Hit> touchedFaces = new HashMap<>();
        Set<Long> touchedPixels = new LinkedHashSet<>();
        boolean changed = false;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double sampleX = x + 0.5D;
                double sampleY = y + 0.5D;
                if (!insideLineRectangle(sampleX, sampleY, deltaX, deltaY, lengthSquared, length, halfThickness)) {
                    continue;
                }
                Optional<PaintableSailMesh.Hit> sampled = pickAtPreview(sampleX, sampleY, hiddenElements);
                if (sampled.isEmpty() || !isHitInSelection(sampled.get())) {
                    continue;
                }
                PaintableSailMesh.Hit hit = sampled.get();
                long pixelKey = ((long) hit.faceIndex() << 32)
                        ^ ((long) hit.pixelY() << 16)
                        ^ (hit.pixelX() & 0xFFFFL);
                if (!touchedPixels.add(pixelKey)) {
                    continue;
                }
                changed |= PaintableSailMesh.paintBrushPixels(previewShip, hit, previewStage,
                        session.pixels(), color, 1, mirrorVertical, mirrorHorizontal);
                touchedFaces.put(hit.faceIndex(), hit);
            }
        }
        if (!changed) {
            discardLatestUndo();
            return;
        }
        touchedFaces.values().forEach(this::syncHitFace);
        session.markLocallyModified();
        commitPaintBuffer();
    }

    private boolean insideLineRectangle(
            double x,
            double y,
            double deltaX,
            double deltaY,
            double lengthSquared,
            double length,
            double halfThickness
    ) {
        if (lengthSquared < 0.0001D) {
            return Math.abs(x - strokeStartScreenX) <= halfThickness
                    && Math.abs(y - strokeStartScreenY) <= halfThickness;
        }
        double relativeX = x - strokeStartScreenX;
        double relativeY = y - strokeStartScreenY;
        double position = (relativeX * deltaX + relativeY * deltaY) / lengthSquared;
        if (position < 0.0D || position > 1.0D) {
            return false;
        }
        double perpendicularDistance = Math.abs(relativeX * deltaY - relativeY * deltaX) / length;
        return perpendicularDistance <= halfThickness;
    }

    private float lineScreenThickness() {
        float startScale = PaintableSailMesh.projectedPixelScale(previewShip, strokeStartHit);
        float endScale = lastStrokeHit == null
                ? startScale
                : PaintableSailMesh.projectedPixelScale(previewShip, lastStrokeHit);
        return Math.max(1.0F, brushSize * (startScale + endScale) * 0.5F);
    }

    private Optional<Integer> gridFaceIndexUnderMouse() {
        if (activeTool == PaintTool.SELECT || !isInsidePreview(lastMouseX, lastMouseY)) {
            return Optional.empty();
        }

        Optional<PaintableSailMesh.Hit> hit = PaintableSailMesh.pick(
                previewShip,
                leftPos + PREVIEW_X,
                topPos + PREVIEW_Y,
                leftPos + PREVIEW_X + PREVIEW_WIDTH,
                topPos + PREVIEW_Y + PREVIEW_HEIGHT,
                previewYaw,
                previewPitch,
                previewZoom,
                previewPanX,
                previewPanY,
                lastMouseX,
                lastMouseY,
                hiddenElements
        );
        if (hit.isEmpty()) {
            return Optional.empty();
        }

        PaintableSailMesh.Hit value = hit.get();
        if (!isHitInSelection(value)) {
            return Optional.empty();
        }
        return Optional.of(value.faceIndex());
    }

    private boolean selectElementAtPreview(double mouseX, double mouseY) {
        Optional<PaintableSailMesh.Hit> hit = PaintableSailMesh.pick(
                previewShip,
                leftPos + PREVIEW_X,
                topPos + PREVIEW_Y,
                leftPos + PREVIEW_X + PREVIEW_WIDTH,
                topPos + PREVIEW_Y + PREVIEW_HEIGHT,
                previewYaw,
                previewPitch,
                previewZoom,
                previewPanX,
                previewPanY,
                mouseX,
                mouseY,
                Set.of()
        );
        hit.ifPresent(value -> {
            if (!hasControlDown() && !hasShiftDown()) {
                selectedElementIds.clear();
            }
            if (!selectedElementIds.add(value.elementId()) && hasControlDown()) {
                selectedElementIds.remove(value.elementId());
            }
            scrollSelectionIntoView(value.elementId());
        });
        return true;
    }

    private void toggleElementHidden(String elementId) {
        if (!hiddenElements.add(elementId)) {
            hiddenElements.remove(elementId);
        }
    }

    private void changePreviewType(ShipPreviewType newPreviewType) {
        if (newPreviewType == session.shipType()) {
            return;
        }
        HeraldryPatternData current = currentData();
        if (current == null || !sendPatternTransfer(HeraldryPackets.TABLE_DRAFT_UPDATE, current)) {
            return;
        }
        session.markSaved();
        clearPixelSelectionOverlay();
        session.switchShip(newPreviewType);
        if (draftTexture != null) {
            draftTexture.replacePixels(session.pixels());
        }
        switchElementContext();
        previewPanX = 0.0F;
        previewPanY = 0.0F;
        rebuildPreviewShip();
    }

    private void changePreviewStage(int newStage) {
        int clampedStage = Mth.clamp(newStage, 0, 4);
        if (clampedStage == previewStage) {
            return;
        }
        clearPixelSelectionOverlay();
        previewStage = clampedStage;
        switchElementContext();
        PreviewShipFactory.setSailState(previewShip, previewStage);
    }

    private void exportPattern() {
        HeraldryPatternData data = currentData();
        if (data == null) {
            return;
        }
        if (sendPatternTransfer(HeraldryPackets.EXPORT_PATTERN, data)) {
            session.markSaved();
        }
    }

    private void sendDraftUpdate() {
        HeraldryPatternData data = currentData();
        if (data == null) {
            return;
        }
        sendPatternTransfer(HeraldryPackets.TABLE_DRAFT_UPDATE, data);
        session.markSaved();
    }

    private boolean sendPatternTransfer(ResourceLocation packetId, HeraldryPatternData data) {
        byte[] encoded;
        try {
            encoded = HeraldryPatternTransfer.encode(data.toTag());
        } catch (IOException exception) {
            showStatus(Component.translatable("smallshipsheraldry.message.pattern_too_complex"));
            return false;
        }

        int transferId = ++nextTransferId;
        int chunkCount = HeraldryPatternTransfer.chunkCount(encoded.length);
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBlockPos(menu.getBlockPos());
            buf.writeVarInt(transferId);
            buf.writeVarInt(chunkIndex);
            buf.writeVarInt(chunkCount);
            buf.writeVarInt(encoded.length);
            buf.writeByteArray(HeraldryPatternTransfer.chunk(encoded, chunkIndex));
            NetworkManager.sendToServer(packetId, buf);
        }
        return true;
    }

    private void requestDraftSync() {
        if (draftSyncRequested) {
            return;
        }

        draftSyncRequested = true;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(menu.getBlockPos());
        NetworkManager.sendToServer(HeraldryPackets.TABLE_DRAFT_REQUEST, buf);
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
        if (!menu.getBlockPos().equals(blockPos) || patternTag == null) {
            return;
        }
        if (first) {
            pendingDraftSync = HeraldryTableDrafts.blank().withActiveShipType(activeType);
        }
        HeraldryPatternData.tryFromRootTag(patternTag)
                .filter(data -> data.shipType() == draftType)
                .ifPresent(data -> pendingDraftSync = pendingDraftSync.withDraft(data).withActiveShipType(activeType));
        if (!last || !session.applyServerSync(pendingDraftSync)) {
            return;
        }
        clearPixelSelectionOverlay();
        if (draftTexture != null) {
            draftTexture.replacePixels(session.pixels());
        }
        hiddenElementsByContext.clear();
        selectedElementsByContext.clear();
        switchElementContext();
        rebuildPreviewShip();
    }

    private void sendSlotAction(int slot, int action) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(menu.getBlockPos());
        buf.writeVarInt(slot);
        buf.writeVarInt(action);
        NetworkManager.sendToServer(HeraldryPackets.TABLE_SLOT_ACTION, buf);
    }

    private void rebuildPreviewShip() {
        previewShip = PreviewShipFactory.create(session.shipType(), session.pattern().withShipType(session.shipType()), previewStage);
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
            switchElementContext();
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
        if (editorMode == EditorMode.PARTS
                && isInside(mouseX, mouseY, leftPos + RIGHT_X + 46, topPos + 88, 38, 18)) {
            guiGraphics.renderTooltip(font, Component.translatable("smallshipsheraldry.tooltip.isolate"), mouseX, mouseY);
            return;
        }
        if (editorMode == EditorMode.PARTS && isInside(mouseX, mouseY, leftPos + ELEMENT_X, topPos + ELEMENT_Y, ELEMENT_WIDTH, ELEMENT_HEIGHT)) {
            int row = (mouseY - (topPos + ELEMENT_Y)) / ELEMENT_ROW_HEIGHT;
            int index = elementScroll + row;
            List<PaintableSailMesh.Element> elements = elements();
            if (index >= 0 && index < elements.size()) {
                guiGraphics.renderTooltip(font, Component.literal(elements.get(index).id()), mouseX, mouseY);
            }
            return;
        }
        if (editorMode != EditorMode.PAINT) {
            return;
        }
        if ((activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER)
                && isInside(mouseX, mouseY, leftPos + 76, topPos + SYMMETRY_Y, 18, 18)) {
            guiGraphics.renderTooltip(font, Component.translatable("smallshipsheraldry.tooltip.mirror_vertical"), mouseX, mouseY);
            return;
        }
        if ((activeTool == PaintTool.BRUSH || activeTool == PaintTool.ERASER)
                && isInside(mouseX, mouseY, leftPos + 96, topPos + SYMMETRY_Y, 18, 18)) {
            guiGraphics.renderTooltip(font, Component.translatable("smallshipsheraldry.tooltip.mirror_horizontal"), mouseX, mouseY);
            return;
        }
        PaintTool[] tools = PaintTool.values();
        for (int index = 0; index < tools.length; index++) {
            int x = leftPos + TOOL_X + index * (TOOL_WIDTH + TOOL_GAP);
            int y = topPos + TOOL_Y;
            if (isInside(mouseX, mouseY, x, y, TOOL_WIDTH, TOOL_HEIGHT)) {
                guiGraphics.renderTooltip(font, tools[index].displayName(), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderTableSlotTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int slot = tableSlotAt(mouseX, mouseY);
        if (slot == HeraldryTableBlockEntity.PAPER_SLOT) {
            guiGraphics.renderTooltip(font, Component.translatable("smallshipsheraldry.tooltip.paper_slot"), mouseX, mouseY);
        } else if (slot == HeraldryTableBlockEntity.RESULT_SLOT && menu.getResultStack().isEmpty()) {
            guiGraphics.renderTooltip(font, Component.translatable("smallshipsheraldry.tooltip.result_slot"), mouseX, mouseY);
        }
    }

    private void zoomPreviewAt(double mouseX, double mouseY, double amount) {
        float oldZoom = previewZoom;
        float zoomStep = amount > 0.0D ? 1.14F : 1.0F / 1.14F;
        float newZoom = Mth.clamp(oldZoom * zoomStep, 0.30F, 10.0F);
        if (Math.abs(newZoom - oldZoom) < 0.0001F) {
            return;
        }

        float baseCenterX = leftPos + PREVIEW_X + PREVIEW_WIDTH * 0.5F;
        float baseCenterY = topPos + PREVIEW_Y + PREVIEW_HEIGHT * 0.5F + PaintableSailMesh.EDITOR_VERTICAL_OFFSET;
        float ratio = newZoom / oldZoom;
        previewPanX = (float) (mouseX - baseCenterX - (mouseX - baseCenterX - previewPanX) * ratio);
        previewPanY = (float) (mouseY - baseCenterY - (mouseY - baseCenterY - previewPanY) * ratio);
        previewZoom = newZoom;
    }

    private static String shortElementName(String elementId) {
        int slash = elementId.lastIndexOf('/');
        return slash >= 0 ? elementId.substring(slash + 1) : elementId;
    }

    private static double distanceSquared(double x, double y, double targetX, double targetY) {
        double deltaX = x - targetX;
        double deltaY = y - targetY;
        return deltaX * deltaX + deltaY * deltaY;
    }

    private List<PaintableSailMesh.Element> elements() {
        List<PaintableSailMesh.Element> elements = new ArrayList<>(PaintableSailMesh.elements(session.shipType(), previewStage));
        elements.sort((first, second) -> {
            int category = Integer.compare(elementCategory(first.id()), elementCategory(second.id()));
            return category != 0 ? category : first.id().compareTo(second.id());
        });
        return elements;
    }

    private Component friendlyElementName(List<PaintableSailMesh.Element> elements, int index) {
        PaintableSailMesh.Element element = elements.get(index);
        int category = elementCategory(element.id());
        int number = 0;
        for (int current = 0; current <= index; current++) {
            if (elementCategory(elements.get(current).id()) == category) {
                number++;
            }
        }
        String key = switch (category) {
            case 0 -> "smallshipsheraldry.part.sail_number";
            case 1 -> "smallshipsheraldry.part.rope_number";
            default -> "smallshipsheraldry.part.other_number";
        };
        return Component.translatable(key, number);
    }

    private static int elementCategory(String elementId) {
        String normalized = elementId.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("rope")) {
            return 1;
        }
        if (normalized.contains("sail") || normalized.contains("segel") || normalized.contains("base")) {
            return 0;
        }
        return 2;
    }

    private int visibleElementRows() {
        return ELEMENT_HEIGHT / ELEMENT_ROW_HEIGHT;
    }

    private void scrollSelectionIntoView(String elementId) {
        List<PaintableSailMesh.Element> elements = elements();
        for (int index = 0; index < elements.size(); index++) {
            if (!elements.get(index).id().equals(elementId)) {
                continue;
            }
            int rows = visibleElementRows();
            if (index < elementScroll) {
                elementScroll = index;
            } else if (index >= elementScroll + rows) {
                elementScroll = Math.max(0, index - rows + 1);
            }
            selectionAnchorIndex = index;
            return;
        }
    }

    private void switchElementContext() {
        String key = session.shipType().serializedName() + ":" + previewStage;
        hiddenElements = hiddenElementsByContext.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
        selectedElementIds = selectedElementsByContext.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
        elementScroll = 0;
        selectionAnchorIndex = -1;
    }

    private void drawControl(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean selected, Component text) {
        boolean hovered = isInside(lastMouseX, lastMouseY, x, y, width, height);
        guiGraphics.fill(x, y, x + width, y + height, selected || hovered ? HeraldryGuiTheme.GOLD_LIGHT : HeraldryGuiTheme.FRAME_DARK);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, selected || hovered ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.PAPER_ALT);
        Component clipped = Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(1, width - 4)));
        drawCenteredNoShadow(guiGraphics, clipped, x + width / 2, y + (height - 8) / 2, HeraldryGuiTheme.INK);
    }

    private void drawIconControl(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean selected, int iconU) {
        boolean hovered = isInside(lastMouseX, lastMouseY, x, y, width, height);
        guiGraphics.fill(x, y, x + width, y + height, selected || hovered ? HeraldryGuiTheme.GOLD_LIGHT : HeraldryGuiTheme.FRAME_DARK);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, selected || hovered ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.PAPER_ALT);
        guiGraphics.blit(TOOL_ICONS, x + (width - 16) / 2, y + (height - 16) / 2, iconU, 0, 16, 16, TOOL_TEXTURE_WIDTH, 16);
    }

    private void drawPixelSelection(GuiGraphics guiGraphics) {
        if (editorMode != EditorMode.PAINT || activeTool != PaintTool.SELECT) {
            return;
        }
        SelectionRect rect = pixelSelection();
        if (rect == null && !pasteActive) {
            return;
        }

        int previewLeft = leftPos + PREVIEW_X;
        int previewTop = topPos + PREVIEW_Y;
        guiGraphics.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 500.0F);
        guiGraphics.enableScissor(previewLeft, previewTop, previewLeft + PREVIEW_WIDTH, previewTop + PREVIEW_HEIGHT);
        try {
            if (pasteActive) {
                int markerX = previewLeft + pasteX;
                int markerY = previewTop + pasteY;
                guiGraphics.fill(markerX - 4, markerY, markerX + 5, markerY + 1, HeraldryGuiTheme.GOLD_LIGHT);
                guiGraphics.fill(markerX, markerY - 4, markerX + 1, markerY + 5, HeraldryGuiTheme.GOLD_LIGHT);
                guiGraphics.fill(markerX, markerY, markerX + 1, markerY + 1, 0xFFFFFFFF);
            } else if (rect != null) {
                Map<Integer, Integer> halfSizes = new HashMap<>();
                for (PaintableSailMesh.ScreenPixel pixel : surfaceSelection) {
                    int halfSize = halfSizes.computeIfAbsent(
                            pixel.hit().faceIndex(),
                            ignored -> Math.max(1, Mth.ceil(
                                    PaintableSailMesh.projectedPixelScale(previewShip, pixel.hit()) * 0.42F
                            ))
                    );
                    int centerX = Mth.floor(pixel.screenX());
                    int centerY = Mth.floor(pixel.screenY());
                    guiGraphics.fill(
                            centerX - halfSize,
                            centerY - halfSize,
                            centerX + halfSize + 1,
                            centerY + halfSize + 1,
                            0x55E7C778
                    );
                    guiGraphics.fill(centerX, centerY, centerX + 1, centerY + 1, HeraldryGuiTheme.GOLD_LIGHT);
                }
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            guiGraphics.disableScissor();
            guiGraphics.pose().popPose();
            guiGraphics.flush();
            RenderSystem.enableDepthTest();
        }
    }

    private void drawLinePreview(GuiGraphics guiGraphics) {
        double deltaX = strokeEndScreenX - strokeStartScreenX;
        double deltaY = strokeEndScreenY - strokeStartScreenY;
        float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        float thickness = lineScreenThickness();
        guiGraphics.enableScissor(leftPos + PREVIEW_X, topPos + PREVIEW_Y,
                leftPos + PREVIEW_X + PREVIEW_WIDTH, topPos + PREVIEW_Y + PREVIEW_HEIGHT);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(strokeStartScreenX, strokeStartScreenY, 0.0D);
        guiGraphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(deltaY, deltaX)));
        int half = Math.max(1, Mth.ceil(thickness * 0.5F));
        guiGraphics.fill(0, -half, Math.max(1, Mth.ceil(length)), half, colorPicker.color() | 0xA0000000);
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
    }

    private void drawCenteredNoShadow(GuiGraphics guiGraphics, Component text, int centerX, int y, int color) {
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record SelectionRect(int x, int y, int width, int height) {
    }

    private record SelectionClipboard(
            int width,
            int height,
            List<PaintableSailMesh.ClipboardPixel> pixels,
            int originX,
            int originY
    ) {
    }

    private record CapturedPixel(PaintableSailMesh.Hit hit, double screenX, double screenY, int color) {
    }

    private enum PaintTool {
        BRUSH("brush", true, 0),
        ERASER("eraser", true, 16),
        PICKER("picker", false, 32),
        FILL("fill", false, 48),
        SELECT("select", false, 128);

        private final String label;
        private final boolean drags;
        private final int iconU;

        PaintTool(String label, boolean drags, int iconU) {
            this.label = label;
            this.drags = drags;
            this.iconU = iconU;
        }

        private Component displayName() {
            return Component.translatable("smallshipsheraldry.tool." + label);
        }

        private boolean drags() {
            return drags;
        }

        private String label() {
            return label;
        }

        private int iconU() {
            return iconU;
        }

        private static PaintTool byName(String name) {
            if ("fill_face".equalsIgnoreCase(name)) {
                return FILL;
            }
            for (PaintTool tool : values()) {
                if (tool.label.equalsIgnoreCase(name)) {
                    return tool;
                }
            }
            return BRUSH;
        }
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

    private enum StrokeMode {
        FREEHAND("freehand"),
        LINE("line");

        private final String label;

        StrokeMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private static StrokeMode byName(String name) {
            for (StrokeMode mode : values()) {
                if (mode.label.equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return FREEHAND;
        }
    }

    private enum FillMode {
        AREA("area"),
        FACE("face"),
        REPLACE("replace");

        private final String label;

        FillMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private Component displayName() {
            return Component.translatable("smallshipsheraldry.fill." + label);
        }

        private FillMode next() {
            FillMode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }

        private FillMode previous() {
            FillMode[] modes = values();
            return modes[(ordinal() + values().length - 1) % modes.length];
        }

        private static FillMode byName(String name) {
            for (FillMode mode : values()) {
                if (mode.label.equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return AREA;
        }
    }

}
