package crewx.module.modules.misc;

import crewx.gui.ClientFont;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.accountmanager.MushProfileService;
import crewx.clickgui.render.RoundedUtils;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.LoadWorldEvent;
import crewx.events.Render2DEvent;
import crewx.events.TickEvent;
import crewx.module.Module;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.IntProperty;
import crewx.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Overlay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int NAME_WIDTH = 112;
    private static final int HEADER_HEIGHT = 17;
    private static final int ROW_HEIGHT = 17;
    private static final int PANEL_PADDING = 5;
    private static final int COLUMN_GAP = 4;
    private static final int[] COLUMN_WIDTHS = new int[]{NAME_WIDTH, 40, 40, 48, 52, 42, 42, 42, 42, 42};
    private static final String[] COLUMN_NAMES = new String[]{"Players", "Dist.", "K/D", "Wins", "Losses", "WS", "TK/D", "FK/D", "W/L", "Ping"};
    private static final Color TEXT = new Color(226, 231, 238);
    private static final Color MUTED = new Color(154, 166, 184);
    private static final Color RED = new Color(242, 74, 84);
    private static final Color PANEL = new Color(4, 14, 23);
    private static final Color ROW = new Color(8, 27, 40);

    private final Map<String, MushProfileService.OverlayPlayer> players = new HashMap<String, MushProfileService.OverlayPlayer>();
    private final Map<String, Long> requestedAt = new HashMap<String, Long>();
    private final Set<String> failedLookups = new HashSet<String>();
    private final Set<String> knownNames = new HashSet<String>();
    private String serverKey = "";
    private boolean invalidServerNotified;
    private long nextRefresh;
    private float pulse;

    public final FloatProperty scale = new FloatProperty("scale", 0.74F, 0.50F, 1.50F);
    public final IntProperty offX = new IntProperty("position-x", 5, 0, 2000);
    public final IntProperty offY = new IntProperty("position-y", 5, 0, 2000);
    public final IntProperty backgroundAlpha = new IntProperty("background-alpha", 95, 10, 100);
    public final IntProperty refreshInterval = new IntProperty("refresh-interval", 10000, 3000, 60000);
    public final IntProperty maxRows = new IntProperty("max-rows", 12, 1, 20);
    public final FloatProperty highFkdr = new FloatProperty("high-fkdr", 5.0F, 1.0F, 50.0F);
    public final BooleanProperty showAvatar = new BooleanProperty("show-avatar", true);
    public final BooleanProperty showDistance = new BooleanProperty("show-distance", true);
    public final BooleanProperty showPing = new BooleanProperty("show-ping", true);
    public final BooleanProperty showStatus = new BooleanProperty("show-status", true);

    public Overlay() {
        super("Overlay", false);
    }

    @Override
    public void onEnabled() {
        this.players.clear();
        this.requestedAt.clear();
        this.failedLookups.clear();
        this.knownNames.clear();
        this.serverKey = "";
        this.invalidServerNotified = false;
        this.nextRefresh = 0L;
    }

    @Override
    public void onDisabled() {
        this.players.clear();
        this.requestedAt.clear();
        this.failedLookups.clear();
        this.knownNames.clear();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.players.clear();
        this.requestedAt.clear();
        this.failedLookups.clear();
        this.knownNames.clear();
        this.nextRefresh = 0L;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) return;

        String currentServer = this.getServerKey();
        if (!currentServer.equals(this.serverKey)) {
            this.serverKey = currentServer;
            this.players.clear();
            this.requestedAt.clear();
            this.failedLookups.clear();
            this.knownNames.clear();
            this.invalidServerNotified = false;
            this.nextRefresh = 0L;
        }

        if (!this.isMushServer()) {
            if (!this.invalidServerNotified) {
                Notifications.pushRaw("Overlay", "erro: funciona só no mush");
                this.invalidServerNotified = true;
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now < this.nextRefresh) return;
        this.nextRefresh = now + 1000L;
        this.requestPlayers();
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null || !this.isMushServer()) return;

        List<OverlayRow> rows = this.collectRows();
        if (rows.isEmpty()) return;
        int visibleRows = Math.min(this.maxRows.getValue(), rows.size());
        int panelWidth = this.getPanelWidth();
        int panelHeight = PANEL_PADDING + HEADER_HEIGHT + visibleRows * ROW_HEIGHT + PANEL_PADDING;
        ScaledResolution resolution = new ScaledResolution(mc);
        float x = this.offX.getValue();
        float y = this.offY.getValue();
        float renderScale = this.scale.getValue();
        if (x + panelWidth * renderScale > resolution.getScaledWidth()) {
            x = Math.max(0.0F, resolution.getScaledWidth() - panelWidth * renderScale - 4.0F);
        }
        if (y + panelHeight * renderScale > resolution.getScaledHeight()) {
            y = Math.max(0.0F, resolution.getScaledHeight() - panelHeight * renderScale - 4.0F);
        }

        this.pulse += 0.04F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(renderScale, renderScale, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RoundedUtils.drawRoundedRect(0.0F, 0.0F, panelWidth, panelHeight,
                new Color(PANEL.getRed(), PANEL.getGreen(), PANEL.getBlue(), Math.round(255.0F * backgroundAlpha.getValue() / 100.0F)).getRGB(), 5.0F);
        RoundedUtils.drawRoundedOutlinedRect(0.0F, 0.0F, panelWidth, panelHeight,
                new Color(39, 72, 96, 190).getRGB(), 5.0F, 1.0F);
        RenderUtil.drawRect(6.0F, HEADER_HEIGHT - 2.0F, panelWidth - 6.0F, HEADER_HEIGHT - 1.0F,
                new Color(73, 137, 190, 190).getRGB());

        int cursor = PANEL_PADDING;
        for (int i = 0; i < COLUMN_NAMES.length; i++) {
            String header = i == 0 ? COLUMN_NAMES[i] + " " + visibleRows : COLUMN_NAMES[i];
            this.drawCentered(header, cursor, COLUMN_WIDTHS[i], 6.0F, MUTED.getRGB());
            cursor += COLUMN_WIDTHS[i] + COLUMN_GAP;
        }

        for (int i = 0; i < visibleRows; i++) {
            OverlayRow row = rows.get(i);
            float rowY = PANEL_PADDING + HEADER_HEIGHT + i * ROW_HEIGHT;
            if ((i & 1) == 1) {
                RenderUtil.drawRect(4.0F, rowY, panelWidth - 4.0F, rowY + ROW_HEIGHT,
                        new Color(15, 43, 58, Math.round(115.0F * backgroundAlpha.getValue() / 100.0F)).getRGB());
            }
            this.renderRow(row, rowY);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void renderRow(OverlayRow row, float rowY) {
        MushProfileService.OverlayPlayer profile = row.profile;
        boolean highStats = profile != null && profile.getFkdr() >= this.highFkdr.getValue();
        int infoColor = highStats ? RED.getRGB() : TEXT.getRGB();
        int nameColor = row.hasStatus() ? RED.getRGB() : TEXT.getRGB();
        float textY = rowY + 4.0F;
        float nameX = PANEL_PADDING;
        if (this.showAvatar.getValue()) {
            this.drawAvatar(row, (int) nameX, (int) rowY + 1);
            nameX += 18.0F;
        }

        String status = this.showStatus.getValue() ? row.status() : "";
        int statusWidth = status.isEmpty() ? 0 : ClientFont.getStringWidth(" " + status);
        int nameWidth = (int) (NAME_WIDTH - (nameX - PANEL_PADDING) - statusWidth - 2.0F);
        String name = this.trimToWidth(row.name, Math.max(1, nameWidth));
        ClientFont.drawStringWithShadow(name, nameX, textY, nameColor);
        if (!status.isEmpty()) {
            float statusX = nameX + ClientFont.getStringWidth(name) + 2.0F;
            ClientFont.drawStringWithShadow(status, statusX, textY, RED.getRGB());
        }

        int cellX = PANEL_PADDING + COLUMN_WIDTHS[0] + COLUMN_GAP;
        this.drawCentered(this.showDistance.getValue() ? row.distance() : "--", cellX, COLUMN_WIDTHS[1], textY, MUTED.getRGB());
        cellX += COLUMN_WIDTHS[1] + COLUMN_GAP;
        this.drawCentered(profile == null ? "--" : formatRatio(profile.getKd()), cellX, COLUMN_WIDTHS[2], textY, infoColor);
        cellX += COLUMN_WIDTHS[2] + COLUMN_GAP;
        this.drawCentered(profile == null ? "--" : formatValue(profile.getWins()), cellX, COLUMN_WIDTHS[3], textY, infoColor);
        cellX += COLUMN_WIDTHS[3] + COLUMN_GAP;
        this.drawCentered(profile == null ? "--" : formatValue(profile.getLosses()), cellX, COLUMN_WIDTHS[4], textY, infoColor);
        cellX += COLUMN_WIDTHS[4] + COLUMN_GAP;
        this.drawCentered(profile == null ? "--" : formatValue(profile.getWinstreak()), cellX, COLUMN_WIDTHS[5], textY, infoColor);
        cellX += COLUMN_WIDTHS[5] + COLUMN_GAP;
        this.drawCentered(profile == null ? "--" : formatRatio(profile.getTkd()), cellX, COLUMN_WIDTHS[6], textY, infoColor);
        cellX += COLUMN_WIDTHS[6] + COLUMN_GAP;
        this.drawCentered(profile == null ? "--" : formatRatio(profile.getFkdr()), cellX, COLUMN_WIDTHS[7], textY, infoColor);
        cellX += COLUMN_WIDTHS[7] + COLUMN_GAP;
        this.drawCentered(profile == null ? "--" : formatRatio(profile.getWl()), cellX, COLUMN_WIDTHS[8], textY, infoColor);
        cellX += COLUMN_WIDTHS[8] + COLUMN_GAP;
        this.drawCentered(this.showPing.getValue() ? row.ping() : "--", cellX, COLUMN_WIDTHS[9], textY, MUTED.getRGB());
    }

    private void drawAvatar(OverlayRow row, int x, int y) {
        ResourceLocation head = row.profile == null ? null : MushProfileService.getHeadTexture(row.profile.getSkinHash());
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        if (head != null) {
            mc.getTextureManager().bindTexture(head);
            Gui.drawScaledCustomSizeModalRect(x, y, 0.0F, 0.0F, 8, 8, 16, 16, 8.0F, 8.0F);
            return;
        }
        if (row.info.getLocationSkin() != null) {
            mc.getTextureManager().bindTexture(row.info.getLocationSkin());
            Gui.drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, 16, 16, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(x, y, 40.0F, 8.0F, 8, 8, 16, 16, 64.0F, 64.0F);
        }
    }

    private void requestPlayers() {
        Collection<NetworkPlayerInfo> infos = mc.getNetHandler().getPlayerInfoMap();
        long now = System.currentTimeMillis();
        Set<String> visibleNames = new HashSet<String>();
        for (NetworkPlayerInfo info : infos) {
            if (info == null || info.getGameProfile() == null) continue;
            String username = info.getGameProfile().getName();
            String normalized = normalize(username);
            if (normalized.isEmpty() || !this.isVisiblePlayer(username)) continue;
            visibleNames.add(normalized);
            Long last = this.requestedAt.get(normalized);
            if (last != null && now - last < this.refreshInterval.getValue()) continue;
            this.requestedAt.put(normalized, now);
            final String key = normalized;
            final String serverAtRequest = this.serverKey;
            MushProfileService.fetchOverlay(username, new MushProfileService.OverlayCallback() {
                @Override
                public void onResult(MushProfileService.OverlayPlayer player, boolean definitiveFailure) {
                    if (!serverAtRequest.equals(serverKey)) return;
                    if (player != null) {
                        players.put(key, player);
                        failedLookups.remove(key);
                    } else if (definitiveFailure) {
                        players.remove(key);
                        failedLookups.add(key);
                    }
                }
            });
        }
        this.knownNames.retainAll(visibleNames);
        this.requestedAt.keySet().retainAll(visibleNames);
        this.failedLookups.retainAll(visibleNames);
        this.players.keySet().retainAll(visibleNames);
    }

    private List<OverlayRow> collectRows() {
        List<OverlayRow> rows = new ArrayList<OverlayRow>();
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            if (info == null || info.getGameProfile() == null) continue;
            String username = info.getGameProfile().getName();
            if (!this.isVisiblePlayer(username)) continue;
            EntityPlayer entity = mc.theWorld.getPlayerEntityByName(username);
            if (this.isAntiBotPlayer(entity)) continue;
            String key = normalize(username);
            rows.add(new OverlayRow(username, info, entity, players.get(key), failedLookups.contains(key)));
        }
        Collections.sort(rows, new Comparator<OverlayRow>() {
            @Override
            public int compare(OverlayRow first, OverlayRow second) {
                if (first.profile == null && second.profile != null) return 1;
                if (first.profile != null && second.profile == null) return -1;
                if (first.profile != null) {
                    int fkdr = Double.compare(second.profile.getFkdr(), first.profile.getFkdr());
                    if (fkdr != 0) return fkdr;
                }
                return first.name.compareToIgnoreCase(second.name);
            }
        });
        return rows;
    }

    private boolean isVisiblePlayer(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        return username.length() <= 16;
    }

    private boolean isAntiBotPlayer(EntityPlayer entity) {
        if (entity == null || CrewX.moduleManager == null) return false;
        AntiBot antiBot = (AntiBot) CrewX.moduleManager.modules.get(AntiBot.class);
        return antiBot != null && antiBot.isEnabled() && antiBot.isBot(entity);
    }

    private String getServerKey() {
        try {
            if (mc.getCurrentServerData() != null && mc.getCurrentServerData().serverIP != null) {
                return mc.getCurrentServerData().serverIP.toLowerCase(Locale.ROOT);
            }
        }
        catch (Exception ignored) {
        }
        return "singleplayer";
    }

    private boolean isMushServer() {
        return this.serverKey.contains("mush");
    }

    private int getPanelWidth() {
        int width = PANEL_PADDING * 2;
        for (int columnWidth : COLUMN_WIDTHS) width += columnWidth;
        return width + COLUMN_GAP * (COLUMN_WIDTHS.length - 1);
    }

    private void drawCentered(String value, int x, int width, float y, int color) {
        int textWidth = ClientFont.getStringWidth(value);
        ClientFont.drawStringWithShadow(value, x + (width - textWidth) / 2.0F, y, color);
    }

    private String formatValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001D) return String.format(Locale.ROOT, "%.0f", value);
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String formatRatio(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String trimToWidth(String value, int width) {
        if (ClientFont.getStringWidth(value) <= width) return value;
        String result = value;
        while (result.length() > 1 && ClientFont.getStringWidth(result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private final class OverlayRow {
        private final String name;
        private final NetworkPlayerInfo info;
        private final EntityPlayer entity;
        private final MushProfileService.OverlayPlayer profile;
        private final boolean nicked;

        private OverlayRow(String name, NetworkPlayerInfo info, EntityPlayer entity,
                           MushProfileService.OverlayPlayer profile, boolean nicked) {
            this.name = name;
            this.info = info;
            this.entity = entity;
            this.profile = profile;
            this.nicked = nicked;
        }

        private boolean hasStatus() {
            return this.nicked || this.profile != null && this.profile.isBanned();
        }

        private String status() {
            if (this.nicked) return "[NICKED]";
            if (this.profile != null && this.profile.isBanned()) return "[STAFF]";
            return "";
        }

        private String distance() {
            if (this.entity == null) return "--";
            return Math.round(mc.thePlayer.getDistanceToEntity(this.entity)) + "m";
        }

        private String ping() {
            int responseTime = this.info.getResponseTime();
            return responseTime < 0 ? "--" : String.valueOf(responseTime);
        }
    }
}
