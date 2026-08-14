package elara.events;

import elara.event.events.callables.EventCancellable;
import elara.event.types.EventType;

public class TickEvent extends EventCancellable {
    private final EventType type;

    public TickEvent(EventType type) {
        this.type = type;
    }

    public EventType getType() {
        return this.type;
    }
}