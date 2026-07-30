package myau.accountmanager;

import myau.accountmanager.auth.Account;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class Events {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void initGuiEvent(InitGuiEvent.Post event) {

        if (event.gui instanceof GuiDisconnected) {
            try {
                Field f = ReflectionHelper.findField(GuiDisconnected.class, "message", "field_146304_f");
                IChatComponent message = (IChatComponent) f.get(event.gui);
                String text = message.getFormattedText().split("\n\n")[0];
                if (
                        text.equals("§r§cYou are permanently banned from this server!") ||
                                text.equals("§r§cYour account has been blocked.")
                ) {
                    AccountManager.load();
                    for (Account account : AccountManager.accounts) {
                        if (mc.getSession().getUsername().equals(account.getUsername())) {
                            account.setUnban(-1L);
                        }
                    }
                    AccountManager.save();
                    return;
                }

                if (
                        text.matches("§r§cYou are temporarily banned for §r§f.*§r§c from this server!") ||
                                text.matches("§r§cYour account is temporarily blocked for §r§f.*§r§c from this server!")
                ) {
                    String unban = StringUtils.substringBetween(text, "§r§f", "§r§c");
                    if (unban != null) {
                        long time = System.currentTimeMillis();
                        for (String duration : unban.split(" ")) {
                            String type = duration.substring(duration.length() - 1);
                            long value = Long.parseLong(duration.substring(0, duration.length() - 1));
                            switch (type) {
                                case "d": {
                                    time += value * 86400000L;
                                }
                                break;
                                case "h": {
                                    time += value * 3600000L;
                                }
                                break;
                                case "m": {
                                    time += value * 60000L;
                                }
                                break;
                                case "s": {
                                    time += value * 1000L;
                                }
                                break;
                            }
                        }

                        AccountManager.load();
                        for (Account account : AccountManager.accounts) {
                            if (mc.getSession().getUsername().equals(account.getUsername())) {
                                account.setUnban(time);
                            }
                        }
                        AccountManager.save();
                    }
                }
            } catch (Exception e) {
                //
            }
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        ServerData serverData = mc.getCurrentServerData();
        if (serverData != null) {
            String serverIP = serverData.serverIP;
            if (serverIP.endsWith("hypixel.net") || serverIP.endsWith("hypixel.io")) {
                AccountManager.load();
                for (Account account : AccountManager.accounts) {
                    if (mc.getSession().getUsername().equals(account.getUsername())) {
                        account.setUnban(0L);
                    }
                }
                AccountManager.save();
            }
        }
    }
}
