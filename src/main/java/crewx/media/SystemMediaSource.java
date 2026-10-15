package crewx.media;

import crewx.media.backend.LinuxMediaBackend;
import crewx.media.backend.MacMediaBackend;
import crewx.media.backend.MediaBackend;
import crewx.media.backend.WindowsMediaBackend;

import java.io.File;

/**
 * Reads whatever the operating system says is playing: Spotify app, SoundCloud / YouTube / any site in
 * the browser, VLC... No account, no login. Windows is fully supported; Linux needs playerctl;
 * macOS supports the Spotify and Music apps.
 */
public final class SystemMediaSource implements MediaSource {
    private final MediaBackend backend;
    private volatile String status = "Starting";
    private Listener listener;

    public SystemMediaSource(File workDirectory) {
        String os = HttpUtil.osName();
        if (os.contains("win")) this.backend = new WindowsMediaBackend(workDirectory);
        else if (os.contains("mac")) this.backend = new MacMediaBackend();
        else this.backend = new LinuxMediaBackend();
    }

    @Override
    public String getName() {
        return "System";
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        this.backend.start(info -> listener.onMedia(this, info), message -> {
            this.status = message;
            listener.onStatus(this, message);
        });
    }

    @Override
    public void stop() {
        this.backend.stop();
    }

    @Override
    public void control(MediaAction action, long argument) {
        this.backend.send(action, argument);
    }

    @Override
    public void refreshNow() {
        this.backend.refresh();
    }

    @Override
    public String getStatus() {
        return this.status;
    }
}
