package me.ksyz.accountmanager.auth;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.lang.reflect.Field;

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

        }
    }
}