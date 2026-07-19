package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemSoup;
import org.lwjgl.input.Keyboard;

public class AutoSoup extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"AUTOMATIC", "MANUAL"});
    public final IntProperty manualBind = new IntProperty("manual-bind", 0, 0, 255, () -> this.mode.getValue() != 0);
    public final IntProperty health = new IntProperty("health", 10, 1, 20);
    public final BooleanProperty dropSoup = new BooleanProperty("drop-soup", true);
    public final IntProperty healDelay = new IntProperty("heal-delay", 50, 1, 400);
    public final IntProperty dropDelay = new IntProperty("drop-delay", 50, 1, 400);
    public final IntProperty switchDelay = new IntProperty("switch-delay", 50, 1, 400);

    private long lastActionMs = 0L;
    private int soupIndex = Integer.MIN_VALUE;
    private int originalIndex = Integer.MIN_VALUE;
    private int step = 1;
    private boolean start = false;
    private boolean auraWasEnabled = false;

    public AutoSoup() {
        super("AutoSoup", false);
    }

    private int getSoupInHotbar() {
        for (int i = 36; i < 45; i++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stack != null && stack.getItem() instanceof ItemSoup) {
                return i - 36;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean timeElapsed(int ms) {
        return System.currentTimeMillis() - this.lastActionMs >= ms;
    }

    private void resetTimer() {
        this.lastActionMs = System.currentTimeMillis();
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        this.reset();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;

        if (start) {
            Module killAura = Myau.moduleManager.getModule("KillAura");
            if (step == 1 && killAura != null && killAura.isEnabled()) {
                auraWasEnabled = true;
                killAura.setEnabled(false);
            }

            if (step >= 2 && step <= 3 && mc.thePlayer.inventory.currentItem != soupIndex) {
                mc.thePlayer.inventory.currentItem = soupIndex;
            }

            switch (step) {
                case 1:
                    if (timeElapsed(switchDelay.getValue())) {
                        mc.thePlayer.inventory.currentItem = soupIndex;
                        resetTimer();
                        step++;
                    }
                    break;
                case 2:
                    if (timeElapsed(healDelay.getValue())) {
                        if (mc.gameSettings.keyBindUseItem.isKeyDown()) {
                            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                            resetTimer();
                            step++;
                            break;
                        }
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    }
                    break;
                case 3:
                    if (dropSoup.getValue()) {
                        if (timeElapsed(dropDelay.getValue())) {
                            C07PacketPlayerDigging.Action action = GuiScreen.isCtrlKeyDown()
                                    ? C07PacketPlayerDigging.Action.DROP_ALL_ITEMS
                                    : C07PacketPlayerDigging.Action.DROP_ITEM;
                            mc.getNetHandler().getNetworkManager().sendPacket(
                                    new C07PacketPlayerDigging(action, BlockPos.ORIGIN, EnumFacing.DOWN));
                            resetTimer();
                            step++;
                        }
                    } else {
                        step++;
                    }
                    break;
                case 4:
                    if (timeElapsed(switchDelay.getValue())) {
                        mc.thePlayer.inventory.currentItem = originalIndex;
                        resetTimer();
                        step++;
                    }
                    break;
                case 5:
                    if (killAura != null && auraWasEnabled) {
                        killAura.setEnabled(true);
                    }
                    reset();
                    break;
            }
        } else {
            soupIndex = getSoupInHotbar();
            if (soupIndex != Integer.MIN_VALUE) {
                boolean auto = mode.getValue() == 0 && mc.thePlayer.getHealth() <= health.getValue();
                boolean manual = mode.getValue() == 1 && manualBind.getValue() > 0
                        && Keyboard.isKeyDown(manualBind.getValue());
                if (auto || manual) {
                    originalIndex = mc.thePlayer.inventory.currentItem;
                    start = true;
                    auraWasEnabled = false;
                    resetTimer();
                }
            }
        }
    }

    private void reset() {
        this.originalIndex = Integer.MIN_VALUE;
        this.soupIndex = Integer.MIN_VALUE;
        this.start = false;
        this.auraWasEnabled = false;
        this.step = 1;
        this.lastActionMs = 0L;
    }

    @Override
    public void onDisabled() {
        reset();
    }
}