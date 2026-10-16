package crewx.media.backend;

import crewx.media.MediaAction;
import crewx.media.MediaInfo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Shared skeleton for backends that poll an external command (Linux playerctl, macOS osascript). */
abstract class PollingBackend implements MediaBackend {
    /** Polls while nothing plays are this many times slower: a player that is closed doesn't need checking every second. */
    private static final long IDLE_FACTOR = 3L;

    private final long intervalMs;
    private ScheduledExecutorService executor;
    private Consumer<MediaInfo> sink;
    private Consumer<String> status;
    private boolean unavailableReported;
    /** The tool could not be started at all (not installed): no more polling until a manual refresh. */
    private volatile boolean toolMissing;
    private volatile boolean lastHadMedia;

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
        this.toolMissing = false;
        this.executor.schedule(this::loop, 0L, TimeUnit.MILLISECONDS);
    }

    /** One poll, then the next one is scheduled: faster while something plays, never again if the tool is missing. */
    private void loop() {
        tick();
        ScheduledExecutorService current = this.executor;
        if (current == null || current.isShutdown() || this.toolMissing) return;
        long delay = this.lastHadMedia ? this.intervalMs : this.intervalMs * IDLE_FACTOR;
        try {
            current.schedule(this::loop, delay, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // stopped meanwhile
        }
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
        if (current == null) return;
        if (this.toolMissing) {
            // a manual refresh (".spotify" status, clicking the card) gives a missing tool one more chance
            this.toolMissing = false;
            this.unavailableReported = false;
            current.execute(this::loop);
        } else {
            current.execute(this::tick);
        }
    }

    private void tick() {
        try {
            MediaInfo info = poll();
            this.lastHadMedia = info != null;
            this.sink.accept(info);
        } catch (Exception exception) {
            this.lastHadMedia = false;
            // the program could not be started at all: retrying every second only costs a process start each time
            if (exception instanceof Exec.ToolMissingException) this.toolMissing = true;
            this.sink.accept(null);
            if (!this.unavailableReported) {
                this.unavailableReported = true;
                this.status.accept(unavailableMessage());
            }
        }
    }
}
