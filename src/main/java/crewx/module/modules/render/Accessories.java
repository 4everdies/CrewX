package crewx.module.modules.render;

import crewx.module.Module;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ColorProperty;
import crewx.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Accessories extends Module {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final String[] CAPE_NAMES = {
            "Off", "Founder's", "Zombie", "MCE", "2016", "2015", "2013", "2012", "2011",
            "Cherry", "MapMaker", "Mojang", "MojangStudios", "Mojira", "Classic", "Cobalt", "Moonlight"
    };
    private static final String[] CAPE_FILES = {
            null, "Founder's.png", "zombie.png", "MCE.png", "2016.png", "2015.png", "2013.png",
            "2012.png", "2011.png", "Cherry.png", "MapMaker.png", "Mojang.png", "MojangStudios.png",
            "Mojira.png", "Classic.png", "Cobalt.png", "Moonlight.png"
    };
    private static final String[] HEADWEAR = {
            "Off", "Halo", "Golden Crown", "Bunny Ears", "Cat Ears", "Demon Horns", "Straw Hat", "Black Cap"
    };
    private static final Map<Integer, ResourceLocation> CAPE_CACHE = new HashMap<Integer, ResourceLocation>();

    public final ModeProperty cape = new ModeProperty("Cape", 0, CAPE_NAMES);
    public final ModeProperty headwear = new ModeProperty("Head accessory", 0, HEADWEAR);
    public final BooleanProperty wings = new BooleanProperty("Wings", false);
    public final ColorProperty wingColor = new ColorProperty("Wing color", 0xA8323E,
            () -> this.wings.getValue());

    public Accessories() {
        super("Accessories", false);
    }

    public ResourceLocation getCapeTexture() {
        int index = this.cape.getValue() - 1;
        if (index < 0 || index >= CAPE_FILES.length - 1) return null;
        if (CAPE_CACHE.containsKey(index)) return CAPE_CACHE.get(index);

        String file = CAPE_FILES[index + 1];
        try (InputStream stream = Accessories.class.getResourceAsStream("/assets/crewx/capes/" + file)) {
            if (stream == null) return null;
            BufferedImage image = ImageIO.read(stream);
            if (image == null) return null;
            ResourceLocation texture = MC.renderEngine.getDynamicTextureLocation(
                    "accessory_cape_" + index, new DynamicTexture(image));
            CAPE_CACHE.put(index, texture);
            return texture;
        } catch (Exception ignored) {
            return null;
        }
    }
}
