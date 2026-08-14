package elara.config.gui;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Font;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import elara.Elara;
import elara.module.Module;

public class HomeContentOption extends BasicOption {
   private static final int CONTENT_WIDTH = 992;

   public HomeContentOption() {
      super(null, null, "", "", "Home", "About", 2);
   }

   private float centerTextY(float boxY, float boxH, float fontSize, Font font) {
      return boxY + (boxH - fontSize) / 2.0F + fontSize * 0.75F;
   }

   public void draw(long vg, int x, int y, InputHandler inputHandler) {
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      int totalModules = Elara.moduleManager != null ? Elara.moduleManager.modules.size() : 0;
      int enabledModules = 0;
      if (Elara.moduleManager != null) {
         for (Module m : Elara.moduleManager.modules.values()) {
            if (m.isEnabled()) {
               enabledModules++;
            }
         }
      }

      int cy = y;
      nvg.drawText(vg, "ABOUT", x + 16, cy + 4, ElaraColors.accentDim(), 11.0F, Fonts.BOLD);
      float var10003 = x + 16;
      cy += 24;
      nvg.drawText(vg, "Elara Client", var10003, cy + 32, ElaraColors.WHITE, 32.0F, Fonts.BOLD);
      nvg.drawText(vg, "A Minecraft 1.8.9 Forge PvP Client", x + 16, cy + 60, ElaraColors.white60(), 14.0F, Fonts.MEDIUM);
      int rowH = 28;
      float var10002 = x;
      cy += 92;
      nvg.drawRoundedRect(vg, var10002, cy, 992.0F, 64.0F, ElaraColors.GRAY_800, 8.0F);
      float verY = this.centerTextY(cy + 4, rowH, 12.0F, Fonts.MEDIUM);
      nvg.drawText(vg, "Version", x + 20, verY, ElaraColors.white60(), 12.0F, Fonts.MEDIUM);
      float verValY = this.centerTextY(cy + 4, rowH, 14.0F, Fonts.MEDIUM);
      nvg.drawText(vg, Elara.version, x + 160, verValY, ElaraColors.WHITE, 14.0F, Fonts.MEDIUM);
      float authY = this.centerTextY(cy + 36, rowH, 12.0F, Fonts.MEDIUM);
      nvg.drawText(vg, "Author", x + 20, authY, ElaraColors.white60(), 12.0F, Fonts.MEDIUM);
      float authValY = this.centerTextY(cy + 36, rowH, 14.0F, Fonts.MEDIUM);
      nvg.drawText(vg, "Tenyze", x + 160, authValY, ElaraColors.WHITE, 14.0F, Fonts.MEDIUM);
      var10003 = x + 16;
      cy += 80;
      nvg.drawText(vg, "MODULES", var10003, cy + 4, ElaraColors.accentDim(), 11.0F, Fonts.BOLD);
      int cardW = 320;
      int cardGap = 16;
      int cardH = 72;
      cy += 24;
      this.drawStatCard(vg, x, cy, cardW, cardH, "Total Modules", String.valueOf(totalModules));
      this.drawStatCard(vg, x + cardW + cardGap, cy, cardW, cardH, "Enabled", String.valueOf(enabledModules));
      this.drawStatCard(vg, x + (cardW + cardGap) * 2, cy, cardW, cardH, "Categories", "5");
      int var31;
      nvg.drawText(vg, "CREDITS", x + 16, (var31 = cy + cardH + 24) + 4, ElaraColors.accentDim(), 11.0F, Fonts.BOLD);
      cy = var31 + 24;

      for (String[] pair : new String[][]{
         {"Based on", "Minecraft Forge 1.8.9"}, {"UI Framework", "OneConfig by Polyfrost"}, {"Special Thanks", "To all contributors and testers"}
      }) {
         int creditH = 44;
         nvg.drawRoundedRect(vg, x, cy, 992.0F, creditH, ElaraColors.GRAY_800, 6.0F);
         float labelY = this.centerTextY(cy, creditH, 13.0F, Fonts.MEDIUM);
         nvg.drawText(vg, pair[0], x + 20, labelY, ElaraColors.white60(), 13.0F, Fonts.MEDIUM);
         float valY = this.centerTextY(cy, creditH, 14.0F, Fonts.MEDIUM);
         nvg.drawText(vg, pair[1], x + 200, valY, ElaraColors.WHITE, 14.0F, Fonts.MEDIUM);
         cy += creditH + 8;
      }

      var10003 = x + 16;
      cy += 8;
      nvg.drawText(vg, "© 2025 Elara Client. All rights reserved.", var10003, cy, ElaraColors.white30(), 12.0F, Fonts.MEDIUM);
   }

   private void drawStatCard(long vg, int x, int y, int w, int h, String label, String value) {
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      nvg.drawRoundedRect(vg, x, y, w, h, ElaraColors.GRAY_800, 8.0F);
      float labelY = this.centerTextY(y + 8, 24.0F, 13.0F, Fonts.MEDIUM);
      nvg.drawText(vg, label, x + 20, labelY, ElaraColors.white60(), 13.0F, Fonts.MEDIUM);
      float valY = this.centerTextY(y + 32, 32.0F, 24.0F, Fonts.BOLD);
      nvg.drawText(vg, value, x + 20, valY, ElaraColors.WHITE, 24.0F, Fonts.BOLD);
   }

   public int getHeight() {
      return 540;
   }
}
