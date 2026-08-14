package elara.config.gui;

import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;

public final class CollapsibleHeader {
   public static final float HEADER_HEIGHT = 28.0F;
   private static final float TITLE_FONT_SIZE = 13.0F;
   private static final float COUNT_FONT_SIZE = 11.0F;

   private CollapsibleHeader() {
   }

   public static boolean draw(long vg, float x, float y, float width, String name, float progress, int visibleCount, InputHandler inputHandler) {
      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      boolean clicked = inputHandler.isClicked()
         && inputHandler.mouseX() >= x
         && inputHandler.mouseX() <= x + width
         && inputHandler.mouseY() >= y
         && inputHandler.mouseY() <= y + 28.0F;
      float textY = y + 20.0F;
      float arrowX = x;
      if (progress < 0.5F) {
         nvg.drawText(vg, "▶", arrowX, textY, ElaraColors.white90(), 13.0F, Fonts.MEDIUM);
      } else {
         nvg.drawText(vg, "▼", arrowX, textY, ElaraColors.white90(), 13.0F, Fonts.MEDIUM);
      }

      nvg.drawText(vg, name, x + 21.0F, textY, ElaraColors.white90(), 13.0F, Fonts.MEDIUM);
      if (progress < 0.5F && visibleCount > 0) {
         String countText = visibleCount + " items";
         float countW = nvg.getTextWidth(vg, countText, 11.0F, Fonts.MEDIUM);
         nvg.drawText(vg, countText, x + width - countW - 12.0F, textY, ElaraColors.white60(), 11.0F, Fonts.MEDIUM);
      }

      return clicked;
   }
}
