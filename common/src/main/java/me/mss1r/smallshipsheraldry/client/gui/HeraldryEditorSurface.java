package me.mss1r.smallshipsheraldry.client.gui;

import com.talhanation.smallships.world.entity.ship.Ship;
import me.mss1r.smallshipsheraldry.client.render.HeraldryDraftTexture;
import me.mss1r.smallshipsheraldry.client.render.PaintableSailMesh;

import java.util.Optional;
import java.util.Set;

/** Geometry and texture access shared by the editing tools, independent of widget layout. */
interface HeraldryEditorSurface {
    Ship ship();
    int stage();
    Bounds bounds();
    Set<String> selectedElements();
    Set<String> hiddenElements();
    Optional<PaintableSailMesh.Hit> pick(double x, double y);
    HeraldryDraftTexture texture();

    default boolean accepts(PaintableSailMesh.Hit hit) {
        return selectedElements().isEmpty() || selectedElements().contains(hit.elementId());
    }

    default void replacePixels(int[] pixels) {
        if (texture() != null) texture().replacePixels(pixels);
    }

    default void syncFace(int[] pixels, PaintableSailMesh.Hit hit) {
        if (texture() != null) {
            texture().updateRegion(pixels, hit.slotX() - 1, hit.slotY() - 1, hit.slotWidth() + 2, hit.slotHeight() + 2);
        }
    }

    record Bounds(int x, int y, int width, int height) {
    }
}
