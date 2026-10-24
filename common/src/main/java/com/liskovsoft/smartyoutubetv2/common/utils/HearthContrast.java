package com.liskovsoft.smartyoutubetv2.common.utils;

/**
 * HearthTube: the text color to put on a filled pill, chosen from the fill (the same rule as the Hearth launcher, which
 * uses Flutter's ThemeData.estimateBrightnessForColor): dark text on a light fill, white on a dark one. A pill that takes
 * the accent color (any of them, gold included) is then always readable.
 */
public final class HearthContrast {
    /** Hearth's dark text color */
    public static final int DARK = 0xFF141418;
    public static final int LIGHT = 0xFFFFFFFF;

    private HearthContrast() {
    }

    /** Text color for this (opaque) fill, as ARGB */
    public static int onColor(int fill) {
        return isLight(fill) ? DARK : LIGHT;
    }

    /** Flutter's estimateBrightnessForColor: light when (relative luminance + 0.05)^2 > 0.15 */
    static boolean isLight(int color) {
        double luminance = 0.2126 * channel((color >> 16) & 0xFF) + 0.7152 * channel((color >> 8) & 0xFF) + 0.0722 * channel(color & 0xFF);
        double v = luminance + 0.05;

        return v * v > 0.15;
    }

    private static double channel(int value) {
        double c = value / 255.0;

        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
