package crewx.module.modules.player;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.LoadWorldEvent;
import crewx.events.TickEvent;
import crewx.module.Module;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;

/** Uses soup through PlayerController and waits for an inventory change before advancing. */
public class AutoSoup extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long SOUP_USE_TIMEOUT_MS = 1200L;
    private static final long USE_RETRY_INTERVAL_MS = 250L;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Automatic", "Manual"});
    public final IntProperty manualBind = new IntProperty("manual-bind", 0, 0, 255, () -> this.mode.getValue() != 0);
    public final IntProperty health = new IntProperty("health", 10, 1, 20);
    public final IntProperty soupsPerTrigger = new IntProperty("soups-per-trigger", 2, 1, 9);
    public final BooleanProperty dropSoup = new BooleanProperty("drop-soup", true);
    public final IntProperty healDelay = new IntProperty("heal-delay", 50, 1, 400);
    public final IntProperty dropDelay = new IntProperty("drop-delay", 50, 1, 400);
    public final IntProperty switchDelay = new IntProperty("switch-delay", 50, 1, 400);

    private long lastActionMs;
    private long useAttemptStartedAt;
    private long lastUseAttemptMs;
    private int soupIndex = Integer.MIN_VALUE;
    private int originalIndex = Integer.MIN_VALUE;
    private int soupStackBeforeUse;
    private int soupsRemaining;
    private int useAttempts;
    private int step = 1;
    private boolean start;
    private boolean auraWasEnabled;

    public AutoSoup() {
        super("AutoSoup", false);
    }

    public boolean isProcessingSoup() {
        return this.start;
    }

    private int getSoupInHotbar() {
        if (mc.thePlayer == null) return Integer.MIN_VALUE;
        for (int slot = 36; slot < 45; slot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (stack != null && stack.getItem() instanceof ItemSoup) return slot - 36;
        }
        return Integer.MIN_VALUE;
    }

    private int getSoupStackSize(int hotbarIndex) {
        if (mc.thePlayer == null || hotbarIndex < 0 || hotbarIndex > 8) return 0;
        ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(36 + hotbarIndex).getStack();
        return stack != null && stack.getItem() instanceof ItemSoup ? stack.stackSize : 0;
    }

    private boolean currentSoupWasConsumed() {
        return this.soupStackBeforeUse > 0 && this.getSoupStackSize(this.soupIndex) < this.soupStackBeforeUse;
    }

    private boolean timeElapsed(int ms) {
        return System.currentTimeMillis() - this.lastActionMs >= ms;
    }

    private void resetTimer() {
        this.lastActionMs = System.currentTimeMillis();
    }

    private void selectHotbarSlot(int slot) {
        if (mc.thePlayer == null || slot < 0 || slot > 8) return;
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(slot));
            }
        }
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        this.finishSequence();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            if (this.start) this.finishSequence();
            return;
        }

        if (this.start) {
            Module killAura = CrewX.moduleManager == null ? null : CrewX.moduleManager.getModule("KillAura");
            if (this.step == 1 && killAura != null && killAura.isEnabled()) {
                this.auraWasEnabled = true;
                killAura.setEnabled(false);
            }
            switch (this.step) {
                case 1:
                    if (this.timeElapsed(this.switchDelay.getValue())) {
                        if (this.getSoupStackSize(this.soupIndex) <= 0) {
                            this.finishSequence();
                            break;
                        }
                        this.selectHotbarSlot(this.soupIndex);
                        this.resetTimer();
                        this.step = 2;
                    }
                    break;
                case 2:
                    this.selectHotbarSlot(this.soupIndex);
                    if (this.currentSoupWasConsumed()) {
                        this.resetTimer();
                        this.step = 3;
                        break;
                    }
                    long now = System.currentTimeMillis();
                    if (this.useAttemptStartedAt == 0L) {
                        if (this.timeElapsed(this.healDelay.getValue())) this.tryUseSoup(now);
                    } else if (now - this.useAttemptStartedAt >= SOUP_USE_TIMEOUT_MS) {
                        this.finishSequence();
                    } else if (now - this.lastUseAttemptMs >= USE_RETRY_INTERVAL_MS && this.useAttempts < 3) {
                        this.tryUseSoup(now);
                    }
                    break;
                case 3:
                    if (!this.currentSoupWasConsumed()) {
                        if (this.timeElapsed((int) SOUP_USE_TIMEOUT_MS)) this.finishSequence();
                        break;
                    }
                    if (this.dropSoup.getValue()) {
                        if (this.timeElapsed(this.dropDelay.getValue())) {
                            if (mc.getNetHandler() == null) {
                                this.finishSequence();
                                break;
                            }
                            C07PacketPlayerDigging.Action action = GuiScreen.isCtrlKeyDown()
                                    ? C07PacketPlayerDigging.Action.DROP_ALL_ITEMS
                                    : C07PacketPlayerDigging.Action.DROP_ITEM;
                            mc.getNetHandler().getNetworkManager().sendPacket(
                                    new C07PacketPlayerDigging(action, BlockPos.ORIGIN, EnumFacing.DOWN));
                            this.resetTimer();
                            this.step = 4;
                        }
                    } else {
                        this.resetTimer();
                        this.step = 4;
                    }
                    break;
                case 4:
                    if (this.timeElapsed(this.switchDelay.getValue())) {
                        this.restoreOriginalSlot();
                        this.resetTimer();
                        this.step = 5;
                    }
                    break;
                case 5:
                    if (this.soupsRemaining > 1) {
                        int nextSoup = this.getSoupInHotbar();
                        if (nextSoup != Integer.MIN_VALUE) {
                            this.soupsRemaining--;
                            this.soupIndex = nextSoup;
                            this.soupStackBeforeUse = this.getSoupStackSize(nextSoup);
                            this.useAttemptStartedAt = 0L;
                            this.lastUseAttemptMs = 0L;
                            this.useAttempts = 0;
                            this.step = 1;
                            this.resetTimer();
                            break;
                        }
                    }
                    this.finishSequence();
                    break;
                default:
                    this.finishSequence();
                    break;
            }
        } else {
            this.soupIndex = this.getSoupInHotbar();
            if (this.soupIndex == Integer.MIN_VALUE) return;
            boolean auto = this.mode.getValue() == 0 && mc.thePlayer.getHealth() <= this.health.getValue();
            boolean manual = this.mode.getValue() == 1 && this.manualBind.getValue() > 0
                    && Keyboard.isKeyDown(this.manualBind.getValue());
            if (auto || manual) {
                this.originalIndex = mc.thePlayer.inventory.currentItem;
                this.soupsRemaining = this.soupsPerTrigger.getValue();
                this.soupStackBeforeUse = this.getSoupStackSize(this.soupIndex);
                this.useAttemptStartedAt = 0L;
                this.lastUseAttemptMs = 0L;
                this.useAttempts = 0;
                this.start = true;
                this.auraWasEnabled = false;
                this.resetTimer();
            }
        }
    }

    private void tryUseSoup(long now) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null
                || this.getSoupStackSize(this.soupIndex) <= 0) {
            this.finishSequence();
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSoup)) {
            this.selectHotbarSlot(this.soupIndex);
            return;
        }
        if (mc.getNetHandler() == null) {
            this.finishSequence();
            return;
        }
        // sendUseItem dispatches the normal right-click packet and local item action together.
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
        this.useAttempts++;
        this.lastUseAttemptMs = now;
        if (this.useAttemptStartedAt == 0L) this.useAttemptStartedAt = now;
    }

    private void restoreOriginalSlot() {
        if (mc.thePlayer != null && this.originalIndex >= 0 && this.originalIndex < 9) {
            this.selectHotbarSlot(this.originalIndex);
        }
    }

    private void finishSequence() {
        this.restoreOriginalSlot();
        Module killAura = CrewX.moduleManager == null ? null : CrewX.moduleManager.getModule("KillAura");
        boolean restoreAura = this.auraWasEnabled;
        this.reset();
        if (restoreAura && killAura != null) killAura.setEnabled(true);
    }

    private void reset() {
        this.originalIndex = Integer.MIN_VALUE;
        this.soupIndex = Integer.MIN_VALUE;
        this.soupStackBeforeUse = 0;
        this.soupsRemaining = 0;
        this.start = false;
        this.auraWasEnabled = false;
        this.useAttemptStartedAt = 0L;
        this.lastUseAttemptMs = 0L;
        this.useAttempts = 0;
        this.step = 1;
        this.lastActionMs = 0L;
    }

    @Override
    public void onDisabled() {
        this.finishSequence();
    }
}
