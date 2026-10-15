package crewx.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Decides which single lyric excerpt belongs to the current playback position. Pure Java (unit-tested).
 *
 * Synced (LRC) lyrics use their own timestamps. Plain lyrics have no timing, so their lines are spread over
 * the track (longer lines get more time) and shown one at a time, with consecutive repeats merged. Synced
 * repeats keep their own timestamps (a repeated chorus line stays up while it is sung again); the card just
 * does not animate a change when the text stays the same.
 */
public final class LyricsTimeline {
    /** Pace for plain lyrics when the track length is unknown. */
    static final long PLAIN_BASE_MS = 1800L;
    static final long PLAIN_PER_CHAR_MS = 90L;
    /** A line stays up at most this long after its sweep when the next one is far away (instrumental gap). */
    static final long HOLD_AFTER_LINE_MS = 6000L;

    private final List<LyricLine> lines;
    private final boolean estimated;

    private LyricsTimeline(List<LyricLine> lines, boolean estimated) {
        this.lines = lines;
        this.estimated = estimated;
    }

    /** Builds the timeline for a track; {@code durationMs <= 0} when unknown. */
    public static LyricsTimeline of(Lyrics lyrics, long durationMs) {
        if (lyrics == null || lyrics.state != Lyrics.State.FOUND || lyrics.lines.isEmpty()) {
            return new LyricsTimeline(Collections.<LyricLine>emptyList(), false);
        }
        if (lyrics.synced) return new LyricsTimeline(collapse(lyrics.lines, false), false);
        return new LyricsTimeline(estimate(collapse(lyrics.lines, true), durationMs), true);
    }

    /** Same text as the excerpt shown before (ignoring case and spacing): no transition is needed. */
    public boolean sameText(int a, int b) {
        if (a < 0 || b < 0 || a >= this.lines.size() || b >= this.lines.size()) return false;
        return this.lines.get(a).text.trim().replaceAll("\\s+", " ")
                .equalsIgnoreCase(this.lines.get(b).text.trim().replaceAll("\\s+", " "));
    }

    public List<LyricLine> lines() {
        return this.lines;
    }

    /** True when the timing was estimated (plain lyrics), so the UI should not pretend to be word-exact. */
    public boolean isEstimated() {
        return this.estimated;
    }

    /**
     * Index of the excerpt to show at {@code positionMs}, or -1 for none (before the first line, or during a
     * long instrumental gap after a line has been sung).
     */
    public int indexAt(long positionMs) {
        int active = -1;
        for (int i = 0; i < this.lines.size(); i++) {
            if (this.lines.get(i).timeMs <= positionMs) active = i;
            else break;
        }
        if (active < 0 || this.estimated) return active;
        LyricLine line = this.lines.get(active);
        long nextAt = active + 1 < this.lines.size() ? this.lines.get(active + 1).timeMs : Long.MAX_VALUE;
        long hideAt = line.timeMs + sweepMs(line) + HOLD_AFTER_LINE_MS;
        return positionMs >= hideAt && hideAt < nextAt ? -1 : active;
    }

    /** 0..1 progress of the karaoke sweep through excerpt {@code index}. */
    public float progress(int index, long positionMs, long durationMs) {
        if (index < 0 || index >= this.lines.size()) return 0.0F;
        if (this.estimated) return 1.0F;
        LyricLine line = this.lines.get(index);
        long end;
        if (index + 1 < this.lines.size()) end = this.lines.get(index + 1).timeMs;
        else end = durationMs > line.timeMs ? durationMs : line.timeMs + 4000L;
        float gap = Math.max(300L, end - line.timeMs);
        float sweep = Math.max(250.0F, Math.min(gap * 0.92F, sweepMs(line)));
        return Math.max(0.0F, Math.min(1.0F, (positionMs - line.timeMs) / sweep));
    }

    private static long sweepMs(LyricLine line) {
        return Math.max(1, line.text.length()) * 150L + 400L;
    }

    /** Drops empty lines; with {@code mergeRepeats} also merges consecutive lines with the same text. */
    static List<LyricLine> collapse(List<LyricLine> source, boolean mergeRepeats) {
        List<LyricLine> result = new ArrayList<>();
        String previous = null;
        for (LyricLine line : source) {
            String text = line.text == null ? "" : line.text.trim();
            if (text.isEmpty()) continue;
            String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (mergeRepeats && normalized.equals(previous)) continue;
            previous = normalized;
            result.add(new LyricLine(line.timeMs, text));
        }
        return result;
    }

    /** Spreads untimed lines over the track: they start after a short intro and end before the outro. */
    static List<LyricLine> estimate(List<LyricLine> plain, long durationMs) {
        List<LyricLine> result = new ArrayList<>(plain.size());
        if (plain.isEmpty()) return result;
        long[] weights = new long[plain.size()];
        long total = 0L;
        for (int i = 0; i < plain.size(); i++) {
            weights[i] = PLAIN_BASE_MS + plain.get(i).text.length() * PLAIN_PER_CHAR_MS;
            total += weights[i];
        }
        long start = 0L;
        double scale = 1.0D;
        if (durationMs > 0L) {
            start = Math.round(durationMs * 0.08D);
            long span = Math.round(durationMs * 0.84D);
            scale = span / (double) total;
        }
        double at = start;
        for (int i = 0; i < plain.size(); i++) {
            result.add(new LyricLine(Math.round(at), plain.get(i).text));
            at += weights[i] * scale;
        }
        return result;
    }
}
