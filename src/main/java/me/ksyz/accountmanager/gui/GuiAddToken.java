package me.ksyz.accountmanager.gui;

import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.MicrosoftAuth;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.utils.Notification;
import me.ksyz.accountmanager.utils.TextFormatting;
import net.minecraft.client.gui.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.lwjgl.input.Keyboard;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class GuiAddToken extends GuiScreen {
    private final GuiScreen previousScreen;
    private final String state;

    private GuiButton openButton = null;
    private boolean openButtonEnabled = true;
    private GuiButton cancelButton = null;
    private String status = null;
    private String cause = null;
    private ExecutorService executor = null;
    private CompletableFuture<Void> task = null;
    private boolean success = false;
    private GuiTextField tokenField;

    public GuiAddToken(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
        this.state = RandomStringUtils.randomAlphanumeric(8);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        ScaledResolution sr = new ScaledResolution(mc);
        tokenField = new GuiTextField(1, mc.fontRendererObj,sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2, 200, 20);
        tokenField.setMaxStringLength(32767);
        tokenField.setFocused(true);

        buttonList.add(new GuiButton(998, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 30, 200, 20, "Add"));
    }

    @Override
    public void onGuiClosed() {
        if (task != null && !task.isDone()) {
            task.cancel(true);
            executor.shutdownNow();
        }
    }

    @Override
    public void updateScreen() {
        if (success) {
            mc.displayGuiScreen(new GuiAccountManager(
                    previousScreen,
                    new Notification(
                            TextFormatting.translate(String.format(
                                    "&aSuccessful login! (%s)&r",
                                    SessionManager.get().getUsername()
                            )),
                            5000L
                    )
            ));
            success = false;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (openButton != null) {
            openButton.enabled = openButtonEnabled;
        }
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        drawCenteredString(
                fontRendererObj, "Add Token",
                width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2 - fontRendererObj.FONT_HEIGHT * 2 - 14, 11184810
        );
        tokenField.drawTextBox();

        if (status != null) {
            drawCenteredString(
                    fontRendererObj, TextFormatting.translate(status),
                    width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2 - 14, -1
            );
        }

        if (cause != null) {
            String causeText = TextFormatting.translate(cause);
            Gui.drawRect(
                    0, height - 2 - fontRendererObj.FONT_HEIGHT - 3,
                    3 + mc.fontRendererObj.getStringWidth(causeText) + 3, height,
                    0x64000000
            );
            drawString(
                    fontRendererObj, TextFormatting.translate(cause),
                    3, height - 2 - fontRendererObj.FONT_HEIGHT, -1
            );
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_A) {
            tokenField.setCursorPosition(0);
            tokenField.setSelectionPos(tokenField.getText().length());
            return;
        }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_BACK) {
            String text = tokenField.getText();
            int cursor = tokenField.getCursorPosition();
            if (cursor > 0 && !text.isEmpty()) {
                int start = cursor - 1;
                while (start > 0 && text.charAt(start - 1) == ' ') start--;
                while (start > 0 && text.charAt(start - 1) != ' ') start--;
                tokenField.setText(text.substring(0, start) + text.substring(cursor));
                tokenField.setCursorPosition(start);
            }
            return;
        }
        tokenField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if(task == null || task.isDone() || task.isCancelled() || task.isCompletedExceptionally()){
                mc.displayGuiScreen(previousScreen);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }

        if (button.enabled) {
            if (button.id == 998) {
                if (task == null) {
                    if (executor == null) {
                        executor = Executors.newSingleThreadExecutor();
                    }
                    AtomicReference<String> refreshToken = new AtomicReference<>("");
                    AtomicReference<String> accessToken = new AtomicReference<>("");
                    MicrosoftAuth.CLIENT_ID = "00000000402b5328";
                    MicrosoftAuth.SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
                    task = MicrosoftAuth.login(tokenField.getText(), executor)
                            .handle((session, error) -> session != null)
                            .thenComposeAsync(completed -> {
                                if (completed) {
                                    throw new NoSuchElementException();
                                }
                                status = "&7Refreshing Microsoft access tokens...&r";
                                return MicrosoftAuth.refreshMSAccessTokens(tokenField.getText(), executor);
                            })
                            .thenComposeAsync(msAccessTokens -> {
                                status = "&fAcquiring Xbox access token&r";
                                refreshToken.set(msAccessTokens.get("refresh_token"));
                                return MicrosoftAuth.acquireXboxAccessToken(msAccessTokens.get("access_token"), executor);
                            })
                            .thenComposeAsync(xboxAccessToken -> {
                                status = "&fAcquiring Xbox XSTS token&r";
                                return MicrosoftAuth.acquireXboxXstsToken(xboxAccessToken, executor);
                            })
                            .thenComposeAsync(xboxXstsData -> {
                                status = "&fAcquiring Minecraft access token&r";
                                return MicrosoftAuth.acquireMCAccessToken(
                                        xboxXstsData.get("Token"), xboxXstsData.get("uhs"), executor
                                );
                            })
                            .thenComposeAsync(mcToken -> {
                                status = "&fFetching your Minecraft profile&r";
                                accessToken.set(mcToken);
                                return MicrosoftAuth.login(mcToken, executor);
                            })
                            .thenAccept(session -> {
                                status = null;
                                Account acc = new Account(
                                        refreshToken.get(), accessToken.get(), session.getUsername(),"00000000402b5328","service::user.auth.xboxlive.com::MBI_SSL"
                                );
                                for (Account account : AccountManager.accounts) {
                                    if (acc.getUsername().equals(account.getUsername())) {
                                        acc.setUnban(account.getUnban());
                                        break;
                                    }
                                }
                                AccountManager.accounts.add(acc);
                                AccountManager.save();
                                SessionManager.set(session);
                                success = true;
                            })
                            .exceptionally(error -> {
                                openButtonEnabled = false;
                                status = String.format("&c%s&r", error.getMessage());
                                cause = String.format("&c%s&r", error.getCause().getMessage());
                                task.cancel(true);
                                task = null;
                                return null;
                            });
                }
            }
        }
    }
}