package crewx.gui.servers;

import crewx.accountmanager.auth.Account;
import crewx.accountmanager.auth.SessionManager;
import crewx.accountmanager.gui.GuiAccountManager;
import crewx.clickgui.render.RoundedUtils;
import crewx.gui.ClientFont;
import crewx.gui.CrewXTheme;
import crewx.gui.FormattedText;
import crewx.gui.GuiRender;
import crewx.gui.menu.MenuAvatar;
import crewx.module.modules.render.GuiModule;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.gui.GuiScreenServerList;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.network.LanServerDetector;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;







public class CrewXServerSelector extends GuiScreen {
    private static final AtomicInteger PINGER_THREADS = new AtomicInteger();
    private static final ScheduledThreadPoolExecutor PINGERS = new ScheduledThreadPoolExecutor(5, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "CrewX Server Pinger #" + PINGER_THREADS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final ResourceLocation UNKNOWN_SERVER = new ResourceLocation("textures/misc/unknown_server.png");
    private static final boolean FORGE = classExists("net.minecraftforge.fml.client.FMLClientHandler");
    private static final int PROTOCOL = 47;
    private static final float ROW_HEIGHT = 42.0F;
    private static final float ROW_GAP = 4.0F;
    private static final long OPEN_MS = 240L;
    private static final long CLOSE_MS = 150L;
    private static final String[] SORT_NAMES = {"Saved", "Ping", "Players"};

    private static final int PANEL = 0xD90B0C0F;
    private static final int ROW = 0xE0111215;
    private static final int ROW_HOVER = 0xE817181C;
    private static final int ROW_SELECTED = 0xF01B1C22;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8C9099;
    private static final int FAINT = 0xFF5D616B;

    private final GuiScreen parent;
    private final OldServerPinger pinger = new OldServerPinger();
    private ServerList servers;
    private LanServerDetector.LanServerList lanList;
    private LanServerDetector.ThreadLanServerFind lanThread;
    private boolean initialized;

    private final List<Row> rows = new ArrayList<Row>();
    private final List<Row> visible = new ArrayList<Row>();
    private final Map<ServerData, Row> rowByServer = new IdentityHashMap<ServerData, Row>();
    private final Map<String, Row> rowByLan = new java.util.HashMap<String, Row>();
    private Row selected;

    private String search = "";
    private boolean searchFocused;
    private boolean onlineOnly;
    private float onlineAnim;
    private int sort;

    private boolean deleting;
    private boolean adding;
    private boolean editing;
    private boolean directConnect;
    private ServerData pending;
    private Row editingRow;

    private long openedAt;
    private long closingAt;
    private long lastFrame;
    private float scroll;
    private float scrollTarget;
    private String status;
    private long statusUntil;


    private float margin;
    private float headerY;
    private float toolbarY;
    private float toolbarH;
    private float listX;
    private float listY;
    private float listW;
    private float listH;
    private float footerY;
    private float searchX;
    private float searchW;
    private float toggleX;
    private float sortX;
    private float sortW;
    private float profileX;
    private float profileW;
    private final Pill[] pills = {
            new Pill(Action.ADD, "Add"), new Pill(Action.DIRECT, "Direct"), new Pill(Action.REFRESH, "Refresh"),
            new Pill(Action.EDIT, "Edit"), new Pill(Action.DELETE, "Delete"), new Pill(Action.JOIN, "Join"),
            new Pill(Action.BACK, "Back")
    };

    private enum Action { ADD, DIRECT, REFRESH, EDIT, DELETE, JOIN, BACK }

    private static final class Pill {
        final Action action;
        final String label;
        float x;
        float y;
        float w;
        float h;
        float hover;
        boolean shown = true;
        boolean enabled = true;

        Pill(Action action, String label) {
            this.action = action;
            this.label = label;
        }

        boolean contains(float mx, float my) {
            return this.shown && mx >= this.x && mx <= this.x + this.w && my >= this.y && my <= this.y + this.h;
        }
    }


    private static final class Row {
        final ServerData data;
        final boolean lan;
        float hover;
        float select;
        float play;
        long lastClick;
        String iconData;
        DynamicTexture icon;
        ResourceLocation iconLocation;
        String motdSource;
        FormattedText motd1;
        FormattedText motd2;
        String nameSource;
        FormattedText name;

        Row(ServerData data, boolean lan) {
            this.data = data;
            this.lan = lan;
        }
    }

    public CrewXServerSelector(GuiScreen parent) {
        this.parent = parent;
        if (FORGE) ForgeHooks.setupServerList();
    }



    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        if (!this.initialized) {
            this.initialized = true;
            this.servers = new ServerList(this.mc);
            this.servers.loadServerList();
            this.lanList = new LanServerDetector.LanServerList();
            rebuildRows();
        }
        if (this.lanThread == null) {

            try {
                this.lanThread = new LanServerDetector.ThreadLanServerFind(this.lanList);
                this.lanThread.start();
            } catch (Exception ignored) {
                this.lanThread = null;
            }
        }
        if (this.openedAt == 0L) this.openedAt = System.currentTimeMillis();
        this.closingAt = 0L;
        layout();
    }

    @Override
    public void updateScreen() {
        if (this.lanList != null && this.lanList.getWasUpdated()) {
            this.lanList.setWasNotUpdated();
            rebuildRows();
        }
        this.pinger.pingPendingNetworks();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (this.lanThread != null) {
            this.lanThread.interrupt();
            this.lanThread = null;
        }
        this.pinger.clearPendingNetworks();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }


