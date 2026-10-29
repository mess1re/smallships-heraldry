package me.mss1r.smallshipsheraldry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

final class EditorPickTransform {
    private EditorPickTransform() {
    }

    static PoseStack logicalPose(PoseStack renderedPose, float guiScale) {
        PoseStack logical = new PoseStack();
        logical.last().pose().set(renderedPose.last().pose());
        logical.last().pose().scaleLocal(1 / guiScale, 1 / guiScale, 1);
        return logical;
    }
}
