package myau.accountmanager.gui;

import myau.accountmanager.MushProfileService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/** Displays every field returned by the public Mush player endpoint by category. */
public class GuiMushStats extends GuiScreen {
    private final GuiScreen previousScreen;
    private final String username;
    private final List<Row> rows = new ArrayList<Row>();
    private StatsList statsList;
    private String status = "Loading Mush stats...";

    public GuiMushStats(GuiScreen previousScreen, String username) {
        this.previousScreen = previousScreen;
        this.username = username;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 50, height - 28, 100, 20, "Back"));
        statsList = new StatsList(mc);
        requestStats();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (statsList != null) {
            statsList.drawScreen(mouseX, mouseY, partialTicks);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, "Mush Stats", width / 2, 12, 0xFFFFFF);
        drawCenteredString(fontRendererObj, username, width / 2, 25, 0xAAAAAA);
        int tableLeft = width / 2 - (statsList == null ? 220 : statsList.getListWidth() / 2);
        int tableRight = width / 2 + (statsList == null ? 220 : statsList.getListWidth() / 2);
        fontRendererObj.drawString("Statistic", tableLeft + 7, 57, 0xFFFFFF);
        String valueHeader = "Value";
        fontRendererObj.drawString(valueHeader, tableRight - fontRendererObj.getStringWidth(valueHeader) - 7, 57, 0xFFFFFF);
        if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, status, width / 2, 40, 0xAAAAAA);
        }
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        if (statsList != null) {
            statsList.handleMouseInput();
        }
        super.handleMouseInput();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button != null && button.id == 0) {
            mc.displayGuiScreen(previousScreen);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(previousScreen);
        }
    }

    private void requestStats() {
        MushProfileService.fetchStats(username, new MushProfileService.StatsCallback() {
            @Override
            public void onResult(MushProfileService.Stats stats) {
                rows.clear();
                if (stats == null || stats.getCategories().isEmpty()) {
                    status = "No Mush profile or stats found.";
                    return;
                }
                for (MushProfileService.StatsCategory category : stats.getCategories()) {
                    rows.add(Row.header(category.getName()));
                    for (MushProfileService.Stat stat : category.getValues()) {
                        rows.add(Row.value(stat.getName(), stat.getValue()));
                    }
                    rows.add(Row.spacer());
                }
                status = "";
            }
        });
    }

    private final class StatsList extends GuiSlot {
        private StatsList(Minecraft minecraft) {
            super(minecraft, GuiMushStats.this.width, GuiMushStats.this.height, 70, GuiMushStats.this.height - 36, 18);
        }

        @Override
        protected int getSize() {
            return rows.size();
        }

        @Override
        protected boolean isSelected(int index) {
            return false;
        }

        @Override
        protected int getScrollBarX() {
            return (width + getListWidth()) / 2 + 5;
        }

        @Override
        public int getListWidth() {
            return Math.min(440, width - 28);
        }

        @Override
        protected void drawBackground() {
            drawDefaultBackground();
        }

        @Override
        protected void elementClicked(int index, boolean isDoubleClick, int mouseX, int mouseY) {
        }

        @Override
        protected void drawSlot(int entryID, int x, int y, int heightIn, int mouseXIn, int mouseYIn) {
            Row row = rows.get(entryID);
            if (row.spacer) {
                return;
            }
            if (row.header) {
                fontRendererObj.drawString("\u00A7e\u00A7l" + formatCategory(row.name), x + 7, y + 5, 0xFFFFFF);
                return;
            }
            String value = fontRendererObj.trimStringToWidth(translateFormatting(row.value), getListWidth() / 2 - 20);
            int valueX = x + getListWidth() - fontRendererObj.getStringWidth(value) - 7;
            String name = fontRendererObj.trimStringToWidth(translateFormatting(formatCategory(row.name)), valueX - x - 18);
            fontRendererObj.drawString(name, x + 7, y + 5, 0xCFCFCF);
            fontRendererObj.drawString(value, valueX, y + 5, 0xFFFFFF);
        }
    }

    private String formatCategory(String text) {
        if (text == null || text.isEmpty()) {
            return "General";
        }
        String formatted = text.replace('_', ' ').replace('.', ' ');
        return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
    }

    private String translateFormatting(String text) {
        return text == null ? "" : text.replace('&', '\u00A7');
    }

    private static final class Row {
        private final boolean header;
        private final boolean spacer;
        private final String name;
        private final String value;

        private Row(boolean header, boolean spacer, String name, String value) {
            this.header = header;
            this.spacer = spacer;
            this.name = name;
            this.value = value;
        }

        private static Row header(String name) {
            return new Row(true, false, name, "");
        }

        private static Row value(String name, String value) {
            return new Row(false, false, name, value);
        }

        private static Row spacer() {
            return new Row(false, true, "", "");
        }
    }
}
