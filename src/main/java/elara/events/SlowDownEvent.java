package elara.events;

import elara.event.events.EventStoppable;

public class SlowDownEvent extends EventStoppable {
    public float forward;
    public float strafe;
    private float forwardMultiplier = 0.2F;
    private float strafeMultiplier = 0.2F;

    public SlowDownEvent(float forward, float strafe) {
        this.forward = forward;
        this.strafe = strafe;
    }

    public void setForwardMultiplier(float forwardMultiplier) {
        this.forwardMultiplier = forwardMultiplier;
    }

    public void setStrafeMultiplier(float strafeMultiplier) {
        this.strafeMultiplier = strafeMultiplier;
    }

    public float getForwardMultiplier() {
        return this.forwardMultiplier;
    }

    public float getStrafeMultiplier() {
        return this.strafeMultiplier;
    }
}