    private void rebuildRows() {
        this.rows.clear();
        Map<ServerData, Row> keep = new IdentityHashMap<ServerData, Row>(this.rowByServer);
        this.rowByServer.clear();
        for (int i = 0; i < this.servers.countServers(); i++) {
            ServerData data = this.servers.getServerData(i);
            Row row = keep.get(data);
            if (row == null) row = new Row(data, false);
            this.rowByServer.put(data, row);
            this.rows.add(row);
        }
        Map<String, Row> keepLan = new java.util.HashMap<String, Row>(this.rowByLan);
        this.rowByLan.clear();
        if (this.lanList != null) {
            for (LanServerDetector.LanServer lan : this.lanList.getLanServers()) {
                String key = lan.getServerIpPort();
                Row row = keepLan.get(key);
                if (row == null) {
                    ServerData data = new ServerData(lan.getServerMotd(), key, true);
                    data.field_78841_f = true;
                    data.pingToServer = 0L;
                    data.serverMOTD = lan.getServerMotd();
                    row = new Row(data, true);
                }
                this.rowByLan.put(key, row);
                this.rows.add(row);
            }
        }
        if (this.selected != null && !this.rows.contains(this.selected)) this.selected = null;
    }

    private void layout() {
        this.margin = Math.max(10.0F, Math.min(24.0F, this.width * 0.03F));
        this.headerY = this.margin;
        this.toolbarY = this.headerY + 34.0F;
        this.toolbarH = 26.0F;
        this.footerY = this.height - this.margin - 24.0F;
        this.listX = this.margin;
        this.listW = this.width - this.margin * 2.0F;
        this.listY = this.toolbarY + this.toolbarH + 8.0F;
        this.listH = Math.max(20.0F, this.footerY - 8.0F - this.listY);


        this.sortW = ClientFont.getStringWidthFloat("Sort: Players") + 30.0F;
        this.sortX = this.listX + this.listW - 6.0F - this.sortW;
        float toggleW = ClientFont.getStringWidthFloat("Online only") + 34.0F;
        this.toggleX = this.sortX - 10.0F - toggleW;
        this.searchX = this.listX + 6.0F;
        this.searchW = Math.max(60.0F, this.toggleX - 10.0F - this.searchX);

        String name = SessionManager.get().getUsername();
        this.profileW = Math.max(ClientFont.getStringWidthFloat(name), ClientFont.getStringWidthFloat("Microsoft")) + 50.0F;
        this.profileX = this.width - this.margin - this.profileW;


        float h = 22.0F;
        float gap = 6.0F;
        float x = this.listX;
        for (Pill pill : this.pills) {
            pill.w = ClientFont.getStringWidthFloat(pill.label) + 24.0F;
            pill.h = h;
            pill.y = this.footerY + 1.0F;
        }
        Pill back = pill(Action.BACK);
        back.x = this.listX + this.listW - back.w;
        Pill join = pill(Action.JOIN);
        join.x = back.x - gap - join.w;
        for (Pill pill : this.pills) {
            if (pill.action == Action.BACK || pill.action == Action.JOIN) continue;
            pill.x = x;
            x += pill.w + gap;
            if (pill.action == Action.REFRESH) x += gap * 2.0F;
        }
    }

    private Pill pill(Action action) {
        for (Pill pill : this.pills) if (pill.action == action) return pill;
        throw new IllegalArgumentException(action.name());
    }



    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
        this.lastFrame = now;
        float open = ease(Math.min(1.0F, (now - this.openedAt) / (float) OPEN_MS));
        if (this.closingAt != 0L) {
            float t = Math.min(1.0F, (now - this.closingAt) / (float) CLOSE_MS);
            open = 1.0F - ease(t);
            if (t >= 1.0F) {
                this.mc.displayGuiScreen(this.parent);
                return;
            }
        }
        layout();
        int accent = accent();
        CrewXTheme.drawBackground(this.width, this.height, false);
        GuiRender.rect(0.0, 0.0, this.width, this.height, GuiRender.alpha(0x4D000000, open));

        GlStateManager.pushMatrix();
        float lift = 8.0F * (1.0F - open);
        GlStateManager.translate(0.0F, lift, 0.0F);
        float my = mouseY - lift;
        updateVisible();

