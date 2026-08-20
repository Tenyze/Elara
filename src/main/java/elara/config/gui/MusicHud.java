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
import elara.config.music.CoverManager;
import elara.config.music.MusicEngine;
import elara.config.music.MusicPlayerConfig;
import elara.config.music.MusicPlayerManager;

public class MusicHud extends Hud {
   private transient float animAlpha = 0.0F;
   private transient long lastAnimTime;
   private static final transient long ANIM_MS = 400L;
   private final transient float[] smoothedSpec = new float[16];
   private transient Float cachedMusicTextW;
   private final transient java.util.HashMap<String, Float> titleWidthCache = new java.util.HashMap<>();
   @Switch(name = "Round Border", category = "Round", subcategory = "Appearance")
   public boolean roundBorder = true;
   @Slider(name = "Corner Radius", min = 0.0F, max = 20.0F, step = 0, category = "Round", subcategory = "Appearance")
   public float cornerRadius = 8.0F;
   @Switch(name = "Show Outline", category = "Round", subcategory = "Outline")
   public boolean showOutline;
   @Slider(name = "Outline Width", min = 1.0F, max = 5.0F, step = 0, category = "Round", subcategory = "Outline")
   public float outlineWidth = 2.0F;
   @Color(name = "Outline Color", allowAlpha = true, category = "Round", subcategory = "Outline")
   public OneColor outlineColor = new OneColor(90, 200, 250, 255);

   public MusicHud() {
      super(true, 5.0F, 5.0F, 0, 1.0F);
   }

   public void setHudEnabled(boolean e) {
      this.enabled = e;
   }

   public void setHudLocked(boolean l) {
      this.locked = l;
   }

   public void resetHudPosition() {
      this.resetPosition();
   }

   public void setHudScale(float s) {
      this.setScale(s, false);
   }

   private int alpha(int color, float a) {
      int na = (int)((color >>> 24 & 0xFF) * a);
      return na << 24 | color & 16777215;
   }

   protected void draw(UMatrixStack matrices, float x, float y, float scale, boolean example) {
      MusicEngine engine = MusicPlayerManager.getEngine();
      boolean hasMusic = engine != null && engine.isPlaying();
      boolean shouldShow = example || !MusicPlayerConfig.hudHideWhenNotPlaying() || hasMusic;
      float target = shouldShow ? 1.0F : 0.0F;
      long now = System.currentTimeMillis();
      if (this.lastAnimTime == 0L) {
         this.lastAnimTime = now;
      }

      long delta = Math.min(now - this.lastAnimTime, 50L);
      this.lastAnimTime = now;
      if (target > this.animAlpha) {
         this.animAlpha = Math.min(target, this.animAlpha + (float)delta / 400.0F);
      } else if (target < this.animAlpha) {
         this.animAlpha = Math.max(target, this.animAlpha - (float)delta / 400.0F);
      }

      if (!(this.animAlpha <= 0.0F) || !(target <= 0.0F)) {
         float alpha = this.animAlpha;
         NanoVGHelper nvg = NanoVGHelper.INSTANCE;
         nvg.setupAndDraw(
            true,
            vg -> {
               float w = 220.0F * scale;
               float h = 80.0F * scale;
               float rad = this.roundBorder ? this.cornerRadius * scale : 0.0F;
               if (this.showOutline) {
                  int oc = this.alpha(this.outlineColor.getRGB(), alpha);
                  float ow = this.outlineWidth * scale;
                  nvg.drawRoundedRect(vg, x - ow, y - ow, w + ow * 2.0F, h + ow * 2.0F, oc, rad + ow);
               }

               int bg = this.alpha(ColorUtils.setAlpha(ElaraColors.GRAY_800, 204), alpha);
               nvg.drawRoundedRect(vg, x, y, w, h, bg, rad);
               if (example && !hasMusic) {
                  this.drawExample(vg, nvg, x, y, scale, alpha);
               } else {
                  if (MusicPlayerConfig.hudShowCover()) {
                     float cs = 60.0F * scale;
                     String coverPath = engine != null && engine.getCurrentSong() != null ? CoverManager.getCoverPath(engine.getCurrentSong()) : null;
                     if (coverPath != null) {
                        nvg.drawRoundImage(vg, coverPath, x + 8.0F * scale, y + 10.0F * scale, cs, cs, 6.0F * scale, null);
                     } else {
                        nvg.drawRoundedRect(vg, x + 8.0F * scale, y + 10.0F * scale, cs, cs, this.alpha(ElaraColors.GRAY_800, alpha), 6.0F * scale);
                        float mts = 11.0F * scale;
                        if (this.cachedMusicTextW == null) {
                           this.cachedMusicTextW = nvg.getTextWidth(vg, "Music", mts, Fonts.MEDIUM);
                        }

                        nvg.drawText(
                           vg,
                           "Music",
                           x + 8.0F * scale + (cs - this.cachedMusicTextW) / 2.0F,
                           y + 10.0F * scale + cs / 2.0F + mts / 2.0F,
                           this.alpha(ElaraColors.accent(), alpha),
                           mts,
                           Fonts.MEDIUM
                        );
                     }
                  }

                  float tx = MusicPlayerConfig.hudShowCover() ? x + 80.0F * scale : x + 12.0F * scale;
                  float mtw = MusicPlayerConfig.hudShowCover() ? 132.0F * scale : 196.0F * scale;
                  float tfs = 12.0F * scale;
                  String rawTitle = engine != null ? MusicLayout.fullClean(engine.getTitle()) : "No song";
                  // 对过长歌名做像素级截断 + 省略号，避免继续缩小字号导致过密的显示
                  String title = MusicLayout.truncByWidth(vg, rawTitle, tfs, Fonts.BOLD, mtw, this.titleWidthCache);

                  nvg.drawText(vg, title, tx, y + 20.0F * scale, this.alpha(ElaraColors.WHITE, alpha), tfs, Fonts.BOLD);
                  if (MusicPlayerConfig.hudShowProgress()) {
                     float bx = tx;
                     float by = y + 40.0F * scale;
                     float bw = 130.0F * scale;
                     float bh = 3.0F * scale;
                     float prog = engine != null ? engine.getProgress() : 0.0F;
                     nvg.drawRoundedRect(vg, bx, by, bw, bh, this.alpha(ElaraColors.GRAY_700, alpha), 2.0F * scale);
                     if (prog > 0.0F) {
                        nvg.drawRoundedRect(vg, bx, by, bw * prog, bh, this.alpha(ElaraColors.accent(), alpha), 2.0F * scale);
                     }

                     String posS = engine != null ? MusicLayout.fmtTime(engine.getPosition()) : "0:00";
                     String durS = engine != null ? MusicLayout.fmtTime(engine.getDuration()) : "0:00";
                     nvg.drawText(vg, posS, bx, by + 14.0F * scale, this.alpha(ElaraColors.white60(), alpha), 9.0F * scale, Fonts.MEDIUM);
                     float dw = nvg.getTextWidth(vg, durS, 9.0F * scale, Fonts.MEDIUM);
                     nvg.drawText(vg, durS, bx + bw - dw, by + 14.0F * scale, this.alpha(ElaraColors.white60(), alpha), 9.0F * scale, Fonts.MEDIUM);
                  }

                  if (MusicPlayerConfig.hudShowSpectrum()) {
                     float sx = tx;
                     float sy = y + 60.0F * scale;
                     float sw = 130.0F * scale;
                     float sh = 12.0F * scale;
                     int bars = 16;
                     float[] spec = engine != null ? engine.getSpectrum() : new float[bars];

                     for (int i = 0; i < bars; i++) {
                        this.smoothedSpec[i] = this.smoothedSpec[i] + (spec[i] - this.smoothedSpec[i]) * 0.3F;
                     }

                     float barW = sw / bars * 0.7F;
                     float gap = sw / bars * 0.3F;
                     int ac = this.alpha(ElaraColors.accent(), alpha);

                     for (int i = 0; i < bars; i++) {
                        float bh = Math.min(this.smoothedSpec[i] * sh, sh);
                        nvg.drawRoundedRect(vg, sx + i * (barW + gap), sy + sh - bh, barW, Math.max(bh, 1.0F), ac, 1.0F * scale);
                     }
                  }
               }
            }
         );
      }
   }

