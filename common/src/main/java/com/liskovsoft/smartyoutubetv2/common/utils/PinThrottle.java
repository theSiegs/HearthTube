package com.liskovsoft.smartyoutubetv2.common.utils;

/**
 * The same throttle Hearth puts on its parent PIN (Hearth's ParentPinPolicy): after {@value #MAX_TRIES} wrong tries in a
 * row, each further wrong one makes the pad wait, 30 seconds and doubling up to an hour; a right PIN clears it. Pure
 * logic over a saved state; the caller keeps the state and supplies both clocks. The monotonic clock
 * (SystemClock.elapsedRealtime) can't be set back, but restarts at a reboot; then the wall clock tells how long
 * the wait has run, never longer than the wait itself.
 */
public final class PinThrottle {
    public static final int MAX_TRIES = 5;
    public static final long BASE_WAIT_MS = 30_000;
    public static final long MAX_WAIT_MS = 60 * 60_000;
    private static final long NONE = -1;

    /** Wrong tries in a row since the last right PIN, and when the current wait started (both clocks, NONE when none) */
    public static final class State {
        public final int fails;
        public final long lockedAtElapsed;
        public final long lockedAtWall;

        public State(int fails, long lockedAtElapsed, long lockedAtWall) {
            this.fails = fails;
            this.lockedAtElapsed = lockedAtElapsed;
            this.lockedAtWall = lockedAtWall;
        }

        public static State fresh() {
            return new State(0, NONE, NONE);
        }
    }

    private PinThrottle() {
    }

    /** Seconds still to wait (rounded up), 0 when checks are allowed */
    public static int waitSeconds(State state, long nowElapsed, long nowWall) {
        if (state.lockedAtElapsed == NONE) {
            return 0;
        }

        long passed;
        if (nowElapsed >= state.lockedAtElapsed) {
            passed = nowElapsed - state.lockedAtElapsed;
        } else {
            // Rebooted since: the wall clock is all there is. Set back it shows less time passed, so the full wait stands
            passed = Math.max(0, nowWall - state.lockedAtWall);
        }

        long left = waitMs(state.fails) - passed;
        return left > 0 ? (int) ((left + 999) / 1000) : 0;
    }

    /** The state after a wrong PIN: from the {@value #MAX_TRIES}th in a row, each starts a wait (longer each time) */
    public static State afterWrong(State state, long nowElapsed, long nowWall) {
        int fails = state.fails + 1;
        return fails >= MAX_TRIES ? new State(fails, nowElapsed, nowWall) : new State(fails, state.lockedAtElapsed, state.lockedAtWall);
    }

    /** How long the {@code fails}-th wrong PIN in a row makes the pad wait (0: not yet) */
    static long waitMs(int fails) {
        if (fails < MAX_TRIES) {
            return 0;
        }

        return Math.min(MAX_WAIT_MS, BASE_WAIT_MS << Math.min(fails - MAX_TRIES, 20));
    }
}
