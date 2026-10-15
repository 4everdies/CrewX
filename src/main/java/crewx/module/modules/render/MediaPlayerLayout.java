package crewx.module.modules.render;

/**
 * Size math for the {@link MediaPlayer} card. Pure Java (no Minecraft types) so it can be tested on its own.
 *
 * The card is drawn on a grid scaled by its width, so one width is all that is needed to resize the cover,
 * texts, progress bar, controls and lyrics together while keeping their proportions.
 */
final class MediaPlayerLayout {
    /** Below this the title and artist lines start to touch; the card never gets narrower. */
    static final float MIN_WIDTH = 150.0F;
    /** Card height / width (the reference grid is 1006 x 413). */
    static final float CARD_RATIO = 413.0F / 1006.0F;
    /**
     * Gap + lyrics area under the card, as a fraction of the width (upper bound: the area holds one excerpt of
     * at most two rows, about 0.17 of the width at the minimum card size and less when larger).
     */
    static final float LYRICS_RATIO = 0.035F + 0.25F;
    private static final float SCREEN_MARGIN = 16.0F;
    private static final float MAX_SCREEN_WIDTH_SHARE = 0.7F;

    /** After a pause the card stays this long before it hides (avoids flicker between songs/skips). */
    static final long PAUSE_GRACE_MS = 4000L;

    private MediaPlayerLayout() {
    }

    /**
     * The card is only on screen while something plays (plus a short grace after a pause). With the chat open
     * a paused track is shown too, so it can be resumed or moved.
     */
    static boolean shouldShow(boolean hasTrack, boolean playing, long msSinceLastPlaying, boolean chatOpen) {
        if (!hasTrack) return false;
        if (playing || chatOpen) return true;
        return msSinceLastPlaying >= 0L && msSinceLastPlaying < PAUSE_GRACE_MS;
    }

    /**
     * @param baseWidth   the "compact-width" setting (width at 100 %)
     * @param sizePercent the "card-size" slider
     * @return the card width in scaled pixels, or a value below {@link #MIN_WIDTH} when the screen is too small
     */
    static float width(int baseWidth, int sizePercent, float screenWidth, float screenHeight, boolean lyrics) {
        float wanted = baseWidth * (sizePercent / 100.0F);
        float maxByWidth = Math.min(screenWidth - SCREEN_MARGIN, screenWidth * MAX_SCREEN_WIDTH_SHARE);
        float heightPerWidth = CARD_RATIO + (lyrics ? LYRICS_RATIO : 0.0F);
        float maxByHeight = (screenHeight - SCREEN_MARGIN) / heightPerWidth;
        float max = Math.min(maxByWidth, maxByHeight);
        if (max < MIN_WIDTH) return max;
        return Math.max(MIN_WIDTH, Math.min(max, wanted));
    }
}
