package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.smartyoutubetv2.common.utils.UnlinkedProfileRule.Action;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UnlinkedProfileRuleTest {
    @Test
    public void ownAccountOnAKidsCopyIsKeptAndLinked() {
        assertEquals(Action.LINK_SELECTED, UnlinkedProfileRule.decide(false, true, false));
    }

    @Test
    public void nothingSelectedAsksWithoutDeselectingAnything() {
        assertEquals(Action.ASK, UnlinkedProfileRule.decide(false, false, false));
        assertEquals(Action.ASK, UnlinkedProfileRule.decide(true, false, false));
    }

    @Test
    public void ownersSharedCopyHidesTheSelectedAccount() {
        assertEquals(Action.HIDE_SELECTED, UnlinkedProfileRule.decide(true, true, false));
    }

    @Test
    public void adultAccountInAKidsProfileIsHidden() {
        assertEquals(Action.HIDE_SELECTED, UnlinkedProfileRule.decide(false, true, true));
    }
}
