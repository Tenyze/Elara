package elara.events;

import elara.event.events.Event;

public class HitSlowDownEvent implements Event {
    private double slowDown = 0.6;
    private boolean sprint = false;

    public double getSlowDown() {
        return this.slowDown;
    }

    public void setSlowDown(double slowDown) {
        this.slowDown = slowDown;
    }

    public boolean isSprint() {
        return this.sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }
}
