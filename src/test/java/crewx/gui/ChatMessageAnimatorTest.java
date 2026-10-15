package crewx.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChatMessageAnimatorTest {
    @Test
    public void newLinesFadeInWithoutHittingTheOpaqueTrap() {
        assertEquals(0xFFFFFFFF, ChatMessageAnimator.fadeColor(0xFFFFFFFF, 1.0F));
        assertEquals(4, ChatMessageAnimator.fadeColor(0xFFFFFFFF, 0.0F) >>> 24);          // never < 4 (would be opaque)
        assertEquals(128, ChatMessageAnimator.fadeColor(0xFFFFFFFF, 0.5F) >>> 24, 1);
        assertEquals(0x123456, ChatMessageAnimator.fadeColor(0x80123456, 0.5F) & 0xFFFFFF);
        assertEquals(64, ChatMessageAnimator.fadeColor(0x80123456, 0.5F) >>> 24);
        assertEquals(128, ChatMessageAnimator.fadeColor(0x00FFFFFF, 0.5F) >>> 24, 1);    // alpha 0 means opaque
    }
}
