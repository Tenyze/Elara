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
import elara.module.combat.KillAura;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.util.TimerUtil;

public class TargetHud extends Hud {
   private static final transient Minecraft mc = Minecraft.getMinecraft();

   private static final transient int BLACK_75 = ElaraColors.blackAlpha(120);
   private static final transient int BLACK_55 = ElaraColors.blackAlpha(90);
   private static final transient int RED_60 = ColorUtils.getColor(0xD4, 0x3F, 0x3F, 90);
   private static final transient int RED_40 = ColorUtils.getColor(0xF5, 0x34, 0x1B, 60);
   private static final transient int WHITE = ElaraColors.WHITE;
   // 半透明白色：用于文字与血条填充，呈现更干净的极简风格
   private static final transient int WHITE_90 = ElaraColors.white90();
   private static final transient int WHITE_60 = ElaraColors.white60();

   // 淡入淡出 + 缩放（与 MusicHud 一致的缩放/动画逻辑）
   private transient float animAlpha = 0.0F;
   private transient long lastAnimTime;
   private static final transient long ANIM_MS = 400L;

   private int alpha(int color, float a) {
      int na = (int) ((color >>> 24 & 0xFF) * a);
      return na << 24 | color & 16777215;
   }

   @Switch(name="Show Text", description="Show target name and health text", category="Display", subcategory="Content")
   public boolean showText = true;

   @Switch(name="KillAura Only", description="Only show target while KillAura is active; hide on manual attacks", category="Display", subcategory="Targeting")
   public boolean kaOnly = true;

   @Switch(name="Health From Tag", description="Read target health from the below-name scoreboard tag (bypasses spoofed entity health on some anti-cheats)", category="Display", subcategory="Health")
   public boolean healthFromTag = false;

   @Switch(name="Health From Tab", description="Read target health from the tab-list scoreboard", category="Display", subcategory="Health")
   public boolean healthFromTab = false;

   @Switch(name="Red Theme", description="Use red color theme", category="Appearance", subcategory="Style")
   public boolean redTheme = false;

   @Switch(name="Vertical Mode", description="Vertical layout mode", category="Appearance", subcategory="Layout")
   public boolean verticalMode = false;

   @Slider(name="Scale", description="HUD content scale multiplier", min=0.5f, max=2.0f, step=0, category="Appearance", subcategory="Scale")
   public float contentScale = 1.0f;

   @Slider(name="Corner Radius", description="Rounded corner radius of the HUD background", min=0f, max=20f, step=0, category="Round", subcategory="Appearance")
   public float cornerRad = 4.0f;

   @Switch(name="Round Border", description="Enable rounded corners (Round/Around)", category="Round", subcategory="Appearance")
   public boolean roundBorder = true;

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

   private final transient TimerUtil lastAttackTimer = new TimerUtil();
   private transient EntityLivingBase lastTarget = null;
   private transient EntityLivingBase currentTarget = null;
   private transient ResourceLocation headTexture = null;
   private transient float easingHealth = 0f;

   public TargetHud() {
      super(true, 5.0f, 5.0f, 0, 1.0f);
   }

