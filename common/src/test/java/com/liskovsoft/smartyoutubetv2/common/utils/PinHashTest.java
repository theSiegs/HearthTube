package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinHashTest {
    @Test
    public void aSavedPinChecksAndAWrongOneDoesNot() {
        String stored = PinHash.hash("4821");
        assertTrue(stored.startsWith("v2$" + PinHash.ITERATIONS + "$"));
        assertTrue(PinHash.isCurrent(stored));
        assertTrue(PinHash.verify("4821", stored));
        assertFalse(PinHash.verify("4822", stored));
    }

    @Test
    public void eachSaveHasItsOwnSalt() {
        assertNotEquals(PinHash.hash("4821"), PinHash.hash("4821"));
    }

    @Test
    public void olderFormsStillCheckButAreNotCurrent() {
        String oldHash = "sha256:" + sha256("hearthtube-parent-pin:4821");
        assertTrue(PinHash.verify("4821", oldHash));
        assertFalse(PinHash.verify("4822", oldHash));
        assertFalse(PinHash.isCurrent(oldHash));

        assertTrue(PinHash.verify("4821", "4821"));
        assertFalse(PinHash.isCurrent("4821"));
    }

    @Test
    public void matchesRfc7914Pbkdf2Sha256Vector() {
        // RFC 7914 section 11: PBKDF2-HMAC-SHA-256, P="passwd", S="salt", c=1, dkLen=64
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc"
                        + "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
                PinHash.hex(PinHash.pbkdf2("passwd".getBytes(), "salt".getBytes(), 1, 64)));
    }

    private static String sha256(String text) {
        try {
            return PinHash.hex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
