package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.AudioTrack;

import org.junit.Test;

/** Voice boost is an opt-in audio variant: the original track is what plays unless the person picks the other */
public class VoiceBoostTest {
    private static Format audio(String id) {
        return Format.createAudioSampleFormat(id, null, "mp4a.40.2", 128_000, -1, 2, 44100, null, null, 0, "en");
    }

    private static AudioTrack track(Format format) {
        AudioTrack track = new AudioTrack(TrackSelectorManager.RENDERER_INDEX_AUDIO);
        track.format = format;
        return track;
    }

    @Test
    public void aDashVoiceBoostIdIsRecognised() {
        assertTrue(TrackSelectorUtil.isVb(audio("140-vb")));
        assertFalse(TrackSelectorUtil.isVb(audio("140")));
        assertFalse(TrackSelectorUtil.isVb(audio("140-drc")));
        assertFalse(TrackSelectorUtil.isVb(null));
    }

    @Test
    public void aSabrVoiceBoostIsMarkedWithoutChangingItsId() {
        Format marked = TrackSelectorUtil.markVb(audio("140"));

        assertTrue(TrackSelectorUtil.isVb(marked));
        assertEquals("140", marked.id);
        assertFalse(TrackSelectorUtil.isVb(audio("140")));
    }

    @Test
    public void voiceBoostIsLabelledAndTheOriginalIsNot() {
        assertEquals("Voice boost", TrackSelectorUtil.buildVbMark(audio("140-vb")));
        assertEquals("", TrackSelectorUtil.buildVbMark(audio("140")));
    }

    @Test
    public void theAutomaticChoiceNeverFallsOnVoiceBoost() {
        AudioTrack preferred = track(audio("251"));

        assertEquals(-1, preferred.inBounds(track(audio("140-vb"))));
        assertEquals(-1, preferred.inBounds(track(TrackSelectorUtil.markVb(audio("251")))));
        assertTrue(preferred.inBounds(track(audio("140"))) >= 0);
    }

    @Test
    public void aPersonWhoPickedVoiceBoostStillGetsIt() {
        AudioTrack preferred = track(audio("140-vb"));

        assertTrue(preferred.inBounds(track(audio("140-vb"))) >= 0);
    }
}
