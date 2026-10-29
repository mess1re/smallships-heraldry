package me.mss1r.smallshipsheraldry.client.gui;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HeraldryChoicePopupTest {
    private final HeraldryChoicePopup popup = new HeraldryChoicePopup();
    private final AtomicInteger selection = new AtomicInteger(-1);
    private final List<Component> options = List.of(Component.literal("Area"), Component.literal("Face"), Component.literal("Colour"));

    private void open(int selected) {
        popup.open(10, 20, 88, options, selected, selection::set);
    }

    @Test
    void keyboardContinuesFromHoveredRow() {
        open(0);
        popup.mouseMoved(15, 40);
        popup.keyPressed(GLFW.GLFW_KEY_DOWN);
        popup.keyPressed(GLFW.GLFW_KEY_ENTER);
        assertEquals(2, selection.get());
        assertFalse(popup.isOpen());
    }

    @Test
    void hoverDoesNotChangeTheValueUntilConfirmed() {
        open(0);
        popup.mouseMoved(15, 60);
        assertEquals(-1, selection.get());
        popup.keyPressed(GLFW.GLFW_KEY_SPACE);
        assertEquals(2, selection.get());
    }

    @Test
    void clickChoosesTheClickedRowRatherThanTheFocusedRow() {
        open(2);
        popup.mouseClicked(15, 21, 0);
        assertEquals(0, selection.get());
    }

    @Test
    void outsideAndRightClicksOnlyDismissTheMenu() {
        open(0);
        popup.mouseClicked(9, 20, 0);
        assertFalse(popup.isOpen());
        open(0);
        popup.mouseClicked(15, 21, 1);
        assertFalse(popup.isOpen());
        assertEquals(-1, selection.get());
    }

    @Test
    void escapeDoesNotCommitHighlightedValue() {
        open(0);
        popup.keyPressed(GLFW.GLFW_KEY_END);
        popup.keyPressed(GLFW.GLFW_KEY_ESCAPE);
        popup.keyPressed(GLFW.GLFW_KEY_ENTER);
        assertFalse(popup.isOpen());
        assertEquals(-1, selection.get());
    }

    @Test
    void scrollStopsAtTheFirstAndLastRows() {
        open(1);
        for (int i = 0; i < 10; i++) popup.mouseScrolled(1);
        popup.keyPressed(GLFW.GLFW_KEY_ENTER);
        assertEquals(0, selection.get());
        open(1);
        for (int i = 0; i < 10; i++) popup.mouseScrolled(-1);
        popup.keyPressed(GLFW.GLFW_KEY_ENTER);
        assertEquals(2, selection.get());
    }

    @Test
    void arrowsWrapAndInitialSelectionIsClamped() {
        open(100);
        popup.keyPressed(GLFW.GLFW_KEY_DOWN);
        popup.keyPressed(GLFW.GLFW_KEY_ENTER);
        assertEquals(0, selection.get());
        open(-100);
        popup.keyPressed(GLFW.GLFW_KEY_UP);
        popup.keyPressed(GLFW.GLFW_KEY_ENTER);
        assertEquals(2, selection.get());
    }

    @Test
    void emptyOptionsCloseAnExistingMenu() {
        open(0);
        popup.open(10, 20, 88, List.of(), 0, selection::set);
        popup.mouseClicked(15, 21, 0);
        popup.mouseScrolled(-1);
        assertFalse(popup.isOpen());
        assertEquals(-1, selection.get());
    }
}
