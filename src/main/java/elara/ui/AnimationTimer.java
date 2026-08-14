package elara.ui;

/**
 * Smooth animation timer ported from Raven's Timer utility.
 *
 * <p>Provides eased value interpolation between a start and end value
 * over a configurable duration. Used by ClickGUI components for smooth
 * open/close, hover, and scroll animations.</p>
 *
 * <p>The easing function uses a smooth sinusoidal curve that produces
 * natural-feeling acceleration and deceleration, matching the Raven
 * ClickGUI's animation style.</p>
 */
public class AnimationTimer {
    private long start;
    private long duration;
    private boolean started;

    /**
     * Creates a timer with the specified duration in milliseconds.
     *
     * @param durationMs animation duration in milliseconds
     */
    public AnimationTimer(float durationMs) {
        this.duration = (long) durationMs;
    }

    /**
     * Starts (or restarts) the timer.
     */
    public void start() {
        this.start = System.currentTimeMillis();
        this.started = true;
    }

    /**
     * Resets the timer to the initial state.
     */
    public void reset() {
        this.start = 0;
        this.started = false;
    }

    /**
     * Returns whether the timer has been started and is still within its duration.
     */
    public boolean isActive() {
        return started && (System.currentTimeMillis() - start) < duration;
    }

    /**
     * Returns the raw progress ratio (0.0 to 1.0), clamped.
     */
    public float getProgress() {
        if (!started) return 0.0f;
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed >= duration) return 1.0f;
        if (elapsed <= 0) return 0.0f;
        return (float) elapsed / (float) duration;
    }

    /**
     * Returns an eased float value between startValue and endValue.
     *
     * <p>Uses a smooth sinusoidal easing curve:
     * <pre>(1 - cos(progress * PI)) / 2</pre>
     * This produces a gentle ease-in/ease-out feel identical to Raven's
     * ClickGUI animations.</p>
     *
     * @param startValue the value at progress=0
     * @param endValue   the value at progress=1
     * @return the interpolated value
     */
    public float getValueFloat(float startValue, float endValue) {
        if (!started) return startValue;
        float progress = getProgress();
        if (progress >= 1.0f) return endValue;

        // Smooth sinusoidal easing
        float eased = (1.0f - (float) Math.cos(progress * Math.PI)) * 0.5f;
        return startValue + (endValue - startValue) * eased;
    }

    /**
     * Returns an eased int value between startValue and endValue.
     */
    public int getValueInt(int startValue, int endValue) {
        return Math.round(getValueFloat(startValue, endValue));
    }

    /**
     * Returns the elapsed time since start in milliseconds.
     */
    public long getElapsed() {
        return started ? System.currentTimeMillis() - start : 0;
    }

    /**
     * Returns the configured duration in milliseconds.
     */
    public long getDuration() {
        return duration;
    }
}
