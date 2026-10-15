package crewx.management;

import crewx.enums.ChatColors;

import java.awt.*;
import java.io.File;

public class TargetManager extends PlayerFileManager {
    public TargetManager() {
        super(new File(crewx.config.Config.directory(), "enemies.txt"), new Color(ChatColors.DARK_RED.toAwtColor()));
    }
}
