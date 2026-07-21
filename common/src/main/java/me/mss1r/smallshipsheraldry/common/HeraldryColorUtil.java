package me.mss1r.smallshipsheraldry.common;

import net.minecraft.world.item.DyeColor;

public final class HeraldryColorUtil {
    private HeraldryColorUtil() {
    }

    public static String nearestDyeName(int argb) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        DyeColor bestColor = DyeColor.WHITE;
        double bestDistance = Double.MAX_VALUE;

        for (DyeColor dyeColor : DyeColor.values()) {
            float[] diffuse = dyeColor.getTextureDiffuseColors();
            int dyeRed = Math.round(diffuse[0] * 255.0F);
            int dyeGreen = Math.round(diffuse[1] * 255.0F);
            int dyeBlue = Math.round(diffuse[2] * 255.0F);
            double distance = Math.pow(red - dyeRed, 2) + Math.pow(green - dyeGreen, 2) + Math.pow(blue - dyeBlue, 2);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestColor = dyeColor;
            }
        }

        return bestColor.getName();
    }
}