   private EntityLivingBase resolveTarget() {
      KillAura killAura = (KillAura) Elara.moduleManager.modules.get(KillAura.class);

      if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
         return killAura.getTarget();
      }
      if (!kaOnly
              && !lastAttackTimer.hasTimeElapsed(1500L)
              && lastTarget != null && lastTarget.isEntityAlive()) {
         return lastTarget;
      }
      return null;
   }

   private ResourceLocation getSkin(EntityLivingBase entity) {
      if (entity instanceof EntityPlayer) {
         NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(entity.getName());
         if (info != null) return info.getLocationSkin();
      }
      return null;
   }

   /**
    * Returns the target's health for display. By default this is the entity's
    * tracked health, but some anti-cheats spoof {@code getHealth()} so the
    * value jumps randomly. When Health-From-Tag/Tab is enabled, the real
    * value is read from the scoreboard objective the server pushes to the
    * below-name tag (slot 2) or the tab list (slot 0) — many servers expose
    * the true health there. Tag takes priority over Tab when both are on.
    */
   private float getDisplayHealth(EntityLivingBase target) {
      if (!(target instanceof EntityPlayer)) {
         return target.getHealth();
      }
      int slot = -1;
      if (healthFromTag) {
         slot = 2; // BELOW_NAME
      } else if (healthFromTab) {
         slot = 0; // PLAYER_LIST (tab)
      }
      if (slot < 0) {
         return target.getHealth();
      }
      try {
         net.minecraft.scoreboard.Scoreboard sb = mc.theWorld.getScoreboard();
         if (sb == null) return target.getHealth();
         net.minecraft.scoreboard.ScoreObjective objective = sb.getObjectiveInDisplaySlot(slot);
         if (objective == null) return target.getHealth();
         net.minecraft.scoreboard.Score score = sb.getValueFromObjective(target.getName(), objective);
         if (score == null) return target.getHealth();
         int points = score.getScorePoints();
         // A zero score while the entity is clearly alive usually means the
         // server isn't tracking this player in that objective — fall back
         // to entity health rather than rendering a "dead" target.
         if (points <= 0 && target.getHealth() > 0.0F) {
            return target.getHealth();
         }
         return (float) points;
      } catch (Throwable t) {
         return target.getHealth();
      }
   }

   @Override
   protected void draw(UMatrixStack matrices, float x, float y, float scale, boolean example) {
      if (!enabled) return;

      EntityLivingBase rawTarget = example ? getExampleTarget() : resolveTarget();
      boolean hasTarget = rawTarget != null;
      float targetAlpha = (example || hasTarget) ? 1.0F : 0.0F;
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
      if (!hasTarget && !example) return;
      EntityLivingBase target = rawTarget != null ? rawTarget : mc.thePlayer;
      if (target == null) return;

      float effectiveScale = scale * contentScale;
      float displayHealth = getDisplayHealth(target);
      if (target != currentTarget) {
         headTexture = getSkin(target);
         easingHealth = displayHealth;
         currentTarget = target;
      }

      easingHealth += (displayHealth - easingHealth) * 0.15f;
      float maxH = target.getMaxHealth();
      float healthPercent = maxH > 0.0F ? Math.min(easingHealth / maxH, 1F) : 0F;

      final EntityLivingBase finalTarget = target;
      final float finalHealthPercent = healthPercent;
      final int finalDisplayHealth = (int) displayHealth;
      final float fAlpha = this.animAlpha;

      NanoVGHelper nvg = NanoVGHelper.INSTANCE;

      nvg.setupAndDraw(true, vg -> {
         nvg.translate(vg, x, y);
         nvg.scale(vg, effectiveScale, effectiveScale);

         float rad = roundBorder ? cornerRad : 0f;

         if (!verticalMode) {
            float headSize = 35f;
            float headOffset = showText || headTexture != null ? headSize + 8f : 0f;

            float nameWidth = nvg.getTextWidth(vg, finalTarget.getName(), 12.0f, Fonts.MEDIUM);
            float addedLen = showText ? (headOffset + 10 + nameWidth + 60) : 110f;
            float height = 47f;

            if (showOutline) {
               int olColor = alpha(outlineColor.getRGB(), fAlpha);
               nvg.drawRoundedRect(vg, -outlineWidth, -outlineWidth, addedLen + outlineWidth * 2, height + outlineWidth * 2, olColor, rad + outlineWidth);
            }

            if (blurBackground) {
               BlurUtil.drawBlurredBackground(x, y, addedLen * effectiveScale, height * effectiveScale, blurRadius);
            }

            if (redTheme) {
               nvg.drawRoundedRect(vg, 0, 0, addedLen, height, alpha(RED_60, fAlpha), rad);
               nvg.drawRoundedRect(vg, 0, 0, finalHealthPercent * addedLen, height, alpha(RED_40, fAlpha), rad);
            } else {
               nvg.drawRoundedRect(vg, 0, 0, addedLen, height, alpha(BLACK_75, fAlpha), rad);
               nvg.drawRoundedRect(vg, 0, 0, finalHealthPercent * addedLen, height, alpha(WHITE_60, fAlpha), rad);
            }

            // Draw text using NanoVG font (smaller size) — 半透明白色
            if (showText) {
               nvg.drawText(vg, finalTarget.getName(), headOffset + 5, 16 + 4, alpha(WHITE_90, fAlpha), 10.0f, Fonts.MEDIUM);
               nvg.drawText(vg, "Health " + finalDisplayHealth, headOffset + 5, 28 + 4, alpha(WHITE_90, fAlpha), 9.0f, Fonts.MEDIUM);
            }

            nvg.resetTransform(vg);
         } else {
            float width = 47f;
            float height = 120f;

            if (showOutline) {
               int olColor = alpha(outlineColor.getRGB(), fAlpha);
               nvg.drawRoundedRect(vg, -outlineWidth, -outlineWidth, width + outlineWidth * 2, height + outlineWidth * 2, olColor, rad + outlineWidth);
            }

            if (blurBackground) {
               BlurUtil.drawBlurredBackground(x, y, width * effectiveScale, height * effectiveScale, blurRadius);
            }

            if (redTheme) {
               nvg.drawRoundedRect(vg, 0, 0, width, height, alpha(RED_60, fAlpha), rad);
               nvg.drawRoundedRect(vg, 0, height - finalHealthPercent * height, width, height, alpha(RED_40, fAlpha), rad);
            } else {
               nvg.drawRoundedRect(vg, 0, 0, width, height, alpha(BLACK_75, fAlpha), rad);
               nvg.drawRoundedRect(vg, 0, height - finalHealthPercent * height, width, height, alpha(WHITE_60, fAlpha), rad);
            }

            nvg.resetTransform(vg);
         }
      });

      if (!verticalMode && headTexture != null && fAlpha > 0.01F) {
         GlStateManager.pushMatrix();
         GlStateManager.translate(x, y, 0);
         GlStateManager.scale(effectiveScale, effectiveScale, effectiveScale);
         GlStateManager.disableDepth();
         GlStateManager.enableBlend();
         GlStateManager.blendFunc(770, 771);
         GlStateManager.color(1.0F, 1.0F, 1.0F, fAlpha);
         mc.getTextureManager().bindTexture(headTexture);
         Gui.drawScaledCustomSizeModalRect(5, 5, 8.0F, 8.0F, 8, 8, 35, 35, 64.0F, 64.0F);
         Gui.drawScaledCustomSizeModalRect(5, 5, 40.0F, 8.0F, 8, 8, 35, 35, 64.0F, 64.0F);
         GlStateManager.disableBlend();
         GlStateManager.enableDepth();
         GlStateManager.popMatrix();
      }
   }

   private EntityLivingBase getExampleTarget() {
      return mc.thePlayer;
   }

   @Override
   protected float getWidth(float scale, boolean example) {
      return (verticalMode ? 47.0f : 180.0f) * scale * contentScale;
   }

   @Override
   protected float getHeight(float scale, boolean example) {
      return (verticalMode ? 120.0f : 47.0f) * scale * contentScale;
   }

   @EventTarget
   public void onPacket(PacketEvent event) {
      if (event.getType() == EventType.SEND && event.getPacket() instanceof net.minecraft.network.play.client.C02PacketUseEntity) {
         net.minecraft.network.play.client.C02PacketUseEntity packet = (net.minecraft.network.play.client.C02PacketUseEntity) event.getPacket();
         if (packet.getAction() != net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK) return;
         net.minecraft.entity.Entity entity = packet.getEntityFromWorld(mc.theWorld);
         if (entity instanceof EntityLivingBase) {
            lastAttackTimer.reset();
            lastTarget = (EntityLivingBase) entity;
         }
      }
   }
}
