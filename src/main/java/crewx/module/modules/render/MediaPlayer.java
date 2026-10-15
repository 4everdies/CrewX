package crewx.module.modules.render;

import crewx.clickgui.render.RoundedUtils;
import crewx.event.EventTarget;
import crewx.events.Render2DEvent;
import crewx.gui.BackdropBlur;
import crewx.gui.ClickGuiFontRenderer;
import crewx.gui.ClientFont;
import crewx.media.LyricLine;
import crewx.media.Lyrics;
import crewx.media.LyricsTimeline;
import crewx.media.MediaAction;
import crewx.media.CoverBuilder;
import crewx.media.MediaInfo;
import crewx.media.MediaManager;
import crewx.media.SpotifyApiSource;
import crewx.module.Module;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ButtonProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
import crewx.property.properties.PercentProperty;
import crewx.property.properties.TextProperty;
import crewx.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Now-playing card (Spotify, SoundCloud, YouTube, any player) with synchronized lyrics underneath.
 *
 * This class only draws and handles the mouse; everything about finding the song lives in
 * {@link MediaManager} (system media session + optional Spotify Web API) and the lyrics in
 * {@code crewx.media.LyricsService}.
 */
public class MediaPlayer extends Module {
    private static final Minecraft MC = Minecraft.getMinecraft();

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_PURPLE = 0xFF9B6CF0;
    private static final int COLOR_PINK = 0xFFD870B5;
    private static final int COLOR_LYRIC_DIM = 0xFFA7ABB4;
    private static final int COLOR_GRAY_TEXT = 0xFF9C9CA1;
    private static final float CAP_HEIGHT = 17.0F * 0.42F;
    private static final float LYRIC_SOFT_EDGE = 12.0F;
    /** The card is laid out on a 1006-unit-wide grid measured from the reference image. */
    private static final float GRID_WIDTH = 1006.0F;
    private static final float GRID_HEIGHT = 413.0F;

    public final ModeProperty source = new ModeProperty("source", 0, new String[]{"Auto", "System", "Spotify"});
    public final BooleanProperty showLyrics = new BooleanProperty("lyrics", true);
    public final BooleanProperty lyricsBackground = new BooleanProperty("lyrics-background", false);
    public final IntProperty lyricsOffset = new IntProperty("lyrics-offset", 0, -3000, 3000);
    public final IntProperty cardWidth = new IntProperty("compact-width", 290, 180, 460);
    /** Scales the whole card (cover, texts, bar, controls, lyrics) live; saved with the config. */
    public final PercentProperty cardSize = new PercentProperty("card-size", 100, 50, 200, null);
    public final BooleanProperty autoLogin = new BooleanProperty("auto-login", true);
    public final TextProperty clientId = new TextProperty("client-id", "");
    public final ButtonProperty spotifyLogin = new ButtonProperty("spotify-login", this::connect);
    public final ButtonProperty spotifyLogout = new ButtonProperty("spotify-logout", this::logout);
    public final IntProperty offsetX = new IntProperty("offset-x", 0, -2000, 2000);
    public final IntProperty offsetY = new IntProperty("offset-y", 0, -2000, 2000);

    private final MediaManager media;
    /** Default-on modules are enabled without onEnabled(); the detection starts on the first frame then. */
    private boolean mediaStarted;
    private long lastPlayingAt;

    // cover texture
    private DynamicTexture dynamicArtwork;
    private ResourceLocation artworkTexture;
    private float artworkFade = 1.0F;

    // smoothing / animation
    private long lastRenderAt;
    private double displayPos;
    private String displayPosKey = "";
    private boolean displayPosValid;
    private float waveTime;
    private String lyricsKey = "";

    // lyrics: only the excerpt for the current moment is shown; the previous one fades out when it changes
    private LyricsTimeline timeline;
    private Lyrics timelineSource;
    private long timelineDuration = -1L;
    private int lyricShown = -1;
    private int lyricPrevious = -1;
    private float lyricTransition = 1.0F;

    // lyric wrap cache
    private List<LyricLine> wrapSource;
    private float wrapWidthCached;
    private List<List<String>> wrapCache = new ArrayList<>();

    // interaction (chat open): geometry from the last frame
    private boolean mouseWasDown;
    private boolean dragging;
    private float grabX;
    private float grabY;
    private float lastX;
    private float lastY;
    private float lastWidth;
    private float lastHeight;
    private float prevCx;
    private float toggleCx;
    private float nextCx;
    private float controlsCy;
    private float controlsHit;
    private float barLeft;
    private float barRight;
    private float barCy;
    private float barHit;
    private int hoveredButton;

