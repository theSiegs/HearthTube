package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class SupervisionRestrictionsTest {
    private static SupervisionRestrictions.Restrictions with(String... keys) {
        Set<String> set = new HashSet<>(Arrays.asList(keys));
        return set::contains;
    }

    @Test
    public void noRestrictionsIsNotSupervised() {
        assertFalse(SupervisionRestrictions.isSupervised(with(), false));
        assertFalse(SupervisionRestrictions.isSupervised(with(), true));
        assertFalse(SupervisionRestrictions.isSupervised(null, true));
    }

    @Test
    public void theTvRestrictionsMeanSupervisedOnBothBuilds() {
        for (String key : new String[] {"no_config_credentials", "no_grant_admin", "no_add_managed_profile"}) {
            assertTrue(key, SupervisionRestrictions.isSupervised(with(key), false));
            assertTrue(key, SupervisionRestrictions.isSupervised(with(key), true));
        }
    }

    @Test
    public void theUnknownSourcesRestrictionCountsOnlyOnThePhoneBuild() {
        assertTrue(SupervisionRestrictions.isSupervised(with("no_install_unknown_sources"), true));
        assertFalse(SupervisionRestrictions.isSupervised(with("no_install_unknown_sources"), false));
    }

    @Test
    public void unrelatedRestrictionsDoNotCount() {
        assertFalse(SupervisionRestrictions.isSupervised(with("no_sms", "no_outgoing_calls"), true));
    }
}
