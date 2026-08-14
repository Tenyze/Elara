package elara.events;

import elara.event.events.EventStoppable;

public class SlowDownEvent extends EventStoppable {
    public float forward;
    public float strafe;

    public SlowDownEvent(float forward, float strafe) {
        this.forward = forward;
        this.strafe = strafe;
    }
}