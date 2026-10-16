package crewx.module.modules.player;

import crewx.events.KeyEvent;
import crewx.event.EventTarget;
import crewx.module.Module;
import crewx.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/** Selects the next soup in the hotbar each time the configured key is pressed. */
public class SoupHotkey extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty activationBind = new IntProperty("activation-bind", 0, 0, 255);

    public SoupHotkey() {
        super("SoupHotkey", false);
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (!this.isEnabled() || event.getKey() == 0 || event.getKey() != activationBind.getValue()
                || mc.thePlayer == null || mc.currentScreen != null) return;
        int current = mc.thePlayer.inventory.currentItem;
        for (int offset = 1; offset <= 9; offset++) {
            int slot = (current + offset) % 9;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemSoup) {
                mc.thePlayer.inventory.currentItem = slot;
                return;
            }
        }
    }
}
