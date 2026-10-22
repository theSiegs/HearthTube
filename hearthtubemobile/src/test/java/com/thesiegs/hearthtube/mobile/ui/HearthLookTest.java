package com.thesiegs.hearthtube.mobile.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import org.junit.Test;

public class HearthLookTest {
    @Test
    public void lightAccentsGetBlackText() {
        assertEquals(Color.BLACK, HearthLook.onColor(0xFFFFFFFF)); // Hearth's white accent
        assertEquals(Color.BLACK, HearthLook.onColor(0xFFFFD600)); // yellow
        assertEquals(Color.BLACK, HearthLook.onColor(0xFFAEEA00)); // lime
    }

    @Test
    public void darkAccentsGetWhiteText() {
        assertEquals(Color.WHITE, HearthLook.onColor(0xFF7C4DFF)); // Hearth's default purple
        assertEquals(Color.WHITE, HearthLook.onColor(0xFFD50000)); // red
        assertEquals(Color.WHITE, HearthLook.onColor(0xFF2979FF)); // blue
    }

    @Test
    public void brightPicturesGetMoreShadeThanDarkOnes() {
        assertEquals(0.75f, HearthLook.shadeScale(0), 0.001f);
        assertEquals(2.0f, HearthLook.shadeScale(1), 0.001f);
        assertEquals(2.0f, HearthLook.shadeScale(7), 0.001f);
        assertTrue(HearthLook.shadeScale(0.8) > HearthLook.shadeScale(0.2));
    }
}
