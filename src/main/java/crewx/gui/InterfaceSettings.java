package crewx.gui;

import crewx.config.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * "Background" (shown as a switch on the main menu): whether the menus use the CrewX look (main menu, space background, buttons, server list)
 * or Minecraft's own. Kept in its own small file so it does not change with config profiles; in-game modules and
 * the HUD are not affected.
 */
public final class InterfaceSettings {
    private static final String FILE = "interface.txt";
    private static Boolean enabled;

    private InterfaceSettings() {
    }

    public static boolean isEnabled() {
        if (enabled == null) {
            boolean value = true;
            try {
                File file = new File(Config.directory(), FILE);
                if (file.isFile()) {
                    value = !"crewx=false".equals(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim());
                }
            } catch (Throwable ignored) {
                // unreadable: keep the CrewX interface
            }
            enabled = value;
        }
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        try {
            File directory = Config.directory();
            directory.mkdirs();
            Files.write(new File(directory, FILE).toPath(), ("crewx=" + value).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // the choice still applies for this session
        }
    }
}
