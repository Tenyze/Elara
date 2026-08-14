package elara.config.gui;

import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import cc.polyfrost.oneconfig.utils.color.ColorUtils;

public final class ElaraColors {
   public static final int WHITE = ColorUtils.getColor(255, 255, 255, 255);
   public static final int GRAY_300 = ColorUtils.getColor(179, 179, 179, 255);
   public static final int GRAY_600 = ColorUtils.getColor(51, 51, 51, 255);
   public static final int GRAY_750 = ColorUtils.getColor(32, 32, 32, 255);
   public static final int GRAY_700 = ColorUtils.getColor(38, 38, 38, 255);
   public static final int GRAY_800 = ColorUtils.getColor(26, 26, 26, 255);

   private ElaraColors() {
   }

   public static int accent() {
      return ColorPalette.PRIMARY.getNormalColor();
   }

   public static int accentHover() {
      return ColorPalette.PRIMARY.getHoveredColor();
   }

   public static int accentDim() {
      return ColorPalette.PRIMARY.getPressedColor();
   }

   public static int white90() {
      return ColorUtils.setAlpha(WHITE, 230);
   }

   public static int white60() {
      return ColorUtils.setAlpha(WHITE, 153);
   }

   public static int white30() {
      return ColorUtils.setAlpha(WHITE, 77);
   }

   public static int whiteAlpha(int alpha) {
      return ColorUtils.setAlpha(WHITE, alpha);
   }

   public static int gray800Alpha(int alpha) {
      return ColorUtils.setAlpha(GRAY_800, alpha);
   }

   public static int blackAlpha(int alpha) {
      return ColorUtils.getColor(0, 0, 0, alpha);
   }
}
