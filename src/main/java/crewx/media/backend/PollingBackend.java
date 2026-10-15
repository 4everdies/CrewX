package crewx.media.backend;

import crewx.media.MediaAction;
import crewx.media.MediaInfo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Shared skeleton for backends that poll an external command (Linux playerctl, macOS osascript). */
abstract class PollingBackend implements MediaBackend {
    private final long intervalMs;
    private ScheduledExecutorService executor;
    private Consumer<MediaInfo> sink;
    private Consumer<String> status;
    private boolean unavailableReported;

    PollingBackend(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    /** Returns the current media, or null when nothing plays. Throws when the tool is missing. */
    abstract MediaInfo poll() throws Exception;

    abstract void execute(MediaAction action, long argument);

    abstract String unavailableMessage();

    @Override
    public synchronized void start(Consumer<MediaInfo> sink, Consumer<String> status) {
        this.sink = sink;
        this.status = status;
        if (this.executor != null) return;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CrewX media (poll)");
            thread.setDaemon(true);
            return thread;
        });
        this.executor.scheduleWithFixedDelay(this::tick, 0L, this.intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (this.executor != null) this.executor.shutdownNow();
        this.executor = null;
    }

    @Override
    public void send(MediaAction action, long argument) {
        ScheduledExecutorService current = this.executor;
        if (current == null) return;
        current.execute(() -> {
            execute(action, argument);
            tick();
        });
    }

    @Override
    public void refresh() {
        ScheduledExecutorService current = this.executor;
        if (current != null) current.execute(this::tick);
    }

    private void tick() {
        try {
            this.sink.accept(poll());
        } catch (Exception exception) {
            this.sink.accept(null);
            if (!this.unavailableReported) {
                this.unavailableReported = true;
                this.status.accept(unavailableMessage());
            }
        }
    }
}
