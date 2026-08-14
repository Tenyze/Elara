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
import elara.util.BlurUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.*;

public class PotionHud extends Hud {
   private static final transient Minecraft mc = Minecraft.getMinecraft();

   private static final transient int WHITE = ElaraColors.WHITE;
   private static final transient int WHITE_70 = ColorUtils.setAlpha(ElaraColors.WHITE, 204);
   private static final transient int GRAY_400 = ColorUtils.getColor(117, 117, 117, 255);

   // 淡入淡出 + 缩放（与 MusicHud 一致）
   private transient float animAlpha = 0.0F;
   private transient long lastAnimTime;
   private static final transient long ANIM_MS = 400L;

   private int alpha(int color, float a) {
      int na = (int) ((color >>> 24 & 0xFF) * a);
      return na << 24 | color & 16777215;
   }

   @Switch(name="Show Duration", description="Show potion effect duration", category="Display", subcategory="Content")
   public boolean showDuration = true;

   @Switch(name="Show Amplifier", description="Show potion amplifier level", category="Display", subcategory="Content")
   public boolean showAmplifier = false;

   @Switch(name="Show Icon", description="Show potion effect icon", category="Display", subcategory="Content")
   public boolean showIcon = true;

   @Switch(name="Show Background", description="Show HUD background panel", category="Background", subcategory="Appearance")
   public boolean showBackground = true;

   @Switch(name="Blur Background", description="Enable glassmorphism blur effect on background", category="Background", subcategory="Appearance")
   public boolean blurBackground = false;

   @Slider(name="Blur Radius", description="Blur strength (4~16 recommended)", min=4f, max=16f, step=0, category="Background", subcategory="Appearance")
   public float blurRadius = 8.0f;

   @Color(name="Background Color", description="Background color and opacity of the HUD", allowAlpha=true, category="Background", subcategory="Appearance")
   public OneColor backgroundColor = new OneColor(26, 26, 46, 180);

   @Slider(name="Scale", description="HUD content scale multiplier", min=0.5f, max=2.0f, step=0, category="Background", subcategory="Appearance")
   public float contentScale = 1.0f;

   @Switch(name="Round Border", description="Enable rounded corners (Round/Around)", category="Round", subcategory="Appearance")
   public boolean roundBorder = true;

   @Slider(name="Corner Radius", description="Rounded corner radius for the HUD", min=0f, max=20f, step=0, category="Round", subcategory="Appearance")
   public float cornerRadius = 8.0f;

   @Switch(name="Show Outline", description="Show border outline around HUD", category="Round", subcategory="Outline")
   public boolean showOutline = false;

   @Slider(name="Outline Width", description="Width of the border outline", min=1f, max=5f, step=0, category="Round", subcategory="Outline")
   public float outlineWidth = 2.0f;

   @Color(name="Outline Color", description="Color of the border outline", allowAlpha=true, category="Round", subcategory="Outline")
   public OneColor outlineColor = new OneColor(90, 200, 250, 255);

   private static final transient Map<Integer, Integer> POTION_COLORS = new HashMap<>();
   private static final transient Map<String, String> POTION_NAME_MAP = new HashMap<>();

