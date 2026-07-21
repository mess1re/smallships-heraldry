package me.mss1r.smallshipsheraldry.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.client.HeraldryPatternLibrary;
import me.mss1r.smallshipsheraldry.client.render.HeraldryDraftTexture;
import me.mss1r.smallshipsheraldry.client.render.PreviewShipFactory;
import me.mss1r.smallshipsheraldry.client.render.PreviewShipRenderer;
import me.mss1r.smallshipsheraldry.common.HeraldryPatternData;
import me.mss1r.smallshipsheraldry.common.ShipPreviewType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

final class HeraldryLibraryPanel implements AutoCloseable {
    private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");
    private static final int X = 32;
    private static final int Y = 24;
    private static final int WIDTH = 406;
    private static final int HEIGHT = 212;
    private static final int LIST_X = 12;
    private static final int LIST_Y = 48;
    private static final int LIST_WIDTH = 156;
    private static final int ROW_HEIGHT = 13;
    private static final int VISIBLE_ROWS = 9;

    private final Font font;
    private final Controller controller;
    private List<HeraldryPatternLibrary.Entry> entries = List.of();
    private PaperEditBox nameInput;
    private int selection = -1;
    private int scroll;
    private int leftPos;
    private int topPos;
    private boolean visible;
    private Ship previewShip;
    private HeraldryDraftTexture previewTexture;
    private Component message;
    private long messageUntil;
    private long confirmUntil;
    private Action confirmAction;

    HeraldryLibraryPanel(Font font, Controller controller) {
        this.font = font;
        this.controller = controller;
    }

    void init(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
        nameInput = new PaperEditBox(font, leftPos + X + 13, topPos + Y + 24, 148, 14,
                Component.translatable("smallshipsheraldry.library.name"));
        nameInput.setMaxLength(HeraldryPatternLibrary.MAX_NAME_LENGTH);
        nameInput.setBordered(false);
        nameInput.setTextColor(HeraldryGuiTheme.INK);
        nameInput.setTextColorUneditable(HeraldryGuiTheme.MUTED_INK);
        nameInput.setResponder(value -> clearConfirmation());
    }

    boolean isVisible() {
        return visible;
    }

    void open() {
        visible = true;
        refresh(null);
        nameInput.setValue(defaultName());
        nameInput.setFocused(true);
        clearConfirmation();
    }

    void closePanel() {
        visible = false;
        if (nameInput != null) {
            nameInput.setFocused(false);
        }
        closePreview();
        clearConfirmation();
    }

