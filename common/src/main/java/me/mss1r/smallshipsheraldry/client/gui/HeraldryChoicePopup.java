package me.mss1r.smallshipsheraldry.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntConsumer;

import static me.mss1r.smallshipsheraldry.client.gui.HeraldryEditorLayout.isInside;

final class HeraldryChoicePopup {
    private static final int ROW_HEIGHT = 18;
    private List<Component> options = List.of();
    private IntConsumer onSelect;
    private int x;
    private int y;
    private int width;
    private int selected;
    private int focused;

    boolean isOpen() {
        return !options.isEmpty();
    }

    void open(Font font, int x, int y, int anchorWidth, int rightEdge, List<Component> options, int selected, IntConsumer onSelect) {
        int menuWidth = Math.max(anchorWidth, options.stream().mapToInt(font::width).max().orElse(0) + 22);
        open(Math.min(x, rightEdge - menuWidth), y + ROW_HEIGHT + 2, menuWidth, options, selected, onSelect);
    }

    void open(int x, int y, int width, List<Component> options, int selected, IntConsumer onSelect) {
        if (options.isEmpty()) {
            close();
            return;
        }
        this.options = List.copyOf(options);
        this.width = width;
        this.x = x;
        this.y = y;
        this.selected = Math.clamp(selected, 0, options.size() - 1);
        this.focused = this.selected;
        this.onSelect = onSelect;
    }

    void close() {
        options = List.of();
        onSelect = null;
    }

    void render(GuiGraphics graphics, Font font) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        HeraldryGuiTheme.drawColorFrame(graphics, x - 1, y - 1, width + 2, options.size() * ROW_HEIGHT + 2, false);
        for (int index = 0; index < options.size(); index++) {
            int rowY = y + index * ROW_HEIGHT;
            boolean highlighted = index == focused;
            HeraldryGuiTheme.drawChoiceRow(graphics, x, rowY, width, ROW_HEIGHT, highlighted);
            int color = highlighted ? HeraldryGuiTheme.BUTTON_TEXT : HeraldryGuiTheme.INK;
            if (index == selected) {
                for (int pixel = 0; pixel < 3; pixel++) {
                    graphics.fill(x + 4 + pixel, rowY + 8 + pixel, x + 5 + pixel, rowY + 10 + pixel, color);
                }
                for (int pixel = 0; pixel < 5; pixel++) {
                    graphics.fill(x + 6 + pixel, rowY + 10 - pixel, x + 7 + pixel, rowY + 12 - pixel, color);
                }
            }
            graphics.drawString(font, options.get(index), x + 16, rowY + 5, color, false);
        }
        graphics.flush();
        graphics.pose().popPose();
    }

    void mouseMoved(double mouseX, double mouseY) {
        if (isOpen() && isInside(mouseX, mouseY, x, y, width, options.size() * ROW_HEIGHT)) {
            focused = (int) ((mouseY - y) / ROW_HEIGHT);
        }
    }

    void mouseScrolled(double scrollY) {
        if (isOpen() && scrollY != 0) {
            focused = Math.clamp(focused - (int) Math.signum(scrollY), 0, options.size() - 1);
        }
    }

    void mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen()) return;
        if (button == 0 && isInside(mouseX, mouseY, x, y, width, options.size() * ROW_HEIGHT)) {
            choose((int) ((mouseY - y) / ROW_HEIGHT));
        } else {
            close();
        }
    }

    void keyPressed(int keyCode) {
        if (!isOpen()) return;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) close();
        else if (keyCode == GLFW.GLFW_KEY_UP) focused = Math.floorMod(focused - 1, options.size());
        else if (keyCode == GLFW.GLFW_KEY_DOWN) focused = (focused + 1) % options.size();
        else if (keyCode == GLFW.GLFW_KEY_HOME) focused = 0;
        else if (keyCode == GLFW.GLFW_KEY_END) focused = options.size() - 1;
        else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) choose(focused);
    }

    private void choose(int index) {
        IntConsumer selection = onSelect;
        close();
        selection.accept(index);
    }
}
