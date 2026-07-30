package myau.accountmanager.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.accountmanager.AccountManager;
import myau.accountmanager.auth.Account;
import myau.accountmanager.auth.SessionManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.Session;
import org.apache.commons.io.IOUtils;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class GuiSessionLogin extends GuiScreen {
    private GuiScreen previousScreen;

    private String status = "Cookie Account";
    private GuiTextField sessionField;
    private ScaledResolution sr;

    public GuiSessionLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        sr = new ScaledResolution(mc);

        sessionField = new GuiTextField(1, mc.fontRendererObj,sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2, 200, 20);
        sessionField.setMaxStringLength(32767);
        sessionField.setFocused(true);

        buttonList.add(new GuiButton(998, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 30, 200, 20, "Add Cookie"));

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

        mc.fontRendererObj.drawString(status, sr.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(status) / 2, sr.getScaledHeight() / 2 - 30, Color.WHITE.getRGB());
        sessionField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        // The login action validates and exchanges the pasted session payload.
        if (button.id == 998) {
            try {
                String username, uuid, token, session = sessionField.getText();

                if (session.contains(":")) { //if fully formatted string (ign:uuid:token)
                    // Split the launcher-style token into its required fields.
                    String[] parts = session.split(":", 3);
                    if (parts.length != 3) {
                        throw new IllegalArgumentException("Cookie must be username:uuid:token");
                    }
                    username = parts[0];
                    uuid = parts[1];
                    token = parts[2];
                } else { //if only token
                    // Request the profile associated with the access token.
                    HttpURLConnection c = (HttpURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile/").openConnection();
                    c.setRequestProperty("Content-type", "application/json");
                    c.setRequestProperty("Authorization", "Bearer " + sessionField.getText());
                    c.setDoOutput(true);

                    // Parse the profile response as JSON.
                    JsonObject json = new JsonParser().parse(IOUtils.toString(c.getInputStream())).getAsJsonObject();

                    // Build the local session from the returned identity data.
                    username = json.get("name").getAsString();
                    uuid = json.get("id").getAsString();
                    token = session;
                }

                SessionManager.set(new Session(username, uuid, token, "mojang"));
                boolean replaced = false;
                for (Account account : AccountManager.accounts) {
                    if (account.isCookie() && username.equalsIgnoreCase(account.getUsername())) {
                        account.setAccessToken(token);
                        account.setUuid(uuid);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    AccountManager.accounts.add(Account.cookie(username, uuid, token));
                }
                AccountManager.save();
                mc.displayGuiScreen(previousScreen);
            } catch (IOException IOException){
                if(IOException.getMessage().contains("401")){
                    status = "§cError: Invalid session.";
                }  else {
                    IOException.printStackTrace();
                }
            } catch(Exception e) {
                status = "§cError: Couldn't set session (check mc logs)";
                e.printStackTrace();
            }
        }

        super.actionPerformed(button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        sessionField.textboxKeyTyped(typedChar, keyCode);

        if (Keyboard.KEY_ESCAPE == keyCode) mc.displayGuiScreen(previousScreen);
        else super.keyTyped(typedChar, keyCode);
    }
}
