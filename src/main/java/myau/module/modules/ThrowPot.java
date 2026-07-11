package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.lwjgl.input.Keyboard;

public class ThrowPot extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty delay = new FloatProperty("delay", 400.0F, 0.0F, 2000.0F);
    private boolean throwQueued = false;
    private long lastThrow = 0L;

    public ThrowPot() {
        super("ThrowPot", false);
    }

    @Override
    public boolean toggle() {
        if (this.isEnabled()) {
            this.throwQueued = true;
            return false;
        }
        return super.toggle();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        if (this.throwQueued || (this.key != 0 && Keyboard.isKeyDown(this.key))) {
            this.throwQueued = false;
            if (System.currentTimeMillis() - lastThrow < delay.getValue()) return;

            int originalSlot = mc.thePlayer.inventory.currentItem;
            for (int i = 36; i < 45; i++) {
                ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                if (stack != null && stack.getItem() instanceof ItemPotion
                        && ItemPotion.isSplash(stack.getMetadata())
                        && hasHealEffect(stack)) {
                    int potSlot = i - 36;
                    mc.thePlayer.inventory.currentItem = potSlot;
                    mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(potSlot));
                    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
                    break;
                }
            }
            mc.thePlayer.inventory.currentItem = originalSlot;
            mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(originalSlot));
            lastThrow = System.currentTimeMillis();
            this.setEnabled(false);
        }
    }

    private boolean hasHealEffect(ItemStack stack) {
        for (PotionEffect effect : ((ItemPotion) stack.getItem()).getEffects(stack)) {
            if (effect.getPotionID() == Potion.heal.id) return true;
        }
        return false;
    }
}