   private void drawExample(long vg, NanoVGHelper nvg, float x, float y, float scale, float alpha) {
      if (MusicPlayerConfig.hudShowCover()) {
         float cs = 60.0F * scale;
         nvg.drawRoundedRect(vg, x + 8.0F * scale, y + 10.0F * scale, cs, cs, this.alpha(ElaraColors.GRAY_800, alpha), 6.0F * scale);
         float mts = 11.0F * scale;
         float mtw = nvg.getTextWidth(vg, "Music", mts, Fonts.MEDIUM);
         nvg.drawText(
            vg,
            "Music",
            x + 8.0F * scale + (cs - mtw) / 2.0F,
            y + 10.0F * scale + cs / 2.0F + mts / 2.0F,
            this.alpha(ElaraColors.white60(), alpha),
            mts,
            Fonts.MEDIUM
         );
      }

      float tx = MusicPlayerConfig.hudShowCover() ? x + 80.0F * scale : x + 12.0F * scale;
      nvg.drawText(vg, "Music HUD", tx, y + 20.0F * scale, this.alpha(ElaraColors.white90(), alpha), 12.0F * scale, Fonts.BOLD);
      if (MusicPlayerConfig.hudShowProgress()) {
         nvg.drawRoundedRect(vg, tx, y + 40.0F * scale, 130.0F * scale, 3.0F * scale, this.alpha(ElaraColors.GRAY_700, alpha), 2.0F * scale);
         nvg.drawRoundedRect(vg, tx, y + 40.0F * scale, 65.0F * scale, 3.0F * scale, this.alpha(ElaraColors.accent(), alpha), 2.0F * scale);
         nvg.drawText(vg, "1:23", tx, y + 54.0F * scale, this.alpha(ElaraColors.white60(), alpha), 9.0F * scale, Fonts.MEDIUM);
         float dw = nvg.getTextWidth(vg, "3:45", 9.0F * scale, Fonts.MEDIUM);
         nvg.drawText(vg, "3:45", tx + 130.0F * scale - dw, y + 54.0F * scale, this.alpha(ElaraColors.white60(), alpha), 9.0F * scale, Fonts.MEDIUM);
      }

      if (MusicPlayerConfig.hudShowSpectrum()) {
         float sx = tx;
         float sy = y + 60.0F * scale;
         float sw = 130.0F * scale;
         float sh = 12.0F * scale;
         int bars = 16;
         float barW = sw / bars * 0.7F;
         float gap = sw / bars * 0.3F;
         long t = System.currentTimeMillis() / 100L;
         int ac = this.alpha(ElaraColors.accent(), alpha);

         for (int i = 0; i < bars; i++) {
            float bh = (float)(Math.sin(t * 0.3 + i * 0.5) * 0.3 + 0.5) * sh;
            nvg.drawRoundedRect(vg, sx + i * (barW + gap), sy + sh - Math.max(bh, 1.0F), barW, Math.max(bh, 1.0F), ac, 1.0F * scale);
         }
      }
   }

   protected float getWidth(float scale, boolean example) {
      return 220.0F * scale;
   }

   protected float getHeight(float scale, boolean example) {
      return 80.0F * scale;
   }
}
