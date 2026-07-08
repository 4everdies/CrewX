package myau.event.events;

public abstract class EventStoppable implements Event {
    private boolean stopped;

    protected EventStoppable() {
    }

    public void stop() {
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }
}