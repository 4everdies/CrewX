package myau.accountmanager.auth;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.lang.reflect.Field;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class SessionManager {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static Field field = null;

    private static Field getField() {
        if (field == null) {
            try {
                for (Field f : Minecraft.class.getDeclaredFields()) {
                    if (f.getType().isAssignableFrom(Session.class)) {
                        field = f;
                        field.setAccessible(true);
                        break;
                    }
                }
            } catch (Exception e) {
                field = null;
            }
        }

        return field;
    }

    public static Session get() {
        return mc.getSession();
    }

    public static void set(Session session) {
        try {
            getField().set(mc, session);
        } catch (Exception e) {
            //
        }
    }
}
