package crewx.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientPlatformTest {
    @Test
    public void recognisesPojavLauncher() {
        // what PojavLauncher passes: -Dos.name=Linux -Dos.version=Android-14 -Dpojav.path.minecraft=...
        assertTrue(ClientPlatform.looksLikeAndroid("Android-14", "/storage/emulated/0/games/PojavLauncher/.minecraft", "Oracle Corporation", null, null));
        assertTrue(ClientPlatform.looksLikeAndroid("4.14.186", null, "", "/system", "/data"));
    }

    @Test
    public void desktopsAreNotAndroid() {
        assertFalse(ClientPlatform.looksLikeAndroid("6.8.0-45-generic", null, "Eclipse Adoptium", null, null));
        assertFalse(ClientPlatform.looksLikeAndroid("10.0", null, "Oracle Corporation", null, null));
    }
}