    public MediaPlayer() {
        // on by default and hidden from the array list: the card itself only shows up while music plays
        super("Media Player", true, true);
        File gameDir = MC.mcDataDir == null ? new File(".") : MC.mcDataDir;
        File configDir = new File(gameDir, "config/CrewX");
        this.media = new MediaManager(configDir, new MediaManager.Settings() {
            @Override
            public int sourceMode() {
                return MediaPlayer.this.source.getValue();
            }

            @Override
            public String clientIdOverride() {
                return MediaPlayer.this.clientId.getValue();
            }

            @Override
            public boolean autoLogin() {
                return MediaPlayer.this.autoLogin.getValue();
            }
        }, new SpotifyApiSource.Notifier() {
            @Override
            public void chat(String message) {
                ChatUtil.sendFormatted(message);
            }

            @Override
            public void link(String text, String url) {
                ChatComponentText link = new ChatComponentText(text.replace('&', '\u00a7'));
                link.setChatStyle(new ChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
                ChatUtil.send(link);
            }
        });
    }

    // ------------------------------------------------------------------ public API (command / buttons)

    @Override
    public void onEnabled() {
        this.displayPosValid = false;
        resetLyricState();
        this.media.start();
        this.mediaStarted = true;
    }

    @Override
    public void onDisabled() {
        this.media.stop();
        this.mediaStarted = false;
        this.dragging = false;
        this.lastWidth = 0.0F;
    }

    public void connect() {
        if (!this.media.getSpotify().hasClientId()) {
            ChatUtil.sendFormatted("&eNo Spotify login is needed: the Spotify app, SoundCloud and any player in your "
                    + "browser are detected automatically. (Set a client-id only if you want the optional API login.)&r");
            return;
        }
        this.media.start();
        this.media.connectSpotify();
    }

    public void logout() {
        this.media.logoutSpotify();
    }

    public void control(String action) {
        switch (action.toLowerCase(Locale.ROOT)) {
            case "pause": this.media.control(MediaAction.PAUSE); break;
            case "play":
            case "resume": this.media.control(MediaAction.PLAY); break;
            case "toggle": this.media.control(MediaAction.TOGGLE); break;
            case "next": this.media.control(MediaAction.NEXT); break;
            case "previous":
            case "prev": this.media.control(MediaAction.PREVIOUS); break;
            default: break;
        }
    }

    public void refreshNow() {
        this.media.refreshNow();
    }

    public String getStatus() {
        return this.media.getStatus();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.source.getModeString()};
    }

    // ------------------------------------------------------------------ rendering

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        if (!this.mediaStarted) {
            this.media.start();
            this.mediaStarted = true;
        }
        if (MC.theWorld == null || MC.gameSettings.showDebugInfo) return;
        installArtwork();
        MediaInfo current = this.media.getCurrent();
        long nowMs = System.currentTimeMillis();
        if (current != null && current.playing) this.lastPlayingAt = nowMs;
        if (!MediaPlayerLayout.shouldShow(current != null, current != null && current.playing,
                nowMs - this.lastPlayingAt, MC.currentScreen instanceof GuiChat)) {
            // nothing playing: no card on screen (and nothing to click or drag)
            this.lastWidth = 0.0F;
            this.dragging = false;
            this.lastRenderAt = 0L;
            return;
        }
        ScaledResolution resolution = new ScaledResolution(MC);
        boolean lyricsOn = this.showLyrics.getValue();
        float width = MediaPlayerLayout.width(this.cardWidth.getValue(), this.cardSize.getValue(),
                resolution.getScaledWidth(), resolution.getScaledHeight(), lyricsOn);
        if (width < MediaPlayerLayout.MIN_WIDTH) return;
        final float u = width / GRID_WIDTH;
        final float cardH = GRID_HEIGHT * u;
        float lyricsGap = width * 0.035F;
        float lyricsH = lyricsOn ? lyricsAreaHeight(width) : 0.0F;
        float totalH = cardH + (lyricsOn ? lyricsGap + lyricsH : 0.0F);

        long now = System.currentTimeMillis();
        float dt = this.lastRenderAt == 0L ? 0.05F : Math.min(0.1F, (now - this.lastRenderAt) / 1000.0F);
        this.lastRenderAt = now;

