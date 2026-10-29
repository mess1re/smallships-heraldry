package me.mss1r.smallshipsheraldry.client.gui;

record HeraldryEditorLayout(int width, int height, float scale) {
    static final int MIN_WIDTH = 420;
    static final int MIN_HEIGHT = 324;
    static final int RIGHT_WIDTH = 96;
    static final int PREVIEW_Y = 32;
    private static final int MARGIN = 6;

    static HeraldryEditorLayout create(int viewportWidth, int viewportHeight, double minecraftScale) {
        float scale = fitScale(viewportWidth, viewportHeight, minecraftScale);
        return new HeraldryEditorLayout((int) (viewportWidth / scale) - MARGIN * 2,
                (int) (viewportHeight / scale) - MARGIN * 2, scale);
    }

    int rightX() { return width - RIGHT_WIDTH - 8; }
    int previewX() { return 38; }
    int previewRight() { return rightX() - 8; }
    int previewWidth() { return previewRight() - previewX(); }
    int previewHeight() { return navigationY() - 6 - PREVIEW_Y; }
    int footerY() { return height - 28; }
    int navigationY() { return footerY() - 26; }
    int navigationWidth() { return Math.min(292, previewWidth()); }
    int navigationX() { return previewX() + (previewWidth() - navigationWidth()) / 2; }
    int stagePreviousX() { return navigationX() + navigationWidth() - 112; }
    int stageNextX() { return navigationX() + navigationWidth() - 22; }
    int stageCenterX() { return (stagePreviousX() + 22 + stageNextX()) / 2; }
    int shipPreviousX() { return navigationX(); }
    int shipNextX() { return stagePreviousX() - 26; }
    int shipNameX() { return shipPreviousX() + 24; }
    int shipNameWidth() { return shipNextX() - 4 - shipNameX(); }
    int paperSlotX() { return rightX() - 75; }
    int resultSlotX() { return rightX() - 19; }
    int slotY() { return footerY() + 1; }
    float previewScale() { return Math.min(previewWidth() / 252F, previewHeight() / 190F); }

    static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    static float fitScale(int viewportWidth, int viewportHeight, double minecraftScale) {
        double fit = Math.min(viewportWidth / (double) (MIN_WIDTH + MARGIN * 2),
                viewportHeight / (double) (MIN_HEIGHT + MARGIN * 2));
        // Keep texture pixels on physical pixel boundaries, even at a large GUI scale.
        double physicalScale = Math.floor(Math.min(1, fit) * minecraftScale);
        if (physicalScale < 1) return (float) Math.min(1, fit);
        return (float) (physicalScale / minecraftScale);
    }
}
