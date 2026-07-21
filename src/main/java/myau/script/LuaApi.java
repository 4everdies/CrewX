package myau.script;

import myau.Myau;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.ChatUtil;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.function.Function;

public class LuaApi {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static void fn(LuaTable table, String name, Function<Varargs, Varargs> body) {
        table.set(name, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return body.apply(args);
            }
        });
    }

    private static LuaValue nil() {
        return LuaValue.NIL;
    }

    private static boolean inWorld() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    public static void install(LuaScript script, ScriptModule module) {
        LuaValue globals = script.getGlobals();
        globals.set("script", buildScript(script, module));
        globals.set("client", buildClient());
        globals.set("world", buildWorld());
        globals.set("modules", buildModules());
        globals.set("render", buildRender());
        globals.set("util", buildUtil());
    }

    private static LuaTable buildScript(LuaScript script, ScriptModule module) {
        LuaTable t = new LuaTable();

        fn(t, "getName", a -> LuaValue.valueOf(module.getName()));
        fn(t, "isEnabled", a -> LuaValue.valueOf(module.isEnabled()));
        fn(t, "setEnabled", a -> {
            module.setEnabled(a.arg(1).toboolean());
            return nil();
        });

        fn(t, "registerBoolean", a -> {
            module.addProperty(new BooleanProperty(a.arg(1).tojstring(), a.arg(2).toboolean()));
            return nil();
        });
        fn(t, "registerFloat", a -> {
            module.addProperty(new FloatProperty(
                    a.arg(1).tojstring(),
                    (float) a.arg(2).todouble(),
                    (float) a.arg(3).todouble(),
                    (float) a.arg(4).todouble()));
            return nil();
        });
        fn(t, "registerInt", a -> {
            module.addProperty(new IntProperty(
                    a.arg(1).tojstring(),
                    a.arg(2).toint(),
                    a.arg(3).toint(),
                    a.arg(4).toint()));
            return nil();
        });
        fn(t, "registerPercent", a -> {
            module.addProperty(new PercentProperty(a.arg(1).tojstring(), a.arg(2).toint()));
            return nil();
        });
        fn(t, "registerMode", a -> {
            LuaValue modesValue = a.arg(3);
            if (!modesValue.istable()) {
                return nil();
            }
            LuaTable modesTable = modesValue.checktable();
            int length = modesTable.length();
            String[] modes = new String[length];
            for (int i = 0; i < length; i++) {
                modes[i] = modesTable.get(i + 1).tojstring();
            }
            module.addProperty(new ModeProperty(a.arg(1).tojstring(), a.arg(2).toint(), modes));
            return nil();
        });

        fn(t, "get", a -> {
            myau.property.Property<?> property = module.getProperty(a.arg(1).tojstring());
            if (property == null) {
                return nil();
            }
            Object value = property.getValue();
            if (value instanceof Boolean) {
                return LuaValue.valueOf((Boolean) value);
            }
            if (value instanceof Number) {
                return LuaValue.valueOf(((Number) value).doubleValue());
            }
            return LuaValue.valueOf(String.valueOf(value));
        });

        return t;
    }

    private static LuaTable buildClient() {
        LuaTable t = new LuaTable();

        fn(t, "inWorld", a -> LuaValue.valueOf(inWorld()));
        fn(t, "getFPS", a -> LuaValue.valueOf(Minecraft.getDebugFPS()));
        fn(t, "print", a -> {
            ChatUtil.sendFormatted(a.arg(1).tojstring());
            return nil();
        });
        fn(t, "chat", a -> {
            if (inWorld()) {
                mc.thePlayer.sendChatMessage(a.arg(1).tojstring());
            }
            return nil();
        });
        fn(t, "getUsername", a -> mc.getSession() == null
                ? nil()
                : LuaValue.valueOf(mc.getSession().getUsername()));

        fn(t, "getX", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.posX) : nil());
        fn(t, "getY", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.posY) : nil());
        fn(t, "getZ", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.posZ) : nil());
        fn(t, "getMotionX", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.motionX) : nil());
        fn(t, "getMotionY", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.motionY) : nil());
        fn(t, "getMotionZ", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.motionZ) : nil());
        fn(t, "setMotionX", a -> {
            if (inWorld()) mc.thePlayer.motionX = a.arg(1).todouble();
            return nil();
        });
        fn(t, "setMotionY", a -> {
            if (inWorld()) mc.thePlayer.motionY = a.arg(1).todouble();
            return nil();
        });
        fn(t, "setMotionZ", a -> {
            if (inWorld()) mc.thePlayer.motionZ = a.arg(1).todouble();
            return nil();
        });

        fn(t, "getYaw", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.rotationYaw) : nil());
        fn(t, "getPitch", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.rotationPitch) : nil());
        fn(t, "getHealth", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.getHealth()) : nil());
        fn(t, "getHunger", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.getFoodStats().getFoodLevel()) : nil());
        fn(t, "isOnGround", a -> inWorld() && mc.thePlayer.onGround ? LuaValue.TRUE : LuaValue.FALSE);
        fn(t, "isSneaking", a -> LuaValue.valueOf(inWorld() && mc.thePlayer.isSneaking()));
        fn(t, "isSprinting", a -> LuaValue.valueOf(inWorld() && mc.thePlayer.isSprinting()));
        fn(t, "isUsingItem", a -> LuaValue.valueOf(inWorld() && mc.thePlayer.isUsingItem()));
        fn(t, "isInWater", a -> LuaValue.valueOf(inWorld() && mc.thePlayer.isInWater()));
        fn(t, "getHeldItem", a -> {
            if (!inWorld()) return nil();
            ItemStack stack = mc.thePlayer.getHeldItem();
            return stack == null ? nil() : LuaValue.valueOf(stack.getDisplayName());
        });
        fn(t, "getCurrentSlot", a -> inWorld()
                ? LuaValue.valueOf(mc.thePlayer.inventory.currentItem)
                : nil());
        fn(t, "setCurrentSlot", a -> {
            if (inWorld()) {
                int slot = a.arg(1).toint();
                if (slot >= 0 && slot < 9) {
                    mc.thePlayer.inventory.currentItem = slot;
                }
            }
            return nil();
        });

        return t;
    }

    private static LuaTable buildWorld() {
        LuaTable t = new LuaTable();

        fn(t, "getTime", a -> inWorld() ? LuaValue.valueOf(mc.theWorld.getWorldTime()) : nil());

        fn(t, "getPlayers", a -> {
            LuaTable result = new LuaTable();
            if (!inWorld()) {
                return result;
            }
            int index = 1;
            for (Object object : mc.theWorld.playerEntities) {
                if (object instanceof EntityPlayer && object != mc.thePlayer) {
                    result.set(index++, buildEntity((EntityPlayer) object));
                }
            }
            return result;
        });

        fn(t, "getClosestPlayer", a -> {
            if (!inWorld()) {
                return nil();
            }
            double maxRange = a.narg() >= 1 ? a.arg(1).todouble() : 64.0;
            EntityPlayer best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Object object : mc.theWorld.playerEntities) {
                if (!(object instanceof EntityPlayer) || object == mc.thePlayer) {
                    continue;
                }
                EntityPlayer player = (EntityPlayer) object;
                if (player.isDead || player.getHealth() <= 0.0F) {
                    continue;
                }
                double distance = mc.thePlayer.getDistanceToEntity(player);
                if (distance <= maxRange && distance < bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
            return best == null ? nil() : buildEntity(best);
        });

        fn(t, "getBlockAt", a -> {
            if (!inWorld()) {
                return nil();
            }
            BlockPos pos = new BlockPos(a.arg(1).toint(), a.arg(2).toint(), a.arg(3).toint());
            return LuaValue.valueOf(mc.theWorld.getBlockState(pos).getBlock().getRegistryName());
        });

        fn(t, "isBlockSolid", a -> {
            if (!inWorld()) {
                return LuaValue.FALSE;
            }
            BlockPos pos = new BlockPos(a.arg(1).toint(), a.arg(2).toint(), a.arg(3).toint());
            return LuaValue.valueOf(mc.theWorld.getBlockState(pos).getBlock().isFullBlock());
        });

        return t;
    }

    private static LuaTable buildEntity(Entity entity) {
        LuaTable t = new LuaTable();
        t.set("name", LuaValue.valueOf(entity.getName()));
        t.set("x", LuaValue.valueOf(entity.posX));
        t.set("y", LuaValue.valueOf(entity.posY));
        t.set("z", LuaValue.valueOf(entity.posZ));
        t.set("lastX", LuaValue.valueOf(entity.lastTickPosX));
        t.set("lastY", LuaValue.valueOf(entity.lastTickPosY));
        t.set("lastZ", LuaValue.valueOf(entity.lastTickPosZ));
        t.set("motionX", LuaValue.valueOf(entity.motionX));
        t.set("motionY", LuaValue.valueOf(entity.motionY));
        t.set("motionZ", LuaValue.valueOf(entity.motionZ));
        t.set("yaw", LuaValue.valueOf(entity.rotationYaw));
        t.set("pitch", LuaValue.valueOf(entity.rotationPitch));
        t.set("width", LuaValue.valueOf(entity.width));
        t.set("height", LuaValue.valueOf(entity.height));
        t.set("sneaking", LuaValue.valueOf(entity.isSneaking()));
        t.set("invisible", LuaValue.valueOf(entity.isInvisible()));
        t.set("onGround", LuaValue.valueOf(entity.onGround));
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            t.set("health", LuaValue.valueOf(living.getHealth()));
            t.set("maxHealth", LuaValue.valueOf(living.getMaxHealth()));
            t.set("absorption", LuaValue.valueOf(living.getAbsorptionAmount()));
            t.set("eyeHeight", LuaValue.valueOf(living.getEyeHeight()));
        }
        if (mc.thePlayer != null) {
            t.set("distance", LuaValue.valueOf(mc.thePlayer.getDistanceToEntity(entity)));
        }
        return t;
    }

    private static LuaTable buildModules() {
        LuaTable t = new LuaTable();

        fn(t, "isEnabled", a -> {
            Module module = findModule(a.arg(1).tojstring());
            return LuaValue.valueOf(module != null && module.isEnabled());
        });
        fn(t, "setEnabled", a -> {
            Module module = findModule(a.arg(1).tojstring());
            if (module != null) {
                module.setEnabled(a.arg(2).toboolean());
            }
            return nil();
        });
        fn(t, "exists", a -> LuaValue.valueOf(findModule(a.arg(1).tojstring()) != null));
        fn(t, "getNames", a -> {
            LuaTable result = new LuaTable();
            int index = 1;
            for (Module module : Myau.moduleManager.modules.values()) {
                result.set(index++, LuaValue.valueOf(module.getName()));
            }
            return result;
        });

        return t;
    }

    private static Module findModule(String name) {
        for (Module module : Myau.moduleManager.modules.values()) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    private static LuaTable buildRender() {
        LuaTable t = new LuaTable();

        fn(t, "getScreenWidth", a -> LuaValue.valueOf(new ScaledResolution(mc).getScaledWidth()));
        fn(t, "getScreenHeight", a -> LuaValue.valueOf(new ScaledResolution(mc).getScaledHeight()));

        fn(t, "drawString", a -> {
            mc.fontRendererObj.drawStringWithShadow(
                    a.arg(1).tojstring(),
                    (float) a.arg(2).todouble(),
                    (float) a.arg(3).todouble(),
                    a.narg() >= 4 ? a.arg(4).toint() : 0xFFFFFFFF);
            return nil();
        });
        fn(t, "getStringWidth", a ->
                LuaValue.valueOf(mc.fontRendererObj.getStringWidth(a.arg(1).tojstring())));

        fn(t, "drawRect", a -> {
            RenderUtil.drawRect(
                    (float) a.arg(1).todouble(),
                    (float) a.arg(2).todouble(),
                    (float) a.arg(3).todouble(),
                    (float) a.arg(4).todouble(),
                    a.arg(5).toint());
            return nil();
        });

        fn(t, "color", a -> {
            int alpha = a.narg() >= 4 ? a.arg(4).toint() : 255;
            return LuaValue.valueOf((alpha << 24) | (a.arg(1).toint() << 16)
                    | (a.arg(2).toint() << 8) | a.arg(3).toint());
        });

        return t;
    }

    private static LuaTable buildUtil() {
        LuaTable t = new LuaTable();

        fn(t, "getTime", a -> LuaValue.valueOf(System.currentTimeMillis()));
        fn(t, "random", a -> {
            double min = a.arg(1).todouble();
            double max = a.arg(2).todouble();
            return LuaValue.valueOf(min + Math.random() * (max - min));
        });
        fn(t, "wrapAngle", a ->
                LuaValue.valueOf(net.minecraft.util.MathHelper.wrapAngleTo180_float((float) a.arg(1).todouble())));
        fn(t, "getRotationsTo", a -> {
            if (!inWorld()) {
                return nil();
            }
            double x = a.arg(1).todouble();
            double y = a.arg(2).todouble();
            double z = a.arg(3).todouble();
            double dx = x - mc.thePlayer.posX;
            double dy = y - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
            double dz = z - mc.thePlayer.posZ;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
            LuaTable result = new LuaTable();
            result.set("yaw", LuaValue.valueOf(RotationUtil.quantizeAngle(yaw)));
            result.set("pitch", LuaValue.valueOf(RotationUtil.quantizeAngle(pitch)));
            return result;
        });

        return t;
    }
}