        handleMouse(resolution);
        float x = clamp(8.0F + this.offsetX.getValue(), 4.0F, resolution.getScaledWidth() - width - 4.0F);
        float y = clamp(8.0F + this.offsetY.getValue(), 4.0F, resolution.getScaledHeight() - totalH - 4.0F);

        MediaInfo info = this.media.getCurrent();
        updateDisplayPosition(dt, info);
        long position = (long) this.displayPos;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();

        drawCard(resolution, x, y, width, cardH, u, info, position, dt);
        if (lyricsOn) {
            drawLyricsArea(x, y + cardH + lyricsGap, width, lyricsH, dt, position);
        }

        this.lastX = x;
        this.lastY = y;
        this.lastWidth = width;
        this.lastHeight = totalH;

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void drawCard(ScaledResolution resolution, float x, float y, float width, float height, float u,
                          MediaInfo info, long position, float dt) {
        float radius = 75.0F * u;
        float feather = clamp(width * 0.04F, 7.0F, 18.0F);

        // soft frosted edge: blurred backdrop that fades out past the border, plus a faint shadow
        BackdropBlur.drawFeatheredHalo(x, y, width, height, radius, 22.0F, feather);
        for (int i = 5; i >= 1; i--) {
            float grow = feather * i / 5.0F;
            RoundedUtils.drawRoundedRect(x - grow, y - grow, width + grow * 2.0F, height + grow * 2.0F, 0x0C000000, radius + grow);
        }
        BackdropBlur.drawRoundedPanel(x, y, width, height, radius, 0x94161618, 30.0F);
        RoundedUtils.drawRoundedOutlinedRect(x, y, width, height, 0x40FFFFFF,
                radius * resolution.getScaleFactor() / 2.0F, 1.5F);

        // ---- cover
        drawCover(x + 33.0F * u, y + 34.0F * u, 143.0F * u, info != null, dt);

        // ---- title / artist / explicit badge
        boolean hasInfo = info != null;
        String title = hasInfo ? info.title : "Nothing playing";
        String artist = hasInfo ? info.artist : "Play something in Spotify, SoundCloud or your browser";
        float titleScale = Math.max(0.7F, 26.0F * u / CAP_HEIGHT);
        float artistScale = Math.max(0.6F, 22.0F * u / CAP_HEIGHT);
        float infoX = x + 203.0F * u;
        float infoWidth = 657.0F * u;
        boolean explicit = hasInfo && info.explicit;
        String fittedTitle = fitText(title, infoWidth - (explicit ? 40.0F * u : 0.0F), titleScale);
        float titleWidth = ClientFont.getStringWidthFloat(fittedTitle) * titleScale;
        drawStyledText(fittedTitle, infoX, y + 85.0F * u - CAP_HEIGHT * titleScale * 0.5F, titleScale,
                offset -> COLOR_WHITE, 0.28F, 0x55000000);
        if (explicit) {
            float badge = 28.0F * u;
            float badgeX = infoX + titleWidth + 12.0F * u;
            float badgeY = y + 85.0F * u - badge * 0.5F;
            RoundedUtils.drawRoundedRect(badgeX, badgeY, badge, badge, 0xE6EDEDED, 5.0F * u);
            float letterScale = Math.max(0.5F, 17.0F * u / CAP_HEIGHT);
            float letterWidth = ClientFont.getStringWidthFloat("E") * letterScale;
            drawScaledText("E", badgeX + (badge - letterWidth) / 2.0F, badgeY + (badge - CAP_HEIGHT * letterScale) / 2.0F,
                    letterScale, 0xFF202022);
        }
        drawScaledText(fitText(artist, infoWidth, artistScale), infoX,
                y + 127.0F * u - CAP_HEIGHT * artistScale * 0.5F, artistScale, COLOR_GRAY_TEXT);

        // ---- animated "now playing" bars (top right)
        drawWaveform(x + 918.0F * u, y + 96.0F * u, u, hasInfo && info.playing, dt);

        // ---- progress row
        float timeScale = Math.max(0.6F, 21.0F * u / CAP_HEIGHT);
        float timeCy = y + 224.0F * u;
        long duration = hasInfo ? info.durationMs : 0L;
        String elapsed = formatTime(position);
        String remaining = duration > 0L ? "-" + formatTime(Math.max(0L, duration - position)) : "";
        float elapsedWidth = ClientFont.getStringWidthFloat(elapsed) * timeScale;
        float remainingWidth = ClientFont.getStringWidthFloat(remaining) * timeScale;
        float textY = timeCy - CAP_HEIGHT * timeScale * 0.5F;
        drawScaledText(elapsed, x + 51.0F * u, textY, timeScale, COLOR_GRAY_TEXT);
        if (!remaining.isEmpty()) drawScaledText(remaining, x + 958.0F * u - remainingWidth, textY, timeScale, COLOR_GRAY_TEXT);
        float barL = Math.max(x + 133.0F * u, x + 51.0F * u + elapsedWidth + 14.0F * u);
        float barR = Math.min(x + 856.0F * u, x + 958.0F * u - remainingWidth - 14.0F * u);
        if (barR - barL < 20.0F) barR = barL + 20.0F;
        float barH = Math.max(3.0F, 18.0F * u);
        RoundedUtils.drawRoundedRect(barL, timeCy - barH / 2.0F, barR - barL, barH, 0x59FFFFFF, barH / 2.0F);
        if (duration > 0L) {
            float fraction = clamp(position / (float) duration, 0.0F, 1.0F);
            float filled = Math.max(barH, (barR - barL) * fraction);
            if (fraction > 0.0F) {
                RoundedUtils.drawRoundedRect(barL, timeCy - barH / 2.0F, filled, barH, 0xB3E6E6EA, barH / 2.0F);
            }
        }
        this.barLeft = barL;
        this.barRight = barR;
        this.barCy = timeCy;
        this.barHit = Math.max(barH, 40.0F * u) / 2.0F;

        // ---- controls
        boolean canControl = hasInfo && info.canControl;
        this.controlsCy = y + 326.0F * u;
        this.controlsHit = 62.0F * u;
        this.prevCx = x + 293.0F * u;
        this.toggleCx = x + 503.0F * u;
        this.nextCx = x + 713.0F * u;
        int base = canControl ? 0xFFF2F2F4 : 0x66FFFFFF;
        drawDoubleTriangle(this.prevCx, this.controlsCy, 88.0F * u, 64.0F * u, true,
                this.hoveredButton == 1 && canControl ? COLOR_WHITE : base);
        if (hasInfo && info.playing) {
            int color = this.hoveredButton == 2 ? COLOR_WHITE : base;
            float barW = 24.0F * u;
            float barLen = 85.0F * u;
            RoundedUtils.drawRoundedRect(this.toggleCx - 32.0F * u, this.controlsCy - barLen / 2.0F, barW, barLen, color, 5.0F * u);
            RoundedUtils.drawRoundedRect(this.toggleCx + 8.0F * u, this.controlsCy - barLen / 2.0F, barW, barLen, color, 5.0F * u);
        } else {
            drawTriangle(this.toggleCx - 28.0F * u, this.controlsCy - 43.0F * u, this.toggleCx + 40.0F * u,
                    this.controlsCy, this.toggleCx - 28.0F * u, this.controlsCy + 43.0F * u,
                    this.hoveredButton == 2 ? COLOR_WHITE : base);
        }
        drawDoubleTriangle(this.nextCx, this.controlsCy, 90.0F * u, 64.0F * u, false,
                this.hoveredButton == 3 && canControl ? COLOR_WHITE : base);
        drawAirplay(x + 918.0F * u, this.controlsCy, u);
    }

