package crewx.media;

/** A provider of "what is playing" (system media session, Spotify Web API, ...). */
public interface MediaSource {
    interface Listener {
        /** Called from a worker thread. {@code info == null} means nothing is playing. */
        void onMedia(MediaSource source, MediaInfo info);

        void onStatus(MediaSource source, String status);
    }

    String getName();

    void start(Listener listener);

    void stop();

    /** {@code argument} is the target position in ms for {@link MediaAction#SEEK}. */
    void control(MediaAction action, long argument);

    void refreshNow();

    String getStatus();
}
