package crewx.media;

/** Immutable snapshot of what is playing. Positions are extrapolated with {@link #positionNow()}. */
public final class MediaInfo {
    public final String key;
    public final String title;
    public final String artist;
    public final String album;
    /** http(s) URL, "file:" path written by a backend, or empty. */
    public final String artworkUrl;
    /** Human-readable origin ("Spotify", "Chrome", ...). */
    public final String source;
    public final long durationMs;
    public final long positionMs;
    /** System.currentTimeMillis() at which {@link #positionMs} was valid. */
    public final long capturedAt;
    public final boolean playing;
    public final boolean explicit;
    public final boolean canControl;
    public final boolean canSeek;

    private MediaInfo(Builder b) {
        this.key = b.key;
        this.title = b.title;
        this.artist = b.artist;
        this.album = b.album;
        this.artworkUrl = b.artworkUrl;
        this.source = b.source;
        this.durationMs = Math.max(0L, b.durationMs);
        this.positionMs = Math.max(0L, b.positionMs);
        this.capturedAt = b.capturedAt;
        this.playing = b.playing;
        this.explicit = b.explicit;
        this.canControl = b.canControl;
        this.canSeek = b.canSeek;
    }

    public long positionNow() {
        long position = this.positionMs;
        if (this.playing) position += Math.max(0L, System.currentTimeMillis() - this.capturedAt);
        if (this.durationMs > 0L) position = Math.min(position, this.durationMs);
        return Math.max(0L, position);
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.key = this.key;
        b.title = this.title;
        b.artist = this.artist;
        b.album = this.album;
        b.artworkUrl = this.artworkUrl;
        b.source = this.source;
        b.durationMs = this.durationMs;
        b.positionMs = this.positionMs;
        b.capturedAt = this.capturedAt;
        b.playing = this.playing;
        b.explicit = this.explicit;
        b.canControl = this.canControl;
        b.canSeek = this.canSeek;
        return b;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public String key = "";
        public String title = "";
        public String artist = "";
        public String album = "";
        public String artworkUrl = "";
        public String source = "";
        public long durationMs;
        public long positionMs;
        public long capturedAt = System.currentTimeMillis();
        public boolean playing;
        public boolean explicit;
        public boolean canControl = true;
        public boolean canSeek;

        /** Sets play state and re-anchors the position to "now". */
        public Builder playing(boolean playing, long positionMs) {
            this.playing = playing;
            this.positionMs = positionMs;
            this.capturedAt = System.currentTimeMillis();
            return this;
        }

        public MediaInfo build() {
            if (this.title == null) this.title = "";
            if (this.artist == null) this.artist = "";
            if (this.album == null) this.album = "";
            if (this.artworkUrl == null) this.artworkUrl = "";
            if (this.source == null) this.source = "";
            if (this.key == null || this.key.isEmpty()) this.key = this.source + "|" + this.title + "|" + this.artist;
            return new MediaInfo(this);
        }
    }
}
