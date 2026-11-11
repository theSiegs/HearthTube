package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;

import com.liskovsoft.smartyoutubetv2.common.utils.PinThrottle.State;

import org.junit.Test;

public class PinThrottleTest {
    private static final long WALL = 1_700_000_000_000L;

    private static State wrong(State state, int times, long elapsed, long wall) {
        for (int i = 0; i < times; i++) {
            state = PinThrottle.afterWrong(state, elapsed, wall);
        }
        return state;
    }

    @Test
    public void fourWrongTriesStillAllowAFifth() {
        State state = wrong(State.fresh(), 4, 1_000, WALL);
        assertEquals(4, state.fails);
        assertEquals(0, PinThrottle.waitSeconds(state, 1_000, WALL));
    }

    @Test
    public void theFifthWrongTryStartsAHalfMinuteWait() {
        State state = wrong(State.fresh(), 5, 1_000, WALL);
        assertEquals(30, PinThrottle.waitSeconds(state, 1_000, WALL));
        assertEquals(10, PinThrottle.waitSeconds(state, 21_000, WALL + 20_000));
        assertEquals(1, PinThrottle.waitSeconds(state, 30_500, WALL + 29_500));
    }

    @Test
    public void eachWrongTryAfterAWaitDoublesTheNext() {
        State state = wrong(State.fresh(), 5, 1_000, WALL);
        assertEquals(0, PinThrottle.waitSeconds(state, 31_000, WALL + 30_000));

        state = wrong(state, 1, 40_000, WALL + 39_000);
        assertEquals(60, PinThrottle.waitSeconds(state, 40_000, WALL + 39_000));

        state = wrong(state, 1, 200_000, WALL + 199_000);
        assertEquals(120, PinThrottle.waitSeconds(state, 200_000, WALL + 199_000));
    }

    @Test
    public void theWaitNeverPassesAnHour() {
        assertEquals(PinThrottle.MAX_WAIT_MS, PinThrottle.waitMs(12));
        assertEquals(PinThrottle.MAX_WAIT_MS, PinThrottle.waitMs(1_000));
    }

    @Test
    public void aClockSetBackDoesNotShortenTheWait() {
        State state = wrong(State.fresh(), 5, 1_000, WALL);
        assertEquals(30, PinThrottle.waitSeconds(state, 1_000, WALL - 3_600_000));
        assertEquals(20, PinThrottle.waitSeconds(state, 11_000, WALL - 3_600_000)); // the monotonic clock rules
    }

    @Test
    public void afterARebootTheWallClockTellsTheWait() {
        State state = wrong(State.fresh(), 5, 500_000, WALL);
        assertEquals(10, PinThrottle.waitSeconds(state, 2_000, WALL + 20_000));
        assertEquals(0, PinThrottle.waitSeconds(state, 2_000, WALL + 90_000));
    }

    @Test
    public void afterARebootWithTheWallClockSetBackTheFullWaitStands() {
        State state = wrong(State.fresh(), 5, 500_000, WALL);
        assertEquals(30, PinThrottle.waitSeconds(state, 2_000, WALL - 5_000_000));
    }

    @Test
    public void aFreshStateNeverWaits() {
        assertEquals(0, PinThrottle.waitSeconds(State.fresh(), 0, 0));
    }
}
