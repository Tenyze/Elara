package elara.config.gui;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import elara.Elara;
import elara.module.Module;

public class HomeContentOption extends BasicOption {
    private static final int PAD_X = 24;
    private static final int CONTENT_W = 960;

    public HomeContentOption() {
        super(null, null, "", "", "Home", "About", 2);
    }

    @Override
    public void draw(long vg, int x, int y, InputHandler inputHandler) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;

        int totalModules = Elara.moduleManager != null ? Elara.moduleManager.modules.size() : 0;
        int enabledModules = 0;
        if (Elara.moduleManager != null) {
            for (Module m : Elara.moduleManager.modules.values()) {
                if (m.isEnabled()) enabledModules++;
            }
        }

        int leftX = x + PAD_X;
        float cy = y + 8;

        // ---------- Header ----------
        nvg.drawText(vg, "HOME", leftX, cy + 12, ElaraColors.accentDim(), 11F, Fonts.BOLD);
        cy += 30;
        nvg.drawText(vg, "Elara Client", leftX, cy + 36, ElaraColors.WHITE, 36F, Fonts.BOLD);
        cy += 36 + 8;
        nvg.drawText(vg, "A minimal Minecraft 1.8.9 PvP client — made for clean games.",
                leftX, cy + 14, ElaraColors.white60(), 14F, Fonts.MEDIUM);
        cy += 14 + 24;
        nvg.drawLine(vg, leftX, cy, leftX + CONTENT_W, cy, 1F, ElaraColors.GRAY_600);
        cy += 28;

        // ---------- Stats row (3 cards) ----------
        int cardW = (CONTENT_W - 32) / 3;
        int cardH = 88;
        int gap = 16;
        drawStatCard(vg, x, (int) cy, cardW, cardH,
                "Total modules", String.valueOf(totalModules));
        drawStatCard(vg, x + cardW + gap, (int) cy, cardW, cardH,
                "Enabled", String.valueOf(enabledModules));
        drawStatCard(vg, x + (cardW + gap) * 2, (int) cy, cardW, cardH,
                "Categories", "5");
        cy += cardH + 24;

        // ---------- Info card ----------
        int infoH = 72;
        nvg.drawRoundedRect(vg, x, cy, CONTENT_W, infoH, ElaraColors.GRAY_800, 8F);
        nvg.drawText(vg, "Version",  leftX + 4, cy + 24 + 14, ElaraColors.white60(), 12F, Fonts.MEDIUM);
        nvg.drawText(vg, "Author",   leftX + 4, cy + 24 + 14 + 30, ElaraColors.white60(), 12F, Fonts.MEDIUM);
        nvg.drawText(vg, Elara.version, leftX + 144, cy + 24 + 14, ElaraColors.WHITE, 14F, Fonts.MEDIUM);
        nvg.drawText(vg, "Tenyze",      leftX + 144, cy + 24 + 14 + 30, ElaraColors.WHITE, 14F, Fonts.MEDIUM);
        cy += infoH + 24;

        // ---------- Credits ----------
        nvg.drawText(vg, "CREDITS", leftX, cy + 12, ElaraColors.accentDim(), 11F, Fonts.BOLD);
        cy += 30;
        String[][] rows = new String[][]{
                {"Based on",         "Minecraft Forge 1.8.9"},
                {"UI Framework",     "OneConfig by Polyfrost"},
                {"Special Thanks",   "To all contributors and testers"},
        };
        for (String[] row : rows) {
            int h = 40;
            nvg.drawRoundedRect(vg, x, cy, CONTENT_W, h, ElaraColors.GRAY_800, 6F);
            nvg.drawText(vg, row[0], leftX + 4, cy + 14 + 12, ElaraColors.white60(), 13F, Fonts.MEDIUM);
            nvg.drawText(vg, row[1], leftX + 180, cy + 14 + 13, ElaraColors.WHITE, 14F, Fonts.MEDIUM);
            cy += h + 8;
        }
        cy += 16;
        nvg.drawText(vg, "© 2025 Elara Client — All rights reserved.",
                leftX, cy + 12, ElaraColors.white30(), 12F, Fonts.MEDIUM);
    }

    private static void drawStatCard(long vg, int x, int y, int w, int h, String label, String value) {
        NanoVGHelper nvg = NanoVGHelper.INSTANCE;
        nvg.drawRoundedRect(vg, x, y, w, h, ElaraColors.GRAY_800, 8F);
        nvg.drawText(vg, label, x + 18, y + 16 + 13, ElaraColors.white60(), 13F, Fonts.MEDIUM);
        nvg.drawText(vg, value, x + 18, y + 16 + 13 + 32, ElaraColors.WHITE, 26F, Fonts.BOLD);
    }

    @Override
    public int getHeight() {
        return 560;
    }
}
