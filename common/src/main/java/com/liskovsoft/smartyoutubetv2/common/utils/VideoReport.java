package com.liskovsoft.smartyoutubetv2.common.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube: one report about a video or a channel, for the community lists (not a filter: reporting changes nothing
 * on screen). Holds public facts only (the ids, the title and channel name as YouTube shows them, a type and when);
 * nobody's name or profile. Plain Java, so how reports are written, read and listed is tested apart from Android.
 */
public final class VideoReport {
    public enum Target { VIDEO, CHANNEL }

    /** What is being reported; AI generated may say more (see {@link #detail}), "age" may carry an age */
    public enum Kind { AI_GENERATED, AGE, SPONSORED, OTHER }

    /** The optional "more specific" tags, as written in a report */
    public static final String[] AI_DETAILS = {"video", "music", "voiceover", "images", "script"};
    public static final String[] SPONSORED_DETAILS = {"paid-promotion", "self-promotion"};
    /** Ages offered for "not appropriate for some ages" (reported as "not for under N") */
    public static final int[] AGES = {7, 10, 13, 16, 18};

    private static final String SEP = "\t";

    public final Target target;
    /** A video's id, or a channel's (UC...) */
    public final String id;
    public final Kind kind;
    /** One of the tags above, or null (the reporter didn't say more) */
    public final String detail;
    /** For {@link Kind#AGE}: not for under this age; 0 when not said */
    public final int age;
    /** As shown on screen, so a person reading the list knows what it is; may be empty */
    public final String title;
    public final String channel;
    public final long timeMs;

    public VideoReport(Target target, String id, Kind kind, String detail, int age, String title, String channel, long timeMs) {
        this.target = target;
        this.id = id;
        this.kind = kind;
        this.detail = detail;
        this.age = age;
        this.title = clean(title);
        this.channel = clean(channel);
        this.timeMs = timeMs;
    }

    /** The same thing reported again, whatever the detail: one report per target and kind, the newest detail wins */
    public boolean isSameAs(VideoReport other) {
        return target == other.target && kind == other.kind && id.equals(other.id);
    }

    /** One line for saving: fields separated by a tab (the text fields have no tabs or line breaks, see clean) */
    public String encode() {
        return target.name() + SEP + id + SEP + kind.name() + SEP + (detail != null ? detail : "") + SEP + age + SEP + timeMs
                + SEP + title + SEP + channel;
    }

    /** Null for a line that isn't a report (a damaged save is skipped, not fatal) */
    public static VideoReport decode(String line) {
        if (line == null) {
            return null;
        }

        String[] fields = line.split("\t", -1);

        if (fields.length < 8 || fields[1].isEmpty()) {
            return null;
        }

        try {
            return new VideoReport(Target.valueOf(fields[0]), fields[1], Kind.valueOf(fields[2]),
                    fields[3].isEmpty() ? null : fields[3], Integer.parseInt(fields[4]), fields[6], fields[7], Long.parseLong(fields[5]));
        } catch (IllegalArgumentException e) { // an unknown name or a number that isn't one
            return null;
        }
    }

    /**
     * What a person reads in the list: "AI generated (music): Title - Channel", or "...: Channel Name" for a channel. The
     * words come from the screen's strings; ageText holds a %d for the age.
     */
    public String describe(String aiGenerated, String ageText, String sponsored, String other, String channelWord) {
        String what;

        switch (kind) {
            case AI_GENERATED:
                what = aiGenerated;
                break;
            case AGE:
                what = age > 0 ? ageText.replace("%d", String.valueOf(age)) : ageText.replace("%d", "?");
                break;
            case SPONSORED:
                what = sponsored;
                break;
            default:
                what = other;
                break;
        }

        String more = detail != null ? " (" + detail + ")" : "";
        String name = target == Target.CHANNEL ? channelWord + " " + channel : title + (channel.isEmpty() ? "" : " - " + channel);
        return what + more + ": " + name;
    }

    /** All the reports a text holds, one per line, skipping lines that aren't one */
    public static List<VideoReport> decodeAll(String text) {
        List<VideoReport> reports = new ArrayList<>();

        if (text != null) {
            for (String line : text.split("\n")) {
                VideoReport report = decode(line);

                if (report != null) {
                    reports.add(report);
                }
            }
        }

        return reports;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }
}
