package crewx.media.backend;

import crewx.media.MediaAction;
import crewx.media.MediaInfo;

import java.util.function.Consumer;

/** No way to read the system media here (Android): reports why once and never polls anything. */
public final class UnavailableMediaBackend implements MediaBackend {
    private final String reason;

    public UnavailableMediaBackend(String reason) {
        this.reason = reason;
    }

    @Override
    public void start(Consumer<MediaInfo> sink, Consumer<String> status) {
        sink.accept(null);
        status.accept(this.reason);
    }

    @Override
    public void stop() {
    }

    @Override
    public void send(MediaAction action, long argument) {
    }

    @Override
    public void refresh() {
    }
}