    private void drawCover(float x, float y, float size, boolean hasTrack, float dt) {
        float radius = size * CoverBuilder.RADIUS_RATIO;
        if (!hasTrack || this.artworkTexture == null) {
            RoundedUtils.drawRoundedRect(x, y, size, size, 0x33FFFFFF, radius);
            return;
        }
        this.artworkFade = Math.min(1.0F, this.artworkFade + dt * 4.0F);
        if (this.artworkFade < 1.0F) RoundedUtils.drawRoundedRect(x, y, size, size, 0x33FFFFFF, radius);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, this.artworkFade);
        MC.getTextureManager().bindTexture(this.artworkTexture);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        renderer.pos(x, y, 0.0D).tex(0.0D, 0.0D).endVertex();
        renderer.pos(x, y + size, 0.0D).tex(0.0D, 1.0D).endVertex();
        renderer.pos(x + size, y + size, 0.0D).tex(1.0D, 1.0D).endVertex();
        renderer.pos(x + size, y, 0.0D).tex(1.0D, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void installArtwork() {
        if (this.media.takeArtworkCleared() && this.artworkTexture != null) {
            crewx.util.DynamicTextures.release(this.artworkTexture);
            this.artworkTexture = null;
            this.dynamicArtwork = null;
        }
        BufferedImage image = this.media.takeArtwork();
        if (image == null) return;
        try {
            if (this.artworkTexture != null) crewx.util.DynamicTextures.release(this.artworkTexture);
            this.dynamicArtwork = new DynamicTexture(image);
            this.artworkTexture = MC.getTextureManager().getDynamicTextureLocation("crewx_media_cover", this.dynamicArtwork);
            // filtering set once here, not on every draw (that can stall the driver)
            MC.getTextureManager().bindTexture(this.artworkTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            this.artworkFade = 0.0F;
        } catch (Exception ignored) {
            this.artworkTexture = null;
        }
    }

    private void drawWaveform(float cx, float cy, float u, boolean playing, float dt) {
        if (playing) this.waveTime += dt;
        float[] base = {0.55F, 0.95F, 0.7F, 1.0F, 0.5F};
        float barW = 7.0F * u;
        float gap = 6.0F * u;
        float maxH = 42.0F * u;
        float total = 5.0F * barW + 4.0F * gap;
        float left = cx - total / 2.0F;
        for (int i = 0; i < 5; i++) {
            float level = playing ? 0.35F + 0.65F * Math.abs((float) Math.sin(this.waveTime * (2.6F + i * 0.55F) + i * 1.7F)) : 0.3F;
            float h = Math.max(barW, maxH * base[i] * level);
            RoundedUtils.drawRoundedRect(left + i * (barW + gap), cy - h / 2.0F, barW, h, 0x80A8A8AC, barW / 2.0F);
        }
    }

    private void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        // no glIsEnabled here: a state query makes the CPU wait for the GPU (a hitch when it is busy)
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();   // the HUD draws with culling on
    }

    /** Rewind / fast-forward glyph: two triangles side by side. */
    private void drawDoubleTriangle(float cx, float cy, float width, float height, boolean pointLeft, int color) {
        float half = width / 2.0F;
        float one = width / 2.0F;
        for (int i = 0; i < 2; i++) {
            float start = cx - half + i * one;
            if (pointLeft) {
                drawTriangle(start + one, cy - height / 2.0F, start, cy, start + one, cy + height / 2.0F, color);
            } else {
                drawTriangle(start, cy - height / 2.0F, start + one, cy, start, cy + height / 2.0F, color);
            }
        }
    }

    private void drawAirplay(float cx, float cy, float u) {
        int color = 0xFFA0A0A4;
        float centerY = cy - 4.0F * u;
        drawArc(cx, centerY, 26.0F * u, 4.5F * u, 150.0F, 390.0F, color);
        drawArc(cx, centerY, 14.0F * u, 4.5F * u, 150.0F, 390.0F, color);
        drawTriangle(cx - 17.0F * u, cy + 25.0F * u, cx + 17.0F * u, cy + 25.0F * u, cx, cy + 3.0F * u, color);
    }

    private void drawArc(float cx, float cy, float radius, float thickness, float fromDeg, float toDeg, int color) {
        // no glIsEnabled here: a state query makes the CPU wait for the GPU (a hitch when it is busy)
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        int steps = 28;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            GL11.glVertex2f(cx + cos * radius, cy + sin * radius);
            GL11.glVertex2f(cx + cos * (radius - thickness), cy + sin * (radius - thickness));
        }
        GL11.glEnd();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();   // the HUD draws with culling on
    }

