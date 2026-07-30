package myau.accountmanager.gui;

import myau.accountmanager.AccountManager;
import myau.accountmanager.MushProfileService;
import myau.accountmanager.auth.Account;
import myau.accountmanager.utils.Notification;
import myau.accountmanager.utils.TextFormatting;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Adds a cracked account locally or continues with the existing Microsoft flow. */
public class GuiAddAccount extends GuiScreen {
    private final GuiScreen previousScreen;
    private GuiTextField usernameField;
    private GuiButton addButton;
    private String status = "";

    public GuiAddAccount(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);

        int x = width / 2 - 100;
        int y = height / 2 - 20;
        usernameField = new GuiTextField(0, fontRendererObj, x, y, 200, 20);
        usernameField.setMaxStringLength(64);
        usernameField.setFocused(true);

        buttonList.add(addButton = new GuiButton(2, x, y + 32, 98, 20, "Add"));
        buttonList.add(new GuiButton(3, x + 102, y + 32, 98, 20, "Cancel"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        usernameField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Add Account", width / 2, height / 2 - 52, 0xFFFFFF);
        drawString(fontRendererObj, "Username", width / 2 - 100, height / 2 - 32, 0xAAAAAA);
        usernameField.drawTextBox();
        if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, TextFormatting.translate(status), width / 2, height / 2 + 92, 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        usernameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(previousScreen);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            actionPerformed(addButton);
            return;
        }
        usernameField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }
        if (button.id == 3) {
            mc.displayGuiScreen(previousScreen);
            return;
        }
        if (button.id != 2) {
            return;
        }

        String username = usernameField.getText().trim();
        if (!username.matches("[A-Za-z0-9_]{1,16}")) {
            status = "&cUse a Minecraft username with up to 16 characters.&r";
            return;
        }

        for (Account existing : AccountManager.accounts) {
            if (existing.isCracked() && username.equalsIgnoreCase(existing.getUsername())) {
                status = "&eThis cracked account is already in the list.&r";
                return;
            }
        }

        final Account account = Account.cracked(username);
        AccountManager.accounts.add(account);
        AccountManager.save();
        MushProfileService.lookup(username, new MushProfileService.Callback() {
            @Override
            public void onResult(MushProfileService.Profile profile) {
                if (profile == null) {
                    return;
                }
                Account target = findCrackedAccount(account.getUsername());
                if (target == null) {
                    return;
                }
                if (!profile.getUsername().isEmpty()) {
                    target.setUsername(profile.getUsername());
                }
                target.setUuid(profile.getUuid());
                target.setSkin(profile.getSkinHash(), profile.isSlim());
                AccountManager.save();
            }
        });

        mc.displayGuiScreen(new GuiAccountManager(
                getManagerParent(),
                new Notification(TextFormatting.translate("&aCracked account added!&r"), 3500L)
        ));
    }

    private GuiScreen getManagerParent() {
        return previousScreen instanceof GuiAccountManager
                ? ((GuiAccountManager) previousScreen).getPreviousScreen()
                : previousScreen;
    }

    private Account findCrackedAccount(String username) {
        for (Account candidate : AccountManager.accounts) {
            if (candidate.isCracked() && username.equalsIgnoreCase(candidate.getUsername())) {
                return candidate;
            }
        }
        return null;
    }
}
