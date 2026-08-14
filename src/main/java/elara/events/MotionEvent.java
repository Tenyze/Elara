package elara.events;

import elara.event.events.Event;

public class MotionEvent implements Event {
    private final EventState state;

    public MotionEvent(EventState state) {
        this.state = state;
    }

    public EventState getEventState() {
        return state;
    }

    public enum EventState {
        PRE, POST
    }
}