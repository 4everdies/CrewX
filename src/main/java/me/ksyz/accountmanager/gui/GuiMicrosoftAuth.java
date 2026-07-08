package me.ksyz.accountmanager.gui;

import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.MicrosoftAuth;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.utils.Notification;
import me.ksyz.accountmanager.utils.SystemUtils;
import me.ksyz.accountmanager.utils.TextFormatting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.apache.commons.lang3.RandomStringUtils;
import org.lwjgl.input.Keyboard;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class GuiMicrosoftAuth extends GuiScreen {
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

    public GuiMicrosoftAuth(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
        this.state = RandomStringUtils.randomAlphanumeric(8);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(openButton = new GuiButton(
                0,
                width / 2 - 75 - 2,
                height / 2 + fontRendererObj.FONT_HEIGHT / 2 + fontRendererObj.FONT_HEIGHT,
                75,
                20,
                "Open"
        ));
        buttonList.add(cancelButton = new GuiButton(
                1,
                width / 2 + 2,
                height / 2 + fontRendererObj.FONT_HEIGHT / 2 + fontRendererObj.FONT_HEIGHT,
                75,
                20,
                "Cancel"
        ));

        if (task == null) {
            MicrosoftAuth.CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
            MicrosoftAuth.SCOPE = "XboxLive.signin XboxLive.offline_access";
            URI url = MicrosoftAuth.getMSAuthLink(state);
            SystemUtils.setClipboard(url != null ? url.toString() : "");
            status = "&fLogin link has been copied to the clipboard!&r";

            if (executor == null) {
                executor = Executors.newSingleThreadExecutor();
            }
            AtomicReference<String> refreshToken = new AtomicReference<>("");
            AtomicReference<String> accessToken = new AtomicReference<>("");
            task = MicrosoftAuth.acquireMSAuthCode(state, executor)
                    .thenComposeAsync(msAuthCode -> {
                        openButtonEnabled = false;
                        status = "&fAcquiring Microsoft access tokens&r";
                        return MicrosoftAuth.acquireMSAccessTokens(msAuthCode, executor);
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
                                refreshToken.get(), accessToken.get(), session.getUsername(),"42a60a84-599d-44b2-a7c6-b00cdef1d6a2","XboxLive.signin XboxLive.offline_access"
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
                        return null;
                    });
        }
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
                fontRendererObj, "Microsoft Authentication",
                width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2 - fontRendererObj.FONT_HEIGHT * 2, 11184810
        );

        if (status != null) {
            drawCenteredString(
                    fontRendererObj, TextFormatting.translate(status),
                    width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2, -1
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
        if (keyCode == Keyboard.KEY_ESCAPE) {
            actionPerformed(cancelButton);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }

        if (button.enabled) {
            switch (button.id) {
                case 0: {
                    SystemUtils.openWebLink(MicrosoftAuth.getMSAuthLink(state));
                }
                break;
                case 1: {
                    mc.displayGuiScreen(new GuiAccountManager(previousScreen));
                }
                break;
            }
        }
    }
}