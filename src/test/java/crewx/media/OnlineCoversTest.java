package crewx.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OnlineCoversTest {
    @Test
    public void asksTheCdnForABigCover() {
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/6f/x/20UMGIM12176.rgb.jpg/600x600bb.jpg",
                OnlineCovers.bigger("https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/6f/x/20UMGIM12176.rgb.jpg/100x100bb.jpg"));
        assertEquals("", OnlineCovers.bigger(""));
    }
}