   static {
      POTION_COLORS.put(1, 0x7FB238);
      POTION_COLORS.put(2, 0x5A0000);
      POTION_COLORS.put(3, 0xE6CE4E);
      POTION_COLORS.put(4, 0x4A4A4A);
      POTION_COLORS.put(5, 0x930000);
      POTION_COLORS.put(6, 0x930000);
      POTION_COLORS.put(8, 0x7FB238);
      POTION_COLORS.put(9, 0x2A2A8A);
      POTION_COLORS.put(10, 0xCD5CAB);
      POTION_COLORS.put(11, 0x9933FF);
      POTION_COLORS.put(12, 0xE6CE4E);
      POTION_COLORS.put(13, 0xE6CE4E);
      POTION_COLORS.put(14, 0x2323B0);
      POTION_COLORS.put(15, 0x1A1A1A);
      POTION_COLORS.put(16, 0x587858);
      POTION_COLORS.put(17, 0x5A0000);
      POTION_COLORS.put(18, 0x4E933D);
      POTION_COLORS.put(19, 0x352A27);
      POTION_COLORS.put(20, 0xC8843B);
      POTION_COLORS.put(21, 0xC8843B);
      POTION_COLORS.put(22, 0xF6B2C8);

      POTION_NAME_MAP.put("potion.moveSpeed", "Speed");
      POTION_NAME_MAP.put("potion.moveSlowdown", "Slowness");
      POTION_NAME_MAP.put("potion.digSpeed", "Haste");
      POTION_NAME_MAP.put("potion.digSlowDown", "Mining Fatigue");
      POTION_NAME_MAP.put("potion.damageBoost", "Strength");
      POTION_NAME_MAP.put("potion.heal", "Instant Health");
      POTION_NAME_MAP.put("potion.harm", "Instant Damage");
      POTION_NAME_MAP.put("potion.jump", "Jump Boost");
      POTION_NAME_MAP.put("potion.confusion", "Nausea");
      POTION_NAME_MAP.put("potion.regeneration", "Regeneration");
      POTION_NAME_MAP.put("potion.resistance", "Resistance");
      POTION_NAME_MAP.put("potion.fireResistance", "Fire Resistance");
      POTION_NAME_MAP.put("potion.waterBreathing", "Water Breathing");
      POTION_NAME_MAP.put("potion.invisibility", "Invisibility");
      POTION_NAME_MAP.put("potion.blindness", "Blindness");
      POTION_NAME_MAP.put("potion.nightVision", "Night Vision");
      POTION_NAME_MAP.put("potion.hunger", "Hunger");
      POTION_NAME_MAP.put("potion.weakness", "Weakness");
      POTION_NAME_MAP.put("potion.poison", "Poison");
      POTION_NAME_MAP.put("potion.wither", "Wither");
      POTION_NAME_MAP.put("potion.healthBoost", "Health Boost");
      POTION_NAME_MAP.put("potion.absorption", "Absorption");
      POTION_NAME_MAP.put("potion.saturation", "Saturation");
   }

   public PotionHud() {
      super(true, 5.0f, 5.0f, 0, 1.0f);
   }

   private String getPotionName(PotionEffect effect) {
      Potion potion = Potion.potionTypes[effect.getPotionID()];
      if (potion == null) return "Unknown";
      String name = potion.getName();
      return POTION_NAME_MAP.getOrDefault(name, name);
   }

   private String formatDuration(int ticks) {
      int seconds = ticks / 20;
      if (seconds < 60) return seconds + "s";
      int minutes = seconds / 60;
      int rem = seconds % 60;
      return minutes + ":" + (rem < 10 ? "0" : "") + rem;
   }

   private int getPotionColor(int potionId) {
      return POTION_COLORS.getOrDefault(potionId, 0x888888);
   }

