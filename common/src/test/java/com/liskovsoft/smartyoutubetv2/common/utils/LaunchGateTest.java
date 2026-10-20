package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;

import com.liskovsoft.smartyoutubetv2.common.utils.LaunchGate.Decision;

import org.junit.Test;

public class LaunchGateTest {
    // decide(kids, hasAccount, isDeepLink)

    @Test
    public void aSignedInProfileAlwaysProceeds() {
        assertEquals(Decision.PROCEED, LaunchGate.decide(true, true, true));
        assertEquals(Decision.PROCEED, LaunchGate.decide(true, true, false));
        assertEquals(Decision.PROCEED, LaunchGate.decide(false, true, false));
    }

    @Test
    public void aKidWithNobodySignedInAsksWhateverOpenedTheApp() {
        assertEquals(Decision.ASK, LaunchGate.decide(true, false, true));
        assertEquals(Decision.ASK, LaunchGate.decide(true, false, false));
    }

    @Test
    public void anAdultWithNobodySignedInPlaysALinkFirstAndAsksOnAPlainLaunch() {
        assertEquals(Decision.PROCEED, LaunchGate.decide(false, false, true));
        assertEquals(Decision.ASK, LaunchGate.decide(false, false, false));
    }

    @Test
    public void thePhoneSelectsTheOnlyAccountForAKidAndTheFirstForAnAdult() {
        assertEquals(0, LaunchGate.phoneAutoSelect(true, 1));
        assertEquals(-1, LaunchGate.phoneAutoSelect(true, 2)); // which one is the kid's isn't known
        assertEquals(0, LaunchGate.phoneAutoSelect(false, 3));
        assertEquals(-1, LaunchGate.phoneAutoSelect(true, 0));
        assertEquals(-1, LaunchGate.phoneAutoSelect(false, 0));
    }
}
