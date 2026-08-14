package elara.util;

public class MSTimer {
    private long time = 0;

    public MSTimer() {
        this.reset();
    }

    public void reset() {
        this.time = System.currentTimeMillis();
    }

    public boolean hasTimePassed(long ms) {
        return System.currentTimeMillis() >= this.time + ms;
    }

    public long getTime() {
        return System.currentTimeMillis() - this.time;
    }

    public long getElapsed() {
        return System.currentTimeMillis() - this.time;
    }

    public boolean hasReached(long ms) {
        return this.getTime() >= ms;
    }

    public void delay(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}