package crewx.clickgui.clone.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClickGuiInteractionTest {

    private static final class StubGfx implements Gfx {
        @Override public void roundRect(float x, float y, float w, float h, float tl, float tr, float br, float bl, int argb) {}
        @Override public void roundRectH(float x, float y, float w, float h, float r, int left, int right) {}
        @Override public void text(FontId font, String s, float x, float cy, int argb) {}
        @Override public void glyph(FontId font, String s, float cx, float cy, int argb) {}
        @Override public float textWidth(FontId font, String s) { return s == null ? 0f : s.length() * 6f; }
        @Override public void pushClip(float x, float y, float w, float h) {}
        @Override public void popClip() {}
    }

    private ClickGui buildGui(ClickModule mod) {
        List<ClickCategory> cats = new ArrayList<ClickCategory>();
        ClickCategory cat = new ClickCategory("Test", "a");
        cat.modules.add(mod);
        cats.add(cat);
        ClickGui gui = new ClickGui(new Theme(), cats);
        gui.setScreenSize(1280, 720);
        return gui;
    }

    @Test
    public void editsColorAndTextAndRunsAction() {
        final AtomicInteger actionRuns = new AtomicInteger(0);
        TextSetting text = new TextSetting("Name", "abc");
        ColorSetting color = new ColorSetting("Accent", 0xFFFF00FF);
        ActionSetting action = new ActionSetting("DoIt", new Runnable() {
            @Override public void run() { actionRuns.incrementAndGet(); }
        });
        ClickModule mod = new ClickModule("Demo");
        mod.add(text);
        mod.add(color);
        mod.add(action);
        mod.setExpanded(true, true);

        ClickGui gui = buildGui(mod);
        gui.render(new StubGfx(), 0, 0, 0.016f);

        text.editing = true;
        assertTrue(gui.beginTextEdit(text));
        assertTrue(gui.isEditingText());
        gui.keyTyped('X', 45, false);
        assertEquals("abcX", text.get());
        gui.keyTyped('\n', 28, false);
        assertFalse(gui.isEditingText());
        assertEquals("abcX", text.get());

        color.set(0xFF00FF00);
        assertEquals(0xFF00FF00, color.get());

        action.run();
        assertEquals(1, actionRuns.get());
        assertEquals(1, action.getRuns());
    }

    @Test
    public void modeSelectUpdatesIndex() {
        ModeSetting mode = new ModeSetting("Mode", 0, "Normal", "Backwards", "GodBridge");
        ClickModule mod = new ClickModule("Scaffold");
        mod.add(mode);
        mod.setExpanded(true, true);
        ClickGui gui = buildGui(mod);
        gui.render(new StubGfx(), 0, 0, 0.016f);
        mode.setExpanded(true, true);
        mode.setIndex(1);
        assertEquals(1, mode.getIndex());
        assertEquals("Backwards", mode.get());
    }
}
