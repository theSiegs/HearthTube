package com.liskovsoft.smartyoutubetv2.common.utils;

/**
 * The same throttle Hearth puts on its parent PIN: after {@value #MAX_TRIES} wrong tries, checks are refused for a
 * minute. Pure logic over a saved state; the caller keeps the state and supplies both clocks. The monotonic clock
 * (SystemClock.elapsedRealtime) can't be set back, but restarts at a reboot; then the wall clock tells how long
 * the wait has run, never longer than the wait itself.
 */
public final class PinThrottle {
    public static final int MAX_TRIES = 5;
    public static final long WAIT_MS = 60_000;
    private static final long NONE = -1;

    /** Wrong tries since the last lock or success, and when the current lock started (both clocks, NONE when not locked) */
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

        long left = WAIT_MS - passed;
        return left > 0 ? (int) ((left + 999) / 1000) : 0;
    }

    /** The state after a wrong PIN: the {@value #MAX_TRIES}th starts the wait */
    public static State afterWrong(State state, long nowElapsed, long nowWall) {
        int fails = state.fails + 1;
        return fails >= MAX_TRIES ? new State(0, nowElapsed, nowWall) : new State(fails, state.lockedAtElapsed, state.lockedAtWall);
    }
}
