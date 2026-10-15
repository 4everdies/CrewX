package crewx.accountmanager;

import crewx.accountmanager.utils.Nan0EventRegister;
import net.minecraftforge.common.MinecraftForge;

/** Loaded only when Forge is present; keeps AccountManager loadable on Lunar. */
final class ForgeBinding {
    private ForgeBinding() {}

    static void register() {
        Nan0EventRegister.register(MinecraftForge.EVENT_BUS, new Events());
    }
}
