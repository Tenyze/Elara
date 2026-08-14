package elara.config.gui;

import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Font;
import cc.polyfrost.oneconfig.utils.InputHandler;
import java.util.HashMap;

public final class MusicLayout {
   public static final int CONTENT_W = 920;
   public static final int LEFT_PAD = 20;
   public static final int TAB_BAR_H = 60;
   public static final int TAB_GAP = 8;
   public static final int TOP_MARGIN = 40;
   public static final int CONTENT_START_Y = 72;
   public static final int GAP_XS = 4;
   public static final int GAP_SM = 8;
   public static final int GAP_MD = 12;
   public static final int GAP_LG = 16;
   public static final int GAP_XL = 24;
   public static final int GAP_XXL = 32;
   public static final float FONT_XS = 10.0F;
   public static final float FONT_SM = 11.0F;
   public static final float FONT_MD = 13.0F;
   public static final float FONT_LG = 14.0F;
   public static final float FONT_XL = 16.0F;
   public static final int FONT_TITLE = 20;
   public static final int FONT_HERO = 26;
   public static final int ROW_H = 44;
   public static final int BTN_H = 36;
   public static final int PROGRESS_BAR_H = 6;
   public static final int PROGRESS_KNOB = 24;
   public static final int BG_ALPHA = 204;
   public static final int TEXT_CACHE_MAX = 256;
   public static final long TAB_ANIM_MS = 220L;
   public static final long LIST_ANIM_MS = 300L;

   private MusicLayout() {
   }

   public static float centerY(float boxY, float boxH, float fontSize) {
      return boxY + (boxH - fontSize) / 2.0F + fontSize * 0.75F;
   }

   public static float textWidth(long vg, String text, float fontSize, Font font, HashMap<String, Float> cache) {
      if (text != null && !text.isEmpty()) {
         String key = text + "|" + fontSize + "|" + font.hashCode();
         Float c = cache.get(key);
         if (c != null) {
            return c;
         }

         float w = NanoVGHelper.INSTANCE.getTextWidth(vg, text, fontSize, font);
         if (cache.size() < 256) {
            cache.put(key, w);
         }

         return w;
      } else {
         return 0.0F;
      }
   }

   public static String fmtTime(int sec) {
      if (sec < 0) {
         sec = 0;
      }

      return String.format("%d:%02d", sec / 60, sec % 60);
   }

   public static String trunc(String s, int max) {
      if (s == null) {
         return "";
      }
      // 先去掉两端控制字符/不可见字符，避免这些幽灵字符在 UI 中造成显示错乱
      // 或占用额外的字符配额，导致最后一个真实字母被"挤掉"（之前
      // 出现过 decaying -> decayin、Fading Wind -> Fading Win 这类掉尾字）。
      String cleaned = fullClean(s);
      int len = cleaned.length();
      if (len <= max) {
         return cleaned;
      }
      // 如果只剩 1 个字符空间，直接省略号无意义；max <=0 返回空
      if (max <= 0) return "";
      if (max == 1) return "…";
      return cleaned.substring(0, max - 1) + "…";
   }

   /**
    * 按像素宽度截断（更精确，避免按字符数截断时 W/M 等宽字超出面板或 i/j 等窄字留空过多）。
    * 返回的字符串渲染宽度 <= maxWidth（考虑末尾省略号的宽度）。
    * 计算前会先做 fullClean，所以不会因为末尾鬼字符造成视觉上被裁切一个字。
    */
   public static String truncByWidth(long vg, String s, float fontSize, cc.polyfrost.oneconfig.renderer.font.Font font,
                                     float maxWidth, HashMap<String, Float> cache) {
      if (s == null) return "";
      String cleaned = fullClean(s);
      if (cleaned.isEmpty()) return "";
      if (maxWidth <= 0) return "";
      float fullW = textWidth(vg, cleaned, fontSize, font, cache);
      if (fullW <= maxWidth) return cleaned;
      String ellipsis = "…";
      float ellW = textWidth(vg, ellipsis, fontSize, font, cache);
      if (ellW >= maxWidth) return ellipsis;
      float budget = maxWidth - ellW;
      // 二分查找最大可保留前缀长度；codepoint 感知，避免切断 surrogate pair
      int[] cps = cleaned.codePoints().toArray();
      int lo = 0;
      int hi = cps.length;
      int best = 0;
      while (lo <= hi) {
         int mid = (lo + hi) >>> 1;
         String prefix = new String(cps, 0, mid);
         float pw = textWidth(vg, prefix, fontSize, font, cache);
         if (pw <= budget) {
            best = mid;
            lo = mid + 1;
         } else {
            hi = mid - 1;
         }
      }
      return new String(cps, 0, best) + ellipsis;
   }

   /**
    * 彻底清理字符串：两端 & 内部不可见/控制字符全部去掉，
    * 合并连续空白并去掉首尾空格。
    * 这是 MusicList 上显示文本之前的统一清洗入口，保证和 truncByWidth、
    * textWidth 缓存看到的"逻辑文本"一致。
    */
   public static String fullClean(String s) {
      if (s == null || s.isEmpty()) return "";
      int[] cps = s.codePoints().toArray();
      StringBuilder sb = new StringBuilder(cps.length);
      boolean lastSpace = false;
      for (int cp : cps) {
         int type = Character.getType(cp);
         if (cp == 0xFEFF || cp == 0xFFFE || cp == 0xFFFF
                 || cp == '\u200B' || cp == '\u200C' || cp == '\u200D' || cp == '\u2060'
                 || cp == '\u202A' || cp == '\u202B' || cp == '\u202C' || cp == '\u202D' || cp == '\u202E'
                 || cp == '\u00AD'
                 || cp == '\u034F' || cp == '\u2061' || cp == '\u2062' || cp == '\u2063' || cp == '\u2064'
                 || cp == '\u180E'
                 || type == Character.CONTROL
                 || type == Character.FORMAT
                 || type == Character.PRIVATE_USE
                 || type == Character.UNASSIGNED
                 || type == Character.SURROGATE
                 || type == Character.COMBINING_SPACING_MARK
                 || type == Character.ENCLOSING_MARK
                 || type == Character.NON_SPACING_MARK && !Character.isLetterOrDigit(cp)) {
            continue;
         }
         if (Character.isWhitespace(cp) || cp == '\u3000' || cp == '\u2028' || cp == '\u2029') {
            if (!lastSpace && sb.length() > 0) {
               sb.append(' ');
               lastSpace = true;
            }
            continue;
         }
         lastSpace = false;
         sb.appendCodePoint(cp);
      }
      while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
         sb.setLength(sb.length() - 1);
      }
      return sb.toString();
   }

   /**
    * 仅移除末尾不可见/控制字符（旧 API，保留以兼容）。
    * 新代码优先使用 {@link #fullClean(String)} 或 trunc。
    */
   public static String stripTrailingInvisibles(String s) {
      if (s == null || s.isEmpty()) return "";
      return fullClean(s);
   }

   public static boolean btnClicked(InputHandler h, float x, float y, float w, float hgt) {
      return h.isClicked() && h.mouseX() >= x && h.mouseX() <= x + w && h.mouseY() >= y && h.mouseY() <= y + hgt;
   }

   public static float easeOut(float t) {
      float f = 1.0F - t;
      return 1.0F - f * f * f;
   }

   public static float clamp01(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }
}
