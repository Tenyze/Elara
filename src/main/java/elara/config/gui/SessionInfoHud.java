package elara.config.gui;

import cc.polyfrost.oneconfig.config.annotations.Color;
import cc.polyfrost.oneconfig.config.annotations.Slider;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.hud.Hud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.color.ColorUtils;
import elara.Elara;
import elara.util.BlurUtil;
import net.minecraft.client.Minecraft;

public class SessionInfoHud extends Hud {
   private static final transient Minecraft mc = Minecraft.getMinecraft();

   private static final transient int WHITE = ElaraColors.WHITE;
   private static final transient int WHITE_70 = ColorUtils.setAlpha(ElaraColors.WHITE, 204);
   private static final transient int BLACK_40 = ElaraColors.blackAlpha(60);
   private static final transient int BLACK_90 = ElaraColors.blackAlpha(180);

   // 淡入淡出 + 缩放（与 MusicHud 一致）
   private transient float animAlpha = 0.0F;
   private transient long lastAnimTime;
   private static final transient long ANIM_MS = 400L;

   private int alpha(int color, float a) {
      int na = (int) ((color >>> 24 & 0xFF) * a);
      return na << 24 | color & 16777215;
   }

   @Switch(name="Show Play Time", description="Show session play time", category="Display", subcategory="Content")
   public boolean showPlayTime = true;

   @Switch(name="Show Kills", description="Show kill count", category="Display", subcategory="Content")
   public boolean showKills = true;

   @Slider(name="Scale", description="HUD content scale multiplier", min=0.5f, max=2.0f, step=0, category="Appearance", subcategory="Scale")
   public float contentScale = 1.0f;

   @Switch(name="Round Border", description="Enable rounded corners (Round/Around)", category="Round", subcategory="Appearance")
   public boolean roundBorder = true;

   @Slider(name="Corner Radius", description="Rounded corner radius for the HUD", min=0f, max=20f, step=0, category="Round", subcategory="Appearance")
   public float cornerRadius = 10.0f;

   @Switch(name="Show Outline", description="Show border outline around HUD", category="Round", subcategory="Outline")
   public boolean showOutline = false;

   @Slider(name="Outline Width", description="Width of the border outline", min=1f, max=5f, step=0, category="Round", subcategory="Outline")
   public float outlineWidth = 2.0f;

   @Color(name="Outline Color", description="Color of the border outline", allowAlpha=true, category="Round", subcategory="Outline")
   public OneColor outlineColor = new OneColor(90, 200, 250, 255);

   @Switch(name="Blur Background", description="Enable glassmorphism blur effect on background", category="Round", subcategory="Appearance")
   public boolean blurBackground = false;

   @Slider(name="Blur Radius", description="Blur strength (4~16 recommended)", min=4f, max=16f, step=0, category="Round", subcategory="Appearance")
   public float blurRadius = 8.0f;

   private transient long sessionStartTime = 0L;

   public SessionInfoHud() {
      super(true, 5.0f, 5.0f, 0, 1.0f);
   }

   private String getPlayTime() {
      if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis();
      long elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000L;
      int h = (int) (elapsed / 3600L);
      int m = (int) ((elapsed % 3600L) / 60L);
      int s = (int) (elapsed % 60L);
      return String.format("%dh %dm %ds", h, m, s);
   }

   @Override
   protected void draw(UMatrixStack matrices, float x, float y, float scale, boolean example) {
      try {
         float targetAlpha = 1.0F; // SessionInfoHud 常驻，始终淡入
         long now = System.currentTimeMillis();
         if (this.lastAnimTime == 0L) this.lastAnimTime = now;
         long delta = Math.min(now - this.lastAnimTime, 50L);
         this.lastAnimTime = now;
         if (targetAlpha > this.animAlpha) {
            this.animAlpha = Math.min(targetAlpha, this.animAlpha + delta / (float) ANIM_MS);
         } else if (targetAlpha < this.animAlpha) {
            this.animAlpha = Math.max(targetAlpha, this.animAlpha - delta / (float) ANIM_MS);
         }
         final float fAlpha = this.animAlpha;
         drawInternal(x, y, scale, fAlpha);
      } catch (Throwable e) {
         System.err.println("[Elara] SessionInfoHud draw failed: " + e);
      }
   }

   private void drawInternal(float x, float y, float scale, final float fAlpha) {
      float effectiveScale = scale * contentScale;

      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      nvg.setupAndDraw(true, vg -> {
         nvg.translate(vg, x, y);
         nvg.scale(vg, effectiveScale, effectiveScale);

         final float w = 220.0f;
         final float titleBarH = 24.0f;
         final float contentH = 66.0f;
         final float totalH = titleBarH + contentH;
         final float radius = roundBorder ? cornerRadius : 0f;
         final float padX = 10.0f;

         if (showOutline) {
            int olColor = alpha(outlineColor.getRGB(), fAlpha);
            nvg.drawRoundedRect(vg, -outlineWidth, -outlineWidth, w + outlineWidth * 2, totalH + outlineWidth * 2, olColor, radius + outlineWidth);
         }

         if (blurBackground) {
            BlurUtil.drawBlurredBackground(x, y, w * effectiveScale, totalH * effectiveScale, blurRadius);
         }

         nvg.drawRoundedRect(vg, 0, 0, w, totalH, alpha(BLACK_90, fAlpha), radius);

         nvg.drawRoundedRect(vg, 0, 0, w, titleBarH, alpha(BLACK_40, fAlpha), radius);

         float titleW = nvg.getTextWidth(vg, "Session Information", 12.0f, Fonts.MEDIUM);
         nvg.drawText(vg, "Session Information", (w - titleW) / 2.0f, titleBarH / 2.0f, alpha(WHITE, fAlpha), 12.0f, Fonts.MEDIUM);

         final float contentStartY = titleBarH + 14.0f;
         final float largeTextSize = 16.0f;
         final float smallTextSize = 10.0f;
         final float lineGap = 6.0f;

         float currentY = contentStartY;

         if (showPlayTime) {
            nvg.drawText(vg, getPlayTime(), padX, currentY, alpha(WHITE, fAlpha), largeTextSize, Fonts.BOLD);
            currentY += largeTextSize + lineGap;
         }

         if (showKills) {
            int kills = Elara.killCounter != null ? Elara.killCounter.getKillCount() : 0;
            nvg.drawText(vg, "You have gotten " + kills + " kills", padX, currentY, alpha(WHITE_70, fAlpha), smallTextSize, Fonts.MEDIUM);
            currentY += smallTextSize + lineGap;
         }

         nvg.resetTransform(vg);
      });
   }

   @Override
   protected float getWidth(float scale, boolean example) {
      return 220.0f * scale * contentScale;
   }

   @Override
   protected float getHeight(float scale, boolean example) {
      return 90.0f * scale * contentScale;
   }
}
