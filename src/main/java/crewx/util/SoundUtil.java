package crewx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.util.ResourceLocation;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;

import java.lang.reflect.Field;
import java.net.URL;

public class SoundUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Object TOGGLE_LOCK = new Object();
    /** Short two-note chimes (synthesised by tools/sounds/synth_toggle_sounds.py): rising "plim" / falling "toom". */
    private static final String TOGGLE_ON = "/assets/crewx/sounds/toggle_on.ogg";
    private static final String TOGGLE_OFF = "/assets/crewx/sounds/toggle_off.ogg";
    private static final float TOGGLE_VOLUME = 0.7F;
    private static ISound activeToggleSound;
    private static String activeToggleSource;
    private static int toggleCounter;
    private static Field managerField;
    private static Field systemField;
    private static Field loadedField;
    private static boolean reflectionFailed;

    public static void playSound(String soundName) {
        SoundHandler soundHandler = mc.getSoundHandler();
        if (soundHandler != null) {
            PositionedSoundRecord positionedSoundRecord = PositionedSoundRecord.create(new ResourceLocation(soundName));
            soundHandler.playSound(positionedSoundRecord);
        }
    }

    public static void playToggleSound(boolean enabled) {
        SoundHandler soundHandler = mc.getSoundHandler();
        if (soundHandler == null) return;
        synchronized (TOGGLE_LOCK) {
            if (playChime(soundHandler, enabled)) return;
            // fallback: vanilla clicks (sound engine not ready / unexpected environment)
            if (activeToggleSound != null) {
                soundHandler.stopSound(activeToggleSound);
            }
            activeToggleSound = PositionedSoundRecord.create(
                    new ResourceLocation(enabled ? "gui.button.press" : "random.click"));
            soundHandler.playSound(activeToggleSound);
        }
    }

    /**
     * Plays the chime straight through Minecraft's own sound engine (paulscode), reading the .ogg from the
     * client jar: it follows the game's master volume and needs no resource pack.
     */
    private static boolean playChime(SoundHandler handler, boolean enabled) {
        try {
            SoundSystem system = soundSystem(handler);
            if (system == null) return false;
            URL url = SoundUtil.class.getResource(enabled ? TOGGLE_ON : TOGGLE_OFF);
            if (url == null) return false;
            float volume = mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
            if (volume <= 0.0F) return true;   // muted: stay silent, no fallback click
            if (activeToggleSource != null) system.removeSource(activeToggleSource);   // fast toggles: no overlap
            String source = "crewx_toggle_" + (toggleCounter++);
            system.newSource(false, source, url, enabled ? "crewx_toggle_on.ogg" : "crewx_toggle_off.ogg", false,
                    0.0F, 0.0F, 0.0F, SoundSystemConfig.ATTENUATION_NONE, 0.0F);
            system.setVolume(source, volume * TOGGLE_VOLUME);
            system.play(source);
            activeToggleSource = source;
            return true;
        } catch (Throwable failure) {
            return false;
        }
    }

    /** SoundHandler -> SoundManager -> SoundSystem, found by type so it works with any field names. */
    private static SoundSystem soundSystem(SoundHandler handler) throws IllegalAccessException {
        if (reflectionFailed) return null;
        if (managerField == null) {
            managerField = fieldOfType(SoundHandler.class, SoundManager.class);
            systemField = fieldOfType(SoundManager.class, SoundSystem.class);
            loadedField = fieldOfType(SoundManager.class, boolean.class);
            if (managerField == null || systemField == null) {
                reflectionFailed = true;
                return null;
            }
        }
        Object manager = managerField.get(handler);
        if (manager == null) return null;
        if (loadedField != null && !loadedField.getBoolean(manager)) return null;
        return (SoundSystem) systemField.get(manager);
    }

    private static Field fieldOfType(Class<?> owner, Class<?> type) {
        for (Field field : owner.getDeclaredFields()) {
            if (type.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }
}
