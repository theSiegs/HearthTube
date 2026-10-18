package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.utils.KidsDecision.Signals;

import org.junit.Test;

public class KidsDecisionTest {
    private final Signals mSignals = new Signals();

    @Test
    public void noSignalsIsAnAdult() {
        assertFalse(KidsDecision.isKids(mSignals));
    }

    @Test
    public void aParentTurningItOnMakesAKidsProfile() {
        mSignals.explicit = true;
        assertTrue(KidsDecision.isKids(mSignals));
    }

    @Test
    public void hearthSayingKidsMakesAKidsProfile() {
        mSignals.hearthKids = true;
        assertTrue(KidsDecision.isKids(mSignals));
    }

    @Test
    public void supervisionMakesAKidsProfileWhateverElseSays() {
        mSignals.supervised = true;
        mSignals.explicit = false;
        mSignals.hearthKids = false;
        mSignals.hearthLive = true;
        assertTrue(KidsDecision.isKids(mSignals));
    }

    @Test
    public void aParentTurningItOffBeatsTheSuspendedAppsGuess() {
        mSignals.explicit = false;
        mSignals.suspendedGuessAllowed = true;
        mSignals.manySuspended = true;
        assertFalse(KidsDecision.isKids(mSignals));
    }

    @Test
    public void aLiveHearthSayingNotKidsBeatsTheSuspendedAppsGuess() {
        mSignals.hearthKids = false;
        mSignals.hearthLive = true;
        mSignals.suspendedGuessAllowed = true;
        mSignals.manySuspended = true;
        assertFalse(KidsDecision.isKids(mSignals));
    }

    @Test
    public void aSilentHearthSayingNotKidsDoesNotBeatTheGuess() {
        mSignals.hearthKids = false;
        mSignals.hearthLive = false;
        mSignals.suspendedGuessAllowed = true;
        mSignals.manySuspended = true;
        assertTrue(KidsDecision.isKids(mSignals));
    }

    @Test
    public void manySuspendedAppsMakeAKidsProfileOnATv() {
        mSignals.suspendedGuessAllowed = true;
        mSignals.manySuspended = true;
        assertTrue(KidsDecision.isKids(mSignals));
    }

    @Test
    public void theSuspendedAppsGuessIsOffWhereNotAllowed() {
        mSignals.suspendedGuessAllowed = false;
        mSignals.manySuspended = true;
        assertFalse(KidsDecision.isKids(mSignals));
    }
}
