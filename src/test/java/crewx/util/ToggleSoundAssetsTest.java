package crewx.util;

import org.junit.Test;
import paulscode.sound.SoundBuffer;
import paulscode.sound.codecs.CodecJOrbis;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The toggle chimes must be decodable by the same Ogg codec Minecraft 1.8.9 uses to play them. */
public class ToggleSoundAssetsTest {
    private static void assertPlayable(String path) {
        URL url = ToggleSoundAssetsTest.class.getResource(path);
        assertNotNull(path + " must be packaged", url);
        CodecJOrbis codec = new CodecJOrbis();
        assertTrue("codec accepts " + path, codec.initialize(url));
        SoundBuffer buffer = codec.readAll();
        codec.cleanup();
        assertNotNull(buffer);
        assertEquals(44100.0F, buffer.audioFormat.getSampleRate(), 0.0F);
        float seconds = buffer.audioData.length / (buffer.audioFormat.getFrameSize() * buffer.audioFormat.getSampleRate());
        assertTrue("short chime, got " + seconds + " s", seconds > 0.3F && seconds < 0.6F);
    }

    @Test
    public void onChimeIsPlayable() {
        assertPlayable("/assets/crewx/sounds/toggle_on.ogg");
    }

    @Test
    public void offChimeIsPlayable() {
        assertPlayable("/assets/crewx/sounds/toggle_off.ogg");
    }
}
