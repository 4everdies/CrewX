package myau.module.modules;

import myau.module.Module;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Cape extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static final String[] CAPE_NAMES = {
            "Founder's", "Zombie", "MCE", "2016", "2015", "2013",
            "2012", "2011", "Cherry", "MapMaker", "Mojang",
            "MojangStudios", "Mojira", "Classic", "Cobalt", "Moonlight"
    };

    public static final String[] CAPE_FILES = {
            "Founder's.png", "zombie.png", "MCE.png", "2016.png", "2015.png",
            "2013.png", "2012.png", "2011.png", "Cherry.png", "MapMaker.png",
            "Mojang.png", "MojangStudios.png", "Mojira.png", "Classic.png",
            "Cobalt.png", "Moonlight.png"
    };

    private static final Map<Integer, ResourceLocation> capeCache = new HashMap<>();

    public final ModeProperty selectedCape = new ModeProperty("cape", 0, CAPE_NAMES);

    public Cape() {
        super("Cape", false);
    }

    public ResourceLocation getCapeTexture() {
        int idx = this.selectedCape.getValue();
        if (idx < 0 || idx >= CAPE_NAMES.length) return null;
        if (capeCache.containsKey(idx)) return capeCache.get(idx);

        try {
            InputStream stream = Cape.class.getResourceAsStream("/assets/myau/capes/" + CAPE_FILES[idx]);
            if (stream == null) return null;
            BufferedImage img = ImageIO.read(stream);
            stream.close();
            ResourceLocation rl = mc.renderEngine.getDynamicTextureLocation("cape_" + idx, new DynamicTexture(img));
            capeCache.put(idx, rl);
            return rl;
        } catch (Exception e) {
            return null;
        }
    }
}
