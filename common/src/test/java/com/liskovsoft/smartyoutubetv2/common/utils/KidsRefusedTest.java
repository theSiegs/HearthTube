package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KidsRefusedTest {
    @Test
    public void aSignedOutAnswerGetsOneRetryFromTheStart() {
        assertEquals(KidsRefused.Recovery.RESET_AND_RETRY, KidsRefused.recoveryFor(false, false));
    }

    @Test
    public void aSecondSignedOutAnswerForTheSameVideoIsFinal() {
        assertEquals(KidsRefused.Recovery.REFUSE, KidsRefused.recoveryFor(false, true));
    }

    @Test
    public void youtubeSayingNoIsFinalAtOnce() {
        assertEquals(KidsRefused.Recovery.REFUSE, KidsRefused.recoveryFor(true, false));
        assertEquals(KidsRefused.Recovery.REFUSE, KidsRefused.recoveryFor(true, true));
    }
}
