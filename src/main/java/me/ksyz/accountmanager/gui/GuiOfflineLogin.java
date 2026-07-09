package me.ksyz.accountmanager.gui;

import me.ksyz.accountmanager.auth.SessionManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class GuiOfflineLogin extends GuiScreen {
    private final GuiScreen previousScreen;
    private String status = "Offline Login";
    private GuiTextField usernameField;

    public GuiOfflineLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        usernameField = new GuiTextField(1, mc.fontRendererObj,
                this.width / 2 - 100, this.height / 2, 200, 20);
        usernameField.setMaxStringLength(16);
        usernameField.setFocused(true);
        buttonList.add(new GuiButton(998, this.width / 2 - 100, this.height / 2 + 30, 200, 20, "Login"));
        super.initGui();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        mc.fontRendererObj.drawString(status,
                this.width / 2 - mc.fontRendererObj.getStringWidth(status) / 2,
                this.height / 2 - 30, 0xFFFFFF);
        usernameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 998) {
            String username = usernameField.getText().trim();
            if (username.isEmpty()) {
                status = "user cannot be empty.";
                return;
            }
            if (username.length() > 16) {
                status = "user must be 16 characters or less.";
                return;
            }
            String offlineUuid = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)
            ).toString().replace("-", "");
            SessionManager.set(new Session(username, offlineUuid, "0", "legacy"));
            mc.displayGuiScreen(previousScreen);
        }
        super.actionPerformed(button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        usernameField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == 28) {
            actionPerformed(buttonList.get(0));
        }
        if (keyCode == 1) {
            mc.displayGuiScreen(previousScreen);
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }
}