        drawHeader(mouseX, my, dt, open, accent);
        drawToolbar(mouseX, my, dt, open, accent);
        String tooltip = drawList(mouseX, my, dt, open, accent);
        drawFooter(mouseX, my, dt, open, accent);
        drawStatus(open);
        GlStateManager.popMatrix();
        if (tooltip != null && this.closingAt == 0L) drawTooltip(tooltip, mouseX, mouseY);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawHeader(float mouseX, float mouseY, float dt, float open, int accent) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.margin + 2.0F, this.headerY + 2.0F, 0.0F);
        GlStateManager.scale(1.45F, 1.45F, 1.0F);
        ClientFont.drawStringStyled("Multiplayer", 0.0F, 0.0F, offset -> GuiRender.alpha(TEXT, open), 0.3F, 0, 0.0F);
        GlStateManager.popMatrix();
        int saved = this.servers.countServers();
        int online = 0;
        for (Row row : this.rows) if (!row.lan && row.data.pingToServer >= 0L && row.data.field_78841_f) online++;
        String sub = saved + (saved == 1 ? " server" : " servers") + "  ·  " + online + " online"
                + (this.rowByLan.isEmpty() ? "" : "  ·  " + this.rowByLan.size() + " LAN");
        GuiRender.text(sub, this.margin + 2.0F, this.headerY + 18.0F, GuiRender.alpha(MUTED, open));


        String name = SessionManager.get().getUsername();
        Account account = MenuAvatar.findAccount(name);
        String token = SessionManager.get().getToken();
        boolean premium = account != null ? !account.isCracked() && !account.isCookie()
                : token != null && token.length() > 2;
        String kind = account == null ? (premium ? "Premium" : "Offline")
                : account.isCracked() ? "Cracked" : account.isCookie() ? "Token" : "Microsoft";
        float x = this.profileX;
        float y = this.headerY;
        float h = 28.0F;
        boolean hover = mouseX >= x && mouseX <= x + this.profileW && mouseY >= y && mouseY <= y + h;
        RoundedUtils.drawRoundedRect(x, y, this.profileW, h, GuiRender.alpha(hover ? 0xE61A1B20 : PANEL, open), 8.0F);
        GuiRender.roundedOutline(x, y, this.profileW, h, 8.0, 1.0F, GuiRender.alpha(hover ? GuiRender.alpha(accent, 0.6F) : 0x16FFFFFF, open));
        MenuAvatar.draw(name, account, x + 5.0F, y + 4.0F, 20.0F, 5.0F, open);
        GuiRender.text(name, x + 31.0F, y + 5.0F, GuiRender.alpha(TEXT, open));
        GuiRender.text(kind, x + 31.0F, y + 15.5F, GuiRender.alpha(premium ? 0xFF5BD68A : MUTED, open));
    }

    private void drawToolbar(float mouseX, float mouseY, float dt, float open, int accent) {
        float x = this.listX;
        float y = this.toolbarY;
        RoundedUtils.drawRoundedRect(x, y, this.listW, this.toolbarH, GuiRender.alpha(PANEL, open), 9.0F);
        GuiRender.roundedOutline(x, y, this.listW, this.toolbarH, 9.0, 1.0F, GuiRender.alpha(0x12FFFFFF, open));


        float sy = y + 4.0F;
        float sh = this.toolbarH - 8.0F;
        boolean searchHover = mouseX >= this.searchX && mouseX <= this.searchX + this.searchW && mouseY >= sy && mouseY <= sy + sh;
        RoundedUtils.drawRoundedRect(this.searchX, sy, this.searchW, sh,
                GuiRender.alpha(this.searchFocused ? 0xFF16171B : searchHover ? 0xFF131418 : 0xFF0F1013, open), 6.0F);
        if (this.searchFocused) {
            GuiRender.rect(this.searchX + 6.0, sy + sh - 1.0, this.searchW - 12.0, 1.0, GuiRender.alpha(accent, open));
        }
        drawSearchGlyph(this.searchX + 10.0F, sy + sh / 2.0F, GuiRender.alpha(MUTED, open));
        float tx = this.searchX + 20.0F;
        float ty = sy + (sh - ClientFont.getHeight()) / 2.0F + 0.5F;
        if (this.search.isEmpty() && !this.searchFocused) {
            GuiRender.text("Search servers...", tx, ty, GuiRender.alpha(FAINT, open));
        } else {
            String shown = GuiRender.trimEnd(this.search, (int) (this.searchW - 32.0F));
            GuiRender.text(shown, tx, ty, GuiRender.alpha(TEXT, open));
            if (this.searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                GuiRender.rect(tx + ClientFont.getStringWidthFloat(shown) + 1.0, ty - 0.5, 1.0, ClientFont.getHeight() + 1.0,
                        GuiRender.alpha(TEXT, open));
            }
        }


        this.onlineAnim = approach(this.onlineAnim, this.onlineOnly ? 1.0F : 0.0F, dt, 16.0F);
        float switchY = y + (this.toolbarH - 10.0F) / 2.0F;
        RoundedUtils.drawRoundedRect(this.toggleX, switchY, 20.0F, 10.0F,
                GuiRender.alpha(GuiRender.mix(0xFF2A2B31, accent | 0xFF000000, this.onlineAnim), open), 5.0F);
        RoundedUtils.drawRoundedRect(this.toggleX + 1.0F + this.onlineAnim * 10.0F, switchY + 1.0F, 8.0F, 8.0F,
                GuiRender.alpha(knob(accent, this.onlineAnim), open), 4.0F);
        GuiRender.text("Online only", this.toggleX + 26.0F, y + (this.toolbarH - ClientFont.getHeight()) / 2.0F + 0.5F,
                GuiRender.alpha(this.onlineOnly ? TEXT : 0xFFC9CBD1, open));


        float sortY = y + 4.0F;
        float sortH = this.toolbarH - 8.0F;
        boolean sortHover = mouseX >= this.sortX && mouseX <= this.sortX + this.sortW && mouseY >= sortY && mouseY <= sortY + sortH;
        RoundedUtils.drawRoundedRect(this.sortX, sortY, this.sortW, sortH,
                GuiRender.alpha(GuiRender.mix(accent | 0xFF000000, 0xFFFFFFFF, sortHover ? 0.12F : 0.0F), 0.9F * open), 6.0F);
        int sortText = luminance(accent) > 0.6F ? 0xFF0B0B0D : 0xFFFFFFFF;
        GuiRender.text("Sort: " + SORT_NAMES[this.sort], this.sortX + 9.0F, sortY + (sortH - ClientFont.getHeight()) / 2.0F + 0.5F,
                GuiRender.alpha(sortText, open));
        drawChevron(this.sortX + this.sortW - 11.0F, sortY + sortH / 2.0F, GuiRender.alpha(sortText, open));
    }


    private String drawList(float mouseX, float mouseY, float dt, float open, int accent) {
        RoundedUtils.drawRoundedRect(this.listX, this.listY, this.listW, this.listH, GuiRender.alpha(PANEL, open), 10.0F);
        GuiRender.roundedOutline(this.listX, this.listY, this.listW, this.listH, 10.0, 1.0F, GuiRender.alpha(0x12FFFFFF, open));

        float content = this.visible.size() * (ROW_HEIGHT + ROW_GAP);
        float inner = this.listH - 12.0F;
        float maxScroll = Math.max(0.0F, content - inner);
        this.scrollTarget = Math.max(0.0F, Math.min(maxScroll, this.scrollTarget));
        this.scroll += (this.scrollTarget - this.scroll) * (1.0F - (float) Math.exp(-dt * 14.0F));
        if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) this.scroll = this.scrollTarget;

        if (this.visible.isEmpty()) {
            boolean filtered = !this.search.isEmpty() || this.onlineOnly;
            String title = this.rows.isEmpty() ? "No servers yet" : "No servers match";
            String hint = this.rows.isEmpty() ? "Use Add to save a server or Direct to join one"
                    : filtered ? "Change the search or turn off Online only" : "";
            float cy = this.listY + this.listH / 2.0F;
            GuiRender.textCentered(title, this.listX + this.listW / 2.0F, cy - 10.0F, GuiRender.alpha(TEXT, open));
            GuiRender.textCentered(hint, this.listX + this.listW / 2.0F, cy + 3.0F, GuiRender.alpha(MUTED, open));
            return null;
        }

        String tooltip = null;
        float rowX = this.listX + 6.0F;
        float rowW = this.listW - 12.0F - (maxScroll > 0.0F ? 6.0F : 0.0F);
        boolean listHovered = mouseX >= this.listX && mouseX <= this.listX + this.listW && mouseY >= this.listY && mouseY <= this.listY + this.listH;
        GuiRender.scissorStart(this.listX + 2.0F, this.listY + 2.0F, this.listW - 4.0F, this.listH - 4.0F);
        long now = System.currentTimeMillis();
        for (int i = 0; i < this.visible.size(); i++) {
            Row row = this.visible.get(i);
            float y = this.listY + 6.0F + i * (ROW_HEIGHT + ROW_GAP) - this.scroll;
            if (y + ROW_HEIGHT < this.listY || y > this.listY + this.listH) {
                row.hover = 0.0F;
                continue;
            }
            ensurePinged(row);
            boolean hovered = listHovered && mouseY >= y && mouseY <= y + ROW_HEIGHT && mouseX >= rowX && mouseX <= rowX + rowW;
            row.hover = approach(row.hover, hovered ? 1.0F : 0.0F, dt, 14.0F);
            row.select = approach(row.select, row == this.selected ? 1.0F : 0.0F, dt, 14.0F);
            float rowIn = ease(Math.max(0.0F, Math.min(1.0F, (now - this.openedAt - 35L * Math.min(i, 8)) / 260.0F)));
            String tip = drawRow(row, rowX + 6.0F * (1.0F - rowIn), y, rowW, open * rowIn, accent, mouseX, mouseY, hovered, dt);
            if (tip != null) tooltip = tip;
        }
        GuiRender.scissorEnd();

        if (maxScroll > 0.0F) {
            float trackX = this.listX + this.listW - 8.0F;
            float trackY = this.listY + 6.0F;
            float barH = Math.max(18.0F, inner * inner / content);
            float barY = trackY + (inner - barH) * (this.scroll / maxScroll);
            RoundedUtils.drawRoundedRect(trackX, trackY, 3.0F, inner, GuiRender.alpha(0x0CFFFFFF, open), 1.5F);
            RoundedUtils.drawRoundedRect(trackX, barY, 3.0F, barH, GuiRender.alpha(accent, 0.75F * open), 1.5F);
        }
        return tooltip;
    }

    private String drawRow(Row row, float x, float y, float w, float a, int accent, float mouseX, float mouseY,
                           boolean hovered, float dt) {
        ServerData data = row.data;
        int bg = GuiRender.mix(GuiRender.mix(ROW, ROW_HOVER, row.hover), ROW_SELECTED, row.select);
        RoundedUtils.drawRoundedRect(x, y, w, ROW_HEIGHT, GuiRender.alpha(bg, a), 8.0F);
        if (row.select > 0.01F) {
            GuiRender.roundedOutline(x, y, w, ROW_HEIGHT, 8.0, 1.0F, GuiRender.alpha(accent, 0.55F * row.select * a));
            RoundedUtils.drawRoundedRect(x + 3.0F, y + 11.0F, 2.5F, (ROW_HEIGHT - 22.0F) * row.select, GuiRender.alpha(accent, a), 1.25F);
        } else if (row.hover > 0.01F) {
            GuiRender.roundedOutline(x, y, w, ROW_HEIGHT, 8.0, 1.0F, GuiRender.alpha(0x1CFFFFFF, row.hover * a));
        }


        float icon = 30.0F;
        float ix = x + 9.0F;
        float iy = y + (ROW_HEIGHT - icon) / 2.0F - 2.0F;
        prepareIcon(row);
        MenuAvatar.drawRoundedTexture(row.icon != null ? row.iconLocation : UNKNOWN_SERVER, ix, iy, icon, 6.0F, a);
        String ping = pingLabel(data, row.lan);
        int pingColor = pingColor(data, row.lan);
        float pw = ClientFont.getStringWidthFloat(ping) * 0.8F + 7.0F;
        float ph = 8.5F;
        float px = ix + (icon - pw) / 2.0F;
        float py = iy + icon - ph / 2.0F;
        RoundedUtils.drawRoundedRect(px - 0.5F, py - 0.5F, pw + 1.0F, ph + 1.0F, GuiRender.alpha(0xFF0E0F12, a), (ph + 1.0F) / 2.0F);
        RoundedUtils.drawRoundedRect(px, py, pw, ph, GuiRender.alpha(GuiRender.mix(0xFF101114, pingColor, 0.85F), a), ph / 2.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(px + 3.5F, py + (ph - ClientFont.getHeight() * 0.8F) / 2.0F + 0.3F, 0.0F);
        GlStateManager.scale(0.8F, 0.8F, 1.0F);
        GuiRender.text(ping, 0.0, 0.0, GuiRender.alpha(luminance(pingColor) > 0.55F ? 0xFF0B0B0D : 0xFFFFFFFF, a));
        GlStateManager.popMatrix();


        float pr = 9.0F;
        float pcx = x + w - 18.0F;
        float pcy = y + ROW_HEIGHT / 2.0F;
        boolean playHover = (mouseX - pcx) * (mouseX - pcx) + (mouseY - pcy) * (mouseY - pcy) <= (pr + 2.0F) * (pr + 2.0F);
        row.play = approach(row.play, playHover ? 1.0F : 0.0F, dt, 16.0F);
        if (row.play > 0.01F) {
            RoundedUtils.drawRoundedRect(pcx - pr, pcy - pr, pr * 2.0F, pr * 2.0F, GuiRender.alpha(accent, 0.9F * row.play * a), pr);
        }
        GuiRender.circleOutline(pcx, pcy, pr, 1.0F, GuiRender.alpha(GuiRender.mix(0x66FFFFFF, accent | 0xFF000000, Math.max(row.play, row.select)), a));
        int triangle = row.play > 0.5F && luminance(accent) > 0.6F ? 0xFF0B0B0D : 0xFFFFFFFF;
        drawPlayTriangle(pcx + 1.0F, pcy, 3.6F, GuiRender.alpha(triangle, a * (0.75F + 0.25F * Math.max(row.play, row.hover))));


        float tx = ix + icon + 10.0F;
        float right = pcx - pr - 10.0F;
        if (!data.serverName.equals(row.nameSource)) {
            row.nameSource = data.serverName;
            row.name = FormattedText.of(data.serverName, 0xFFFFFF);
        }
        float nameY = y + 6.0F;
        float nameMax = Math.max(30.0F, (right - tx) * 0.45F);
        row.name.draw(tx, nameY, nameMax, a);
        float cursor = tx + Math.min(row.name.width, nameMax) + 6.0F;
        String tooltip = null;
        boolean incompatible = !row.lan && data.field_78841_f && data.pingToServer >= 0L && data.version != PROTOCOL;
        if (row.lan) {
            cursor = chip("LAN", cursor, nameY - 1.0F, 0xFF6ED3FF, a) + 4.0F;
        } else if (data.pingToServer >= 0L && data.field_78841_f) {
            String players = FormattedText.strip(data.populationInfo).trim();
            if (!players.isEmpty()) {
                float start = cursor;
                cursor = chip(players + " players", cursor, nameY - 1.0F, 0xFFC9CBD1, a) + 4.0F;
                if (data.playerList != null && !data.playerList.isEmpty() && mouseX >= start && mouseX <= cursor
                        && mouseY >= nameY - 1.0F && mouseY <= nameY + ClientFont.getHeight() + 2.0F) {
                    tooltip = data.playerList;
                }
            }
            String version = FormattedText.strip(data.gameVersion).trim();
            if (!version.isEmpty() && cursor < right - 20.0F) {
                float start = cursor;
                String shown = GuiRender.trim(version, (int) Math.max(20.0F, right - cursor - 8.0F));
                cursor = chip(shown, cursor, nameY - 1.0F, incompatible ? 0xFFFF6B6B : 0xFF8C9099, a) + 4.0F;
                if (incompatible && mouseX >= start && mouseX <= cursor && mouseY >= nameY - 1.0F && mouseY <= nameY + ClientFont.getHeight() + 2.0F) {
                    tooltip = data.version > PROTOCOL ? "Client out of date!" : "Server out of date!";
                }
            }
        }


        String motd = data.serverMOTD == null ? "" : data.serverMOTD;
        if (!motd.equals(row.motdSource)) {
            row.motdSource = motd;
            String[] lines = motd.split("\n", 3);
            row.motd1 = FormattedText.of(lines.length > 0 ? lines[0].trim() : "", 0x8C9099);
            row.motd2 = FormattedText.of(lines.length > 1 ? lines[1].trim() : "", 0x8C9099);
        }
        float motdW = right - tx;
        if (!row.lan && data.pingToServer == -2L) {
            GuiRender.text("Pinging" + dots(), tx, y + 19.0F, GuiRender.alpha(FAINT, a));
        } else if (row.lan) {
            GuiRender.text(data.serverIP, tx, y + 19.0F, GuiRender.alpha(MUTED, a));
            GuiRender.text("Local network world", tx, y + 29.0F, GuiRender.alpha(FAINT, a));
        } else {
            row.motd1.draw(tx, y + 19.0F, motdW, a);
            row.motd2.draw(tx, y + 29.0F, motdW, a * 0.85F);
        }
        if (playHover) tooltip = "Join " + FormattedText.strip(data.serverName);
        else if (hovered && mouseX >= ix && mouseX <= ix + icon && mouseY >= iy && mouseY <= iy + icon + 4.0F) tooltip = data.serverIP;
        return tooltip;
    }


    private float chip(String text, float x, float y, int color, float a) {
        float w = ClientFont.getStringWidthFloat(text) * 0.85F + 9.0F;
        float h = ClientFont.getHeight() * 0.85F + 3.5F;
        RoundedUtils.drawRoundedRect(x, y, w, h, GuiRender.alpha(color, 0.14F * a), h / 2.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 4.5F, y + 1.9F, 0.0F);
        GlStateManager.scale(0.85F, 0.85F, 1.0F);
        GuiRender.text(text, 0.0, 0.0, GuiRender.alpha(color, a));
        GlStateManager.popMatrix();
        return x + w;
    }

    private void drawFooter(float mouseX, float mouseY, float dt, float open, int accent) {
        boolean savedSelected = this.selected != null && !this.selected.lan;
        for (Pill pill : this.pills) {
            switch (pill.action) {
                case EDIT:
                case DELETE:
                    pill.shown = true;
                    pill.enabled = savedSelected;
                    break;
                case JOIN:
                    pill.shown = true;
                    pill.enabled = this.selected != null;
                    break;
                default:
                    pill.shown = true;
                    pill.enabled = true;
                    break;
            }
            boolean hovered = pill.enabled && pill.contains(mouseX, mouseY);
            pill.hover = approach(pill.hover, hovered ? 1.0F : 0.0F, dt, 14.0F);
            boolean primary = pill.action == Action.JOIN;
            int base = primary ? accent | 0xFF000000 : 0xFF1A1B20;
            int fill = primary ? GuiRender.mix(GuiRender.alpha(base, 0.85F), base, pill.hover) : GuiRender.mix(base, 0xFF26282F, pill.hover);
            float a = open * (pill.enabled ? 1.0F : 0.4F);
            RoundedUtils.drawRoundedRect(pill.x, pill.y, pill.w, pill.h, GuiRender.alpha(fill, a), pill.h / 2.0F);
            if (!primary) GuiRender.roundedOutline(pill.x, pill.y, pill.w, pill.h, pill.h / 2.0F, 1.0F, GuiRender.alpha(0x14FFFFFF, a));
            int textColor = pill.action == Action.DELETE && pill.enabled ? GuiRender.mix(0xFFE6E7EA, 0xFFFF6B6B, pill.hover) : 0xFFFFFFFF;
            if (primary && luminance(base) > 0.6F) textColor = 0xFF0B0B0D;
            GuiRender.textCentered(pill.label, pill.x + pill.w / 2.0F, pill.y + (pill.h - ClientFont.getHeight()) / 2.0F + 0.5F,
                    GuiRender.alpha(textColor, a));
        }
    }

    private void drawStatus(float open) {
        if (this.status == null || System.currentTimeMillis() > this.statusUntil) return;
        float w = ClientFont.getStringWidthFloat(this.status) + 20.0F;
        float h = ClientFont.getHeight() + 9.0F;
        float x = (this.width - w) / 2.0F;
        float y = this.footerY - h - 10.0F;
        GuiRender.shadow(x, y, w, h, h / 2.0F);
        RoundedUtils.drawRoundedRect(x, y, w, h, GuiRender.alpha(0xF0151619, open), h / 2.0F);
        GuiRender.textCentered(this.status, x + w / 2.0F, y + 5.0F, GuiRender.alpha(TEXT, open));
    }

    private void drawTooltip(String text, int mouseX, int mouseY) {
        String[] lines = text.split("\n");
        List<FormattedText> parsed = new ArrayList<FormattedText>(lines.length);
        float w = 0.0F;
        for (String line : lines) {
            FormattedText t = FormattedText.of(line, 0xFFFFFF);
            parsed.add(t);
            w = Math.max(w, t.width);
        }
        float lineH = ClientFont.getHeight() + 2.0F;
        float h = parsed.size() * lineH + 6.0F;
        w += 12.0F;
        float x = Math.min(mouseX + 10.0F, this.width - w - 4.0F);
        float y = Math.min(mouseY + 10.0F, this.height - h - 4.0F);
        RoundedUtils.drawRoundedRect(x, y, w, h, 0xF2111216, 5.0F);
        GuiRender.roundedOutline(x, y, w, h, 5.0, 1.0F, 0x22FFFFFF);
        for (int i = 0; i < parsed.size(); i++) parsed.get(i).draw(x + 6.0F, y + 4.0F + i * lineH, w, 1.0F);
    }




    private void ensurePinged(Row row) {
        final ServerData data = row.data;
        if (row.lan || data.field_78841_f) return;
        data.field_78841_f = true;
        data.pingToServer = -2L;
        data.serverMOTD = "";
        data.populationInfo = "";
        PINGERS.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    CrewXServerSelector.this.pinger.ping(data);
                } catch (UnknownHostException e) {
                    data.pingToServer = -1L;
                    data.serverMOTD = EnumChatFormatting.DARK_RED + "Can't resolve hostname";
                } catch (Exception e) {
                    data.pingToServer = -1L;
                    data.serverMOTD = EnumChatFormatting.DARK_RED + "Can't connect to server.";
                }
            }
        });
    }


    private void prepareIcon(Row row) {
        String encoded = row.data.getBase64EncodedIconData();
        if (encoded == null ? row.iconData == null : encoded.equals(row.iconData)) return;
        row.iconData = encoded;
        if (row.iconLocation == null || !row.iconLocation.getResourcePath().equals("servers/" + row.data.serverIP + "/icon")) {
            row.iconLocation = new ResourceLocation("servers/" + row.data.serverIP + "/icon");
            row.icon = null;
        }
        if (encoded == null) {
            row.icon = null;
            crewx.util.TextureGuard.forget(row.iconLocation);
            return;
        }
        try {
            byte[] bytes = java.util.Base64.getMimeDecoder().decode(encoded);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() != 64 || image.getHeight() != 64) throw new IOException("icon must be 64x64");
            ITextureObject existing = this.mc.getTextureManager().getTexture(row.iconLocation);
            if (existing instanceof DynamicTexture
                    && existing != net.minecraft.client.renderer.texture.TextureUtil.missingTexture) {
                row.icon = (DynamicTexture) existing;
            } else {
                row.icon = new DynamicTexture(64, 64);
                this.mc.getTextureManager().loadTexture(row.iconLocation, row.icon);
            }
            image.getRGB(0, 0, 64, 64, row.icon.getTextureData(), 0, 64);
            row.icon.updateDynamicTexture();
            crewx.util.TextureGuard.watch(row.iconLocation, row.icon, 64, 64, false);
            if (!row.lan) this.servers.saveServerList();
        } catch (Throwable invalid) {
            row.data.setBase64EncodedIconData(null);
            row.iconData = null;
            row.icon = null;
        }
    }

    private static String pingLabel(ServerData data, boolean lan) {
        if (lan) return "LAN";
        if (!data.field_78841_f || data.pingToServer == -2L) return "...";
        if (data.pingToServer < 0L) return "offline";
        return data.pingToServer + "ms";
    }

    private static int pingColor(ServerData data, boolean lan) {
        if (lan) return 0xFF6ED3FF;
        long ping = data.pingToServer;
        if (!data.field_78841_f || ping == -2L) return 0xFF6B6F78;
        if (ping < 0L) return 0xFFFF5A5A;
        if (ping < 80L) return 0xFF3FB97A;
        if (ping < 150L) return 0xFF8FCB4A;
        if (ping < 300L) return 0xFFF2C14E;
        if (ping < 600L) return 0xFFFF8A3D;
        return 0xFFFF5A5A;
    }

    private static int players(ServerData data) {
        String text = FormattedText.strip(data.populationInfo);
        int slash = text.indexOf('/');
        try {
            return Integer.parseInt((slash < 0 ? text : text.substring(0, slash)).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }


    private void updateVisible() {
        this.visible.clear();
        String query = this.search.trim().toLowerCase(Locale.ROOT);
        for (Row row : this.rows) {
            ServerData data = row.data;
            if (this.onlineOnly && !row.lan && !(data.field_78841_f && data.pingToServer >= 0L)) continue;
            if (!query.isEmpty()) {
                boolean match = FormattedText.strip(data.serverName).toLowerCase(Locale.ROOT).contains(query)
                        || (data.serverIP != null && data.serverIP.toLowerCase(Locale.ROOT).contains(query))
                        || FormattedText.strip(data.serverMOTD).toLowerCase(Locale.ROOT).contains(query);
                if (!match) continue;
            }
            this.visible.add(row);
        }
        if (this.sort == 1) {
            Collections.sort(this.visible, PING_ORDER);
        } else if (this.sort == 2) {
            Collections.sort(this.visible, PLAYER_ORDER);
        }
    }

    private static final Comparator<Row> PING_ORDER = new Comparator<Row>() {
        @Override
        public int compare(Row a, Row b) {
            return Long.compare(pingKey(a), pingKey(b));
        }

        private long pingKey(Row row) {
            if (row.lan) return -1L;
            long ping = row.data.pingToServer;
            if (!row.data.field_78841_f || ping == -2L) return Long.MAX_VALUE - 1L;
            return ping < 0L ? Long.MAX_VALUE : ping;
        }
    };

    private static final Comparator<Row> PLAYER_ORDER = new Comparator<Row>() {
        @Override
        public int compare(Row a, Row b) {
            return Integer.compare(players(b.data), players(a.data));
        }
    };



    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) this.scrollTarget -= Math.signum(wheel) * (ROW_HEIGHT + ROW_GAP) * 1.5F;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0 || this.closingAt != 0L) return;
        for (Pill pill : this.pills) {
            if (pill.enabled && pill.contains(mouseX, mouseY)) {
                click();
                perform(pill.action);
                return;
            }
        }

        if (mouseX >= this.profileX && mouseX <= this.profileX + this.profileW && mouseY >= this.headerY && mouseY <= this.headerY + 28.0F) {
            click();
            this.mc.displayGuiScreen(new GuiAccountManager(this));
            return;
        }
        float sy = this.toolbarY + 4.0F;
        float sh = this.toolbarH - 8.0F;
        this.searchFocused = mouseX >= this.searchX && mouseX <= this.searchX + this.searchW && mouseY >= sy && mouseY <= sy + sh;
        if (this.searchFocused) return;
        if (mouseY >= this.toolbarY && mouseY <= this.toolbarY + this.toolbarH) {
            if (mouseX >= this.toggleX && mouseX <= this.sortX - 10.0F) {
                click();
                this.onlineOnly = !this.onlineOnly;
                this.scrollTarget = 0.0F;
                return;
            }
            if (mouseX >= this.sortX && mouseX <= this.sortX + this.sortW) {
                click();
                this.sort = (this.sort + 1) % SORT_NAMES.length;
                return;
            }
        }
        if (mouseX < this.listX || mouseX > this.listX + this.listW || mouseY < this.listY || mouseY > this.listY + this.listH) return;
        int index = (int) Math.floor((mouseY - this.listY - 6.0F + this.scroll) / (ROW_HEIGHT + ROW_GAP));
        if (index < 0 || index >= this.visible.size()) return;
        float rowY = this.listY + 6.0F + index * (ROW_HEIGHT + ROW_GAP) - this.scroll;
        if (mouseY > rowY + ROW_HEIGHT) return;
        Row row = this.visible.get(index);
        float rowW = this.listW - 12.0F - (maxScroll() > 0.0F ? 6.0F : 0.0F);
        float pcx = this.listX + 6.0F + rowW - 18.0F;
        float pcy = rowY + ROW_HEIGHT / 2.0F;
        this.selected = row;
        if ((mouseX - pcx) * (mouseX - pcx) + (mouseY - pcy) * (mouseY - pcy) <= 11.0F * 11.0F) {
            click();
            join(row);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - row.lastClick < 250L) join(row);
        row.lastClick = now;
    }

    private float maxScroll() {
        return Math.max(0.0F, this.visible.size() * (ROW_HEIGHT + ROW_GAP) - (this.listH - 12.0F));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                this.searchFocused = false;
            } else if (keyCode == Keyboard.KEY_BACK) {
                if (!this.search.isEmpty()) {
                    this.search = isCtrlKeyDown() ? "" : this.search.substring(0, this.search.length() - 1);
                    this.scrollTarget = 0.0F;
                }
            } else if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && this.search.length() < 48) {
                this.search += typedChar;
                this.scrollTarget = 0.0F;
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        if (keyCode == Keyboard.KEY_F && isCtrlKeyDown()) {
            this.searchFocused = true;
            return;
        }
        if (keyCode == Keyboard.KEY_F5) {
            refresh();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.selected != null) join(this.selected);
            return;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (this.selected != null && !this.selected.lan) perform(Action.DELETE);
            return;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            int step = keyCode == Keyboard.KEY_UP ? -1 : 1;
            int index = this.visible.indexOf(this.selected);
            boolean reorder = isShiftKeyDown() && this.selected != null && !this.selected.lan
                    && this.sort == 0 && this.search.isEmpty() && !this.onlineOnly;
            if (reorder) {
                int saved = savedIndex(this.selected);
                int target = saved + step;
                if (saved >= 0 && target >= 0 && target < this.servers.countServers()) {
                    this.servers.swapServers(saved, target);
                    this.servers.saveServerList();
                    rebuildRows();
                    updateVisible();
                    ensureVisible(this.visible.indexOf(this.selected));
                }
                return;
            }
            if (this.visible.isEmpty()) return;
            int next = index < 0 ? (step > 0 ? 0 : this.visible.size() - 1) : Math.max(0, Math.min(this.visible.size() - 1, index + step));
            this.selected = this.visible.get(next);
            ensureVisible(next);
        }
    }

    private void ensureVisible(int index) {
        if (index < 0) return;
        float top = index * (ROW_HEIGHT + ROW_GAP);
        float inner = this.listH - 12.0F;
        if (top < this.scrollTarget) this.scrollTarget = top;
        else if (top + ROW_HEIGHT > this.scrollTarget + inner) this.scrollTarget = top + ROW_HEIGHT - inner;
    }

    private int savedIndex(Row row) {
        for (int i = 0; i < this.servers.countServers(); i++) if (this.servers.getServerData(i) == row.data) return i;
        return -1;
    }

    private void perform(Action action) {
        switch (action) {
            case ADD:
                this.adding = true;
                this.pending = new ServerData(I18n.format("selectServer.defaultName"), "", false);
                this.mc.displayGuiScreen(new GuiScreenAddServer(this, this.pending));
                break;
            case DIRECT:
                this.directConnect = true;
                this.pending = new ServerData(I18n.format("selectServer.defaultName"), "", false);
                this.mc.displayGuiScreen(new GuiScreenServerList(this, this.pending));
                break;
            case REFRESH:
                refresh();
                break;
            case EDIT:
                if (this.selected == null || this.selected.lan) return;
                this.editing = true;
                this.editingRow = this.selected;
                ServerData source = this.selected.data;
                this.pending = new ServerData(source.serverName, source.serverIP, false);
                this.pending.copyFrom(source);
                this.mc.displayGuiScreen(new GuiScreenAddServer(this, this.pending));
                break;
            case DELETE:
                if (this.selected == null || this.selected.lan) return;
                int index = savedIndex(this.selected);
                if (index < 0) return;
                this.deleting = true;
                String name = this.selected.data.serverName;
                this.mc.displayGuiScreen(new GuiYesNo(this, I18n.format("selectServer.deleteQuestion"),
                        "'" + name + "' " + I18n.format("selectServer.deleteWarning"),
                        I18n.format("selectServer.deleteButton"), I18n.format("gui.cancel"), index));
                break;
            case JOIN:
                if (this.selected != null) join(this.selected);
                break;
            case BACK:
                close();
                break;
            default:
                break;
        }
    }


    @Override
    public void confirmClicked(boolean result, int id) {
        if (this.deleting) {
            this.deleting = false;
            if (result && id >= 0 && id < this.servers.countServers()) {
                this.servers.removeServerData(id);
                this.servers.saveServerList();
                this.selected = null;
                rebuildRows();
                showStatus("Server removed");
            }
            this.mc.displayGuiScreen(this);
        } else if (this.directConnect) {
            this.directConnect = false;
            if (result) connect(this.pending);
            else this.mc.displayGuiScreen(this);
        } else if (this.adding) {
            this.adding = false;
            if (result) {
                this.servers.addServerData(this.pending);
                this.servers.saveServerList();
                rebuildRows();
                this.selected = this.rowByServer.get(this.pending);
                this.scrollTarget = Float.MAX_VALUE;
                showStatus("Server added");
            }
            this.mc.displayGuiScreen(this);
        } else if (this.editing) {
            this.editing = false;
            Row row = this.editingRow;
            this.editingRow = null;
            if (result && row != null && savedIndex(row) >= 0) {
                ServerData data = row.data;
                boolean ipChanged = !String.valueOf(data.serverIP).equals(this.pending.serverIP);
                data.serverName = this.pending.serverName;
                data.serverIP = this.pending.serverIP;
                data.copyFrom(this.pending);
                if (ipChanged) {
                    data.field_78841_f = false;
                    data.setBase64EncodedIconData(null);
                }
                this.servers.saveServerList();
                rebuildRows();
            }
            this.mc.displayGuiScreen(this);
        }
    }

    private void join(Row row) {
        if (row == null) return;
        this.selected = row;
        connect(row.lan ? new ServerData(row.data.serverName, row.data.serverIP, true) : row.data);
    }

    private void connect(ServerData data) {
        if (FORGE) ForgeHooks.connect(this, data);
        else this.mc.displayGuiScreen(new GuiConnecting(this, this.mc, data));
    }


    private void refresh() {
        this.pinger.clearPendingNetworks();
        this.servers.loadServerList();
        for (int i = 0; i < this.servers.countServers(); i++) this.servers.getServerData(i).field_78841_f = false;
        this.rowByServer.clear();
        this.selected = null;
        rebuildRows();
        showStatus("Refreshing servers...");
    }

    private void close() {
        if (this.closingAt == 0L) this.closingAt = System.currentTimeMillis();
    }

    private void showStatus(String text) {
        this.status = text;
        this.statusUntil = System.currentTimeMillis() + 1800L;
    }

    private void click() {
        this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }



    private static void drawPlayTriangle(float cx, float cy, float size, int color) {
        triangle(cx - size * 0.8F, cy - size, cx - size * 0.8F, cy + size, cx + size, cy, color);
    }

    private static void drawChevron(float cx, float cy, int color) {
        triangle(cx - 3.0F, cy - 1.5F, cx, cy + 1.8F, cx + 3.0F, cy - 1.5F, color);
    }

    private static void drawSearchGlyph(float cx, float cy, int color) {
        GuiRender.circleOutline(cx - 0.8, cy - 0.8, 3.2, 1.3F, color);
        GuiRender.rect(cx + 1.6, cy + 1.6, 1.4, 1.4, color);
        GuiRender.rect(cx + 2.6, cy + 2.6, 1.3, 1.3, color);
    }

    private static void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION);
        wr.pos(x1, y1, 0.0D).endVertex();
        wr.pos(x2, y2, 0.0D).endVertex();
        wr.pos(x3, y3, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static String dots() {
        int n = (int) (System.currentTimeMillis() / 350L % 4L);
        return n == 0 ? "" : n == 1 ? "." : n == 2 ? ".." : "...";
    }

    private static int knob(int accent, float on) {
        return luminance(accent) > 0.72F ? GuiRender.mix(0xFFFFFFFF, 0xFF141416, on) : 0xFFFFFFFF;
    }

    private static int accent() {
        try {
            return GuiModule.getAccent().getRGB() | 0xFF000000;
        } catch (Throwable ignored) {
            return CrewXTheme.ACCENT;
        }
    }

    private static float luminance(int rgb) {
        return (0.2126F * (rgb >> 16 & 255) + 0.7152F * (rgb >> 8 & 255) + 0.0722F * (rgb & 255)) / 255.0F;
    }

    private static float ease(float t) {
        float inv = 1.0F - Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - inv * inv * inv;
    }

    private static float approach(float current, float target, float dt, float speed) {
        float next = current + (target - current) * (1.0F - (float) Math.exp(-dt * speed));
        return Math.abs(target - next) < 0.002F ? target : next;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, CrewXServerSelector.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }


    private static final class ForgeHooks {
        static void setupServerList() {
            net.minecraftforge.fml.client.FMLClientHandler.instance().setupServerList();
        }

        static void connect(GuiScreen screen, ServerData data) {
            net.minecraftforge.fml.client.FMLClientHandler.instance().connectToServer(screen, data);
        }
    }
}
