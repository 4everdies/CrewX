package crewx.management;

import crewx.enums.ChatColors;

import java.awt.*;
import java.io.File;

public class FriendManager extends PlayerFileManager {
    public FriendManager() {
        super(new File(crewx.config.Config.directory(), "friends.txt"), new Color(ChatColors.DARK_GREEN.toAwtColor()));
    }
}
