package elara.config.gui;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import elara.config.music.MusicApiManager;
import elara.config.music.MusicPlayerManager;

public class MusicApiOption extends BasicOption {
   public MusicApiOption() {
      super(null, null, "Music API", "Music streaming service", "Music", "General", 1);
   }

   public void draw(long vg, int x, int y, InputHandler inputHandler) {
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      MusicApiManager apiManager = MusicPlayerManager.getApiManager();
      if (apiManager == null) {
         nvg.drawText(vg, "API Manager not initialized", x, y + 24, ElaraColors.white60(), 14.0F, Fonts.MEDIUM);
      } else {
         MusicApiManager.Platform currentPlatform = apiManager.getCurrentPlatform();
         nvg.drawText(vg, "Music API", x, y + 24, ElaraColors.whiteAlpha(205), 14.0F, Fonts.MEDIUM);
         int dropdownX = x + 600;
         int dropdownW = 280;
         int dropdownH = 36;
         nvg.drawRoundedRect(vg, dropdownX, y + 8, dropdownW, dropdownH, ElaraColors.GRAY_800, 8.0F);
         nvg.drawRoundedRect(vg, dropdownX, y + 8, dropdownW, dropdownH, ElaraColors.GRAY_700, 8.0F);
         String currentText = currentPlatform.getIcon() + " " + currentPlatform.getDisplayName();
         float textW = nvg.getTextWidth(vg, currentText, 14.0F, Fonts.MEDIUM);
         nvg.drawText(vg, currentText, dropdownX + (dropdownW - textW) / 2.0F, y + 28, ElaraColors.WHITE, 14.0F, Fonts.MEDIUM);
      }
   }

   public int getHeight() {
      return 50;
   }
}
