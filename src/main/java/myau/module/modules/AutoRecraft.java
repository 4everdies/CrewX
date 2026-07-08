package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Random;

public class AutoRecraft extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    public final IntProperty startMinDelay = new IntProperty("start-min-delay", 30, 1, 400);
    public final IntProperty startMaxDelay = new IntProperty("start-max-delay", 42, 1, 400);
    public final IntProperty recraftMinDelay = new IntProperty("recraft-min-delay", 30, 1, 400);
    public final IntProperty recraftMaxDelay = new IntProperty("recraft-max-delay", 42, 1, 400);
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"AUTOMATIC", "MANUAL", "BOTH"});
    public final IntProperty manualBind = new IntProperty("manual-bind", 0, 0, 255,
            () -> this.mode.getValue() != 0);
    public final IntProperty startWith = new IntProperty("start-with", 3, 0, 41);
    public final BooleanProperty cactusMode = new BooleanProperty("cactus", false);
    public final BooleanProperty cocoaMode = new BooleanProperty("cocoa", true);
    public final BooleanProperty mushroomMode = new BooleanProperty("mushroom", true);

    private long startLastMs = 0L;
    private long recraftLastMs = 0L;
    private long recraftDelay = 0L;
    private int step = 1;
    private final HashMap<String, Integer> recraftMap = new HashMap<>();
    private boolean start = false;

    public AutoRecraft() {
        super("AutoRecraft", false);
    }

    public boolean isRunning() { return this.start; }

    private long randomDelay(int mn, int mx) {
        int a = Math.min(mn, mx);
        int b = Math.max(mn, mx);
        return b <= a ? a : a + RANDOM.nextInt(b - a + 1);
    }

    private int getTotalSoupsInInventory() {
        int c = 0;
        for (int i = 9; i < 45; i++) {
            ItemStack s = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (s != null && s.getItem() instanceof ItemSoup) c++;
        }
        return c;
    }

    private boolean hasCocoaRecraft() {
        boolean bowl = false, cocoa = false;
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s == null) continue;
            if (s.getItem() instanceof ItemDye
                    && EnumDyeColor.byDyeDamage(s.getMetadata()) == EnumDyeColor.BROWN) cocoa = true;
            else if (s.getItem() == Items.bowl) bowl = true;
        }
        return bowl && cocoa;
    }

    private boolean hasMushroomRecraft() {
        boolean bowl = false, red = false, brown = false;
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s == null) continue;
            if (s.getItem() instanceof ItemBlock) {
                Block b = ((ItemBlock) s.getItem()).getBlock();
                if (b == Blocks.red_mushroom) red = true;
                else if (b == Blocks.brown_mushroom) brown = true;
            } else if (s.getItem() == Items.bowl) {
                bowl = true;
            }
        }
        return bowl && red && brown;
    }

    private boolean hasCactusRecraft() {
        boolean bowl = false, cactus = false;
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s == null) continue;
            if (s.getItem() instanceof ItemBlock
                    && ((ItemBlock) s.getItem()).getBlock() == Blocks.cactus) cactus = true;
            else if (s.getItem() == Items.bowl) bowl = true;
        }
        return bowl && cactus;
    }

    private void buildRecraftMap(int m) {
        recraftMap.clear();
        for (int i = 9; i < 45; i++) {
            ItemStack s = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (s == null) continue;
            switch (m) {
                case 1:
                    if (s.getItem() == Items.bowl) recraftMap.putIfAbsent("bowl", i);
                    else if (s.getItem() instanceof ItemBlock
                            && ((ItemBlock) s.getItem()).getBlock() == Blocks.cactus)
                        recraftMap.putIfAbsent("cactus", i);
                    break;
                case 2:
                    if (s.getItem() == Items.bowl) recraftMap.putIfAbsent("bowl", i);
                    else if (s.getItem() instanceof ItemDye
                            && EnumDyeColor.byDyeDamage(s.getMetadata()) == EnumDyeColor.BROWN)
                        recraftMap.putIfAbsent("cocoa", i);
                    break;
                case 3:
                    if (s.getItem() == Items.bowl) recraftMap.putIfAbsent("bowl", i);
                    else if (s.getItem() instanceof ItemBlock) {
                        Block b = ((ItemBlock) s.getItem()).getBlock();
                        if (b == Blocks.red_mushroom) recraftMap.putIfAbsent("red", i);
                        else if (b == Blocks.brown_mushroom) recraftMap.putIfAbsent("brown", i);
                    }
                    break;
            }
        }
    }

    private void click(int slot, int button, int type) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, button, type, mc.thePlayer);
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

        if (start) {
            if (recraftMap.isEmpty()) {
                reset();
                return;
            }
            if (System.currentTimeMillis() - recraftLastMs < recraftDelay) return;
            recraftDelay = randomDelay(recraftMinDelay.getValue(), recraftMaxDelay.getValue());
            recraftLastMs = System.currentTimeMillis();

            if (recraftMap.size() == 2) {

                switch (step) {
                    case 1: click(recraftMap.get("bowl"), 1, 0); step++; break;
                    case 2: click(1, 0, 0); step++; break;
                    case 3:
                        if (cactusMode.getValue() && recraftMap.containsKey("cactus"))
                            click(recraftMap.get("cactus"), 1, 0);
                        else if (cocoaMode.getValue() && recraftMap.containsKey("cocoa"))
                            click(recraftMap.get("cocoa"), 1, 0);
                        step++;
                        break;
                    case 4: click(2, 0, 0); step++; break;
                    case 5: click(0, 0, 1); step++; break;
                    case 6:
                        if (mc.thePlayer.inventoryContainer.getSlot(2).getStack() == null) { step++; break; }
                        click(2, 0, 1);
                        break;
                    case 7:
                        if (mc.thePlayer.inventoryContainer.getSlot(1).getStack() == null) { step++; break; }
                        click(1, 0, 1);
                        break;
                    case 8: reset(); break;
                }
            } else if (recraftMap.size() == 3) {

                switch (step) {
                    case 1: click(recraftMap.get("bowl"), 1, 0); step++; break;
                    case 2: click(1, 0, 0); step++; break;
                    case 3: click(recraftMap.get("red"), 1, 0); step++; break;
                    case 4: click(2, 0, 0); step++; break;
                    case 5: click(recraftMap.get("brown"), 1, 0); step++; break;
                    case 6: click(3, 0, 0); step++; break;
                    case 7: click(0, 0, 1); step++; break;
                    case 8:
                        if (mc.thePlayer.inventoryContainer.getSlot(3).getStack() == null) { step++; break; }
                        click(3, 0, 1);
                        break;
                    case 9:
                        if (mc.thePlayer.inventoryContainer.getSlot(2).getStack() == null) { step++; break; }
                        click(2, 0, 1);
                        break;
                    case 10:
                        if (mc.thePlayer.inventoryContainer.getSlot(1).getStack() == null) { step++; break; }
                        click(1, 0, 1);
                        break;
                    case 11: reset(); break;
                }
            }
        } else {
            boolean auto = mode.getValue() != 1
                    && getTotalSoupsInInventory() <= startWith.getValue();
            boolean manual = mode.getValue() != 0
                    && manualBind.getValue() > 0
                    && Keyboard.isKeyDown(manualBind.getValue());
            if (!(auto || manual)) return;

            if (System.currentTimeMillis() - startLastMs < randomDelay(startMinDelay.getValue(), startMaxDelay.getValue()))
                return;
            startLastMs = System.currentTimeMillis();

            if (cactusMode.getValue() && hasCactusRecraft()) buildRecraftMap(1);
            else if (cocoaMode.getValue() && hasCocoaRecraft()) buildRecraftMap(2);
            else if (mushroomMode.getValue() && hasMushroomRecraft()) buildRecraftMap(3);
            else return;

            recraftDelay = randomDelay(recraftMinDelay.getValue(), recraftMaxDelay.getValue());
            recraftLastMs = System.currentTimeMillis();
            step = 1;
            start = true;
        }
    }

    private void reset() {
        this.recraftMap.clear();
        this.start = false;
        this.step = 1;
        this.recraftDelay = 0L;
        this.recraftLastMs = 0L;
        this.startLastMs = 0L;
    }

    @Override
    public void onDisabled() { reset(); }
}