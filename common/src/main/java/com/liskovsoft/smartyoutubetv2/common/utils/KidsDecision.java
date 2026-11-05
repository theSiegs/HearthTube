package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

/**
 * HearthTube: is this a kids profile? The signals differ by device (a TV has Hearth and suspended launcher apps; a phone
 * has an explicit parent setting and Family Link's restrictions), so the answer is one plain function of them, tested
 * apart from Android.
 */
public final class KidsDecision {
    private KidsDecision() {
    }

    /** What the device can tell. A null Boolean is "no word". */
    public static final class Signals {
        /** A parent turned kids mode on (true) or off (false) in HearthTube itself; null: never set */
        @Nullable public Boolean explicit;
        /** Hearth's word on this profile, when it answered */
        @Nullable public Boolean hearthKids;
        /** Hearth is there and answering now, so its "not a kids profile" can be trusted */
        public boolean hearthLive;
        /** Glint's kids mode on a phone, when a Glint signed with its key answered */
        @Nullable public Boolean glintKids;
        /** Family Link supervision restrictions on our own user */
        public boolean supervised;
        /** The suspended launcher apps guess is used on this device (TV only) */
        public boolean suspendedGuessAllowed;
        /** Many launcher apps are suspended, as in a kids profile */
        public boolean manySuspended;
    }

    public static boolean isKids(Signals s) {
        // A phone with Glint: the parent's choice there (behind Glint's PIN) decides, or a parent's switch here turning
        // kids mode on. Glint reads Family Link supervision itself, so it isn't read twice (the chosen rule, option B).
        if (s.glintKids != null) {
            return s.glintKids || Boolean.TRUE.equals(s.explicit);
        }

        // A parent switched it on, or Hearth says so, or Family Link supervises this user: always kids. Nothing a kid
        // can turn off here beats supervision.
        if (Boolean.TRUE.equals(s.explicit) || Boolean.TRUE.equals(s.hearthKids) || s.supervised) {
            return true;
        }

        // A parent switched it off in HearthTube, or Hearth is up and says it isn't: apps an adult paused don't count
        if (Boolean.FALSE.equals(s.explicit) || (Boolean.FALSE.equals(s.hearthKids) && s.hearthLive)) {
            return false;
        }

        return s.suspendedGuessAllowed && s.manySuspended;
    }
}
