package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HearthLinkDataTest {
    @Test
    public void hearthsAnswerSetsTheSwitchWhenItChanges() {
        assertEquals(Boolean.TRUE, HearthLinkData.hearthAnswerToApply(1, null));
        assertEquals(Boolean.FALSE, HearthLinkData.hearthAnswerToApply(0, null));
        assertEquals(Boolean.FALSE, HearthLinkData.hearthAnswerToApply(0, 1));
        assertEquals(Boolean.TRUE, HearthLinkData.hearthAnswerToApply(1, 0));
    }

    @Test
    public void theSameAnswerLeavesAChoiceMadeInHearthTube() {
        assertNull(HearthLinkData.hearthAnswerToApply(1, 1));
        assertNull(HearthLinkData.hearthAnswerToApply(0, 0));
    }

    @Test
    public void neverAskedLeavesTheSwitch() {
        assertNull(HearthLinkData.hearthAnswerToApply(null, null));
        assertNull(HearthLinkData.hearthAnswerToApply(null, 1));
    }
}
