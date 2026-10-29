package me.mss1r.smallshipsheraldry.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorPickCacheLifecycleTest {
    @Test
    void clearingReleasesTheLastPreviewReference() throws Exception {
        var field = PaintableSailMesh.class.getDeclaredField("lastEditorPickCache");
        field.setAccessible(true);
        var constructor = field.getType().getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        try {
            field.set(null, constructor.newInstance(null, 4, 123, null));
            assertNotNull(field.get(null));
            PaintableSailMesh.clearEditorPickCache();
            assertNull(field.get(null));
        } finally {
            PaintableSailMesh.clearEditorPickCache();
        }
    }

    @Test
    void clearingAnEmptyCacheIsSafe() {
        assertDoesNotThrow(() -> {
            PaintableSailMesh.clearEditorPickCache();
            PaintableSailMesh.clearEditorPickCache();
        });
    }
}
