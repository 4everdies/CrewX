package crewx.util;

import java.util.Locale;

/** What the game is running on, for the few things that must behave differently there. */
public final class ClientPlatform {
    private static final boolean ANDROID = detectAndroid();
    private static final boolean FORK_SPAWN = "FORK".equalsIgnoreCase(System.getProperty("jdk.lang.Process.launchMechanism", ""));

    private ClientPlatform() {
    }

    /**
     * PojavLauncher (and other Android launchers): the JVM reports os.name "Linux", but runs inside an Android app.
     * Pojav marks itself with os.version "Android-&lt;release&gt;" and its pojav.* properties.
     */
    public static boolean isAndroid() {
        return ANDROID;
    }

    /**
     * True when starting an external program duplicates the whole game process (fork). PojavLauncher forces this
     * (-Djdk.lang.Process.launchMechanism=FORK, as posix_spawn does not work on Android): every start then copies the
     * page tables of the whole Minecraft heap and makes every thread of the game fault on its next memory writes,
     * which shows up as a short freeze. Nothing may start programs periodically there.
     */
    public static boolean spawnIsExpensive() {
        return FORK_SPAWN || ANDROID;
    }

    private static boolean detectAndroid() {
        try {
            return looksLikeAndroid(System.getProperty("os.version", ""), System.getProperty("pojav.path.minecraft"),
                    System.getProperty("java.vendor", ""), System.getenv("ANDROID_ROOT"), System.getenv("ANDROID_DATA"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean looksLikeAndroid(String osVersion, String pojavPath, String vendor, String androidRoot, String androidData) {
        if (osVersion != null && osVersion.toLowerCase(Locale.ROOT).startsWith("android")) return true;
        if (pojavPath != null) return true;
        if (vendor != null && vendor.toLowerCase(Locale.ROOT).contains("android")) return true;
        return androidRoot != null && androidData != null;
    }
}
