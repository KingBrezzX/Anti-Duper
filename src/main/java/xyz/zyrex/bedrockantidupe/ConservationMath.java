package xyz.zyrex.bedrockantidupe;

/** Pure arithmetic used by transaction conservation checks. */
final class ConservationMath {
    private ConservationMath() {}

    static int netDelta(int playerBefore, int playerAfter, int containerBefore, int containerAfter) {
        long delta = (long) playerAfter + containerAfter - playerBefore - containerBefore;
        if (delta > Integer.MAX_VALUE || delta < Integer.MIN_VALUE) {
            throw new ArithmeticException("conservation delta overflow");
        }
        return (int) delta;
    }
}
