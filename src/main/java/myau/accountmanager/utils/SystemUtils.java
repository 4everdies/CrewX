package myau.accountmanager.utils;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class SystemUtils {
    public static void openWebLink(URI url) {
        try {
            Class<?> desktop = Class.forName("java.awt.Desktop");
            Object object = desktop.getMethod("getDesktop", new Class[0]).invoke(null);
            desktop.getMethod("browse", new Class[]{URI.class}).invoke(object, url);
        } catch (Exception exception) {
            // Browsing is optional on headless or restricted desktop environments.
        }
    }

    public static void setClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (Exception exception) {
            // Clipboard access may be unavailable when the desktop is headless or locked.
        }
    }
}
