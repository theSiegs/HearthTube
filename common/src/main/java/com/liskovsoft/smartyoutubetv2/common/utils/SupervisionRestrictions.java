package com.liskovsoft.smartyoutubetv2.common.utils;

/**
 * HearthTube: does this Android user look supervised by Family Link? Family Link puts restrictions on a supervised
 * user that an ordinary user doesn't have. The TV's kids profiles show the first three (seen on a Google TV); a
 * supervised phone is expected to show the unknown-sources one while the parent keeps "Apps from unknown sources" off
 * (that one is a guess from Family Link's settings, to confirm on a real phone with
 * {@code adb shell dumpsys user | grep -i restrictions}). Plain Java, so the rule is unit-tested.
 */
public final class SupervisionRestrictions {
    /** Asks whether the user has a restriction (UserManager's key, such as "no_config_credentials") */
    public interface Restrictions {
        boolean has(String key);
    }

    /** What Family Link sets on a supervised Google TV profile */
    private static final String[] TV = {"no_config_credentials", "no_grant_admin", "no_add_managed_profile"};
    /** Only checked on the phone and tablet build: a TV adult is not expected to have it either way, but it's unproven there */
    private static final String[] PHONE = {"no_install_unknown_sources"};

    private SupervisionRestrictions() {
    }

    public static boolean isSupervised(Restrictions restrictions, boolean mobileBuild) {
        if (restrictions == null) {
            return false;
        }

        for (String key : TV) {
            if (restrictions.has(key)) {
                return true;
            }
        }

        if (mobileBuild) {
            for (String key : PHONE) {
                if (restrictions.has(key)) {
                    return true;
                }
            }
        }

        return false;
    }
}
