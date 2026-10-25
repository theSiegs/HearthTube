package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HearthContrastTest {
    @Test
    public void darkTextOnLightFills() {
        assertEquals(HearthContrast.DARK, HearthContrast.onColor(0xFFFFFFFF));
        assertEquals(HearthContrast.DARK, HearthContrast.onColor(0xFFFFAB00)); // the gold accent
        assertEquals(HearthContrast.DARK, HearthContrast.onColor(0xFF00E5FF)); // cyan
    }

    @Test
    public void tabAndChipFillsGetDarkText() {
        assertEquals(HearthContrast.DARK, HearthContrast.onColor(0xFFECEBEF)); // the open tab
        assertEquals(HearthContrast.LIGHT, HearthContrast.onColor(HearthContrast.GLASS));
    }

    @Test
    public void whiteTextOnDarkFills() {
        assertEquals(HearthContrast.LIGHT, HearthContrast.onColor(0xFF141418));
        assertEquals(HearthContrast.LIGHT, HearthContrast.onColor(0xFF2979FF)); // blue
        assertEquals(HearthContrast.LIGHT, HearthContrast.onColor(0xFF7B1FA2)); // purple
    }
}
