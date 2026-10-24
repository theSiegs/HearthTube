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
    public void startedVideosCanBeResumed() {
        assertTrue(HearthSections.canResume(1));
        assertTrue(HearthSections.canResume(40));
        assertTrue(HearthSections.canResume(95));
    }

    @Test
    public void unstartedUnknownAndFinishedVideosCannot() {
        assertFalse(HearthSections.canResume(0));
        assertFalse(HearthSections.canResume(-1));
        assertFalse(HearthSections.canResume(96));
        assertFalse(HearthSections.canResume(100));
    }
}
