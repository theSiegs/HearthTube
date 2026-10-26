package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.liskovsoft.sharedutils.helpers.Helpers;

import org.junit.Test;

public class HiddenVideoDataTest {
    @Test
    public void aTitleIsKept() {
        assertArrayEquals(new String[] {"abc123", "Sailing yacht"}, HiddenVideoData.parse(Helpers.mergeObj("abc123", "Sailing yacht")));
    }

    @Test
    public void noTitleSurvivesSavingAsTheId() {
        assertEquals("abc123", HiddenVideoData.titleOrId("abc123", null));
        assertEquals("abc123", HiddenVideoData.titleOrId("abc123", "  "));
        // what hide() stores is what comes back after a restart
        String saved = Helpers.mergeObj("abc123", HiddenVideoData.titleOrId("abc123", ""));
        assertArrayEquals(new String[] {"abc123", "abc123"}, HiddenVideoData.parse(saved));
    }

    @Test
    public void anEntrySavedWithAnEmptyTitleStillLoads() {
        // data written by the first version, which saved "" for a missing title
        String[] entry = HiddenVideoData.parse(Helpers.mergeObj("abc123", ""));

        assertArrayEquals(new String[] {"abc123", "abc123"}, entry);
    }

    @Test
    public void somethingThatIsNotAnEntryIsIgnored() {
        assertNull(HiddenVideoData.parse(null));
        assertNull(HiddenVideoData.parse(""));
    }
}
