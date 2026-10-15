package crewx.media;

import java.util.Collections;
import java.util.List;

/** Result of a lyrics lookup for one track. */
public final class Lyrics {
    public enum State { LOADING, FOUND, NOT_FOUND, INSTRUMENTAL, ERROR }

    public static final Lyrics NONE = new Lyrics("", State.NOT_FOUND, Collections.<LyricLine>emptyList(), false);

    public final String key;
    public final State state;
    public final List<LyricLine> lines;
    public final boolean synced;

    public Lyrics(String key, State state, List<LyricLine> lines, boolean synced) {
        this.key = key;
        this.state = state;
        this.lines = lines;
        this.synced = synced;
    }

    public static Lyrics loading(String key) {
        return new Lyrics(key, State.LOADING, Collections.<LyricLine>emptyList(), false);
    }
}
