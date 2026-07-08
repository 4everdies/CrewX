package myau.event.events.callables;

import myau.event.events.Cancellable;
import myau.event.events.Event;

public abstract class EventCancellable implements Event, Cancellable {
    private boolean cancelled;

    protected EventCancellable() {
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean state) {
        cancelled = state;
    }
}