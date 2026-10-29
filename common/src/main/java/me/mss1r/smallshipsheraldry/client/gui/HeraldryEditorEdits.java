package me.mss1r.smallshipsheraldry.client.gui;

import net.minecraft.network.chat.Component;

/** Transaction boundary: tools change pixels; the editor validates and commits the result. */
interface HeraldryEditorEdits {
    boolean pushUndo();
    void commit();
    void status(Component message);
}
