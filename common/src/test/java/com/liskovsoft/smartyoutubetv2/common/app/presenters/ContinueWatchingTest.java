package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContinueWatchingTest {
    @Test
    public void finishedVideosAreLeftOut() {
        assertTrue(HearthSections.isFinished(100));
        assertTrue(HearthSections.isFinished(96));
    }

    @Test
    public void startedAndUnknownVideosStay() {
        assertFalse(HearthSections.isFinished(95));
        assertFalse(HearthSections.isFinished(40));
        assertFalse(HearthSections.isFinished(0));
        assertFalse(HearthSections.isFinished(-1));
    }
}
