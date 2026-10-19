package com.liskovsoft.smartyoutubetv2.common.utils;

/**
 * What to do when the Google TV profile in front has no (working) link to a signed-in account, but an account is
 * already selected. The old answer was to deselect it, which signed the person out whenever the link went missing.
 * An account is now deselected only when it can't be this profile's: on the owner's shared copy of the app (the
 * adults' accounts live side by side) or a kids' profile holding an adult's account.
 */
public final class UnlinkedProfileRule {
    public enum Action {
        /** The selected account is this profile's: link it and carry on, nobody is signed out */
        LINK_SELECTED,
        /** Nothing selected: ask in the picker */
        ASK,
        /** The selected account is someone else's: deselect it (it stays signed in), then ask */
        HIDE_SELECTED
    }

    private UnlinkedProfileRule() {
    }

    /**
     * @param ownerUser            the owner's copy of the app, shared by the adults; other Google TV profiles each run
     *                             their own copy, where every account is that profile's person's
     * @param selectedAccount      an account is selected right now
     * @param kidsProfileWithAdult a kids' profile whose selected account is an adult's (and no parent has unlocked it)
     */
    public static Action decide(boolean ownerUser, boolean selectedAccount, boolean kidsProfileWithAdult) {
        if (!selectedAccount) {
            return Action.ASK;
        }

        if (ownerUser || kidsProfileWithAdult) {
            return Action.HIDE_SELECTED;
        }

        return Action.LINK_SELECTED;
    }
}
