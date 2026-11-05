package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CastApprovalsTest {
    private final CastApprovals mApprovals = new CastApprovals();

    @Test
    public void anUnknownSenderIsNeverApproved() {
        assertFalse(mApprovals.allApproved(Collections.emptyList()));
        assertFalse(mApprovals.isApproved(null));
    }

    @Test
    public void everyConnectedPhoneMustBeApproved() {
        mApprovals.approve("phone-a", "Alex's phone");
        assertTrue(mApprovals.allApproved(Collections.singletonList("phone-a")));
        assertFalse(mApprovals.allApproved(Arrays.asList("phone-a", "phone-b")));
    }

    @Test
    public void aRevokedPhoneNeedsThePinAgain() {
        mApprovals.approve("phone-a", "Alex's phone");
        mApprovals.revoke("phone-a");
        assertFalse(mApprovals.isApproved("phone-a"));
    }

    @Test
    public void keepsNamesWithOddCharactersAcrossRestarts() {
        mApprovals.approve("id|1;x", "Sam's phone; the old one");
        mApprovals.approve("id2", null);

        CastApprovals restored = new CastApprovals();
        restored.restore(mApprovals.save());

        assertEquals(mApprovals.list(), restored.list());
        assertTrue(restored.isApproved("id|1;x"));
    }
}
