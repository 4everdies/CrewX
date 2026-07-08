package myau.management;

import myau.enums.ChatColors;

import java.awt.*;
import java.io.File;

public class TargetManager extends PlayerFileManager {
    public TargetManager() {
        super(new File("./config/Myau/", "enemies.txt"), new Color(ChatColors.DARK_RED.toAwtColor()));
    }
}