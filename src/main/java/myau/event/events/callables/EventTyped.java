package myau.event.events.callables;

import myau.event.events.Event;
import myau.event.events.Typed;

public abstract class EventTyped implements Event, Typed {
    private final byte type;

    protected EventTyped(byte eventType) {
        type = eventType;
    }

    @Override
    public byte getType() {
        return type;
    }
}