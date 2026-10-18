package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KidsRefusedRuleTest {
    // isNotAllowed(mobileBuild, signedIn, answerSignedIn, unplayable)

    @Test
    public void aSignedOutKidOnThePhoneBuildPlaysNothingEvenFromAnAnswerSaidToBeSignedIn() {
        assertTrue(KidsRefused.isNotAllowed(true, false, true, false));
        assertTrue(KidsRefused.isNotAllowed(true, false, false, false));
        assertTrue(KidsRefused.isNotAllowed(true, false, true, true));
    }

    @Test
    public void aSignedOutKidOnTheTvPlaysAsBefore() {
        assertFalse(KidsRefused.isNotAllowed(false, false, true, false));
        assertFalse(KidsRefused.isNotAllowed(false, false, false, false));
    }

    @Test
    public void aSignedInKidPlaysWhatTheirAccountMay() {
        assertFalse(KidsRefused.isNotAllowed(true, true, true, false));
        assertFalse(KidsRefused.isNotAllowed(false, true, true, false));
    }

    @Test
    public void aSignedInKidIsRefusedWhatCameSignedOutOrYoutubeSaidNoTo() {
        assertTrue(KidsRefused.isNotAllowed(true, true, false, false));
        assertTrue(KidsRefused.isNotAllowed(false, true, false, false));
        assertTrue(KidsRefused.isNotAllowed(true, true, true, true));
        assertTrue(KidsRefused.isNotAllowed(false, true, true, true));
    }
}
