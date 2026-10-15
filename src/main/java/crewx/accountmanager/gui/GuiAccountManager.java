package crewx.accountmanager.gui;


import crewx.CrewX;
import crewx.accountmanager.AccountManager;
import crewx.accountmanager.MushProfileService;
import crewx.accountmanager.auth.Account;
import crewx.accountmanager.auth.MicrosoftAuth;
import crewx.accountmanager.auth.SessionManager;
import crewx.accountmanager.utils.Notification;
import crewx.accountmanager.utils.RandomAltName;
import crewx.accountmanager.utils.TextFormatting;
import crewx.clickgui.render.RoundedUtils;
import crewx.gui.ClientFont;
import crewx.gui.CrewXTheme;
import crewx.gui.GuiRender;
import crewx.gui.menu.MenuAvatar;
import crewx.module.Module;
import crewx.module.modules.render.HUD;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Alt Manager in the CrewX style (same dark panels, white type and theme accent as the ClickGUI, over the
 * menu's space background). Only the presentation changed: adding, deleting, reordering (Ctrl+Up/Down),
 * copying (Ctrl+C), logging in (double click / Enter / Login), Browser Login, Mush stats and ban status
 * all use the original logic below.
 */
public class GuiAccountManager extends GuiScreen {
    private static final float ROW_HEIGHT = 40.0F;
    private static final float ROW_GAP = 4.0F;
    private static final float HEADER = 48.0F;
    private static final float FOOTER = 44.0F;
    private static final int PANEL = 0xF20B0B0D;
    private static final int ROW = 0xFF111215;
    private static final int ROW_HOVER = 0xFF16171B;
    private static final int ROW_SELECTED = 0xFF1B1D23;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8C9099;
    private static final int FAINT = 0xFF5D616B;
    private static final long OPEN_MS = 240L;
    private static final long CLOSE_MS = 150L;

    private final GuiScreen previousScreen;

    private Notification notification;
    private int selectedAccount = -1;
    private ExecutorService executor;
    private CompletableFuture<Void> task;
    private final Map<String, MushProfileService.Stats> accountStats = new HashMap<String, MushProfileService.Stats>();

    // presentation state
    private final Map<Account, float[]> rowAnim = new HashMap<Account, float[]>();
    private static final int RANDOM_ALT = 7;
    /** Back is the last one (on the left); the rest line up on the right in this order. */
    private final PillButton[] footer = {
            new PillButton(1, "Add", "Add"), new PillButton(RANDOM_ALT, "Random Alt", "Random"),
            new PillButton(3, "Browser Login", "Browser"), new PillButton(5, "Login", "Login"),
            new PillButton(4, "Delete", "Delete"), new PillButton(6, "Back", "Back")
    };
    private final RandomAltName randomNames = new RandomAltName();
    /** The account just created by Random Alt glows briefly in the list. */
    private Account freshAccount;
    private long freshAt;
    /** When a Random Alt name is being checked (one at a time). */
    private long randomPendingSince;
    private long openedAt;
    private long closingAt;
    private long lastFrame;
    private float scroll;
    private float scrollTarget;
    private int lastClickIndex = -1;
    private long lastClickAt;
    private float panelX;
    private float panelY;
    private float panelW;
    private float panelH;
    private float listY;
    private float listH;

    private static final class PillButton {
        final int id;
        final String fullLabel;
        final String shortLabel;
        String label;
        float x;
        float y;
        float w;
        float h;
        float hover;
        boolean enabled = true;

        PillButton(int id, String label, String shortLabel) {
            this.id = id;
            this.fullLabel = label;
            this.shortLabel = shortLabel;
            this.label = label;
        }

        boolean contains(float mx, float my) {
            return mx >= this.x && mx <= this.x + this.w && my >= this.y && my <= this.y + this.h;
        }
    }

    public GuiAccountManager(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    public GuiAccountManager(GuiScreen previousScreen, Notification notification) {
        this.previousScreen = previousScreen;
        this.notification = notification;
    }

    public GuiScreen getPreviousScreen() {
        return previousScreen;
    }

    @Override
    public void initGui() {
        AccountManager.load();
        refreshCrackedProfiles();
        refreshAccountStats();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        if (this.openedAt == 0L) this.openedAt = System.currentTimeMillis();
        this.closingAt = 0L;
        layout();
    }

    private void layout() {
        this.panelW = Math.min(460.0F, this.width - 32.0F);
        this.panelH = Math.min(this.height - 36.0F, 410.0F);
        this.panelX = (this.width - this.panelW) / 2.0F;
        this.panelY = (this.height - this.panelH) / 2.0F;
        this.listY = this.panelY + HEADER;
        this.listH = this.panelH - HEADER - FOOTER;
        float y = this.panelY + this.panelH - FOOTER + 10.0F;
        float h = 22.0F;
        // narrow panels: shorter labels first, then tighter padding and gaps, so the pills never overlap
        float available = this.panelW - 24.0F;
        boolean compact = footerWidth(false, 24.0F, 6.0F) > available;
        float padding = 24.0F;
        float gap = 6.0F;
        while ((padding > 10.0F || gap > 3.0F) && footerWidth(compact, padding, gap) > available) {
            padding = Math.max(10.0F, padding - 1.0F);
            gap = Math.max(3.0F, gap - 0.25F);
        }
        // Back on the left, actions on the right
        int last = this.footer.length - 1;
        PillButton back = this.footer[last];
        for (PillButton b : this.footer) {
            b.label = compact ? b.shortLabel : b.fullLabel;
            b.w = ClientFont.getStringWidthFloat(b.label) + padding;
            b.h = h;
            b.y = y;
        }
        back.x = this.panelX + 12.0F;
        float right = this.panelX + this.panelW - 12.0F;
        for (int i = last - 1; i >= 0; i--) {
            PillButton b = this.footer[i];
            b.x = right - b.w;
            right = b.x - gap;
        }
    }

    /** Width of the whole footer row (Back + a wider gap + the actions) for the given label set and spacing. */
    private float footerWidth(boolean compact, float padding, float gap) {
        float total = 0.0F;
        for (PillButton b : this.footer) {
            total += ClientFont.getStringWidthFloat(compact ? b.shortLabel : b.fullLabel) + padding;
        }
        return total + gap * (this.footer.length - 2) + gap * 2.0F;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public void updateScreen() {
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
        this.lastFrame = now;
        float open = ease(Math.min(1.0F, (now - this.openedAt) / (float) OPEN_MS));
        if (this.closingAt != 0L) {
            float closeT = Math.min(1.0F, (now - this.closingAt) / (float) CLOSE_MS);
            open = 1.0F - ease(closeT);
            if (closeT >= 1.0F) {
                mc.displayGuiScreen(previousScreen);
                return;
            }
        }
        layout();
        int accent = accent();

        CrewXTheme.drawBackground(this.width, this.height);
        GuiRender.rect(0.0, 0.0, this.width, this.height, GuiRender.alpha(0x59000000, open));

        GlStateManager.pushMatrix();
        float lift = 10.0F * (1.0F - open);
        GlStateManager.translate(0.0F, lift, 0.0F);

        GuiRender.shadow(this.panelX, this.panelY, this.panelW, this.panelH, 12.0);
        RoundedUtils.drawRoundedRect(this.panelX, this.panelY, this.panelW, this.panelH, GuiRender.alpha(PANEL, open), 12.0F);
        GuiRender.roundedOutline(this.panelX, this.panelY, this.panelW, this.panelH, 12.0, 1.0F, GuiRender.alpha(0x16FFFFFF, open));

        drawHeader(open, accent);
        drawList(mouseX, mouseY - lift, dt, open, accent);
        drawFooter(mouseX, mouseY - lift, dt, open, accent);
        drawToast(open);

        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawHeader(float open, int accent) {
        float x = this.panelX + 16.0F;
        float y = this.panelY + 13.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(1.25F, 1.25F, 1.0F);
        ClientFont.drawStringStyled("Alt Manager", 0.0F, 0.0F, offset -> GuiRender.alpha(TEXT, open), 0.3F, 0, 0.0F);
        GlStateManager.popMatrix();
        int count = AccountManager.accounts.size();
        GuiRender.text(count + (count == 1 ? " account" : " accounts"), x, y + 15.0F, GuiRender.alpha(MUTED, open));

        // the account in use, on the right
        String current = SessionManager.get().getUsername();
        float avatar = 22.0F;
        float nameW = ClientFont.getStringWidthFloat(current);
        float right = this.panelX + this.panelW - 16.0F;
        float ax = right - avatar;
        float ay = this.panelY + 12.0F;
        GuiRender.textRight(current, ax - 8.0F, ay + 2.0F, GuiRender.alpha(TEXT, open));
        GuiRender.textRight("In use", ax - 8.0F, ay + 13.0F, GuiRender.alpha(MUTED, open));
        RoundedUtils.drawRoundedRect(ax - 1.0F, ay - 1.0F, avatar + 2.0F, avatar + 2.0F, GuiRender.alpha(accent, 0.65F * open), (avatar + 2.0F) / 2.0F);
        MenuAvatar.draw(current, MenuAvatar.findAccount(current), ax, ay, avatar, avatar / 2.0F, open);

        GuiRender.rect(this.panelX + 12.0F, this.panelY + HEADER - 6.0F, this.panelW - 24.0F, 1.0, GuiRender.alpha(0x10FFFFFF, open));
    }

    private void drawList(float mouseX, float mouseY, float dt, float open, int accent) {
        int size = AccountManager.accounts.size();
        float content = size * (ROW_HEIGHT + ROW_GAP);
        float maxScroll = Math.max(0.0F, content - this.listH + 4.0F);
        this.scrollTarget = Math.max(0.0F, Math.min(maxScroll, this.scrollTarget));
        this.scroll += (this.scrollTarget - this.scroll) * (1.0F - (float) Math.exp(-dt * 14.0F));
        if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) this.scroll = this.scrollTarget;

        float listX = this.panelX + 10.0F;
        float listW = this.panelW - 20.0F - (maxScroll > 0.0F ? 6.0F : 0.0F);
        if (size == 0) {
            String empty = "No accounts yet";
            String hint = "Use Add, Random Alt or Browser Login to create one";
            float cy = this.listY + this.listH / 2.0F;
            GuiRender.textCentered(empty, this.panelX + this.panelW / 2.0F, cy - 10.0F, GuiRender.alpha(TEXT, open));
            GuiRender.textCentered(hint, this.panelX + this.panelW / 2.0F, cy + 3.0F, GuiRender.alpha(MUTED, open));
            return;
        }

        GuiRender.scissorStart(this.panelX + 2.0F, this.listY + (10.0F * (1.0F - open)), this.panelW - 4.0F, this.listH);
        boolean listHovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= this.listY && mouseY <= this.listY + this.listH;
        String current = SessionManager.get().getUsername();
        for (int i = 0; i < size; i++) {
            float rowY = this.listY + 2.0F + i * (ROW_HEIGHT + ROW_GAP) - this.scroll;
            if (rowY + ROW_HEIGHT < this.listY - 2.0F || rowY > this.listY + this.listH + 2.0F) continue;
            Account account = AccountManager.accounts.get(i);
            float[] anim = this.rowAnim.get(account);
            if (anim == null) {
                anim = new float[2];
                this.rowAnim.put(account, anim);
            }
            boolean hovered = listHovered && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            anim[0] = approach(anim[0], hovered ? 1.0F : 0.0F, dt, 14.0F);
            anim[1] = approach(anim[1], i == this.selectedAccount ? 1.0F : 0.0F, dt, 14.0F);
            // rows cascade in on open
            float rowIn = ease(Math.max(0.0F, Math.min(1.0F, (System.currentTimeMillis() - this.openedAt - 40L * Math.min(i, 8)) / 260.0F)));
            float a = open * rowIn;
            drawRow(account, i, listX + 6.0F * (1.0F - rowIn), rowY, listW, anim, a, accent,
                    account.getUsername().equals(current), mouseX, mouseY);
        }
        GuiRender.scissorEnd();

        if (maxScroll > 0.0F) {
            float trackX = this.panelX + this.panelW - 12.0F;
            float barH = Math.max(18.0F, this.listH * this.listH / content);
            float barY = this.listY + (this.listH - barH) * (this.scroll / maxScroll);
            RoundedUtils.drawRoundedRect(trackX, this.listY, 3.0F, this.listH, GuiRender.alpha(0x0CFFFFFF, open), 1.5F);
            RoundedUtils.drawRoundedRect(trackX, barY, 3.0F, barH, GuiRender.alpha(0x40FFFFFF, open), 1.5F);
        }
    }

    private void drawRow(Account account, int index, float x, float y, float w, float[] anim, float a, int accent,
                         boolean inUse, float mouseX, float mouseY) {
        int bg = GuiRender.mix(GuiRender.mix(ROW, ROW_HOVER, anim[0]), ROW_SELECTED, anim[1]);
        RoundedUtils.drawRoundedRect(x, y, w, ROW_HEIGHT, GuiRender.alpha(bg, a), 8.0F);
        if (account == this.freshAccount) {
            // freshly generated alt: a soft accent wash that fades out
            float glow = 1.0F - Math.min(1.0F, (System.currentTimeMillis() - this.freshAt) / 1400.0F);
            if (glow > 0.0F) RoundedUtils.drawRoundedRect(x, y, w, ROW_HEIGHT, GuiRender.alpha(accent, 0.22F * glow * glow * a), 8.0F);
        }
        if (anim[1] > 0.01F) {
            GuiRender.roundedOutline(x, y, w, ROW_HEIGHT, 8.0, 1.0F, GuiRender.alpha(accent, 0.55F * anim[1] * a));
            RoundedUtils.drawRoundedRect(x + 3.0F, y + 10.0F, 2.5F, (ROW_HEIGHT - 20.0F) * anim[1], GuiRender.alpha(accent, a), 1.25F);
        }

        float avatar = 26.0F;
        float ax = x + 10.0F;
        float ay = y + (ROW_HEIGHT - avatar) / 2.0F;
        MenuAvatar.draw(account.getUsername(), account, ax, ay, avatar, 6.0F, a);

        String username = StringUtils.isBlank(account.getUsername()) ? "Unknown" : account.getUsername();
        float tx = ax + avatar + 10.0F;
        float nameY = y + 8.0F;
        ClientFont.drawStringStyled(username, tx, nameY, offset -> GuiRender.alpha(TEXT, a), 0.25F, 0, 0.0F);
        float cursor = tx + ClientFont.getStringWidthFloat(username) + 6.0F;
        if (inUse) {
            cursor = pill("In use", cursor, nameY - 1.5F, 0xFF3FB97A, a) + 4.0F;
        }

        String type = account.isCracked() ? "Cracked" : account.isCookie() ? "Token" : "Microsoft";
        int typeColor = account.isCracked() ? 0xFF8C9099 : account.isCookie() ? 0xFFA77BFF : 0xFF4C9AFF;
        float second = y + 22.0F;
        float after = pill(type, tx, second - 1.5F, typeColor, a) + 6.0F;
        String uuid = StringUtils.isBlank(account.getUuid()) ? "UUID unavailable" : account.getUuid();

        // right side: ban status + stats
        float statsW = ClientFont.getStringWidthFloat("Stats") + 16.0F;
        float statsX = x + w - statsW - 10.0F;
        float statsY = y + (ROW_HEIGHT - 16.0F) / 2.0F;
        boolean statsHovered = mouseX >= statsX && mouseX <= statsX + statsW && mouseY >= statsY && mouseY <= statsY + 16.0F;
        RoundedUtils.drawRoundedRect(statsX, statsY, statsW, 16.0F, GuiRender.alpha(statsHovered ? 0xFF2A2C33 : 0xFF1C1E23, a), 8.0F);
        GuiRender.textCentered("Stats", statsX + statsW / 2.0F, statsY + (16.0F - ClientFont.getHeight()) / 2.0F + 0.5F,
                GuiRender.alpha(statsHovered ? TEXT : 0xFFC9CBD1, a));
        float limit = statsX - 8.0F;
        if (isBanned(account)) {
            float bw = ClientFont.getStringWidthFloat("Banned") + 12.0F;
            pill("Banned", statsX - bw - 6.0F, statsY + 1.5F, 0xFFFF5A5A, a);
            limit = statsX - bw - 12.0F;
        }
        GuiRender.text(GuiRender.trim(uuid, (int) Math.max(10.0F, limit - after)), after, second, GuiRender.alpha(FAINT, a));
    }

    /** Small rounded tag (tinted background, coloured text); returns the x after it. */
    private float pill(String text, float x, float y, int color, float a) {
        float w = ClientFont.getStringWidthFloat(text) + 10.0F;
        float h = ClientFont.getHeight() + 3.0F;
        RoundedUtils.drawRoundedRect(x, y, w, h, GuiRender.alpha(color, 0.16F * a), h / 2.0F);
        GuiRender.text(text, x + 5.0F, y + 1.5F, GuiRender.alpha(color, a));
        return x + w;
    }

    private void drawFooter(float mouseX, float mouseY, float dt, float open, int accent) {
        boolean selected = this.selectedAccount >= 0 && this.selectedAccount < AccountManager.accounts.size();
        boolean busy = this.task != null && !this.task.isDone();
        for (PillButton b : this.footer) {
            b.enabled = (b.id != 5 && b.id != 4) || (selected && !(b.id == 5 && busy));
            if (b.id == RANDOM_ALT) b.enabled = this.randomPendingSince == 0L || System.currentTimeMillis() - this.randomPendingSince >= 8000L;
            boolean hovered = b.enabled && b.contains(mouseX, mouseY);
            b.hover = approach(b.hover, hovered ? 1.0F : 0.0F, dt, 14.0F);
            boolean primary = b.id == 5;
            int base = primary ? (accent & 0xFFFFFF) | 0xFF000000 : 0xFF1A1B20;
            int fill = primary ? GuiRender.mix(GuiRender.alpha(base, 0.85F), base, b.hover)
                    : GuiRender.mix(base, 0xFF26282F, b.hover);
            float a = open * (b.enabled ? 1.0F : 0.45F);
            RoundedUtils.drawRoundedRect(b.x, b.y, b.w, b.h, GuiRender.alpha(fill, a), b.h / 2.0F);
            if (!primary) GuiRender.roundedOutline(b.x, b.y, b.w, b.h, b.h / 2.0F, 1.0F, GuiRender.alpha(0x14FFFFFF, a));
            int textColor = b.id == 4 && b.enabled ? GuiRender.mix(0xFFE6E7EA, 0xFFFF6B6B, b.hover) : 0xFFFFFFFF;
            if (primary && luminance(base) > 0.6F) textColor = 0xFF0B0B0D;   // light theme colour: dark label
            GuiRender.textCentered(b.label, b.x + b.w / 2.0F, b.y + (b.h - ClientFont.getHeight()) / 2.0F + 0.5F,
                    GuiRender.alpha(textColor, a));
        }
    }

    private void drawToast(float open) {
        if (notification == null || notification.isExpired()) return;
        // the messages carry Minecraft colour codes: draw them as coloured runs in the client font
        List<String> parts = new ArrayList<String>();
        List<Integer> colors = new ArrayList<Integer>();
        splitColors(notification.getMessage(), parts, colors);
        float textW = 0.0F;
        for (String part : parts) textW += ClientFont.getStringWidthFloat(part);
        float w = Math.min(this.panelW - 40.0F, textW + 22.0F);
        float h = ClientFont.getHeight() + 9.0F;
        float x = this.panelX + (this.panelW - w) / 2.0F;
        float y = this.panelY + this.panelH - FOOTER - h - 8.0F;
        GuiRender.shadow(x, y, w, h, h / 2.0F);
        RoundedUtils.drawRoundedRect(x, y, w, h, GuiRender.alpha(0xF0151619, open), h / 2.0F);
        GuiRender.roundedOutline(x, y, w, h, h / 2.0F, 1.0F, GuiRender.alpha(0x1AFFFFFF, open));
        float cursor = x + 11.0F;
        float limit = x + w - 11.0F;
        for (int i = 0; i < parts.size() && cursor < limit; i++) {
            String part = GuiRender.trim(parts.get(i), (int) (limit - cursor));
            GuiRender.text(part, cursor, y + 5.0F, GuiRender.alpha(colors.get(i), open));
            cursor += ClientFont.getStringWidthFloat(part);
        }
    }

    /** "\u00a7aDone \u00a7f(x)" -> ["Done ", "(x)"] with their colours (format codes are dropped). */
    private static void splitColors(String message, List<String> parts, List<Integer> colors) {
        int color = 0xFFFFFFFF;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '\u00a7' && i + 1 < message.length()) {
                int next = colorOf(message.charAt(++i), color);
                if (run.length() > 0) {
                    parts.add(run.toString());
                    colors.add(color);
                    run.setLength(0);
                }
                color = next;
                continue;
            }
            run.append(c);
        }
        if (run.length() > 0) {
            parts.add(run.toString());
            colors.add(color);
        }
    }

    private static int colorOf(char code, int current) {
        switch (Character.toLowerCase(code)) {
            case 'a': return 0xFF5BD68A;   // green
            case 'c': return 0xFFFF6B6B;   // red
            case 'e': return 0xFFFFD166;   // yellow
            case '7': return 0xFF9A9EA8;   // gray
            case '8': return 0xFF6B6F78;
            case 'b': return 0xFF6ED3FF;
            case 'd': return 0xFFD08CFF;
            case '6': return 0xFFFFB347;
            case 'f':
            case 'r': return 0xFFFFFFFF;
            default: return current;       // other colours / formatting: keep the current colour
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            this.scrollTarget -= Math.signum(wheel) * (ROW_HEIGHT + ROW_GAP) * 1.5F;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0 || this.closingAt != 0L) return;
        for (PillButton b : this.footer) {
            if (b.enabled && b.contains(mouseX, mouseY)) {
                click();
                action(b.id);
                return;
            }
        }
        float listX = this.panelX + 10.0F;
        float listW = this.panelW - 20.0F;
        if (mouseX < listX || mouseX > listX + listW || mouseY < this.listY || mouseY > this.listY + this.listH) return;
        int index = (int) Math.floor((mouseY - this.listY - 2.0F + this.scroll) / (ROW_HEIGHT + ROW_GAP));
        if (index < 0 || index >= AccountManager.accounts.size()) return;
        float rowY = this.listY + 2.0F + index * (ROW_HEIGHT + ROW_GAP) - this.scroll;
        if (mouseY > rowY + ROW_HEIGHT) return;
        float statsW = ClientFont.getStringWidthFloat("Stats") + 16.0F;
        float statsX = listX + listW - statsW - 10.0F - (maxScroll() > 0.0F ? 6.0F : 0.0F);
        if (mouseX >= statsX && mouseX <= statsX + statsW) {
            click();
            mc.displayGuiScreen(new GuiMushStats(GuiAccountManager.this, AccountManager.accounts.get(index).getUsername()));
            return;
        }
        long now = System.currentTimeMillis();
        boolean doubleClick = index == this.lastClickIndex && now - this.lastClickAt < 350L;
        this.lastClickIndex = index;
        this.lastClickAt = now;
        selectedAccount = index;
        updateScreen();
        if (doubleClick) {
            loginSelected();
        }
    }

    private float maxScroll() {
        float content = AccountManager.accounts.size() * (ROW_HEIGHT + ROW_GAP);
        return Math.max(0.0F, content - this.listH + 4.0F);
    }

    private void action(int id) {
        switch (id) {
            case 1:
                mc.displayGuiScreen(new GuiAddAccount(this));
                break;
            case RANDOM_ALT:
                addRandomAlt();
                break;
            case 3:
                mc.displayGuiScreen(new GuiMicrosoftAuth(previousScreen));
                break;
            case 4:
                deleteSelected();
                break;
            case 5:
                loginSelected();
                break;
            case 6:
                close();
                break;
            default:
                break;
        }
    }

    private void close() {
        if (this.closingAt == 0L) this.closingAt = System.currentTimeMillis();
    }

    /**
     * Random Alt: generates a natural-looking name that is free in the list and adds it the same way "Add" adds a
     * cracked account (offline, no credentials, nothing is authenticated). The new alt is selected; Login uses it.
     * A name that already belongs to a registered Mush player is skipped (it would take that player's profile);
     * when the lookup is not reachable, only the local list is checked.
     */
    private void addRandomAlt() {
        if (this.randomPendingSince != 0L && System.currentTimeMillis() - this.randomPendingSince < 8000L) return;
        this.randomPendingSince = System.currentTimeMillis();
        tryRandomAlt(0);
    }

    private void tryRandomAlt(final int attempt) {
        final String name = this.randomNames.next(this::isNameInList);
        if (name == null) {
            this.randomPendingSince = 0L;
            notification = new Notification(TextFormatting.translate("&cCould not find a free name, try again.&r"), 3000L);
            return;
        }
        notification = new Notification(TextFormatting.translate("&7Generating a random alt...&r"), -1L);
        MushProfileService.lookup(name, new MushProfileService.Callback() {
            @Override
            public void onResult(MushProfileService.Profile profile) {
                if (profile != null || isNameInList(name)) {
                    // taken by a registered player (or added meanwhile): try another name
                    if (attempt < 5) {
                        tryRandomAlt(attempt + 1);
                    } else {
                        randomPendingSince = 0L;
                        notification = new Notification(TextFormatting.translate("&cCould not find a free name, try again.&r"), 3000L);
                    }
                    return;
                }
                randomPendingSince = 0L;
                addGeneratedAlt(name);
            }
        });
    }

    private void addGeneratedAlt(String name) {
        Account account = Account.cracked(name);
        AccountManager.accounts.add(account);
        AccountManager.save();
        this.selectedAccount = AccountManager.accounts.size() - 1;
        this.freshAccount = account;
        this.freshAt = System.currentTimeMillis();
        ensureVisible();
        notification = new Notification(TextFormatting.translate(
                "&aRandom alt added: &f" + name + " &7(cracked, not logged in yet)&r"), 3500L);
    }

    /** Any account in the list already using this name (case-insensitive, like Minecraft usernames). */
    private boolean isNameInList(String name) {
        for (Account existing : AccountManager.accounts) {
            if (name.equalsIgnoreCase(existing.getUsername())) return true;
        }
        return false;
    }

    private void click() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    private void ensureVisible() {
        if (this.selectedAccount < 0) return;
        float top = this.selectedAccount * (ROW_HEIGHT + ROW_GAP);
        if (top < this.scrollTarget) this.scrollTarget = top;
        else if (top + ROW_HEIGHT + 4.0F > this.scrollTarget + this.listH) this.scrollTarget = top + ROW_HEIGHT + 4.0F - this.listH;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_UP && selectedAccount > 0) {
            --selectedAccount;
            if (isCtrlKeyDown()) {
                Collections.swap(AccountManager.accounts, selectedAccount, selectedAccount + 1);
                AccountManager.save();
            }
            ensureVisible();
            updateScreen();
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN && selectedAccount < AccountManager.accounts.size() - 1) {
            ++selectedAccount;
            if (isCtrlKeyDown()) {
                Collections.swap(AccountManager.accounts, selectedAccount, selectedAccount - 1);
                AccountManager.save();
            }
            ensureVisible();
            updateScreen();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            loginSelected();
            return;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            deleteSelected();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        if (isKeyComboCtrlC(keyCode) && selectedAccount >= 0) {
            setClipboardString(AccountManager.accounts.get(selectedAccount).getUsername());
        }
    }

    private int accent() {
        Module module = CrewX.moduleManager == null ? null : CrewX.moduleManager.modules.get(HUD.class);
        if (module instanceof HUD) {
            int rgb = ((HUD) module).getColor(System.currentTimeMillis()).getRGB();
            // very light theme colours (e.g. white) would vanish on the white text: keep a visible tint
            return rgb | 0xFF000000;
        }
        return CrewXTheme.ACCENT;
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

    // ------------------------------------------------------------------ account logic (unchanged)

    private void deleteSelected() {
        if (selectedAccount < 0 || selectedAccount >= AccountManager.accounts.size()) {
            return;
        }
        AccountManager.accounts.remove(selectedAccount);
        if (selectedAccount >= AccountManager.accounts.size()) {
            selectedAccount = AccountManager.accounts.size() - 1;
        }
        AccountManager.save();
        updateScreen();
    }

    private void loginSelected() {
        if (selectedAccount < 0 || selectedAccount >= AccountManager.accounts.size()
                || (task != null && !task.isDone())) {
            return;
        }
        Account account = AccountManager.accounts.get(selectedAccount);
        if (account.isCracked()) {
            SessionManager.set(new Session(account.getUsername(), account.getUuid(), "0", "mojang"));
            showSuccess(account.getUsername());
            return;
        }
        if (account.isCookie()) {
            SessionManager.set(new Session(account.getUsername(), account.getUuid(), account.getAccessToken(), "mojang"));
            showSuccess(account.getUsername());
            return;
        }
        loginMicrosoft(account);
    }

    private void loginMicrosoft(final Account account) {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
        final String username = StringUtils.isBlank(account.getUsername()) ? "???" : account.getUsername();
        final AtomicReference<String> refreshToken = new AtomicReference<String>("");
        final AtomicReference<String> accessToken = new AtomicReference<String>("");
        notification = new Notification(TextFormatting.translate(String.format(
                "&7Fetching your Minecraft profile... (%s)&r", username
        )), -1L);
        MicrosoftAuth.CLIENT_ID = account.getClientId();
        MicrosoftAuth.SCOPE = account.getScope();
        task = MicrosoftAuth.login(account.getAccessToken(), executor)
                .handle((session, error) -> {
                    if (session != null) {
                        account.setUsername(session.getUsername());
                        account.setUuid(session.getPlayerID());
                        AccountManager.save();
                        SessionManager.set(session);
                        showSuccess(account.getUsername());
                        return true;
                    }
                    return false;
                })
                .thenComposeAsync(completed -> {
                    if (completed) {
                        throw new NoSuchElementException();
                    }
                    notification = new Notification(TextFormatting.translate(String.format(
                            "&7Refreshing Microsoft access tokens... (%s)&r", username
                    )), -1L);
                    return MicrosoftAuth.refreshMSAccessTokens(account.getRefreshToken(), executor);
                })
                .thenComposeAsync(msAccessTokens -> {
                    notification = new Notification(TextFormatting.translate(String.format(
                            "&7Acquiring Xbox access token... (%s)&r", username
                    )), -1L);
                    refreshToken.set(msAccessTokens.get("refresh_token"));
                    return MicrosoftAuth.acquireXboxAccessToken(msAccessTokens.get("access_token"), executor);
                })
                .thenComposeAsync(xboxAccessToken -> {
                    notification = new Notification(TextFormatting.translate(String.format(
                            "&7Acquiring Xbox XSTS token... (%s)&r", username
                    )), -1L);
                    return MicrosoftAuth.acquireXboxXstsToken(xboxAccessToken, executor);
                })
                .thenComposeAsync(xboxXstsData -> {
                    notification = new Notification(TextFormatting.translate(String.format(
                            "&7Acquiring Minecraft access token... (%s)&r", username
                    )), -1L);
                    return MicrosoftAuth.acquireMCAccessToken(xboxXstsData.get("Token"), xboxXstsData.get("uhs"), executor);
                })
                .thenComposeAsync(mcToken -> {
                    notification = new Notification(TextFormatting.translate(String.format(
                            "&7Fetching your Minecraft profile... (%s)&r", username
                    )), -1L);
                    accessToken.set(mcToken);
                    return MicrosoftAuth.login(mcToken, executor);
                })
                .thenAccept(session -> {
                    account.setRefreshToken(refreshToken.get());
                    account.setAccessToken(accessToken.get());
                    account.setUsername(session.getUsername());
                    account.setUuid(session.getPlayerID());
                    AccountManager.save();
                    SessionManager.set(session);
                    showSuccess(account.getUsername());
                })
                .exceptionally(error -> {
                    if (!(error.getCause() instanceof NoSuchElementException)) {
                        String message = error.getMessage() == null ? "Unable to login" : error.getMessage();
                        notification = new Notification(TextFormatting.translate(String.format(
                                "&c%s (%s)&r", message, username
                        )), 5000L);
                    }
                    return null;
                });
    }

    private void showSuccess(String username) {
        notification = new Notification(TextFormatting.translate(String.format(
                "&aSuccessful login! (%s)&r", username
        )), 3500L);
    }

    private void refreshCrackedProfiles() {
        for (final Account account : AccountManager.accounts) {
            if (!account.isCracked() || StringUtils.isBlank(account.getUsername())) {
                continue;
            }
            MushProfileService.lookup(account.getUsername(), new MushProfileService.Callback() {
                @Override
                public void onResult(MushProfileService.Profile profile) {
                    if (profile == null || !AccountManager.accounts.contains(account) || !account.isCracked()) {
                        return;
                    }
                    if (!profile.getUsername().isEmpty()) {
                        account.setUsername(profile.getUsername());
                    }
                    account.setUuid(profile.getUuid());
                    account.setSkin(profile.getSkinHash(), profile.isSlim());
                    AccountManager.save();
                }
            });
        }
    }

    private void refreshAccountStats() {
        accountStats.clear();
        for (final Account account : AccountManager.accounts) {
            if (StringUtils.isBlank(account.getUsername())) {
                continue;
            }
            MushProfileService.fetchStats(account.getUsername(), new MushProfileService.StatsCallback() {
                @Override
                public void onResult(MushProfileService.Stats stats) {
                    if (stats != null) {
                        accountStats.put(normalizeUsername(account.getUsername()), stats);
                    }
                }
            });
        }
    }

    private boolean isBanned(Account account) {
        MushProfileService.Stats stats = accountStats.get(normalizeUsername(account.getUsername()));
        return stats != null && "Yes".equals(stats.getValue("banned"));
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.toLowerCase();
    }

}
