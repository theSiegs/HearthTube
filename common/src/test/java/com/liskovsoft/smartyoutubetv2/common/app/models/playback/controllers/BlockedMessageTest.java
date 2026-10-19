package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BlockedMessageTest {
    private static final String NETWORK_HINT = "Playback blocked by YouTube. Try another network or VPN.";

    @Test
    public void botCheckKeepsTheNetworkHint() {
        assertEquals(NETWORK_HINT, ErrorFixerController.getBlockedMessage("Sign in to confirm you're not a bot", NETWORK_HINT));
    }

    @Test
    public void emptyAnswerKeepsTheNetworkHint() {
        assertEquals(NETWORK_HINT, ErrorFixerController.getBlockedMessage(null, NETWORK_HINT));
        assertEquals(NETWORK_HINT, ErrorFixerController.getBlockedMessage("", NETWORK_HINT));
        assertEquals(NETWORK_HINT, ErrorFixerController.getBlockedMessage("fromNullable result is null", NETWORK_HINT));
    }

    @Test
    public void otherReasonsAreShownAsYouTubeGaveThem() {
        String reason = "This video is private. Sign in if you have been granted access.";
        assertEquals(reason, ErrorFixerController.getBlockedMessage(reason, NETWORK_HINT));
    }

    @Test
    public void wordsContainingBotAreNotTheBotCheck() {
        String reason = "Both accounts need to sign in to watch this";
        assertEquals(reason, ErrorFixerController.getBlockedMessage(reason, NETWORK_HINT));
    }
}
