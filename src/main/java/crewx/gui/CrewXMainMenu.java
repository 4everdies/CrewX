package crewx.gui;

import crewx.accountmanager.AccountManager;
import crewx.accountmanager.auth.Account;
import crewx.accountmanager.auth.SessionManager;
import crewx.accountmanager.gui.GuiAccountManager;
import crewx.clickgui.render.RoundedUtils;
import crewx.gui.menu.MainMenuLayout;
import crewx.gui.menu.MenuAvatar;
import crewx.gui.menu.MenuClock;
import crewx.gui.menu.MenuIcons;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;

import java.io.IOException;

/**
 * CrewX main menu (Jello-style): a large live clock, the logged-in profile and a row of round buttons over
 * the parallax space background. Used both by the Forge build (GuiMainMenu is swapped for this screen) and
 * by the injected build (which already swaps any title screen for it).
 */
public final class CrewXMainMenu extends GuiScreen {
    /** Kept for the injected Badlion path, which draws CrewX buttons on other clients' menus. */
    public static final class MenuButton extends GuiButton {
        private final int crewxWidth;
        private final int crewxHeight;

        public MenuButton(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label);
            this.crewxWidth = width;
            this.crewxHeight = height;
        }

        public int menuWidth() { return crewxWidth; }
        public int menuHeight() { return crewxHeight; }
    }

    private static final int SINGLEPLAYER = 1;
    private static final int MULTIPLAYER = 2;
    private static final int ALTS = 3;
    private static final int OPTIONS = 4;
    private static final int QUIT = 5;
    private static final int CIRCLE = 0xFF0E0F12;
    private static final int CIRCLE_HOVER = 0xFF1C1E24;

    private static final class RoundButton {
        final int action;
        final String label;
        final String icon;
        final boolean small;
        final long delay;
        float cx;
        float cy;
        float radius;
        float hover;
        float press;

        RoundButton(int action, String label, String icon, boolean small, long delay) {
            this.action = action;
            this.label = label;
            this.icon = icon;
            this.small = small;
            this.delay = delay;
        }

        boolean contains(float x, float y) {
            float dx = x - this.cx;
            float dy = y - this.cy;
            float r = this.radius + 3.0F;
            return dx * dx + dy * dy <= r * r;
        }
    }

    private final RoundButton[] buttons = {
            new RoundButton(SINGLEPLAYER, "Singleplayer", MenuIcons.SINGLEPLAYER, false, 260L),
            new RoundButton(MULTIPLAYER, "Multiplayer", MenuIcons.MULTIPLAYER, false, 320L),
            new RoundButton(ALTS, "Alts", MenuIcons.ALTS, false, 380L),
            new RoundButton(OPTIONS, "Options", MenuIcons.OPTIONS, true, 440L),
            new RoundButton(QUIT, "Quit", MenuIcons.QUIT, true, 480L),
    };
    private final long openedAt = System.currentTimeMillis();
    private MainMenuLayout layout;
    private long lastFrame;
    private float profileHover;
    private float interfaceHover;
    private float interfaceSwitch = 1.0F;
    private long interfaceOffAt;
    private Account cachedAccount;
    private String cachedAccountName;

    @Override
    public void initGui() {
        this.buttonList.clear();
        if (AccountManager.accounts.isEmpty()) AccountManager.load();
        this.cachedAccountName = null;
        float widestLabel = 0.0F;
        for (RoundButton button : this.buttons) {
            if (!button.small) widestLabel = Math.max(widestLabel, ClientFont.getStringWidthFloat(button.label) * 0.92F);
        }
        this.layout = new MainMenuLayout(this.width, this.height, ClientFont.getHeight(), widestLabel + 14.0F);
        int main = 0;
        for (RoundButton button : this.buttons) {
            if (button.small) {
                int index = button.action == QUIT ? 0 : 1;
                button.cx = this.layout.smallCenterX[index];
                button.cy = this.layout.smallCenterY;
                button.radius = this.layout.smallRadius;
            } else {
                button.cx = this.layout.buttonCenterX(main++, 3);
                button.cy = this.layout.buttonCenterY;
                button.radius = this.layout.buttonRadius;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.layout == null) initGui();
        long now = System.currentTimeMillis();
        float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
        this.lastFrame = now;
        long elapsed = now - this.openedAt;

        CrewXTheme.drawBackground(this.width, this.height, false);
        drawBranding();

        // clock
        float clockIn = appear(elapsed, 80L);
        MenuClock.draw(MenuClock.currentText(), this.width / 2.0F, this.layout.clockTop - 10.0F * (1.0F - clockIn),
                this.layout.clockCap, GuiRender.alpha(0xF2F3F4F6, clockIn));

        drawProfile(mouseX, mouseY, dt, appear(elapsed, 180L));
        drawInterfaceToggle(mouseX, mouseY, dt, appear(elapsed, 500L));
        if (this.interfaceOffAt != 0L && now - this.interfaceOffAt > 180L) {
            // let the switch slide first, then hand over to Minecraft's own menu
            this.mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
            return;
        }

        for (RoundButton button : this.buttons) {
            drawButton(button, mouseX, mouseY, dt, appear(elapsed, button.delay));
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Discord link top-left; the credits only where they do not run into the button labels. */
    private void drawBranding() {
        GuiRender.text("discord.gg/crackcrew", 10.0, 10.0, 0x99C7CBD6);
        String credits = "Made by Crowly, Forever & Shinra";
        float scale = 0.85F;
        float creditsRight = 10.0F + ClientFont.getStringWidthFloat(credits) * scale;
        float labelsLeft = this.layout.buttonCenterX(0, 3)
                - ClientFont.getStringWidthFloat(this.buttons[0].label) * 0.46F;
        float y = this.height - 8.0F - ClientFont.getHeight() * scale;
        if (creditsRight + 10.0F < labelsLeft || y + ClientFont.getHeight() < this.layout.labelY) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(10.0F, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            ClientFont.drawString(credits, 0.0F, 0.0F, 0x667A7F8C, false);
            GlStateManager.popMatrix();
        }
    }

    // "Background": a small switch in the bottom-right corner; off = Minecraft's own menus
    private static final String INTERFACE_LABEL = "Background";

    private float interfaceX() {
        return this.width - 10.0F - interfaceWidth();
    }

    private float interfaceY() {
        return this.height - 10.0F - 16.0F;
    }

    private float interfaceWidth() {
        return ClientFont.getStringWidthFloat(INTERFACE_LABEL) * 0.9F + 38.0F;
    }

    private boolean isOverInterfaceToggle(int mouseX, int mouseY) {
        return mouseX >= interfaceX() && mouseX <= interfaceX() + interfaceWidth()
                && mouseY >= interfaceY() && mouseY <= interfaceY() + 16.0F;
    }

    private void drawInterfaceToggle(int mouseX, int mouseY, float dt, float in) {
        if (in <= 0.0F) return;
        float x = interfaceX();
        float y = interfaceY();
        float w = interfaceWidth();
        float h = 16.0F;
        this.interfaceHover = approach(this.interfaceHover, isOverInterfaceToggle(mouseX, mouseY) ? 1.0F : 0.0F, dt, 12.0F);
        this.interfaceSwitch = approach(this.interfaceSwitch, this.interfaceOffAt == 0L ? 1.0F : 0.0F, dt, 18.0F);
        RoundedUtils.drawRoundedRect(x, y, w, h, GuiRender.alpha(GuiRender.mix(0x8C0E0F12, 0xB8191A1F, this.interfaceHover), in), h / 2.0F);
        GuiRender.roundedOutline(x, y, w, h, h / 2.0F, 1.0F, GuiRender.alpha(0x16FFFFFF, in));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 8.0F, y + (h - ClientFont.getHeight() * 0.9F) / 2.0F + 0.5F, 0.0F);
        GlStateManager.scale(0.9F, 0.9F, 1.0F);
        ClientFont.drawString(INTERFACE_LABEL, 0.0F, 0.0F,
                GuiRender.alpha(GuiRender.mix(0xFFC9CBD1, 0xFFFFFFFF, this.interfaceHover), in), false);
        GlStateManager.popMatrix();
        float sw = 18.0F;
        float sh = 9.0F;
        float sx = x + w - sw - 5.0F;
        float sy = y + (h - sh) / 2.0F;
        int accent = crewx.module.modules.render.GuiModule.getAccent().getRGB() | 0xFF000000;
        RoundedUtils.drawRoundedRect(sx, sy, sw, sh, GuiRender.alpha(GuiRender.mix(0xFF34363C, accent, this.interfaceSwitch), in), sh / 2.0F);
        boolean lightAccent = (0.2126F * (accent >> 16 & 255) + 0.7152F * (accent >> 8 & 255) + 0.0722F * (accent & 255)) / 255.0F > 0.72F;
        int knob = lightAccent ? GuiRender.mix(0xFFFFFFFF, 0xFF141416, this.interfaceSwitch) : 0xFFFFFFFF;
        RoundedUtils.drawRoundedRect(sx + 1.0F + this.interfaceSwitch * (sw - sh), sy + 1.0F, sh - 2.0F, sh - 2.0F,
                GuiRender.alpha(knob, in), (sh - 2.0F) / 2.0F);
    }

    private void drawProfile(int mouseX, int mouseY, float dt, float in) {
        if (in <= 0.0F) return;
        String name = SessionManager.get().getUsername();
        if (!name.equals(this.cachedAccountName)) {
            this.cachedAccountName = name;
            this.cachedAccount = MenuAvatar.findAccount(name);
        }
        Session session = SessionManager.get();
        String token = session.getToken();
        boolean online = token != null && !token.isEmpty() && !"0".equals(token) && !"-".equals(token);
        String status = online ? "Online" : "Offline";

        float size = this.layout.avatarSize;
        float nameW = ClientFont.getStringWidthFloat(name);
        float statusW = ClientFont.getStringWidthFloat(status) * 0.92F;
        float gap = 7.0F;
        float total = size + gap + nameW + 4.0F + statusW;
        float x = this.width / 2.0F - total / 2.0F;
        float y = this.layout.profileCenterY - size / 2.0F + 6.0F * (1.0F - in);

        boolean hovered = mouseX >= x - 6 && mouseX <= x + total + 8 && mouseY >= y - 4 && mouseY <= y + size + 4;
        this.profileHover = approach(this.profileHover, hovered ? 1.0F : 0.0F, dt, 10.0F);
        if (this.profileHover > 0.01F) {
            // the profile opens the Alt Manager too: hint it with a soft pill
            RoundedUtils.drawRoundedRect(x - 5.0F, y - 3.0F, total + 14.0F, size + 6.0F,
                    GuiRender.alpha(0x66141519, in * this.profileHover), (size + 6.0F) / 2.0F);
        }
        RoundedUtils.drawRoundedRect(x - 1.0F, y - 1.0F, size + 2.0F, size + 2.0F,
                GuiRender.alpha(0x40000000, in), (size + 2.0F) / 2.0F);
        MenuAvatar.draw(name, this.cachedAccount, x, y, size, size / 2.0F, in);

        float textY = y + (size - ClientFont.getHeight()) / 2.0F + 0.5F;
        float tx = x + size + gap;
        ClientFont.drawStringStyled(name, tx, textY, offset -> GuiRender.alpha(0xFFFFFFFF, in), 0.3F,
                GuiRender.alpha(0x50000000, in), 0.6F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(tx + nameW + 4.0F, textY + ClientFont.getHeight() * 0.06F, 0.0F);
        GlStateManager.scale(0.92F, 0.92F, 1.0F);
        ClientFont.drawString(status, 0.0F, 0.0F, GuiRender.alpha(online ? 0xFF8C9099 : 0xFF6E727B, in * 0.85F), false);
        GlStateManager.popMatrix();
    }

    private void drawButton(RoundButton button, int mouseX, int mouseY, float dt, float in) {
        if (in <= 0.0F) return;
        boolean hovered = button.contains(mouseX, mouseY);
        button.hover = approach(button.hover, hovered ? 1.0F : 0.0F, dt, 12.0F);
        button.press = approach(button.press, 0.0F, dt, 9.0F);
        float scale = (0.85F + 0.15F * in) * (1.0F + 0.07F * button.hover - 0.08F * button.press);
        float r = button.radius * scale;
        float cy = button.cy + 8.0F * (1.0F - in);

        // soft shadow, glassy dark disc, faint rim that brightens on hover
        RoundedUtils.drawRoundedRect(button.cx - r - 2.0F, cy - r - 1.0F, r * 2.0F + 4.0F, r * 2.0F + 4.0F,
                GuiRender.alpha(0x38000000, in), r + 2.0F);
        int fill = GuiRender.mix(CIRCLE, CIRCLE_HOVER, button.hover);
        RoundedUtils.drawRoundedRect(button.cx - r, cy - r, r * 2.0F, r * 2.0F,
                GuiRender.alpha(fill, (0.82F + 0.13F * button.hover) * in), r);
        if (button.hover > 0.01F) {
            RoundedUtils.drawRoundedRect(button.cx - r, cy - r, r * 2.0F, r * 2.0F,
                    GuiRender.alpha(0x10FFFFFF, button.hover * in), r);
        }
        float iconSize = r * (button.small ? 1.05F : 1.0F);
        MenuIcons.draw(button.icon, button.cx, cy, iconSize,
                GuiRender.alpha(GuiRender.mix(0xFFD5D7DC, 0xFFFFFFFF, button.hover), in));

        if (!button.small) {
            float labelW = ClientFont.getStringWidthFloat(button.label) * 0.92F;
            float ly = this.layout.labelY + 8.0F * (1.0F - in) - button.hover;
            GlStateManager.pushMatrix();
            GlStateManager.translate(button.cx - labelW / 2.0F, ly, 0.0F);
            GlStateManager.scale(0.92F, 0.92F, 1.0F);
            ClientFont.drawString(button.label, 0.0F, 0.0F,
                    GuiRender.alpha(GuiRender.mix(0xFFC9CBD1, 0xFFFFFFFF, button.hover), in * (0.85F + 0.15F * button.hover)), false);
            GlStateManager.popMatrix();
        } else if (button.hover > 0.02F) {
            // small buttons: label tooltip under the icon
            float labelW = ClientFont.getStringWidthFloat(button.label);
            float tx = Math.min(this.width - labelW - 14.0F, button.cx - labelW / 2.0F);
            float ty = cy + r + 6.0F + 3.0F * (1.0F - button.hover);
            RoundedUtils.drawRoundedRect(tx - 5.0F, ty - 3.0F, labelW + 10.0F, ClientFont.getHeight() + 5.0F,
                    GuiRender.alpha(0xE60E0F12, button.hover * in), 4.0F);
            ClientFont.drawString(button.label, tx, ty, GuiRender.alpha(0xFFFFFFFF, button.hover * in), false);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0 || this.layout == null) return;
        if (isOverInterfaceToggle(mouseX, mouseY) && this.interfaceOffAt == 0L) {
            click();
            InterfaceSettings.setEnabled(false);
            this.interfaceOffAt = System.currentTimeMillis();
            return;
        }
        for (RoundButton button : this.buttons) {
            if (button.contains(mouseX, mouseY)) {
                button.press = 1.0F;
                click();
                perform(button.action);
                return;
            }
        }
        if (isOverProfile(mouseX, mouseY)) {
            click();
            perform(ALTS);
        }
    }

    private boolean isOverProfile(int mouseX, int mouseY) {
        float size = this.layout.avatarSize;
        float half = Math.max(60.0F, ClientFont.getStringWidthFloat(SessionManager.get().getUsername()) / 2.0F + size);
        return Math.abs(mouseX - this.width / 2.0F) <= half && Math.abs(mouseY - this.layout.profileCenterY) <= size / 2.0F + 4.0F;
    }

    private void perform(int action) {
        switch (action) {
            case SINGLEPLAYER:
                this.mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case MULTIPLAYER:
                this.mc.displayGuiScreen(new crewx.gui.servers.CrewXServerSelector(this));
                break;
            case ALTS:
                this.mc.displayGuiScreen(new GuiAccountManager(this));
                break;
            case OPTIONS:
                this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
                break;
            case QUIT:
                this.mc.shutdown();
                break;
            default:
                break;
        }
    }

    private void click() {
        this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // the title screen has nothing to close with Escape
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static float appear(long elapsed, long delay) {
        float t = Math.max(0.0F, Math.min(1.0F, (elapsed - delay) / 520.0F));
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private static float approach(float current, float target, float dt, float speed) {
        float next = current + (target - current) * (1.0F - (float) Math.exp(-dt * speed));
        return Math.abs(target - next) < 0.002F ? target : next;
    }
}