   @Override
   protected void draw(UMatrixStack matrices, float x, float y, float scale, boolean example) {
      if (!enabled) return;

      List<PotionEffect> effects = new ArrayList<>();
      if (!example && mc.thePlayer != null) {
         effects.addAll(mc.thePlayer.getActivePotionEffects());
      } else if (example) {
         effects.add(new PotionEffect(1, 300, 1));
         effects.add(new PotionEffect(5, 600, 0));
         effects.add(new PotionEffect(10, 1200, 2));
      }

      // 动画：有药水/示例时淡入，否则淡出（保留 No effects 示例）
      boolean anyContent = !effects.isEmpty() || example;
      float targetAlpha = anyContent ? 1.0F : 0.0F;
      long now = System.currentTimeMillis();
      if (this.lastAnimTime == 0L) this.lastAnimTime = now;
      long delta = Math.min(now - this.lastAnimTime, 50L);
      this.lastAnimTime = now;
      if (targetAlpha > this.animAlpha) {
         this.animAlpha = Math.min(targetAlpha, this.animAlpha + delta / (float) ANIM_MS);
      } else if (targetAlpha < this.animAlpha) {
         this.animAlpha = Math.max(targetAlpha, this.animAlpha - delta / (float) ANIM_MS);
      }
      if (this.animAlpha <= 0.001F && targetAlpha <= 0.0F) return;
      final float fAlpha = this.animAlpha;

      float effectiveScale = scale * contentScale;

      if (effects.isEmpty()) {
         if (example) {
            NanoVGHelper nvg = NanoVGHelper.INSTANCE;
            nvg.setupAndDraw(true, vg -> {
               nvg.translate(vg, x, y);
               nvg.scale(vg, effectiveScale, effectiveScale);

               float w = 180.0f;
               float h = 36.0f;
               float r = roundBorder ? cornerRadius : 0f;
               int bgColor = alpha(backgroundColor.getRGB(), fAlpha);

               if (showOutline) {
                  int olColor = alpha(outlineColor.getRGB(), fAlpha);
                  nvg.drawRoundedRect(vg, -outlineWidth, -outlineWidth, w + outlineWidth * 2, h + outlineWidth * 2, olColor, r + outlineWidth);
               }

               nvg.drawRoundedRect(vg, 0, 0, w, h, bgColor, r);
               float textW = nvg.getTextWidth(vg, "No effects", 12.0f, Fonts.MEDIUM);
               nvg.drawText(vg, "No effects", (w - textW) / 2.0f, h / 2.0f, alpha(WHITE_70, fAlpha), 12.0f, Fonts.MEDIUM);

               nvg.resetTransform(vg);
            });
         }
         return;
      }

      NanoVGHelper nvg = NanoVGHelper.INSTANCE;
      nvg.setupAndDraw(true, vg -> {
         nvg.translate(vg, x, y);
         nvg.scale(vg, effectiveScale, effectiveScale);

         final float entryHeight = 28.0f;
         final float iconSize = 20.0f;
         final float iconGap = 8.0f;
         final float padX = 10.0f;
         final float padY = 6.0f;
         final float nameSize = 13.0f;
         final float durationSize = 11.0f;
         final float radius = roundBorder ? cornerRadius : 0f;
         final float gap = 8.0f;

         float leftContent = padX + (showIcon ? iconSize + iconGap : 0);

         float maxNameWidth = 0;
         float maxDurWidth = 0;
         for (PotionEffect effect : effects) {
            String name = getPotionName(effect);
            if (showAmplifier && effect.getAmplifier() > 0) {
               name += " " + toRoman(effect.getAmplifier() + 1);
            }
            float nameW = nvg.getTextWidth(vg, name, nameSize, Fonts.MEDIUM);
            if (nameW > maxNameWidth) maxNameWidth = nameW;

            if (showDuration) {
               String dur = formatDuration(effect.getDuration());
               float durW = nvg.getTextWidth(vg, dur, durationSize, Fonts.MEDIUM);
               if (durW > maxDurWidth) maxDurWidth = durW;
            }
         }

         float panelWidth = leftContent + maxNameWidth + (showDuration ? maxDurWidth + gap : 0) + padX;

         float panelHeight = padY * 2 + effects.size() * entryHeight + (effects.size() - 1) * 2;

         if (showBackground) {
            int bgColor = alpha(backgroundColor.getRGB(), fAlpha);

            if (showOutline) {
               int olColor = alpha(outlineColor.getRGB(), fAlpha);
               nvg.drawRoundedRect(vg, -outlineWidth, -outlineWidth, panelWidth + outlineWidth * 2, panelHeight + outlineWidth * 2, olColor, radius + outlineWidth);
            }

            if (blurBackground) {
               // Draw blurred background using absolute screen coords and proper blur radius
               BlurUtil.drawBlurredBackground(x, y, panelWidth * effectiveScale, panelHeight * effectiveScale, blurRadius);
            }

            nvg.drawRoundedRect(vg, 0, 0, panelWidth, panelHeight, bgColor, radius);
         }

         float currentY = padY;
         for (int idx = 0; idx < effects.size(); idx++) {
            PotionEffect effect = effects.get(idx);
            float centerY = currentY + entryHeight / 2.0f;

            if (showIcon) {
               float iconX = padX;
               float iconY = centerY - iconSize / 2.0f;
               int potColor = getPotionColor(effect.getPotionID());

               nvg.drawRoundedRect(vg, iconX, iconY, iconSize, iconSize, alpha((potColor & 0xFFFFFF) | 0x66000000, fAlpha), 4.0f);

               String letter = getPotionInitial(effect.getPotionID());
               float letterSize = iconSize * 0.55f;
               float letterW = nvg.getTextWidth(vg, letter, letterSize, Fonts.BOLD);
               nvg.drawText(vg, letter, iconX + (iconSize - letterW) / 2.0f, iconY + iconSize / 2.0f, alpha(WHITE, fAlpha), letterSize, Fonts.BOLD);

               if (showAmplifier && effect.getAmplifier() > 0) {
                  String ampStr = String.valueOf(effect.getAmplifier() + 1);
                  float ampSize = 10.0f;
                  float ampX = iconX + iconSize - ampSize;
                  float ampY = iconY + iconSize - ampSize;
                  nvg.drawRoundedRect(vg, ampX, ampY, ampSize, ampSize, alpha(ElaraColors.blackAlpha(204), fAlpha), 2.0f);
                  float tw = nvg.getTextWidth(vg, ampStr, 8.0f, Fonts.BOLD);
                  nvg.drawText(vg, ampStr, ampX + (ampSize - tw) / 2.0f, ampY + ampSize / 2.0f, alpha(WHITE, fAlpha), 8.0f, Fonts.BOLD);
               }
            }

            String name = getPotionName(effect);
            if (showAmplifier && effect.getAmplifier() > 0) {
               name += " " + toRoman(effect.getAmplifier() + 1);
            }
            nvg.drawText(vg, name, leftContent, centerY, alpha(WHITE, fAlpha), nameSize, Fonts.MEDIUM);

            if (showDuration) {
               String dur = formatDuration(effect.getDuration());
               float durW = nvg.getTextWidth(vg, dur, durationSize, Fonts.MEDIUM);
               float durX = panelWidth - padX - durW;
               nvg.drawText(vg, dur, durX, centerY, alpha(WHITE_70, fAlpha), durationSize, Fonts.MEDIUM);
            }

            if (!showBackground && idx < effects.size() - 1) {
               float lineY = currentY + entryHeight + 1;
               nvg.drawLine(vg, padX, lineY, panelWidth - padX, lineY, 0.5f, alpha(GRAY_400, fAlpha));
            }

            currentY += entryHeight + 2;
         }

         nvg.resetTransform(vg);
      });
   }

   private String getPotionInitial(int potionId) {
      switch (potionId) {
         case 1: return "S";
         case 2: return "S";
         case 3: return "H";
         case 4: return "F";
         case 5: return "D";
         case 6: return "D";
         case 8: return "J";
         case 9: return "N";
         case 10: return "R";
         case 11: return "R";
         case 12: return "F";
         case 13: return "W";
         case 14: return "I";
         case 15: return "B";
         case 16: return "N";
         case 17: return "H";
         case 18: return "W";
         case 19: return "P";
         case 20: return "W";
         case 21: return "H";
         case 22: return "A";
         default: return "?";
      }
   }

   private String toRoman(int num) {
      if (num <= 0) return "";
      if (num >= 10) return String.valueOf(num);
      String[] romans = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
      return romans[num];
   }

   @Override
   protected float getWidth(float scale, boolean example) {
      return 180.0f * scale * contentScale;
   }

   @Override
   protected float getHeight(float scale, boolean example) {
      return 120.0f * scale * contentScale;
   }

   public boolean isEnabled() {
      return enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean isLocked() {
      return locked;
   }

   public void setLocked(boolean locked) {
      this.locked = locked;
   }

   public void doResetPosition() {
      resetPosition();
   }
}
