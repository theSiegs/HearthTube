package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HearthTube's own parent PIN (only where neither Hearth nor Glint keeps one), kept the way Hearth keeps its:
 * {@code v2$<iterations>$<salt>$<hash>}, PBKDF2-HMAC-SHA256 (RFC 8018) with a random salt per PIN, in hex. Older
 * HearthTube versions kept {@code sha256:<hex>} with one salt for every app, or the digits themselves; both still check,
 * and {@link #isCurrent} tells the caller to save the PIN again in this form. Plain Java, so it's unit-tested.
 */
public final class PinHash {
    static final int ITERATIONS = 10_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final SecureRandom RANDOM = new SecureRandom();

    private PinHash() {
    }

    public static String hash(String pin) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return "v2$" + ITERATIONS + "$" + hex(salt) + "$" + hex(pbkdf2(pin.getBytes(UTF_8), salt, ITERATIONS, HASH_BYTES));
    }

    /** Whether {@code stored} is in the form {@link #hash} makes now */
    public static boolean isCurrent(@Nullable String stored) {
        return stored != null && stored.startsWith("v2$");
    }

    public static boolean verify(@Nullable String pin, @Nullable String stored) {
        if (pin == null || stored == null) {
            return false;
        }

        try {
            if (stored.startsWith("v2$")) {
                String[] parts = stored.split("\\$");
                int cost = Integer.parseInt(parts[1]);

                if (parts.length != 4 || cost < 1 || cost > 200_000) {
                    return false;
                }

                return same(hex(pbkdf2(pin.getBytes(UTF_8), unhex(parts[2]), cost, HASH_BYTES)), parts[3]);
            }

            if (stored.startsWith("sha256:")) {
                byte[] old = MessageDigest.getInstance("SHA-256").digest(("hearthtube-parent-pin:" + pin).getBytes(UTF_8));
                return same("sha256:" + hex(old), stored);
            }

            return same(pin, stored); // the digits themselves, from the oldest versions
        } catch (Exception e) {
            return false;
        }
    }

    /** PBKDF2-HMAC-SHA256, as Hearth computes it */
    static byte[] pbkdf2(byte[] password, byte[] salt, int cost, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(password.length == 0 ? new byte[1] : password, "HmacSHA256"));
            byte[] out = new byte[length];
            int done = 0;

            for (int block = 1; done < length; block++) {
                mac.update(salt);
                mac.update(new byte[]{(byte) (block >>> 24), (byte) (block >>> 16), (byte) (block >>> 8), (byte) block});
                byte[] u = mac.doFinal();
                byte[] t = u.clone();

                for (int i = 1; i < cost; i++) {
                    u = mac.doFinal(u);
                    for (int j = 0; j < t.length; j++) {
                        t[j] ^= u[j];
                    }
                }

                int n = Math.min(t.length, length - done);
                System.arraycopy(t, 0, out, done, n);
                done += n;
            }

            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();

        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }

        return sb.toString();
    }

    private static byte[] unhex(String hex) {
        byte[] out = new byte[hex.length() / 2];

        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }

        return out;
    }

    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(UTF_8), b.getBytes(UTF_8));
    }
}
