package me.mss1r.smallshipsheraldry.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class HeraldryTableScreen extends AbstractContainerScreen<HeraldryTableMenu> {
    private static final ResourceLocation TOOL_ICONS = new ResourceLocation(SmallShipsHeraldry.MOD_ID, "textures/gui/heraldry_tools.png");
    private static final int MAX_HISTORY_ENTRIES = 64;
    private static final int MAX_HISTORY_BYTES = 32 * 1024 * 1024;

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
    private static final int COLOR_X = RIGHT_X + 4;
    private static final int COLOR_Y = 26;
    private static final int COLOR_WIDTH = 62;
    private static final int COLOR_HEIGHT = 52;
    private static final int HUE_X = COLOR_X + COLOR_WIDTH + 6;
    private static final int HUE_WIDTH = 8;
    private static final int OPACITY_X = RIGHT_X + 4;
    private static final int OPACITY_Y = 80;
    private static final int OPACITY_WIDTH = RIGHT_WIDTH - 8;
    private static final int OPACITY_HEIGHT = 5;
    private static final int CURRENT_COLOR_X = RIGHT_X + 4;
    private static final int CURRENT_COLOR_Y = 88;
    private static final int CURRENT_COLOR_SIZE = 14;
    private static final int HEX_X = RIGHT_X + 22;
    private static final int HEX_Y = 88;
    private static final int HEX_WIDTH = 62;
    private static final int HEX_HEIGHT = 14;
    private static final int CUSTOM_X = RIGHT_X + 4;
    private static final int CUSTOM_Y = 106;
    private static final int SWATCH_X = RIGHT_X + 4;
    private static final int SWATCH_Y = 118;
    private static final int SWATCH_SIZE = 8;
    private static final int SWATCH_GAP = 2;
    private static final int SWATCH_COLUMNS = 8;
    private static final int TOOL_X = ELEMENT_X + 16;
    private static final int TOOL_Y = 42;
    private static final int TOOL_WIDTH = 18;
    private static final int TOOL_HEIGHT = 18;
    private static final int TOOL_GAP = 4;
    private static final int TOOL_TEXTURE_WIDTH = 128;
    private static final int SETTINGS_LABEL_X = ELEMENT_X;
    private static final int SIZE_Y = 68;
    private static final int STROKE_Y = 88;
    private static final int SYMMETRY_Y = 108;
    private static final int FILL_MODE_Y = 68;
    private static final int SCOPE_ACTION_Y = 164;

    private static final int[] BASE_COLORS = {
            0xFFFFFFFF, 0xFFD87F33, 0xFFB24CD8, 0xFF6699D8,
            0xFFE5E533, 0xFF7FCC19, 0xFFF27FA5, 0xFF4C4C4C,
            0xFF999999, 0xFF4C7F99, 0xFF7F3FB2, 0xFF334CB2,
            0xFF664C33, 0xFF667F33, 0xFF993333, 0xFF191919
    };
    private static final int[] CUSTOM_COLORS = new int[SWATCH_COLUMNS];
    private static boolean customColorsLoaded;

    private HeraldryPatternData patternData = HeraldryPatternData.blank();
    private HeraldryTableDrafts drafts = HeraldryTableDrafts.blank();
    private int[] paintPixels = new int[HeraldryPatternData.MESH_PIXEL_COUNT];
    private HeraldryDraftTexture draftTexture;
    private ShipPreviewType previewType = ShipPreviewType.COG;
    private Ship previewShip;
    private final Map<String, Set<String>> hiddenElementsByContext = new HashMap<>();
    private final Map<String, Set<String>> selectedElementsByContext = new HashMap<>();
    private final Map<ShipPreviewType, EditHistory> histories = new HashMap<>();
    private Set<String> hiddenElements = new LinkedHashSet<>();
    private Set<String> selectedElementIds = new LinkedHashSet<>();
    private PaperEditBox hexInput;
    private PaperButton printButton;
    private int previewStage = 4;
    private float previewYaw = 180.0F;
    private float previewPitch = -10.0F;
    private float previewZoom = 1.0F;
    private float previewPanX = 0.0F;
    private float previewPanY = 0.0F;
    private float hue = 0.0F;
    private float saturation = 0.66F;
    private float brightness = 0.60F;
    private int brushColor = 0xFF993333;
    private int brushOpacity = 255;
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
    private boolean pickingColor;
    private boolean pickingHue;
    private boolean pickingOpacity;
    private boolean strokeUndoCaptured;
    private PaintableSailMesh.Hit strokeStartHit;
    private PaintableSailMesh.Hit lastStrokeHit;
    private double strokeStartScreenX;
    private double strokeStartScreenY;
    private double strokeEndScreenX;
    private double strokeEndScreenY;
    private boolean gridVisible = true;
    private boolean helpVisible;
    private double lastMouseX;
    private double lastMouseY;
    private boolean syncingHexInput;
    private boolean draftInitialized;
    private boolean editorSettingsLoaded;
    private boolean draftSyncRequested;
    private boolean localDirty;
    private boolean draftSavePending;
    private long draftSaveAt;
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
        ensureCustomColorsLoaded();
        if (!editorSettingsLoaded) {
            HeraldryClientSettings.EditorSettings settings = HeraldryClientSettings.loadEditorSettings();
            setBrushColor(settings.brushColor());
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
        if (!draftInitialized) {
            drafts = menu.getInitialDrafts();
            previewType = drafts.activeShipType();
            patternData = drafts.get(previewType);
            paintPixels = patternData.toDenseMeshPixels();
            draftInitialized = true;
        }
        switchElementContext();
        this.patternData = patternData.withShipType(previewType);
        if (draftTexture == null) {
            draftTexture = new HeraldryDraftTexture("table", paintPixels);
        }
        rebuildPreviewShip();
        requestDraftSync();

        int x0 = leftPos;
        int y0 = topPos;
        hexInput = new PaperEditBox(font, x0 + HEX_X + 3, y0 + HEX_Y + 2, HEX_WIDTH - 6, HEX_HEIGHT - 4, Component.translatable("smallshipsheraldry.screen.hex"));
        hexInput.setMaxLength(7);
        hexInput.setBordered(false);
        hexInput.setTextColor(HeraldryGuiTheme.INK);
        hexInput.setTextColorUneditable(HeraldryGuiTheme.MUTED_INK);
        hexInput.setFilter(HeraldryTableScreen::isHexDraft);
        hexInput.setResponder(this::handleHexInput);
        hexInput.visible = editorMode == EditorMode.PAINT;
        syncHexInput();
        addRenderableWidget(hexInput);

        int previewCenter = x0 + PREVIEW_X + PREVIEW_WIDTH / 2;
        int controlY = y0 + 206;
        addRenderableWidget(new PaperButton(x0 + imageWidth - HELP_SIZE - HELP_MARGIN, y0 + HELP_Y, HELP_SIZE, HELP_SIZE, Component.literal("?"), button -> helpVisible = !helpVisible));
        addRenderableWidget(new PaperButton(x0 + PREVIEW_X + 12, controlY, 20, 18, Component.literal("<"), button -> changePreviewType(previewType.previous())));
        addRenderableWidget(new PaperButton(x0 + PREVIEW_X + PREVIEW_WIDTH - 32, controlY, 20, 18, Component.literal(">"), button -> changePreviewType(previewType.next())));
        addRenderableWidget(new PaperButton(previewCenter - 62, controlY, 24, 18, Component.literal("S<"), button -> changePreviewStage(previewStage - 1)));
        addRenderableWidget(new PaperButton(previewCenter + 38, controlY, 24, 18, Component.literal("S>"), button -> changePreviewStage(previewStage + 1)));
        printButton = addRenderableWidget(new PaperButton(x0 + RIGHT_X + 4, y0 + 230, 82, 18, Component.translatable("smallshipsheraldry.button.export"), button -> exportPattern()));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        HeraldryGuiTheme.drawWindow(guiGraphics, x0, y0, imageWidth, imageHeight);

        drawLeftPanel(guiGraphics, x0, y0);
        drawPreview(guiGraphics, x0, y0);
        drawRightPanel(guiGraphics, x0, y0);
        drawTableSlots(guiGraphics, x0, y0);

        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.screen.preview"), x0 + PREVIEW_X + PREVIEW_WIDTH / 2, y0 + 18, HeraldryGuiTheme.INK);
        drawCenteredNoShadow(guiGraphics, Component.translatable("smallshipsheraldry.screen.stage", previewStage + 1), x0 + PREVIEW_X + PREVIEW_WIDTH / 2, y0 + 211, HeraldryGuiTheme.MUTED_INK);
        drawCenteredNoShadow(guiGraphics, previewType.getDisplayName(), x0 + PREVIEW_X + PREVIEW_WIDTH / 2, y0 + 230, HeraldryGuiTheme.SELECTED);
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
        if (draftSavePending && System.currentTimeMillis() >= draftSaveAt) {
            sendDraftUpdate();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (hexInput != null) {
            hexInput.visible = editorMode == EditorMode.PAINT;
        }
        if (draftTexture != null) {
            draftTexture.uploadIfDirty();
        }
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
                brushColor,
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
        if (draftTexture != null) {
            draftTexture.close();
            draftTexture = null;
        }
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
                if (hasShiftDown()) {
                    panningPreview = true;
                } else {
                    rotatingPreview = true;
                }
                return true;
            }
            if (button == 2) {
                panningPreview = true;
                return true;
            }
            if (editorMode == EditorMode.PARTS) {
                return selectElementAtPreview(mouseX, mouseY);
            }
            if (editorMode != EditorMode.PAINT) {
                return true;
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
        if (pickingColor) {
            setColorFromField(mouseX, mouseY);
            return true;
        }
        if (pickingHue) {
            setHueFromBar(mouseY);
            return true;
        }
        if (pickingOpacity) {
            setOpacityFromBar(mouseX);
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
                if (hit.isPresent() && isHitInSelection(hit.get()) && hit.get().faceIndex() == strokeStartHit.faceIndex()) {
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
        boolean completedPaintStroke = paintingPreview && button == 0;
        boolean completedLine = completedPaintStroke && strokeMode == StrokeMode.LINE && strokeStartHit != null && !temporaryPicker;
        if (completedLine) {
            applyLineStroke();
        }
        if (button == 0 || button == 1 || button == 2) {
            paintingPreview = false;
            rotatingPreview = false;
            panningPreview = false;
            pickingColor = false;
            pickingHue = false;
            pickingOpacity = false;
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
        if (hexInput != null && hexInput.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (helpVisible && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            helpVisible = false;
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
            activeTool = PaintTool.BRUSH;
            editorMode = EditorMode.PAINT;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E) {
            activeTool = PaintTool.ERASER;
            editorMode = EditorMode.PAINT;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_I) {
            activeTool = PaintTool.PICKER;
            editorMode = EditorMode.PAINT;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F) {
            activeTool = PaintTool.FILL;
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
        if (isInsidePreview(mouseX, mouseY)) {
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
            drawScreenLine(guiGraphics, (int) strokeStartScreenX, (int) strokeStartScreenY, (int) strokeEndScreenX, (int) strokeEndScreenY, brushColor | 0xFF000000);
        }
    }

    private void drawRightPanel(GuiGraphics guiGraphics, int x0, int y0) {
        HeraldryGuiTheme.drawPanel(guiGraphics, x0 + RIGHT_X - 4, y0 + 18, RIGHT_WIDTH + 8, ELEMENT_HEIGHT + 22);
        switch (editorMode) {
            case PAINT -> {
                drawColorPicker(guiGraphics, x0, y0);
                drawCustomSwatches(guiGraphics, x0, y0);
                drawBaseSwatches(guiGraphics, x0, y0);
                drawControl(guiGraphics, x0 + RIGHT_X + 4, y0 + 142, 80, 17, false, Component.translatable("smallshipsheraldry.button.base"));
            }
            case PARTS -> drawPartsActions(guiGraphics, x0, y0);
            case VIEW -> drawViewDetails(guiGraphics, x0, y0);
        }
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

    private void drawColorPicker(GuiGraphics guiGraphics, int x0, int y0) {
        guiGraphics.fill(x0 + COLOR_X - 1, y0 + COLOR_Y - 1, x0 + COLOR_X + COLOR_WIDTH + 1, y0 + COLOR_Y + COLOR_HEIGHT + 1, HeraldryGuiTheme.FRAME_DARK);
        for (int x = 0; x < COLOR_WIDTH; x += 2) {
            for (int y = 0; y < COLOR_HEIGHT; y += 2) {
                float localSaturation = x / (float) (COLOR_WIDTH - 1);
                float localBrightness = 1.0F - y / (float) (COLOR_HEIGHT - 1);
                int color = 0xFF000000 | Mth.hsvToRgb(hue, localSaturation, localBrightness);
                guiGraphics.fill(x0 + COLOR_X + x, y0 + COLOR_Y + y, x0 + COLOR_X + x + 2, y0 + COLOR_Y + y + 2, color);
            }
        }
        guiGraphics.fill(x0 + HUE_X - 1, y0 + COLOR_Y - 1, x0 + HUE_X + HUE_WIDTH + 1, y0 + COLOR_Y + COLOR_HEIGHT + 1, HeraldryGuiTheme.FRAME_DARK);
        for (int y = 0; y < COLOR_HEIGHT; y++) {
            float localHue = Math.min(0.995F, y / (float) COLOR_HEIGHT);
            int color = 0xFF000000 | Mth.hsvToRgb(localHue, 1.0F, 1.0F);
            guiGraphics.fill(x0 + HUE_X, y0 + COLOR_Y + y, x0 + HUE_X + HUE_WIDTH, y0 + COLOR_Y + y + 1, color);
        }
        drawOpacityBar(guiGraphics, x0, y0);
        int markerX = x0 + COLOR_X + Mth.clamp(Math.round(saturation * (COLOR_WIDTH - 1)), 0, COLOR_WIDTH - 1);
        int markerY = y0 + COLOR_Y + Mth.clamp(Math.round((1.0F - brightness) * (COLOR_HEIGHT - 1)), 0, COLOR_HEIGHT - 1);
        guiGraphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY - 1, 0xFFFFFFFF);
        guiGraphics.fill(markerX - 2, markerY + 2, markerX + 3, markerY + 3, 0xFFFFFFFF);
        guiGraphics.fill(markerX - 2, markerY - 2, markerX - 1, markerY + 3, 0xFFFFFFFF);
        guiGraphics.fill(markerX + 2, markerY - 2, markerX + 3, markerY + 3, 0xFFFFFFFF);

        int hueY = y0 + COLOR_Y + Mth.clamp(Math.round(hue * COLOR_HEIGHT), 0, COLOR_HEIGHT - 1);
        guiGraphics.fill(x0 + HUE_X - 2, hueY - 1, x0 + HUE_X + HUE_WIDTH + 2, hueY, 0xFF07101D);
        guiGraphics.fill(x0 + HUE_X - 2, hueY, x0 + HUE_X + HUE_WIDTH + 2, hueY + 2, 0xFFFFFFFF);
        int opacityX = x0 + OPACITY_X + Mth.clamp(Math.round((brushOpacity - 1) * (OPACITY_WIDTH - 1) / 254.0F), 0, OPACITY_WIDTH - 1);
        guiGraphics.fill(opacityX - 1, y0 + OPACITY_Y - 2, opacityX, y0 + OPACITY_Y + OPACITY_HEIGHT + 2, 0xFF07101D);
        guiGraphics.fill(opacityX, y0 + OPACITY_Y - 2, opacityX + 2, y0 + OPACITY_Y + OPACITY_HEIGHT + 2, 0xFFFFFFFF);
        guiGraphics.fill(x0 + CURRENT_COLOR_X - 1, y0 + CURRENT_COLOR_Y - 1, x0 + CURRENT_COLOR_X + CURRENT_COLOR_SIZE + 1, y0 + CURRENT_COLOR_Y + CURRENT_COLOR_SIZE + 1, 0xFF07101D);
        drawChecker(guiGraphics, x0 + CURRENT_COLOR_X, y0 + CURRENT_COLOR_Y, CURRENT_COLOR_SIZE, CURRENT_COLOR_SIZE);
        guiGraphics.fill(x0 + CURRENT_COLOR_X, y0 + CURRENT_COLOR_Y, x0 + CURRENT_COLOR_X + CURRENT_COLOR_SIZE, y0 + CURRENT_COLOR_Y + CURRENT_COLOR_SIZE, brushColor);
        guiGraphics.fill(x0 + HEX_X - 1, y0 + HEX_Y - 1, x0 + HEX_X + HEX_WIDTH + 1, y0 + HEX_Y + HEX_HEIGHT + 1, hexInput != null && hexInput.isFocused() ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.FRAME_DARK);
        guiGraphics.fill(x0 + HEX_X, y0 + HEX_Y, x0 + HEX_X + HEX_WIDTH, y0 + HEX_Y + HEX_HEIGHT, HeraldryGuiTheme.PAPER);
    }

    private void drawOpacityBar(GuiGraphics guiGraphics, int x0, int y0) {
        guiGraphics.fill(x0 + OPACITY_X - 1, y0 + OPACITY_Y - 1, x0 + OPACITY_X + OPACITY_WIDTH + 1, y0 + OPACITY_Y + OPACITY_HEIGHT + 1, HeraldryGuiTheme.FRAME_DARK);
        drawChecker(guiGraphics, x0 + OPACITY_X, y0 + OPACITY_Y, OPACITY_WIDTH, OPACITY_HEIGHT);
        int rgb = brushColor & 0x00FFFFFF;
        for (int x = 0; x < OPACITY_WIDTH; x++) {
            int alpha = Mth.clamp(Math.round(1.0F + x * 254.0F / (OPACITY_WIDTH - 1)), 1, 255);
            guiGraphics.fill(x0 + OPACITY_X + x, y0 + OPACITY_Y, x0 + OPACITY_X + x + 1, y0 + OPACITY_Y + OPACITY_HEIGHT, (alpha << 24) | rgb);
        }
    }

    private void drawChecker(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        for (int localY = 0; localY < height; localY += 2) {
            for (int localX = 0; localX < width; localX += 2) {
                boolean bright = ((localX + localY) / 2) % 2 == 0;
                guiGraphics.fill(x + localX, y + localY, x + Math.min(localX + 2, width), y + Math.min(localY + 2, height), bright ? 0xFFE1E6EF : 0xFF77869A);
            }
        }
    }

    private void drawCustomSwatches(GuiGraphics guiGraphics, int x0, int y0) {
        for (int index = 0; index < SWATCH_COLUMNS; index++) {
            int x = x0 + CUSTOM_X + index * (SWATCH_SIZE + SWATCH_GAP);
            int y = y0 + CUSTOM_Y;
            int color = CUSTOM_COLORS[index];
            boolean empty = color == 0;
            boolean selected = color != 0 && color == brushColor;
            guiGraphics.fill(x - 1, y - 1, x + SWATCH_SIZE + 1, y + SWATCH_SIZE + 1, selected ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.FRAME_DARK);
            if (empty) {
                guiGraphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, HeraldryGuiTheme.PAPER_ALT);
            } else {
                drawChecker(guiGraphics, x, y, SWATCH_SIZE, SWATCH_SIZE);
                guiGraphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, color);
            }
            if (empty) {
                int centerX = x + SWATCH_SIZE / 2;
                int centerY = y + SWATCH_SIZE / 2;
                guiGraphics.fill(centerX - 2, centerY, centerX + 3, centerY + 1, 0xFF6E7F96);
                guiGraphics.fill(centerX, centerY - 2, centerX + 1, centerY + 3, 0xFF6E7F96);
            }
        }
    }

    private void drawBaseSwatches(GuiGraphics guiGraphics, int x0, int y0) {
        for (int index = 0; index < BASE_COLORS.length; index++) {
            int column = index % SWATCH_COLUMNS;
            int row = index / SWATCH_COLUMNS;
            int x = x0 + SWATCH_X + column * (SWATCH_SIZE + SWATCH_GAP);
            int y = y0 + SWATCH_Y + row * (SWATCH_SIZE + SWATCH_GAP);
            int color = BASE_COLORS[index];
            boolean selected = (color & 0x00FFFFFF) == (brushColor & 0x00FFFFFF);
            guiGraphics.fill(x - 1, y - 1, x + SWATCH_SIZE + 1, y + SWATCH_SIZE + 1, selected ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.FRAME_DARK);
            guiGraphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, color);
        }
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
        int height = 204;
        HeraldryGuiTheme.drawWindow(guiGraphics, x - 2, y - 2, width + 4, height + 4);
        guiGraphics.drawString(font, Component.translatable("smallshipsheraldry.help.title"), x + 8, y + 8, HeraldryGuiTheme.INK, false);

        Component[] lines = {
                Component.translatable("smallshipsheraldry.help.print"),
                Component.translatable("smallshipsheraldry.help.ship"),
                Component.translatable("smallshipsheraldry.help.paint"),
                Component.translatable("smallshipsheraldry.help.elements"),
                Component.translatable("smallshipsheraldry.help.tools"),
                Component.translatable("smallshipsheraldry.help.colors"),
                Component.translatable("smallshipsheraldry.help.opacity"),
                Component.translatable("smallshipsheraldry.help.history")
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
        drawCenteredNoShadow(guiGraphics, Component.literal("\u2192"), arrowCenter, y0 + HeraldryTableMenu.RESULT_SLOT_Y + 4, HeraldryGuiTheme.MUTED_INK);
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
                editorMode = modes[index];
                if (hexInput != null) {
                    hexInput.visible = editorMode == EditorMode.PAINT;
                    if (!hexInput.visible) {
                        hexInput.setFocused(false);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean handlePaintControlClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
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
            fillScope(brushColor, false);
            return true;
        }
        if (isInside(mouseX, mouseY, leftPos + ELEMENT_X + 60, topPos + SCOPE_ACTION_Y, 56, 17)) {
            fillScope(0, true);
            return true;
        }
        if (isInside(mouseX, mouseY, leftPos + RIGHT_X + 4, topPos + 142, 80, 17)) {
            if (pushUndo()) {
                patternData = patternData.withBaseColor(brushColor);
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
                fillScope(brushColor, false);
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
        if (!PaintableSailMesh.fillScopedPixels(previewShip, previewStage, paintPixels, color, selectedElementIds, hiddenElements)) {
            discardLatestUndo();
            return;
        }
        draftTexture.replacePixels(paintPixels);
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
                activeTool = tools[index];
                return true;
            }
        }
        return false;
    }

    private boolean handleColorClick(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) {
            return false;
        }
        if (button == 0 && isInside(mouseX, mouseY, leftPos + COLOR_X, topPos + COLOR_Y, COLOR_WIDTH, COLOR_HEIGHT)) {
            pickingColor = true;
            setColorFromField(mouseX, mouseY);
            return true;
        }
        if (button == 0 && isInside(mouseX, mouseY, leftPos + HUE_X, topPos + COLOR_Y, HUE_WIDTH, COLOR_HEIGHT)) {
            pickingHue = true;
            setHueFromBar(mouseY);
            return true;
        }
        if (button == 0 && isInside(mouseX, mouseY, leftPos + OPACITY_X, topPos + OPACITY_Y, OPACITY_WIDTH, OPACITY_HEIGHT)) {
            pickingOpacity = true;
            setOpacityFromBar(mouseX);
            return true;
        }
        for (int index = 0; index < SWATCH_COLUMNS; index++) {
            int x = leftPos + CUSTOM_X + index * (SWATCH_SIZE + SWATCH_GAP);
            int y = topPos + CUSTOM_Y;
            if (isInside(mouseX, mouseY, x, y, SWATCH_SIZE, SWATCH_SIZE)) {
                if (button == 1 || hasShiftDown() || CUSTOM_COLORS[index] == 0) {
                    CUSTOM_COLORS[index] = brushColor;
                    HeraldryClientSettings.saveCustomColors(CUSTOM_COLORS);
                } else {
                    setBrushColor(CUSTOM_COLORS[index]);
                }
                return true;
            }
        }
        if (button != 0) {
            return false;
        }
        for (int index = 0; index < BASE_COLORS.length; index++) {
            int column = index % SWATCH_COLUMNS;
            int row = index / SWATCH_COLUMNS;
            int x = leftPos + SWATCH_X + column * (SWATCH_SIZE + SWATCH_GAP);
            int y = topPos + SWATCH_Y + row * (SWATCH_SIZE + SWATCH_GAP);
            if (isInside(mouseX, mouseY, x, y, SWATCH_SIZE, SWATCH_SIZE)) {
                setBrushColor(BASE_COLORS[index]);
                return true;
            }
        }
        return false;
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
            int sampledColor = paintPixels[paintHit.pixelY() * HeraldryPatternData.MESH_ATLAS_WIDTH + paintHit.pixelX()];
            setBrushColor((sampledColor >>> 24) == 0 ? patternData.baseColorArgb() : sampledColor);
            return true;
        }

        if (activeTool == PaintTool.FILL) {
            if (!pushUndo()) {
                return true;
            }
            boolean changed = switch (fillMode) {
                case AREA -> PaintableSailMesh.floodFillPixels(previewShip, paintHit, previewStage, paintPixels, brushColor);
                case FACE -> PaintableSailMesh.fillFacePixels(previewShip, paintHit.faceIndex(), previewStage, paintPixels, brushColor);
                case REPLACE -> {
                    int source = paintPixels[paintHit.pixelY() * HeraldryPatternData.MESH_ATLAS_WIDTH + paintHit.pixelX()];
                    yield PaintableSailMesh.replaceColorPixels(previewShip, previewStage, paintPixels, source, brushColor, selectedElementIds, hiddenElements);
                }
            };
            if (!changed) {
                discardLatestUndo();
                return true;
            }
            if (fillMode == FillMode.REPLACE) {
                draftTexture.replacePixels(paintPixels);
            } else {
                syncHitFace(paintHit);
            }
            commitPaintBuffer();
            return true;
        }

        int color = activeTool == PaintTool.ERASER ? 0 : brushColor;
        if (!strokeUndoCaptured) {
            if (!pushUndo()) {
                return true;
            }
            strokeUndoCaptured = true;
        }
        boolean changed;
        if (lastStrokeHit != null && lastStrokeHit.faceIndex() == paintHit.faceIndex()) {
            changed = PaintableSailMesh.paintLinePixels(previewShip, lastStrokeHit, paintHit, previewStage, paintPixels, color,
                    brushSize, mirrorVertical, mirrorHorizontal);
        } else {
            changed = PaintableSailMesh.paintBrushPixels(previewShip, paintHit, previewStage, paintPixels, color,
                    brushSize, mirrorVertical, mirrorHorizontal);
        }
        lastStrokeHit = paintHit;
        if (changed) {
            syncHitFace(paintHit);
            localDirty = true;
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
            draftTexture.updateRegion(paintPixels, hit.slotX() - 1, hit.slotY() - 1, hit.slotWidth() + 2, hit.slotHeight() + 2);
            draftTexture.uploadIfDirty();
        }
    }

    private void applyLineStroke() {
        if (strokeStartHit == null || lastStrokeHit == null || !pushUndo()) {
            return;
        }
        int color = activeTool == PaintTool.ERASER ? 0 : brushColor;
        boolean changed = PaintableSailMesh.paintLinePixels(previewShip, strokeStartHit, lastStrokeHit, previewStage,
                paintPixels, color, brushSize, mirrorVertical, mirrorHorizontal);
        if (!changed) {
            discardLatestUndo();
            return;
        }
        syncHitFace(strokeStartHit);
        if (lastStrokeHit.faceIndex() != strokeStartHit.faceIndex()) {
            syncHitFace(lastStrokeHit);
        }
        localDirty = true;
        commitPaintBuffer();
    }

    private Optional<Integer> gridFaceIndexUnderMouse() {
        if (!isInsidePreview(lastMouseX, lastMouseY)) {
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
        if (newPreviewType == previewType || currentData() == null) {
            return;
        }
        drafts = drafts.withActiveShipType(newPreviewType);
        previewType = newPreviewType;
        patternData = drafts.get(previewType);
        paintPixels = patternData.toDenseMeshPixels();
        if (draftTexture != null) {
            draftTexture.replacePixels(paintPixels);
        }
        switchElementContext();
        previewPanX = 0.0F;
        previewPanY = 0.0F;
        markDirty();
        rebuildPreviewShip();
    }

    private void changePreviewStage(int newStage) {
        previewStage = Mth.clamp(newStage, 0, 4);
        switchElementContext();
        PreviewShipFactory.setSailState(previewShip, previewStage);
    }

    private void exportPattern() {
        HeraldryPatternData data = currentData();
        if (data == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(menu.getBlockPos());
        buf.writeNbt(data.toTag());
        NetworkManager.sendToServer(HeraldryPackets.EXPORT_PATTERN, buf);
        draftSavePending = false;
    }

    private void sendDraftUpdate() {
        HeraldryPatternData data = currentData();
        if (data == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(menu.getBlockPos());
        buf.writeNbt(drafts.toTag());
        NetworkManager.sendToServer(HeraldryPackets.TABLE_DRAFT_UPDATE, buf);
        draftSavePending = false;
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

    public static void handleDraftSync(BlockPos blockPos, CompoundTag draftTag) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof HeraldryTableScreen screen) {
            screen.applyDraftSync(blockPos, draftTag);
        }
    }

    private void applyDraftSync(BlockPos blockPos, CompoundTag draftTag) {
        if (!menu.getBlockPos().equals(blockPos) || draftTag == null || localDirty) {
            return;
        }

        drafts = HeraldryTableDrafts.fromTag(draftTag);
        previewType = drafts.activeShipType();
        patternData = drafts.get(previewType);
        paintPixels = patternData.toDenseMeshPixels();
        if (draftTexture != null) {
            draftTexture.replacePixels(paintPixels);
        }
        hiddenElementsByContext.clear();
        selectedElementsByContext.clear();
        histories.clear();
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
        previewShip = PreviewShipFactory.create(previewType, patternData.withShipType(previewType), previewStage);
    }

    private void updatePreview() {
        if (previewShip == null) {
            rebuildPreviewShip();
        } else {
            PreviewShipFactory.applyBaseColor(previewShip, patternData.baseColorArgb());
            PreviewShipFactory.setSailState(previewShip, previewStage);
        }
    }

    private HeraldryPatternData currentData() {
        try {
            patternData = HeraldryPatternData.fromMeshPixels(
                    patternData.author(),
                    previewType,
                    patternData.baseColorArgb(),
                    paintPixels
            );
            drafts = drafts.withDraft(patternData);
            return patternData;
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
        EditHistory history = history();
        if (!history.undo.isEmpty()) {
            HeraldryPatternData restored = history.undo.pop();
            history.undoBytes -= restored.estimatedMemoryBytes();
            restoreSnapshot(restored);
        }
    }

    private void restoreSnapshot(HeraldryPatternData snapshot) {
        boolean shipChanged = previewType != snapshot.shipType();
        patternData = snapshot;
        previewType = snapshot.shipType();
        drafts = drafts.withDraft(snapshot).withActiveShipType(previewType);
        paintPixels = snapshot.toDenseMeshPixels();
        if (draftTexture != null) {
            draftTexture.replacePixels(paintPixels);
        }
        markDirty();
        if (shipChanged) {
            switchElementContext();
            rebuildPreviewShip();
        } else {
            updatePreview();
        }
    }

    private void discardLatestUndo() {
        EditHistory history = history();
        if (!history.undo.isEmpty()) {
            history.undoBytes -= history.undo.pop().estimatedMemoryBytes();
        }
    }

    private void showStatus(Component message) {
        statusMessage = message;
        statusMessageUntil = System.currentTimeMillis() + 4_000L;
    }

    private void markDirty() {
        localDirty = true;
        draftSavePending = true;
        draftSaveAt = System.currentTimeMillis() + 750L;
    }

    private boolean pushUndo() {
        HeraldryPatternData snapshot = currentData();
        if (snapshot == null) {
            return false;
        }
        EditHistory history = history();
        history.undo.push(snapshot);
        history.undoBytes += snapshot.estimatedMemoryBytes();
        history.redo.clear();
        history.redoBytes = 0;
        while (history.undo.size() > MAX_HISTORY_ENTRIES || history.undoBytes > MAX_HISTORY_BYTES) {
            history.undoBytes -= history.undo.removeLast().estimatedMemoryBytes();
        }
        return true;
    }

    private void undo() {
        EditHistory history = history();
        if (history.undo.isEmpty()) {
            return;
        }
        HeraldryPatternData current = currentData();
        if (current == null) {
            return;
        }
        history.redo.push(current);
        history.redoBytes += current.estimatedMemoryBytes();
        while (history.redo.size() > MAX_HISTORY_ENTRIES || history.redoBytes > MAX_HISTORY_BYTES) {
            history.redoBytes -= history.redo.removeLast().estimatedMemoryBytes();
        }
        HeraldryPatternData restored = history.undo.pop();
        history.undoBytes -= restored.estimatedMemoryBytes();
        restoreSnapshot(restored);
    }

    private void redo() {
        EditHistory history = history();
        if (history.redo.isEmpty()) {
            return;
        }
        HeraldryPatternData current = currentData();
        if (current == null) {
            return;
        }
        history.undo.push(current);
        history.undoBytes += current.estimatedMemoryBytes();
        while (history.undo.size() > MAX_HISTORY_ENTRIES || history.undoBytes > MAX_HISTORY_BYTES) {
            history.undoBytes -= history.undo.removeLast().estimatedMemoryBytes();
        }
        HeraldryPatternData restored = history.redo.pop();
        history.redoBytes -= restored.estimatedMemoryBytes();
        restoreSnapshot(restored);
    }

    private EditHistory history() {
        return histories.computeIfAbsent(previewType, ignored -> new EditHistory());
    }

    private void setColorFromField(double mouseX, double mouseY) {
        saturation = Mth.clamp((float) ((mouseX - (leftPos + COLOR_X)) / (COLOR_WIDTH - 1)), 0.0F, 1.0F);
        brightness = Mth.clamp(1.0F - (float) ((mouseY - (topPos + COLOR_Y)) / (COLOR_HEIGHT - 1)), 0.0F, 1.0F);
        rebuildBrushColor();
    }

    private void setHueFromBar(double mouseY) {
        hue = Mth.clamp((float) ((mouseY - (topPos + COLOR_Y)) / COLOR_HEIGHT), 0.0F, 0.995F);
        rebuildBrushColor();
    }

    private void setOpacityFromBar(double mouseX) {
        float value = Mth.clamp((float) ((mouseX - (leftPos + OPACITY_X)) / (OPACITY_WIDTH - 1)), 0.0F, 1.0F);
        brushOpacity = Mth.clamp(Math.round(1.0F + value * 254.0F), 1, 255);
        rebuildBrushColor();
    }

    private void rebuildBrushColor() {
        brushColor = (brushOpacity << 24) | Mth.hsvToRgb(hue, saturation, brightness);
        syncHexInput();
    }

    private void setBrushColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        brushOpacity = alpha == 0 ? 255 : alpha;
        brushColor = (argb & 0x00FFFFFF) | (brushOpacity << 24);
        float red = ((brushColor >> 16) & 0xFF) / 255.0F;
        float green = ((brushColor >> 8) & 0xFF) / 255.0F;
        float blue = (brushColor & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;

        brightness = max;
        saturation = max <= 0.0001F ? 0.0F : delta / max;
        if (delta <= 0.0001F) {
            hue = 0.0F;
        } else if (max == red) {
            hue = ((green - blue) / delta) / 6.0F;
        } else if (max == green) {
            hue = (2.0F + (blue - red) / delta) / 6.0F;
        } else {
            hue = (4.0F + (red - green) / delta) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        hue = Mth.clamp(hue, 0.0F, 0.995F);
        syncHexInput();
    }

    private void handleHexInput(String value) {
        if (syncingHexInput) {
            return;
        }
        String clean = cleanHex(value);
        if (clean.length() != 6 || !isHex(clean)) {
            return;
        }
        int color = (brushOpacity << 24) | Integer.parseInt(clean, 16);
        if ((color & 0x00FFFFFF) == (brushColor & 0x00FFFFFF)) {
            return;
        }
        setBrushColor(color);
    }

    private void syncHexInput() {
        if (hexInput == null) {
            return;
        }
        String value = String.format("#%06X", brushColor & 0x00FFFFFF);
        if (value.equals(hexInput.getValue())) {
            return;
        }
        syncingHexInput = true;
        hexInput.setValue(value);
        syncingHexInput = false;
    }

    private static boolean isHexDraft(String value) {
        if (value.length() > 7) {
            return false;
        }
        if (value.indexOf('#') > 0) {
            return false;
        }
        String clean = cleanHex(value);
        return clean.length() <= 6 && isHex(clean);
    }

    private static String cleanHex(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    private static boolean isHex(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean digit = character >= '0' && character <= '9';
            boolean upper = character >= 'A' && character <= 'F';
            boolean lower = character >= 'a' && character <= 'f';
            if (!digit && !upper && !lower) {
                return false;
            }
        }
        return true;
    }

    private static void ensureCustomColorsLoaded() {
        if (customColorsLoaded) {
            return;
        }
        customColorsLoaded = true;
        HeraldryClientSettings.loadCustomColors(CUSTOM_COLORS);
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

    private List<PaintableSailMesh.Element> elements() {
        List<PaintableSailMesh.Element> elements = new ArrayList<>(PaintableSailMesh.elements(previewType, previewStage));
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
        String key = previewType.serializedName() + ":" + previewStage;
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

    private void drawScreenLine(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int stepX = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int stepY = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            if (isInsidePreview(x0, y0)) {
                guiGraphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            }
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int twiceError = error * 2;
            if (twiceError >= dy) {
                error += dy;
                x0 += stepX;
            }
            if (twiceError <= dx) {
                error += dx;
                y0 += stepY;
            }
        }
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

    private enum PaintTool {
        BRUSH("brush", true, 0),
        ERASER("eraser", true, 16),
        PICKER("picker", false, 32),
        FILL("fill", false, 48);

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

    private static final class EditHistory {
        private final Deque<HeraldryPatternData> undo = new ArrayDeque<>();
        private final Deque<HeraldryPatternData> redo = new ArrayDeque<>();
        private int undoBytes;
        private int redoBytes;
    }
}