    private static void setColor(int color) {
        GlStateManager.color(((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F, ((color >>> 24) & 255) / 255.0F);
    }

    // ------------------------------------------------------------------ position smoothing

    /**
     * Advances with the frame clock and is gently pulled towards the source's position, so the
     * progress bar and the lyric sweep never jitter or run backwards between polls.
     */
    private void updateDisplayPosition(float dt, MediaInfo info) {
        if (info == null) {
            this.displayPos = 0.0D;
            this.displayPosValid = false;
            return;
        }
        double raw = info.positionNow();
        if (!this.displayPosValid || !info.key.equals(this.displayPosKey) || !info.playing) {
            this.displayPos = raw;
            this.displayPosValid = true;
            this.displayPosKey = info.key;
            return;
        }
        double advanced = this.displayPos + dt * 1000.0D;
        double error = raw - advanced;
        if (Math.abs(error) > 1500.0D) advanced = raw;
        else advanced += error * Math.min(1.0D, dt * 4.0D);
        if (info.durationMs > 0L) advanced = Math.min(advanced, (double) info.durationMs);
        this.displayPos = Math.max(0.0D, advanced);
    }

    // ------------------------------------------------------------------ mouse (only while the chat is open)

    private void handleMouse(ScaledResolution resolution) {
        boolean chat = MC.currentScreen instanceof GuiChat;
        if (!chat) {
            this.dragging = false;
            this.mouseWasDown = false;
            this.hoveredButton = 0;
            return;
        }
        boolean down = Mouse.isButtonDown(0);
        float mouseX = Mouse.getX() * resolution.getScaledWidth() / (float) MC.displayWidth;
        float mouseY = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / (float) MC.displayHeight - 1.0F;
        boolean overWidget = this.lastWidth > 0.0F && inside(mouseX, mouseY, this.lastX, this.lastY, this.lastWidth, this.lastHeight);

        this.hoveredButton = 0;
        if (overWidget) {
            if (hitCircle(mouseX, mouseY, this.prevCx)) this.hoveredButton = 1;
            else if (hitCircle(mouseX, mouseY, this.toggleCx)) this.hoveredButton = 2;
            else if (hitCircle(mouseX, mouseY, this.nextCx)) this.hoveredButton = 3;
        }

        if (down && !this.mouseWasDown && overWidget) {
            if (this.hoveredButton == 1) {
                this.media.control(MediaAction.PREVIOUS);
            } else if (this.hoveredButton == 2) {
                this.media.control(MediaAction.TOGGLE);
            } else if (this.hoveredButton == 3) {
                this.media.control(MediaAction.NEXT);
            } else if (Math.abs(mouseY - this.barCy) <= this.barHit && mouseX >= this.barLeft - 4.0F
                    && mouseX <= this.barRight + 4.0F && this.media.getCurrent() != null
                    && this.media.getCurrent().canSeek && this.media.getCurrent().durationMs > 0L) {
                float fraction = clamp((mouseX - this.barLeft) / (this.barRight - this.barLeft), 0.0F, 1.0F);
                this.media.seek((long) (fraction * this.media.getCurrent().durationMs));
            } else {
                this.dragging = true;
                this.grabX = mouseX - this.lastX;
                this.grabY = mouseY - this.lastY;
            }
        }
        if (this.dragging) {
            if (down) {
                this.offsetX.setValue(Math.round(mouseX - this.grabX - 8.0F));
                this.offsetY.setValue(Math.round(mouseY - this.grabY - 8.0F));
            } else {
                this.dragging = false;
            }
        }
        this.mouseWasDown = down;
    }

    private boolean hitCircle(float mouseX, float mouseY, float centerX) {
        return Math.abs(mouseX - centerX) <= this.controlsHit && Math.abs(mouseY - this.controlsCy) <= this.controlsHit;
    }

    // ------------------------------------------------------------------ lyrics

    private static float lyricScale(float width) {
        return clamp(width * 0.0046F, 0.85F, 1.6F);
    }

    /** Room for one excerpt: at most two wrapped rows. */
    private static float lyricsAreaHeight(float width) {
        float scale = lyricScale(width);
        float fontHeight = ClientFont.getHeight();
        return width * 0.02F + fontHeight * scale * 1.28F * 2.0F + fontHeight * scale * 0.4F;
    }

    private void resetLyricState() {
        this.lyricShown = -1;
        this.lyricPrevious = -1;
        this.lyricTransition = 1.0F;
    }

    /**
     * One lyric excerpt under the card: the line for the current moment of the song, swept from dim grey to
     * white in time with the music (a short purple/pink glow rides the leading edge). When the moment moves
     * on, the old excerpt rises and fades while the new one fades in; nothing else is shown. Plain (untimed)
     * lyrics are spread over the track and also shown one excerpt at a time.
     */
    private void drawLyricsArea(float x, float top, float width, float height, float dt, long rawPosition) {
        Lyrics lyrics = this.media.getLyrics();
        float centerX = x + width * 0.5F;
        float fontHeight = ClientFont.getHeight();
        float scale = lyricScale(width);
        float lineStep = fontHeight * scale * 1.28F;
        float areaWidth = width * 0.92F;
        float wrapWidth = areaWidth / scale;
        float baseY = top + width * 0.02F;

        if (this.lyricsBackground.getValue() && lyrics.state == Lyrics.State.FOUND) {
            BackdropBlur.drawFeatheredHalo(x, top, width, height, 12.0F, 18.0F, 10.0F);
            RoundedUtils.drawRoundedRect(x, top, width, height, 0x38000000, 12.0F);
        }

        if (!lyrics.key.equals(this.lyricsKey)) {
            this.lyricsKey = lyrics.key;
            resetLyricState();
        }
        switch (lyrics.state) {
            case LOADING:
                return;
            case NOT_FOUND:
                if (this.media.getCurrent() != null) drawLyricNote("Lyrics not found", centerX, baseY, areaWidth, scale);
                return;
            case INSTRUMENTAL:
                drawLyricNote("Instrumental", centerX, baseY, areaWidth, scale);
                return;
            case ERROR:
                drawLyricNote("Lyrics unavailable (offline?)", centerX, baseY, areaWidth, scale);
                return;
            default:
                break;
        }

        MediaInfo info = this.media.getCurrent();
        long duration = info == null ? 0L : info.durationMs;
        if (this.timeline == null || this.timelineSource != lyrics || this.timelineDuration != duration) {
            this.timeline = LyricsTimeline.of(lyrics, duration);
            this.timelineSource = lyrics;
            this.timelineDuration = duration;
            resetLyricState();
        }
        List<LyricLine> list = this.timeline.lines();
        if (list.isEmpty()) return;

        long position = rawPosition + this.lyricsOffset.getValue();
        int index = this.timeline.indexAt(position);
        if (index != this.lyricShown) {
            if (this.timeline.sameText(index, this.lyricShown)) {
                this.lyricShown = index;                 // a repeated line: keep it on screen, no animation
            } else {
                this.lyricPrevious = this.lyricShown;
                this.lyricShown = index;
                this.lyricTransition = 0.0F;
            }
        }
        this.lyricTransition = Math.min(1.0F, this.lyricTransition + dt / 0.35F);
        float t = 1.0F - (1.0F - this.lyricTransition) * (1.0F - this.lyricTransition);
        float rise = lineStep * 0.7F;

        if (this.lyricPrevious >= 0 && this.lyricPrevious < list.size() && t < 1.0F) {
            drawExcerpt(list, this.lyricPrevious, Float.POSITIVE_INFINITY, centerX, baseY - rise * t, scale,
                    lineStep, wrapWidth, 1.0F - t);
        }
        if (this.lyricShown >= 0) {
            float front = this.timeline.isEstimated() ? Float.POSITIVE_INFINITY : Float.NaN;
            drawExcerpt(list, this.lyricShown, front, centerX, baseY + rise * (1.0F - t), scale, lineStep, wrapWidth,
                    t, position, duration);
        }
    }

    private void drawExcerpt(List<LyricLine> list, int index, float front, float centerX, float top, float scale,
                             float lineStep, float wrapWidth, float alpha) {
        drawExcerpt(list, index, front, centerX, top, scale, lineStep, wrapWidth, alpha, 0L, 0L);
    }

    /** {@code front} = sweep front in font units; NaN = compute it from the playback position. */
    private void drawExcerpt(List<LyricLine> list, int index, float front, float centerX, float top, float scale,
                             float lineStep, float wrapWidth, float alpha, long position, long duration) {
        if (alpha <= 0.01F) return;
        List<String> parts = wrappedLyric(list, index, wrapWidth);
        float totalBase = 0.0F;
        for (String part : parts) totalBase += ClientFont.getStringWidthFloat(part);
        float frontBase = Float.isNaN(front)
                ? this.timeline.progress(index, position, duration) * (totalBase + LYRIC_SOFT_EDGE)
                : front;
        float consumed = 0.0F;
        for (int k = 0; k < parts.size(); k++) {
            String part = parts.get(k);
            float partBase = ClientFont.getStringWidthFloat(part);
            final float localFront = frontBase - consumed;
            final float a = alpha;
            drawStyledText(part, centerX - partBase * scale * 0.5F, top + k * lineStep, scale,
                    offset -> lyricColor(localFront, offset, a), 0.28F, withAlpha(0x66000000, alpha));
            consumed += partBase;
        }
    }

    private void drawLyricNote(String text, float centerX, float top, float maxWidth, float scale) {
        String fitted = fitText(text, maxWidth, scale);
        float noteWidth = ClientFont.getStringWidthFloat(fitted) * scale;
        drawStyledText(fitted, centerX - noteWidth * 0.5F, top, scale, offset -> COLOR_LYRIC_DIM, 0.2F, 0x66000000);
    }

    /** Dim -> purple -> pink -> white as the sweep front passes a glyph (front/offset in font units). */
    private static int lyricColor(float front, float offset, float alpha) {
        float u = (front - offset) / LYRIC_SOFT_EDGE;
        int color;
        if (u <= 0.0F) color = COLOR_LYRIC_DIM;
        else if (u < 0.35F) color = lerpColor(COLOR_LYRIC_DIM, COLOR_PURPLE, u / 0.35F);
        else if (u < 0.70F) color = lerpColor(COLOR_PURPLE, COLOR_PINK, (u - 0.35F) / 0.35F);
        else if (u < 1.0F) color = lerpColor(COLOR_PINK, COLOR_WHITE, (u - 0.70F) / 0.30F);
        else color = COLOR_WHITE;
        return withAlpha(color, alpha);
    }

    private List<String> wrappedLyric(List<LyricLine> list, int index, float wrapWidth) {
        if (this.wrapSource != list || Math.abs(this.wrapWidthCached - wrapWidth) > 0.5F
                || this.wrapCache.size() != list.size()) {
            this.wrapSource = list;
            this.wrapWidthCached = wrapWidth;
            this.wrapCache = new ArrayList<List<String>>(Collections.<List<String>>nCopies(list.size(), null));
        }
        List<String> cached = this.wrapCache.get(index);
        if (cached == null) {
            cached = wrapLimited(list.get(index).text, wrapWidth, 2);
            this.wrapCache.set(index, cached);
        }
        return cached;
    }

    private List<String> wrapLimited(String text, float maxBaseWidth, int maxLines) {
        String clean = StringUtils.stripControlCodes(text == null ? "" : text).trim();
        List<String> lines = wrapText(clean, maxBaseWidth);
        if (lines.isEmpty()) lines.add("");
        if (lines.size() <= maxLines) return lines;
        List<String> limited = new ArrayList<>(lines.subList(0, maxLines - 1));
        StringBuilder rest = new StringBuilder();
        for (int i = maxLines - 1; i < lines.size(); i++) {
            if (rest.length() > 0) rest.append(' ');
            rest.append(lines.get(i));
        }
        limited.add(fitText(rest.toString(), maxBaseWidth, 1.0F));
        return limited;
    }

    private List<String> wrapText(String text, float maxBaseWidth) {
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && ClientFont.getStringWidthFloat(candidate) > maxBaseWidth) {
                result.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) result.add(line.toString());
        return result;
    }

    // ------------------------------------------------------------------ text / math helpers

    private String fitText(String text, float maxWidth, float scale) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) return "";
        String clean = StringUtils.stripControlCodes(text);
        float maxBaseWidth = maxWidth / scale;
        if (ClientFont.getStringWidthFloat(clean) > maxBaseWidth) {
            clean = ClientFont.trimStringToWidth(clean, Math.max(1, (int) maxBaseWidth - 8)) + "\u2026";
        }
        return clean;
    }

    private void drawScaledText(String text, float x, float y, float scale, int color) {
        if (text == null || text.isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        ClientFont.drawString(text, 0.0F, 0.0F, color);
        GlStateManager.popMatrix();
    }

    private void drawStyledText(String text, float x, float y, float scale,
                                ClickGuiFontRenderer.GlyphColor color, float bold, int shadow) {
        if (text == null || text.isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        ClientFont.drawStringStyled(text, 0.0F, 0.0F, color, bold, shadow, 0.7F);
        GlStateManager.popMatrix();
    }

    private static String formatTime(long milliseconds) {
        long seconds = Math.max(0L, milliseconds) / 1000L;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * t)));
    }

    private static int lerpColor(int from, int to, float t) {
        float k = clamp(t, 0.0F, 1.0F);
        return (lerpChannel(from >>> 24 & 255, to >>> 24 & 255, k) << 24)
                | (lerpChannel(from >> 16 & 255, to >> 16 & 255, k) << 16)
                | (lerpChannel(from >> 8 & 255, to >> 8 & 255, k) << 8)
                | lerpChannel(from & 255, to & 255, k);
    }

    private static int withAlpha(int color, float multiplier) {
        int alpha = Math.round((color >>> 24 & 255) * clamp(multiplier, 0.0F, 1.0F));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
