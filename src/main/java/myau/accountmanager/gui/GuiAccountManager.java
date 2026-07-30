package myau.accountmanager.gui;

import myau.accountmanager.AccountManager;
import myau.accountmanager.MushProfileService;
import myau.accountmanager.auth.Account;
import myau.accountmanager.auth.MicrosoftAuth;
import myau.accountmanager.auth.SessionManager;
import myau.accountmanager.utils.Notification;
import myau.accountmanager.utils.TextFormatting;
import myau.accountmanager.utils.PlayerSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class GuiAccountManager extends GuiScreen {
    private final GuiScreen previousScreen;

    private GuiAccountList guiAccountList;
    private Notification notification;
    private int selectedAccount = -1;
    private ExecutorService executor;
    private CompletableFuture<Void> task;
    private final Map<String, MushProfileService.Stats> accountStats = new HashMap<String, MushProfileService.Stats>();

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

        int buttonWidth = Math.max(80, Math.min(110, (width - 24) / 3));
        int buttonRowWidth = buttonWidth * 3 + 6;
        int startX = width / 2 - buttonRowWidth / 2;
        int y = height - 28;
        buttonList.add(new GuiButton(1, startX, y, buttonWidth, 20, "Add"));
        buttonList.add(new GuiButton(4, startX + buttonWidth + 3, y, buttonWidth, 20, "Delete"));
        buttonList.add(new GuiButton(3, startX + (buttonWidth + 3) * 2, y, buttonWidth, 20, "Browser Login"));

        guiAccountList = new GuiAccountList(mc);
        guiAccountList.registerScrollButtons(11, 12);
        updateScreen();
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float renderPartialTicks) {
        if (guiAccountList != null) {
            guiAccountList.drawScreen(mouseX, mouseY, renderPartialTicks);
        }
        super.drawScreen(mouseX, mouseY, renderPartialTicks);

        drawCenteredString(fontRendererObj, "Alt Manager", width / 2, 15, 0xFFFFFF);
        drawCenteredString(fontRendererObj, AccountManager.accounts.size() + " accounts", width / 2, 28, 0xAAAAAA);
        drawString(fontRendererObj, "Current: " + SessionManager.get().getUsername(), 6, 6, 0xAAAAAA);

        if (notification != null && !notification.isExpired()) {
            String message = notification.getMessage();
            int messageWidth = fontRendererObj.getStringWidth(message);
            Gui.drawRect(width / 2 - messageWidth / 2 - 4, 39, width / 2 + messageWidth / 2 + 4,
                    39 + fontRendererObj.FONT_HEIGHT + 6, 0x90000000);
            drawCenteredString(fontRendererObj, message, width / 2, 42, 0xFFFFFF);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        if (guiAccountList != null) {
            guiAccountList.handleMouseInput();
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_UP && selectedAccount > 0) {
            --selectedAccount;
            if (isCtrlKeyDown()) {
                Collections.swap(AccountManager.accounts, selectedAccount, selectedAccount + 1);
                AccountManager.save();
            }
            updateScreen();
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN && selectedAccount < AccountManager.accounts.size() - 1) {
            ++selectedAccount;
            if (isCtrlKeyDown()) {
                Collections.swap(AccountManager.accounts, selectedAccount, selectedAccount - 1);
                AccountManager.save();
            }
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
            mc.displayGuiScreen(previousScreen);
            return;
        }
        if (isKeyComboCtrlC(keyCode) && selectedAccount >= 0) {
            setClipboardString(AccountManager.accounts.get(selectedAccount).getUsername());
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        switch (button.id) {
            case 1:
                mc.displayGuiScreen(new GuiAddAccount(this));
                break;
            case 3:
                mc.displayGuiScreen(new GuiMicrosoftAuth(previousScreen));
                break;
            case 4:
                deleteSelected();
                break;
            default:
                guiAccountList.actionPerformed(button);
                break;
        }
    }

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

    private void drawAccountHead(Account account, int x, int y) {
        if (account.isCracked() && !account.getSkinHash().isEmpty()) {
            ResourceLocation head = MushProfileService.getHeadTexture(account.getSkinHash());
            if (head != null) {
                mc.getTextureManager().bindTexture(head);
                Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);
                return;
            }
        }

        ResourceLocation skin = account.isCracked()
                ? DefaultPlayerSkin.getDefaultSkin(EntityPlayer.getOfflineUUID("Steve"))
                : PlayerSkinCache.getSkin(account.getUsername(), null);
        mc.getTextureManager().bindTexture(skin);
        Gui.drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, 32, 32, 64.0F, 64.0F);
        Gui.drawScaledCustomSizeModalRect(x, y, 40.0F, 8.0F, 8, 8, 32, 32, 64.0F, 64.0F);
    }

    private void drawOutline(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, top + 1, color);
        Gui.drawRect(left, bottom - 1, right, bottom, color);
        Gui.drawRect(left, top, left + 1, bottom, color);
        Gui.drawRect(right - 1, top, right, bottom, color);
    }

    private final class GuiAccountList extends GuiSlot {
        private static final int CARD_HEIGHT = 44;

        private GuiAccountList(Minecraft minecraft) {
            super(minecraft, GuiAccountManager.this.width, GuiAccountManager.this.height, 52, GuiAccountManager.this.height - 36, CARD_HEIGHT);
        }

        @Override
        protected int getSize() {
            return AccountManager.accounts.size();
        }

        @Override
        protected boolean isSelected(int slotIndex) {
            // Preserve selection state without asking GuiSlot to draw its built-in rectangle.
            return false;
        }

        @Override
        protected int getScrollBarX() {
            return (width + getListWidth()) / 2 + 5;
        }

        @Override
        public int getListWidth() {
            return Math.min(420, width - 28);
        }

        @Override
        protected int getContentHeight() {
            return AccountManager.accounts.size() * CARD_HEIGHT;
        }

        @Override
        protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
            int cardX = (GuiAccountManager.this.width - getListWidth()) / 2;
            int statsX = cardX + getListWidth() - 54;
            if (mouseX >= statsX && mouseX <= statsX + 48) {
                Account account = AccountManager.accounts.get(slotIndex);
                mc.displayGuiScreen(new GuiMushStats(GuiAccountManager.this, account.getUsername()));
                return;
            }
            selectedAccount = slotIndex;
            updateScreen();
            if (isDoubleClick) {
                loginSelected();
            }
        }

        @Override
        protected void drawBackground() {
            drawDefaultBackground();
        }

        @Override
        protected void drawSlot(int entryID, int x, int y, int heightIn, int mouseXIn, int mouseYIn) {
            Account account = AccountManager.accounts.get(entryID);
            int cardWidth = getListWidth();
            boolean banned = isBanned(account);
            if (entryID == selectedAccount) {
                drawOutline(x, y, x + cardWidth, y + CARD_HEIGHT - 3, 0xFF8A8A8A);
            }

            drawAccountHead(account, x + 6, y + 4);
            FontRenderer renderer = fontRendererObj;
            String username = StringUtils.isBlank(account.getUsername()) ? "Unknown" : account.getUsername();
            if (account.isCracked()) {
                username += " \u00A77[C]";
            }
            if (account.getUsername().equals(SessionManager.get().getUsername())) {
                username = "\u00A7a" + username;
            }
            renderer.drawString(username, x + 44, y + 7, 0xFFFFFF);
            if (banned) {
                String bannedText = "BANNED \uD83D\uDEC7";
                renderer.drawString(bannedText, x + cardWidth - renderer.getStringWidth(bannedText) - 8, y + 7, 0xFFFF5555);
            }

            String uuid = StringUtils.isBlank(account.getUuid()) ? "UUID unavailable" : account.getUuid();
            renderer.drawString("\u00A78" + uuid, x + 44, y + 22, 0xAAAAAA);

            int statsX = x + cardWidth - 54;
            int statsY = y + 20;
            boolean statsHovered = mouseXIn >= statsX && mouseXIn <= statsX + 48 && mouseYIn >= statsY && mouseYIn <= statsY + 15;
            drawOutline(statsX, statsY, statsX + 48, statsY + 15, statsHovered ? 0xFFFFFFFF : 0xFF777777);
            String statsText = "Stats";
            renderer.drawString(statsText, statsX + (48 - renderer.getStringWidth(statsText)) / 2,
                    statsY + (15 - renderer.FONT_HEIGHT) / 2, statsHovered ? 0xFFFFFFFF : 0xCCCCCC);
        }
    }
}