    void tick() {
        if (visible && nameInput != null) {
            nameInput.tick();
        }
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.flush();
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        try {
            drawPopup(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.pose().popPose();
            graphics.flush();
            RenderSystem.enableDepthTest();
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        int x = leftPos + X;
        int y = topPos + Y;
        if (nameInput != null && isInside(mouseX, mouseY, x + 11, y + 22, 152, 17)) {
            nameInput.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (nameInput != null) {
            nameInput.setFocused(false);
        }
        if (isInside(mouseX, mouseY, x + 166, y + 22, 54, 17)) {
            save();
            return true;
        }
        int listX = x + LIST_X;
        int listY = y + LIST_Y;
        if (isInside(mouseX, mouseY, listX, listY, LIST_WIDTH, VISIBLE_ROWS * ROW_HEIGHT)) {
            select(scroll + (int) ((mouseY - listY) / ROW_HEIGHT));
            return true;
        }
        if (isInside(mouseX, mouseY, x + 12, y + 184, 70, 17)) {
            load();
        } else if (isInside(mouseX, mouseY, x + 86, y + 184, 70, 17)) {
            rename();
        } else if (isInside(mouseX, mouseY, x + 160, y + 184, 70, 17)) {
            delete();
        } else if (isInside(mouseX, mouseY, x + 324, y + 184, 70, 17)) {
            closePanel();
        }
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePanel();
            return true;
        }
        if (nameInput != null && nameInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        return nameInput != null && nameInput.charTyped(codePoint, modifiers);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int listX = leftPos + X + LIST_X;
        int listY = topPos + Y + LIST_Y;
        if (isInside(mouseX, mouseY, listX, listY, LIST_WIDTH, VISIBLE_ROWS * ROW_HEIGHT)) {
            int maxScroll = Math.max(0, entries.size() - VISIBLE_ROWS);
            scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, maxScroll);
        }
        return true;
    }

    @Override
    public void close() {
        closePreview();
    }

    private void drawPopup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = leftPos + X;
        int y = topPos + Y;
        HeraldryGuiTheme.drawWindow(graphics, x, y, WIDTH, HEIGHT);
        graphics.drawString(font, Component.translatable("smallshipsheraldry.library.title"), x + 12, y + 10, HeraldryGuiTheme.INK, false);

        graphics.fill(x + 11, y + 22, x + 163, y + 39,
                nameInput != null && nameInput.isFocused() ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.FRAME_DARK);
        graphics.fill(x + 12, y + 23, x + 162, y + 38, HeraldryGuiTheme.PAPER_ALT);
        if (nameInput != null) {
            nameInput.render(graphics, mouseX, mouseY, partialTick);
        }
        drawControl(graphics, mouseX, mouseY, x + 166, y + 22, 54, 17,
                Component.translatable("smallshipsheraldry.library.save"));

        Component currentMessage = System.currentTimeMillis() < messageUntil
                ? message
                : Component.translatable("smallshipsheraldry.library.local_hint");
        if (currentMessage != null) {
            List<FormattedCharSequence> lines = font.split(currentMessage, 170);
            for (int index = 0; index < Math.min(2, lines.size()); index++) {
                graphics.drawString(font, lines.get(index), x + 226, y + 22 + index * 9, HeraldryGuiTheme.MUTED_INK, false);
            }
        }

        drawEntries(graphics, mouseX, mouseY, x, y);
        drawPreview(graphics, x, y);
        drawControl(graphics, mouseX, mouseY, x + 12, y + 184, 70, 17, confirmLabel(Action.LOAD, "smallshipsheraldry.library.load"));
        drawControl(graphics, mouseX, mouseY, x + 86, y + 184, 70, 17, Component.translatable("smallshipsheraldry.library.rename"));
        drawControl(graphics, mouseX, mouseY, x + 160, y + 184, 70, 17, confirmLabel(Action.DELETE, "smallshipsheraldry.library.delete"));
        drawControl(graphics, mouseX, mouseY, x + 324, y + 184, 70, 17, Component.translatable("smallshipsheraldry.library.close"));
    }

    private void drawEntries(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        int listX = x + LIST_X;
        int listY = y + LIST_Y;
        HeraldryGuiTheme.drawPanel(graphics, listX - 2, listY - 2, LIST_WIDTH + 4, VISIBLE_ROWS * ROW_HEIGHT + 4);
        if (entries.isEmpty()) {
            drawCentered(graphics, Component.translatable("smallshipsheraldry.library.empty"), listX + LIST_WIDTH / 2, listY + 52);
            return;
        }
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            int rowY = listY + row * ROW_HEIGHT;
            if (index >= entries.size()) {
                graphics.fill(listX, rowY, listX + LIST_WIDTH, rowY + 11, HeraldryGuiTheme.PAPER_ALT);
                continue;
            }
            HeraldryPatternLibrary.Entry entry = entries.get(index);
            boolean selected = index == selection;
            boolean hovered = isInside(mouseX, mouseY, listX, rowY, LIST_WIDTH, 11);
            graphics.fill(listX, rowY, listX + LIST_WIDTH, rowY + 11,
                    selected ? HeraldryGuiTheme.GOLD : hovered ? HeraldryGuiTheme.INSET : HeraldryGuiTheme.PAPER_ALT);
            graphics.drawString(font, font.plainSubstrByWidth(entry.name(), LIST_WIDTH - 8), listX + 4, rowY + 2, HeraldryGuiTheme.INK, false);
        }
    }

