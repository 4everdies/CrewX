package myau.script;

import myau.Myau;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ButtonProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.TextProperty;
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

    /** Gameplay mutation from Lua is intentionally limited to local worlds. */
    private static boolean canModifyGameplay() {
        return inWorld() && mc.isSingleplayer();
    }

    public static void install(LuaScript script, ScriptModule module) {
        LuaValue globals = script.getGlobals();
        globals.set("script", buildScript(script, module));
        globals.set("client", buildClient());
        globals.set("world", buildWorld());
        globals.set("modules", buildModules());
        globals.set("render", buildRender());
        globals.set("render3d", buildRender3D());
        globals.set("images", buildImages());
        globals.set("skins", buildSkins(script));
        globals.set("gui", buildGui(script));
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

        fn(t, "registerText", a -> {
            module.addProperty(new TextProperty(a.arg(1).tojstring(), a.narg() >= 2 ? a.arg(2).tojstring() : ""));
            return nil();
        });
        fn(t, "registerColor", a -> {
            module.addProperty(new ColorProperty(a.arg(1).tojstring(), a.arg(2).toint() & 0xFFFFFF));
            return nil();
        });
        fn(t, "registerButton", a -> {
            String label = a.arg(1).tojstring();
            String callback = a.narg() >= 2 ? a.arg(2).tojstring() : "onButton";
            module.addProperty(new ButtonProperty(label, () -> script.call(callback)));
            return nil();
        });

        fn(t, "get", a -> {
            myau.property.Property<?> property = module.getProperty(a.arg(1).tojstring());
            if (property == null) {
                return nil();
            }
            if (property instanceof ModeProperty) {
                return LuaValue.valueOf(((ModeProperty) property).getModeString());
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

        fn(t, "has", a -> LuaValue.valueOf(module.getProperty(a.arg(1).tojstring()) != null));
        fn(t, "set", a -> {
            myau.property.Property<?> property = module.getProperty(a.arg(1).tojstring());
            if (property == null || property instanceof ButtonProperty) return LuaValue.FALSE;
            LuaValue value = a.arg(2);
            Object javaValue;
            if (property instanceof ModeProperty && value.isstring()) {
                return LuaValue.valueOf(property.parseString(value.tojstring()));
            }
            if (property instanceof BooleanProperty) javaValue = value.toboolean();
            else if (property instanceof IntProperty || property instanceof PercentProperty || property instanceof ModeProperty) javaValue = value.toint();
            else if (property instanceof FloatProperty) javaValue = (float) value.todouble();
            else if (property instanceof ColorProperty) javaValue = value.toint() & 0xFFFFFF;
            else javaValue = value.tojstring();
            return LuaValue.valueOf(property.setValue(javaValue));
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

        fn(t, "isSingleplayer", a -> LuaValue.valueOf(mc.isSingleplayer()));
        fn(t, "canModifyGameplay", a -> LuaValue.valueOf(canModifyGameplay()));
        fn(t, "isThirdPerson", a -> LuaValue.valueOf(mc.gameSettings.thirdPersonView != 0));

        fn(t, "getX", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.posX) : nil());
        fn(t, "getY", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.posY) : nil());
        fn(t, "getZ", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.posZ) : nil());
        fn(t, "getMotionX", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.motionX) : nil());
        fn(t, "getMotionY", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.motionY) : nil());
        fn(t, "getMotionZ", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.motionZ) : nil());
        fn(t, "setMotionX", a -> {
            if (canModifyGameplay()) mc.thePlayer.motionX = a.arg(1).todouble();
            return nil();
        });
        fn(t, "setMotionY", a -> {
            if (canModifyGameplay()) mc.thePlayer.motionY = a.arg(1).todouble();
            return nil();
        });
        fn(t, "setMotionZ", a -> {
            if (canModifyGameplay()) mc.thePlayer.motionZ = a.arg(1).todouble();
            return nil();
        });

        fn(t, "getYaw", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.rotationYaw) : nil());
        fn(t, "getPitch", a -> inWorld() ? LuaValue.valueOf(mc.thePlayer.rotationPitch) : nil());

        fn(t, "getRenderX", a -> {
            if (!inWorld()) return nil();
            float partial = a.narg() >= 1 ? (float) a.arg(1).todouble() : Render3D.partialTicks();
            return LuaValue.valueOf(mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partial);
        });
        fn(t, "getRenderY", a -> {
            if (!inWorld()) return nil();
            float partial = a.narg() >= 1 ? (float) a.arg(1).todouble() : Render3D.partialTicks();
            return LuaValue.valueOf(mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partial);
        });
        fn(t, "getRenderZ", a -> {
            if (!inWorld()) return nil();
            float partial = a.narg() >= 1 ? (float) a.arg(1).todouble() : Render3D.partialTicks();
            return LuaValue.valueOf(mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partial);
        });
        fn(t, "setRotation", a -> {
            if (canModifyGameplay()) {
                mc.thePlayer.rotationYaw = (float) a.arg(1).todouble();
                mc.thePlayer.rotationPitch = net.minecraft.util.MathHelper.clamp_float((float) a.arg(2).todouble(), -90.0F, 90.0F);
            }
            return LuaValue.valueOf(canModifyGameplay());
        });
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
            if (canModifyGameplay()) {
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

        fn(t, "getSelf", a -> inWorld() ? buildEntity(mc.thePlayer) : nil());

        fn(t, "getPlayer", a -> {
            if (!inWorld()) return nil();
            Entity entity = resolveEntity(a.arg(1));
            return entity instanceof EntityPlayer ? buildEntity(entity) : nil();
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
        t.set("id", LuaValue.valueOf(entity.getEntityId()));
        t.set("name", LuaValue.valueOf(entity.getName()));
        t.set("uuid", LuaValue.valueOf(entity.getUniqueID().toString()));
        t.set("self", LuaValue.valueOf(entity == mc.thePlayer));
        t.set("x", LuaValue.valueOf(entity.posX));
        t.set("y", LuaValue.valueOf(entity.posY));
        t.set("z", LuaValue.valueOf(entity.posZ));
        t.set("lastX", LuaValue.valueOf(entity.lastTickPosX));
        t.set("lastY", LuaValue.valueOf(entity.lastTickPosY));
        t.set("lastZ", LuaValue.valueOf(entity.lastTickPosZ));
        float partial = Render3D.partialTicks();
        t.set("renderX", LuaValue.valueOf(entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial));
        t.set("renderY", LuaValue.valueOf(entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial));
        t.set("renderZ", LuaValue.valueOf(entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial));
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
            if (module != null && (module instanceof ScriptModule || canModifyGameplay())) {
                module.setEnabled(a.arg(2).toboolean());
                return LuaValue.TRUE;
            }
            return LuaValue.FALSE;
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
            int red = net.minecraft.util.MathHelper.clamp_int(a.arg(1).toint(), 0, 255);
            int green = net.minecraft.util.MathHelper.clamp_int(a.arg(2).toint(), 0, 255);
            int blue = net.minecraft.util.MathHelper.clamp_int(a.arg(3).toint(), 0, 255);
            alpha = net.minecraft.util.MathHelper.clamp_int(alpha, 0, 255);
            return LuaValue.valueOf((alpha << 24) | (red << 16) | (green << 8) | blue);
        });
        fn(t, "argb", a -> {
            int rgb = a.arg(1).toint() & 0xFFFFFF;
            int alpha = net.minecraft.util.MathHelper.clamp_int(a.narg() >= 2 ? a.arg(2).toint() : 255, 0, 255);
            return LuaValue.valueOf((alpha << 24) | rgb);
        });
        fn(t, "drawRoundedRect", a -> {
            myau.clickgui.render.RoundedUtils.drawRoundedRect(a.arg(1).todouble(), a.arg(2).todouble(),
                    a.arg(3).todouble(), a.arg(4).todouble(), a.arg(5).toint(),
                    a.narg() >= 6 ? (float) a.arg(6).todouble() : 4.0F);
            return nil();
        });
        fn(t, "drawCircle", a -> {
            myau.clickgui.render.RenderUtils.drawCircle(a.arg(1).todouble(), a.arg(2).todouble(),
                    a.arg(3).todouble(), a.arg(4).toint());
            return nil();
        });
        fn(t, "drawGradientRect", a -> {
            myau.clickgui.render.RenderUtils.drawGradientRect(a.arg(1).todouble(), a.arg(2).todouble(),
                    a.arg(3).todouble(), a.arg(4).todouble(), a.arg(5).toint(), a.arg(6).toint(),
                    a.arg(7).toint(), a.arg(8).toint());
            return nil();
        });
        fn(t, "drawImage", a -> {
            net.minecraft.util.ResourceLocation texture = RemoteImageCache.texture(a.arg(1).tojstring());
            if (texture == null) return LuaValue.FALSE;
            myau.clickgui.render.RenderUtils.drawImage(a.arg(2).todouble(), a.arg(3).todouble(),
                    a.arg(4).todouble(), a.arg(5).todouble(), texture,
                    a.narg() >= 6 ? a.arg(6).toint() : 0xFFFFFFFF);
            return LuaValue.TRUE;
        });

        return t;
    }

    // ------------------------------------------------------------------
    // render3d.*  — geometria no mundo. So faz sentido dentro de onRender3D.
    // Ciclo: render3d.begin() -> primitivas -> render3d.finish().
    // ------------------------------------------------------------------
    private static LuaTable buildRender3D() {
        LuaTable t = new LuaTable();

        fn(t, "begin", a -> LuaValue.valueOf(Render3D.begin(a.narg() >= 1 && a.arg(1).toboolean())));
        fn(t, "finish", a -> { Render3D.finish(); return nil(); });
        fn(t, "isActive", a -> LuaValue.valueOf(Render3D.isActive()));
        fn(t, "push", a -> { Render3D.push(); return nil(); });
        fn(t, "pop", a -> { Render3D.pop(); return nil(); });

        fn(t, "translateWorld", a -> {
            Render3D.translateWorld(a.arg(1).todouble(), a.arg(2).todouble(), a.arg(3).todouble());
            return nil();
        });
        fn(t, "translateEntity", a -> {
            Entity entity = resolveEntity(a.arg(1));
            float partial = a.narg() >= 3 ? (float) a.arg(3).todouble() : Render3D.partialTicks();
            double yOffset = a.narg() >= 2 ? a.arg(2).todouble() : 0.0D;
            Render3D.translateEntity(entity, partial, yOffset);
            return LuaValue.valueOf(entity != null);
        });
        fn(t, "translate", a -> {
            Render3D.translate(a.arg(1).todouble(), a.arg(2).todouble(), a.arg(3).todouble());
            return nil();
        });
        fn(t, "rotate", a -> {
            Render3D.rotate((float) a.arg(1).todouble(), (float) a.arg(2).todouble(),
                    (float) a.arg(3).todouble(), (float) a.arg(4).todouble());
            return nil();
        });
        fn(t, "scale", a -> {
            Render3D.scale((float) a.arg(1).todouble(), (float) a.arg(2).todouble(), (float) a.arg(3).todouble());
            return nil();
        });

        fn(t, "cube", a -> {
            Render3D.cube((float) a.arg(1).todouble(), a.arg(2).toint());
            return nil();
        });
        fn(t, "box", a -> {
            Render3D.box((float) a.arg(1).todouble(), (float) a.arg(2).todouble(), (float) a.arg(3).todouble(),
                    (float) a.arg(4).todouble(), (float) a.arg(5).todouble(), (float) a.arg(6).todouble(),
                    a.arg(7).toint());
            return nil();
        });
        fn(t, "wireBox", a -> {
            Render3D.wireBox((float) a.arg(1).todouble(), (float) a.arg(2).todouble(), (float) a.arg(3).todouble(),
                    (float) a.arg(4).todouble(), (float) a.arg(5).todouble(), (float) a.arg(6).todouble(),
                    a.arg(7).toint(), a.narg() >= 8 ? (float) a.arg(8).todouble() : 1.5F);
            return nil();
        });
        fn(t, "billboardImage", a -> {
            net.minecraft.util.ResourceLocation texture = RemoteImageCache.texture(a.arg(1).tojstring());
            return LuaValue.valueOf(Render3D.billboardImage(texture, (float) a.arg(2).todouble(),
                    (float) a.arg(3).todouble(), a.narg() >= 4 ? a.arg(4).toint() : 0xFFFFFFFF));
        });

        fn(t, "sphere", a -> {
            Render3D.sphere((float) a.arg(1).todouble(), a.arg(2).toint(), a.arg(3).toint(), a.arg(4).toint());
            return nil();
        });
        fn(t, "cylinder", a -> {
            Render3D.cylinder((float) a.arg(1).todouble(), (float) a.arg(2).todouble(),
                    a.arg(3).toint(), a.arg(4).toint());
            return nil();
        });
        fn(t, "line", a -> {
            Render3D.line(a.arg(1).todouble(), a.arg(2).todouble(), a.arg(3).todouble(),
                    a.arg(4).todouble(), a.arg(5).todouble(), a.arg(6).todouble(),
                    a.arg(7).toint(), a.narg() >= 8 ? (float) a.arg(8).todouble() : 2.0F);
            return nil();
        });
        fn(t, "quad", a -> {
            Render3D.quad(
                    tripleFrom(a.arg(1)), tripleFrom(a.arg(2)),
                    tripleFrom(a.arg(3)), tripleFrom(a.arg(4)),
                    a.arg(5).toint());
            return nil();
        });

        fn(t, "getPartialTicks", a -> LuaValue.valueOf(Render3D.partialTicks()));

        return t;
    }

    private static LuaTable buildImages() {
        LuaTable t = new LuaTable();
        fn(t, "fetch", a -> LuaValue.valueOf(RemoteImageCache.fetch(a.arg(1).tojstring(), a.arg(2).tojstring())));
        fn(t, "status", a -> LuaValue.valueOf(RemoteImageCache.status(a.arg(1).tojstring())));
        fn(t, "isReady", a -> LuaValue.valueOf("ready".equals(RemoteImageCache.status(a.arg(1).tojstring()))));
        fn(t, "getError", a -> LuaValue.valueOf(RemoteImageCache.error(a.arg(1).tojstring())));
        fn(t, "remove", a -> LuaValue.valueOf(RemoteImageCache.remove(a.arg(1).tojstring())));
        fn(t, "clear", a -> { RemoteImageCache.clear(); return nil(); });
        return t;
    }

    private static LuaTable buildSkins(LuaScript script) {
        LuaTable t = new LuaTable();
        fn(t, "fetch", a -> {
            Entity entity = resolveEntity(a.arg(1));
            if (!(entity instanceof EntityPlayer)) return LuaValue.FALSE;
            String key = a.arg(2).tojstring();
            String url = a.arg(3).tojstring();
            String model = a.narg() >= 4 ? a.arg(4).tojstring() : "default";
            if (!RemoteImageCache.fetch(key, url)) return LuaValue.FALSE;
            return LuaValue.valueOf(ScriptSkinOverrides.set(script, (EntityPlayer) entity, key, model));
        });
        fn(t, "set", a -> {
            Entity entity = resolveEntity(a.arg(1));
            if (!(entity instanceof EntityPlayer)) return LuaValue.FALSE;
            String model = a.narg() >= 3 ? a.arg(3).tojstring() : "default";
            return LuaValue.valueOf(ScriptSkinOverrides.set(
                    script, (EntityPlayer) entity, a.arg(2).tojstring(), model));
        });
        fn(t, "clear", a -> {
            Entity entity = resolveEntity(a.arg(1));
            return LuaValue.valueOf(entity instanceof EntityPlayer
                    && ScriptSkinOverrides.clear((EntityPlayer) entity));
        });
        fn(t, "clearAll", a -> {
            ScriptSkinOverrides.clearAll();
            return nil();
        });
        fn(t, "getKey", a -> {
            Entity entity = resolveEntity(a.arg(1));
            return entity instanceof EntityPlayer
                    ? LuaValue.valueOf(ScriptSkinOverrides.getTextureKey((EntityPlayer) entity))
                    : LuaValue.valueOf("");
        });
        fn(t, "status", a -> LuaValue.valueOf(RemoteImageCache.status(a.arg(1).tojstring())));
        fn(t, "isReady", a -> LuaValue.valueOf("ready".equals(RemoteImageCache.status(a.arg(1).tojstring()))));
        return t;
    }

    private static LuaTable buildGui(LuaScript script) {
        LuaTable t = new LuaTable();
        fn(t, "open", a -> {
            ScriptGuiScreen.open(script);
            return nil();
        });
        fn(t, "close", a -> {
            ScriptGuiScreen.close(script);
            return nil();
        });
        fn(t, "isOpen", a -> LuaValue.valueOf(ScriptGuiScreen.current(script) != null));
        fn(t, "getWidth", a -> {
            ScriptGuiScreen screen = ScriptGuiScreen.current(script);
            return LuaValue.valueOf(screen == null
                    ? new ScaledResolution(mc).getScaledWidth()
                    : screen.width);
        });
        fn(t, "getHeight", a -> {
            ScriptGuiScreen screen = ScriptGuiScreen.current(script);
            return LuaValue.valueOf(screen == null
                    ? new ScaledResolution(mc).getScaledHeight()
                    : screen.height);
        });
        fn(t, "getMouseX", a -> {
            ScriptGuiScreen screen = ScriptGuiScreen.current(script);
            return LuaValue.valueOf(screen == null ? -1 : screen.getMouseX());
        });
        fn(t, "getMouseY", a -> {
            ScriptGuiScreen screen = ScriptGuiScreen.current(script);
            return LuaValue.valueOf(screen == null ? -1 : screen.getMouseY());
        });
        fn(t, "isHovering", a -> {
            ScriptGuiScreen screen = ScriptGuiScreen.current(script);
            if (screen == null) return LuaValue.FALSE;
            double x = a.arg(1).todouble();
            double y = a.arg(2).todouble();
            double width = a.arg(3).todouble();
            double height = a.arg(4).todouble();
            return LuaValue.valueOf(screen.getMouseX() >= x && screen.getMouseX() <= x + width
                    && screen.getMouseY() >= y && screen.getMouseY() <= y + height);
        });
        return t;
    }

    private static Entity resolveEntity(LuaValue value) {
        if (!inWorld() || value == null || value.isnil()) return null;
        if (value.isnumber()) {
            return mc.theWorld.getEntityByID(value.toint());
        }
        if (value.istable()) {
            LuaValue id = value.get("id");
            if (id.isnumber()) return mc.theWorld.getEntityByID(id.toint());
            LuaValue name = value.get("name");
            if (!name.isnil()) return resolveEntity(name);
            return null;
        }
        String query = value.tojstring();
        if (query.equalsIgnoreCase("self") || query.equalsIgnoreCase("local")) return mc.thePlayer;
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof Entity)) continue;
            Entity entity = (Entity) object;
            if (entity.getName().equalsIgnoreCase(query) || entity.getUniqueID().toString().equalsIgnoreCase(query)) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Le uma tabela Lua {x, y, z} como um vetor de 3 doubles.
     */
    private static double[] tripleFrom(LuaValue value) {
        if (!value.istable()) {
            return new double[]{0, 0, 0};
        }
        LuaTable table = value.checktable();
        return new double[]{
                table.get(1).todouble(),
                table.get(2).todouble(),
                table.get(3).todouble()
        };
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