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
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Random;

public class AutoRefill extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    public final IntProperty refilMinDelay = new IntProperty("min-delay", 3, 1, 400);
    public final IntProperty refilMaxDelay = new IntProperty("max-delay", 5, 1, 400);
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"AUTOMATIC", "MANUAL", "BOTH"});
    public final IntProperty manualBind = new IntProperty("manual-bind", 0, 0, 255,
            () -> this.mode.getValue() != 0);
    public final IntProperty startWith = new IntProperty("start-with", 4, 0, 9);
    public final BooleanProperty randomize = new BooleanProperty("randomize", false);

    private long lastMs = 0L;
    private long delay = 0L;
    private boolean start = false;

    public AutoRefill() {
        super("AutoRefill", false);
    }

    private long randomDelay() {
        int min = Math.min(refilMinDelay.getValue(), refilMaxDelay.getValue());
        int max = Math.max(refilMinDelay.getValue(), refilMaxDelay.getValue());
        return max <= min ? min : min + RANDOM.nextInt(max - min + 1);
    }

    private int getSoupInInventory() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stack != null && stack.getItem() instanceof ItemSoup) return i;
        }
        return Integer.MIN_VALUE;
    }

    private ArrayList<Integer> getSoupSlots() {
        ArrayList<Integer> list = new ArrayList<>();
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stack != null && stack.getItem() instanceof ItemSoup) list.add(i);
        }
        return list;
    }

    private boolean hasSoupInHotbar() {
        for (int i = 36; i < 45; i++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stack != null && stack.getItem() instanceof ItemSoup) return true;
        }
        return false;
    }

    private boolean isHotbarFull() {
        int counter = 0;
        for (int i = 36; i < 45; i++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stack != null) counter++;
        }
        return counter == 9;
    }

    private int getSoupAmountInHotbar() {
        int c = 0;
        for (int i = 36; i < 45; i++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stack != null && stack.getItem() instanceof ItemSoup) c++;
        }
        return c;
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) { reset(); }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) return;
        if (!(mc.currentScreen instanceof GuiInventory)) {
            reset();
            return;
        }

        AutoRecraft autoRecraft = (AutoRecraft) Myau.moduleManager.modules.get(AutoRecraft.class);
        if (autoRecraft != null && autoRecraft.isEnabled() && autoRecraft.isRunning()) {
            reset();
            return;
        }

        if (start) {
            if (hasSoupInHotbar() && isHotbarFull()) {
                start = false;
                return;
            }
            if (System.currentTimeMillis() - lastMs >= delay) {
                delay = randomDelay();
                lastMs = System.currentTimeMillis();

                if (randomize.getValue()) {
                    ArrayList<Integer> slots = getSoupSlots();
                    if (!slots.isEmpty()) {
                        mc.playerController.windowClick(
                                mc.thePlayer.inventoryContainer.windowId,
                                slots.get(RANDOM.nextInt(slots.size())), 0, 1, mc.thePlayer);
                    } else {
                        start = false;
                    }
                } else {
                    int slot = getSoupInInventory();
                    if (slot != Integer.MIN_VALUE) {
                        mc.playerController.windowClick(
                                mc.thePlayer.inventoryContainer.windowId, slot, 0, 1, mc.thePlayer);
                    } else {
                        start = false;
                    }
                }
            }
        } else {
            boolean manual = mode.getValue() != 0 && manualBind.getValue() > 0
                    && Keyboard.isKeyDown(manualBind.getValue());
            boolean auto = mode.getValue() != 1 && getSoupAmountInHotbar() <= startWith.getValue();
            if (manual || auto) {
                start = true;
                lastMs = System.currentTimeMillis();
                delay = randomDelay();
            }
        }
    }

    private void reset() {
        this.start = false;
        this.delay = 0L;
        this.lastMs = 0L;
    }

    @Override
    public void onDisabled() { reset(); }
}