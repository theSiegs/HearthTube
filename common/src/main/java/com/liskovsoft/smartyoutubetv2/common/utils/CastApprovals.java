package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HearthTube: the phones a parent let cast into a kids profile on this TV (any phone paired by "Link with TV code",
 * the official YouTube app included). A phone that isn't on the list can't start or steer anything in a kids profile
 * until a parent approves it with the PIN; the list is in Settings › Cast from phone, where a parent can take one off.
 * Plain Java, so it's unit-tested; {@link CastApprovalsStore} keeps it.
 */
public final class CastApprovals {
    private final Map<String, String> mApproved = new LinkedHashMap<>();

    public synchronized boolean isApproved(@Nullable String deviceId) {
        return deviceId != null && mApproved.containsKey(deviceId);
    }

    /** True when all of these phones are approved (and there is at least one: an unknown sender is never approved) */
    public synchronized boolean allApproved(Collection<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return false;
        }

        for (String id : deviceIds) {
            if (!isApproved(id)) {
                return false;
            }
        }

        return true;
    }

    public synchronized void approve(String deviceId, @Nullable String name) {
        if (deviceId != null && !deviceId.isEmpty()) {
            mApproved.put(deviceId, name != null ? name : "");
        }
    }

    public synchronized void revoke(String deviceId) {
        mApproved.remove(deviceId);
    }

    /** Approved phones, id to name, in the order they were approved */
    public synchronized Map<String, String> list() {
        return new LinkedHashMap<>(mApproved);
    }

    /** id|name;... to keep */
    public synchronized String save() {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> e : mApproved.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(encode(e.getKey())).append('|').append(encode(e.getValue()));
        }

        return sb.toString();
    }

    public synchronized void restore(@Nullable String saved) {
        mApproved.clear();

        if (saved == null || saved.isEmpty()) {
            return;
        }

        for (String item : saved.split(";")) {
            String[] parts = item.split("\\|", -1);

            if (parts.length == 2) {
                String id = decode(parts[0]);
                if (id != null && !id.isEmpty()) {
                    mApproved.put(id, decode(parts[1]));
                }
            }
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    @Nullable
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return null;
        }
    }
}
