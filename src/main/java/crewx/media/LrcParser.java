package crewx.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses LRC ("[mm:ss.xx] text") synchronized lyrics. Pure Java, unit-testable. */
public final class LrcParser {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:\\s*([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);

    private LrcParser() {
    }

    public static List<LyricLine> parseSynced(String text) {
        List<LyricLine> parsed = new ArrayList<>();
        if (text == null || text.isEmpty()) return parsed;
        long offset = 0L;
        Matcher offsetMatcher = OFFSET.matcher(text);
        if (offsetMatcher.find()) {
            try {
                offset = Long.parseLong(offsetMatcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        for (String line : text.split("\\r?\\n")) {
            Matcher matcher = TIMESTAMP.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = 0L;
                if (fraction != null) {
                    if (fraction.length() == 1) millis = Long.parseLong(fraction) * 100L;
                    else if (fraction.length() == 2) millis = Long.parseLong(fraction) * 10L;
                    else millis = Long.parseLong(fraction.substring(0, 3));
                }
                times.add((minutes * 60L + seconds) * 1000L + millis - offset);
                end = matcher.end();
            }
            if (times.isEmpty()) continue;
            String lyric = line.substring(end).trim();
            if (lyric.isEmpty()) continue;
            for (Long time : times) parsed.add(new LyricLine(Math.max(0L, time), lyric));
        }
        Collections.sort(parsed, Comparator.comparingLong(value -> value.timeMs));
        return parsed;
    }

    public static List<LyricLine> parsePlain(String text) {
        List<LyricLine> parsed = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return parsed;
        for (String line : text.split("\\r?\\n")) {
            String clean = line.trim();
            if (!clean.isEmpty()) parsed.add(new LyricLine(-1L, clean));
        }
        return parsed;
    }
}
