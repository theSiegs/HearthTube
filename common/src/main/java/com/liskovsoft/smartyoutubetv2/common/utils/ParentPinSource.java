package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

/**
 * Which parent PIN a kids profile asks for, and who may choose one (ParentGate). Pure logic, so every case is tested.
 * The person at the screen of a kids profile never gets to choose the PIN while a parent could still have set it
 * elsewhere (Hearth is there, Glint is there, or an account is signed in already).
 */
final class ParentPinSource {
    enum Choice {
        /** Ask for Hearth's PIN */
        HEARTH,
        /** Ask for HearthTube's own PIN */
        LOCAL,
        /** Nobody has a PIN yet and it is safe to let this person choose one */
        CREATE,
        /** Hearth is installed but not answering: its PIN can't be checked, and none may be chosen meanwhile */
        HEARTH_SILENT,
        /** Hearth is installed and has no PIN (or isn't live): stay locked until a parent sets it in Hearth */
        SET_IN_HEARTH,
        /** Ask for Glint's PIN (a phone with Glint, the phone launcher: one PIN per phone) */
        GLINT,
        /** Glint is installed but not answering: its PIN can't be checked, and none may be chosen meanwhile */
        GLINT_SILENT,
        /** Glint is installed and has no PIN: stay locked until a parent sets it in Glint */
        SET_IN_GLINT,
        /** No PIN to ask for and none may be chosen here: stay locked */
        LOCKED
    }

    private ParentPinSource() {
    }

    /**
     * @param hearthLive Hearth answered with a row that can be relied on
     * @param hearthHasPin that row says Hearth has a parent PIN
     * @param usingHearthPin HearthTube has been using Hearth's PIN
     * @param hearthGenuine null: Hearth isn't installed; false: an app that only took its name
     * @param hasLocalPin HearthTube's own parent PIN is set
     * @param kids this is a kids profile
     * @param signedIn an account is signed in already (on the phone build the first parent signs in before choosing a PIN: pass false)
     */
    static Choice decide(boolean hearthLive, boolean hearthHasPin, boolean usingHearthPin, @Nullable Boolean hearthGenuine,
                         boolean hasLocalPin, boolean kids, boolean signedIn) {
        return decide(hearthLive, hearthHasPin, usingHearthPin, hearthGenuine, false, null, hasLocalPin, kids, signedIn);
    }

    /**
     * @param glintInstalled a Glint signed with Glint's key is installed (phone build)
     * @param glintHasPin Glint's answer: whether it has a parent PIN; null when it didn't answer
     */
    static Choice decide(boolean hearthLive, boolean hearthHasPin, boolean usingHearthPin, @Nullable Boolean hearthGenuine,
                         boolean glintInstalled, @Nullable Boolean glintHasPin, boolean hasLocalPin, boolean kids,
                         boolean signedIn) {
        if (hearthLive && hearthHasPin) {
            return Choice.HEARTH;
        }

        if (!kids) {
            return hasLocalPin ? Choice.LOCAL : Choice.CREATE; // an adult's own profile
        }

        if (glintInstalled) {
            if (Boolean.TRUE.equals(glintHasPin)) {
                return Choice.GLINT;
            }

            // Glint is silent or has no PIN: HearthTube's own old one stays, never a new one
            if (hasLocalPin) {
                return Choice.LOCAL;
            }

            return glintHasPin == null ? Choice.GLINT_SILENT : Choice.SET_IN_GLINT;
        }

        if (Boolean.TRUE.equals(hearthGenuine)) {
            if (!hearthLive && usingHearthPin) {
                return Choice.HEARTH_SILENT; // busy or restarting: the PIN in force can't be checked
            }

            // Hearth has no PIN (removed there) or isn't answering: HearthTube's own old one stays, never a new one
            return hasLocalPin ? Choice.LOCAL : Choice.SET_IN_HEARTH;
        }

        if (hasLocalPin) {
            return Choice.LOCAL;
        }

        // Hearth is absent for good, and nobody has signed in yet: the parent's first setup
        return hearthGenuine == null && !signedIn ? Choice.CREATE : Choice.LOCKED;
    }
}