    private void drawPreview(GuiGraphics graphics, int x, int y) {
        int previewX = x + 180;
        int previewY = y + 48;
        int previewWidth = 214;
        int previewHeight = 112;
        HeraldryGuiTheme.drawPreviewInset(graphics, previewX - 2, previewY - 2, previewWidth + 4, previewHeight + 4);
        HeraldryPatternLibrary.Entry selected = selectedEntry();
        if (selected == null || previewShip == null || previewTexture == null) {
            return;
        }
        previewTexture.uploadIfDirty();
        RenderSystem.enableDepthTest();
        PreviewShipRenderer.render(graphics, previewX, previewY, previewX + previewWidth, previewY + previewHeight,
                previewShip, 180.0F, -10.0F, 0.82F, 0.0F, 0.0F, Set.of(), previewTexture.textureLocation());
        graphics.flush();
        RenderSystem.disableDepthTest();
        Component details = Component.translatable("smallshipsheraldry.library.details",
                selected.data().shipType().getDisplayName(), controller.previewStage() + 1);
        drawCentered(graphics, details, previewX + previewWidth / 2, y + 166);
    }

    private void refresh(String selectName) {
        entries = HeraldryPatternLibrary.list();
        selection = -1;
        if (selectName != null) {
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index).name().equalsIgnoreCase(selectName)) {
                    selection = index;
                    break;
                }
            }
        }
        if (selection < 0 && !entries.isEmpty()) {
            selection = 0;
        }
        int maxScroll = Math.max(0, entries.size() - VISIBLE_ROWS);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        if (selection >= 0) {
            if (selection < scroll) {
                scroll = selection;
            } else if (selection >= scroll + VISIBLE_ROWS) {
                scroll = selection - VISIBLE_ROWS + 1;
            }
        }
        rebuildPreview();
    }

    private void select(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        selection = index;
        nameInput.setValue(entries.get(index).name());
        clearConfirmation();
        rebuildPreview();
    }

    private void rebuildPreview() {
        closePreview();
        HeraldryPatternLibrary.Entry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        HeraldryPatternData data = entry.data();
        previewTexture = new HeraldryDraftTexture("library", data.toDenseMeshPixels());
        previewShip = PreviewShipFactory.create(data.shipType(), data, controller.previewStage());
    }

    private void closePreview() {
        previewShip = null;
        if (previewTexture != null) {
            previewTexture.close();
            previewTexture = null;
        }
    }

    private void save() {
        HeraldryPatternData data = controller.currentData();
        if (data == null || nameInput == null) {
            return;
        }
        String name = HeraldryPatternLibrary.sanitizeName(nameInput.getValue());
        if (name.isEmpty()) {
            showMessage("smallshipsheraldry.library.invalid_name");
            return;
        }
        boolean overwrite = confirmAction == Action.OVERWRITE && System.currentTimeMillis() < confirmUntil;
        HeraldryPatternLibrary.Result result = HeraldryPatternLibrary.save(name, data, overwrite);
        if (result == HeraldryPatternLibrary.Result.ALREADY_EXISTS) {
            requestConfirmation(Action.OVERWRITE, "smallshipsheraldry.library.overwrite");
            return;
        }
        clearConfirmation();
        if (result == HeraldryPatternLibrary.Result.SUCCESS) {
            refresh(name);
            showMessage("smallshipsheraldry.library.saved");
        } else {
            showResult(result);
        }
    }

    private void load() {
        HeraldryPatternLibrary.Entry entry = selectedEntry();
        if (entry == null || controller.currentData() == null) {
            return;
        }
        HeraldryPatternData imported = entry.data();
        if (controller.hasDraftWork(imported.shipType())
                && (confirmAction != Action.LOAD || System.currentTimeMillis() >= confirmUntil)) {
            requestConfirmation(Action.LOAD, "smallshipsheraldry.library.replace_draft");
            return;
        }
        controller.load(imported);
        closePanel();
        controller.showStatus(Component.translatable("smallshipsheraldry.library.loaded"));
    }

    private void rename() {
        HeraldryPatternLibrary.Entry entry = selectedEntry();
        if (entry == null || nameInput == null) {
            return;
        }
        String name = HeraldryPatternLibrary.sanitizeName(nameInput.getValue());
        HeraldryPatternLibrary.Result result = HeraldryPatternLibrary.rename(entry, name);
        if (result == HeraldryPatternLibrary.Result.SUCCESS) {
            refresh(name);
            showMessage("smallshipsheraldry.library.renamed");
        } else {
            showResult(result);
        }
    }

    private void delete() {
        HeraldryPatternLibrary.Entry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        if (confirmAction != Action.DELETE || System.currentTimeMillis() >= confirmUntil) {
            requestConfirmation(Action.DELETE, "smallshipsheraldry.library.delete_warning");
            return;
        }
        HeraldryPatternLibrary.Result result = HeraldryPatternLibrary.delete(entry);
        clearConfirmation();
        if (result == HeraldryPatternLibrary.Result.SUCCESS) {
            refresh(null);
            showMessage("smallshipsheraldry.library.deleted");
        } else {
            showResult(result);
        }
    }

    private HeraldryPatternLibrary.Entry selectedEntry() {
        return selection >= 0 && selection < entries.size() ? entries.get(selection) : null;
    }

    private Component confirmLabel(Action action, String normalKey) {
        return confirmAction == action && System.currentTimeMillis() < confirmUntil
                ? Component.translatable("smallshipsheraldry.library.confirm")
                : Component.translatable(normalKey);
    }

    private void requestConfirmation(Action action, String messageKey) {
        confirmAction = action;
        confirmUntil = System.currentTimeMillis() + 3_000L;
        showMessage(messageKey);
    }

    private void clearConfirmation() {
        confirmAction = null;
        confirmUntil = 0L;
    }

    private void showResult(HeraldryPatternLibrary.Result result) {
        String key = switch (result) {
            case INVALID_NAME -> "smallshipsheraldry.library.invalid_name";
            case ALREADY_EXISTS -> "smallshipsheraldry.library.exists";
            case TOO_LARGE -> "smallshipsheraldry.library.too_large";
            case NOT_FOUND -> "smallshipsheraldry.library.not_found";
            default -> "smallshipsheraldry.library.io_error";
        };
        showMessage(key);
    }

    private void showMessage(String translationKey) {
        message = Component.translatable(translationKey);
        messageUntil = System.currentTimeMillis() + 4_000L;
    }

    private String defaultName() {
        HeraldryPatternData data = controller.currentData();
        ShipPreviewType shipType = data == null ? ShipPreviewType.COG : data.shipType();
        return shipType.serializedName() + "_" + NAME_TIME.format(LocalDateTime.now());
    }

    private void drawControl(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height, Component text) {
        boolean hovered = isInside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, hovered ? HeraldryGuiTheme.GOLD_LIGHT : HeraldryGuiTheme.FRAME_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, hovered ? HeraldryGuiTheme.GOLD : HeraldryGuiTheme.PAPER_ALT);
        Component clipped = Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(1, width - 4)));
        graphics.drawString(font, clipped, x + (width - font.width(clipped)) / 2, y + (height - 8) / 2, HeraldryGuiTheme.INK, false);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, HeraldryGuiTheme.MUTED_INK, false);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    interface Controller {
        HeraldryPatternData currentData();

        int previewStage();

        boolean hasDraftWork(ShipPreviewType shipType);

        void load(HeraldryPatternData data);

        void showStatus(Component message);
    }

    private enum Action {
        OVERWRITE,
        LOAD,
        DELETE
    }
}
