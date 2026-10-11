package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HearthViewerTest {
    @Test
    public void anotherProfileIsANewViewer() {
        assertTrue(HearthViewer.isNewViewer("user:0:alex", "user:0:sam"));
        assertTrue(HearthViewer.isNewViewer("user:0:alex", "user:0"));
    }

    @Test
    public void sameProfileIsNot() {
        assertFalse(HearthViewer.isNewViewer("user:0:alex", "user:0:alex"));
    }

    @Test
    public void unknownEitherWayIsNot() {
        assertFalse(HearthViewer.isNewViewer(null, "user:0:alex"));
        assertFalse(HearthViewer.isNewViewer("user:0:alex", null));
        assertFalse(HearthViewer.isNewViewer(null, null));
    }
}
