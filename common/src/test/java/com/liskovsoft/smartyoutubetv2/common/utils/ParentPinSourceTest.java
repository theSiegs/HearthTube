package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;

import com.liskovsoft.smartyoutubetv2.common.utils.ParentPinSource.Choice;

import org.junit.Test;

public class ParentPinSourceTest {
    private static final Boolean GENUINE = Boolean.TRUE;
    private static final Boolean FAKE = Boolean.FALSE;
    private static final Boolean NOT_INSTALLED = null;

    // decide(hearthLive, hearthHasPin, usingHearthPin, hearthGenuine, hasLocalPin, kids, signedIn)

    @Test
    public void hearthsPinWinsEverywhere() {
        assertEquals(Choice.HEARTH, ParentPinSource.decide(true, true, false, GENUINE, false, true, true));
        assertEquals(Choice.HEARTH, ParentPinSource.decide(true, true, true, GENUINE, true, true, false));
        assertEquals(Choice.HEARTH, ParentPinSource.decide(true, true, false, GENUINE, false, false, false));
    }

    @Test
    public void aKidNeverChoosesThePinWhileHearthIsThereWithoutOne() {
        assertEquals(Choice.SET_IN_HEARTH, ParentPinSource.decide(true, false, false, GENUINE, false, true, false));
        assertEquals(Choice.SET_IN_HEARTH, ParentPinSource.decide(true, false, true, GENUINE, false, true, false));
        assertEquals(Choice.SET_IN_HEARTH, ParentPinSource.decide(true, false, true, GENUINE, false, true, true));
    }

    @Test
    public void hearthRemovingItsPinKeepsHearthTubesOldOne() {
        assertEquals(Choice.LOCAL, ParentPinSource.decide(true, false, true, GENUINE, true, true, true));
    }

    @Test
    public void silentHearthStaysLockedWhenItsPinWasInUse() {
        assertEquals(Choice.HEARTH_SILENT, ParentPinSource.decide(false, false, true, GENUINE, false, true, false));
        assertEquals(Choice.HEARTH_SILENT, ParentPinSource.decide(false, false, true, GENUINE, true, true, true));
    }

    @Test
    public void silentHearthNeverUsedKeepsTheLocalPinOrStaysLocked() {
        assertEquals(Choice.LOCAL, ParentPinSource.decide(false, false, false, GENUINE, true, true, true));
        assertEquals(Choice.SET_IN_HEARTH, ParentPinSource.decide(false, false, false, GENUINE, false, true, false));
    }

    @Test
    public void hearthUninstalledKeepsTheLocalPin() {
        assertEquals(Choice.LOCAL, ParentPinSource.decide(false, false, true, NOT_INSTALLED, true, true, true));
        assertEquals(Choice.LOCAL, ParentPinSource.decide(false, false, false, NOT_INSTALLED, true, true, false));
    }

    @Test
    public void noHearthNoPinAKidMayChooseOnlyBeforeAnyoneSignedIn() {
        assertEquals(Choice.CREATE, ParentPinSource.decide(false, false, false, NOT_INSTALLED, false, true, false));
        assertEquals(Choice.CREATE, ParentPinSource.decide(false, false, true, NOT_INSTALLED, false, true, false));
        assertEquals(Choice.LOCKED, ParentPinSource.decide(false, false, false, NOT_INSTALLED, false, true, true));
        assertEquals(Choice.LOCKED, ParentPinSource.decide(false, false, true, NOT_INSTALLED, false, true, true));
    }

    @Test
    public void anAppOnlyNamedHearthIsNoReasonToChoose() {
        assertEquals(Choice.LOCKED, ParentPinSource.decide(false, false, false, FAKE, false, true, false));
        assertEquals(Choice.LOCAL, ParentPinSource.decide(false, false, false, FAKE, true, true, false));
    }

    @Test
    public void anAdultsProfileMayAlwaysChoose() {
        assertEquals(Choice.CREATE, ParentPinSource.decide(false, false, false, NOT_INSTALLED, false, false, true));
        assertEquals(Choice.CREATE, ParentPinSource.decide(true, false, false, GENUINE, false, false, true));
        assertEquals(Choice.LOCAL, ParentPinSource.decide(false, false, false, NOT_INSTALLED, true, false, true));
    }

    @Test
    public void aLocalPinIsAskedForInAKidsProfileWithoutHearth() {
        assertEquals(Choice.LOCAL, ParentPinSource.decide(false, false, false, NOT_INSTALLED, true, true, true));
    }
}
