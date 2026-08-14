/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.GlStateManager
 *  net.minecraft.client.renderer.Tessellator
 *  net.minecraft.client.renderer.WorldRenderer
 *  net.minecraft.client.renderer.vertex.DefaultVertexFormats
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.EntityLivingBase
 *  org.lwjgl.opengl.GL11
 */
package elara.module.render;

import elara.Elara;
import elara.event.EventTarget;
import elara.events.Render3DEvent;
import elara.mixin.IAccessorRenderManager;
import elara.module.Module;
import elara.module.combat.KillAura;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

public class CombatVisuals
extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty colorMode = new ModeProperty("color", 0, new String[]{"CUSTOM", "RAINBOW", "SKY", "FADE", "HEALTH", "HUD"});
    public final IntProperty colorRed = new IntProperty("red", 0, 0, 255);
    public final IntProperty colorGreen = new IntProperty("green", 160, 0, 255);
    public final IntProperty colorBlue = new IntProperty("blue", 255, 0, 255);
    private double ticks = 0.0;
    private long lastFrame = 0L;

    public CombatVisuals() {
        super("CombatVisuals", false);
    }

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura)Elara.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            return killAura.getTarget();
        }
        return null;
    }

    private Color hudColor() {
        try {
            HUD hud = (HUD) Elara.moduleManager.modules.get(HUD.class);
            if (hud != null) return hud.getColor(System.currentTimeMillis());
        } catch (Throwable ignored) {}
        return Color.WHITE;
    }

    private Color getColor(EntityLivingBase entity) {
        switch ((Integer)this.colorMode.getValue()) {
            case 1: {
                return new Color(Color.HSBtoRGB((float)(System.currentTimeMillis() % 4000L) / 4000.0f, 1.0f, 1.0f));
            }
            case 2: {
                return new Color(Color.HSBtoRGB((float)(System.currentTimeMillis() % 6000L) / 6000.0f, 0.6f, 1.0f));
            }
            case 3: {
                int r = (Integer)this.colorRed.getValue();
                int g = (Integer)this.colorGreen.getValue();
                int b = (Integer)this.colorBlue.getValue();
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                return new Color(Color.HSBtoRGB(hsb[0] + (float)(System.currentTimeMillis() % 3000L) / 3000.0f, hsb[1], hsb[2]));
            }
            case 4: {
                float healthRatio = entity.getHealth() / entity.getMaxHealth();
                return Color.getHSBColor(healthRatio * 0.33f, 1.0f, 1.0f);
            }
            case 5: {
                return this.hudColor();
            }
        }
        return new Color((Integer)this.colorRed.getValue(), (Integer)this.colorGreen.getValue(), (Integer)this.colorBlue.getValue());
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        EntityLivingBase target = this.resolveTarget();
        if (target == null) {
            return;
        }
        if (this.lastFrame == 0L) {
            this.lastFrame = System.currentTimeMillis();
        }
        this.ticks += 0.004 * (double)(System.currentTimeMillis() - this.lastFrame);
        this.lastFrame = System.currentTimeMillis();
        Color color = this.getColor(target);
        this.drawJelloCircle((Entity)target, event.getPartialTicks(), 0.75, color, 1.0f);
    }

    private void drawJelloCircle(Entity entity, float partialTicks, double radius, Color color, float alphaMult) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double)partialTicks - ((IAccessorRenderManager)mc.getRenderManager()).getRenderPosX();
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double)partialTicks - ((IAccessorRenderManager)mc.getRenderManager()).getRenderPosY() + Math.sin(this.ticks) + 1.0;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double)partialTicks - ((IAccessorRenderManager)mc.getRenderManager()).getRenderPosZ();
        float r = (float)color.getRed() / 255.0f;
        float g = (float)color.getGreen() / 255.0f;
        float b = (float)color.getBlue() / 255.0f;
        GL11.glPushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc((int)770, (int)771);
        GlStateManager.disableDepth();
        GlStateManager.depthMask((boolean)false);
        GL11.glShadeModel((int)7425);
        GlStateManager.disableCull();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        double yBottom = y - Math.sin(this.ticks + 1.0) / (double)2.7f;
        for (int i = 0; i <= 48; ++i) {
            double angle = Math.PI * 2 * (double)i / 48.0;
            double vecX = x + radius * Math.cos(angle);
            double vecZ = z + radius * Math.sin(angle);
            wr.pos(vecX, yBottom, vecZ).color(r, g, b, 0.0f).endVertex();
            wr.pos(vecX, y, vecZ).color(r, g, b, 0.52f * alphaMult).endVertex();
        }
        tessellator.draw();
        GL11.glLineWidth((float)1.5f);
        wr.begin(3, DefaultVertexFormats.POSITION_COLOR);
        float lineAlpha = 0.5f * alphaMult;
        for (int i = 0; i <= 48; ++i) {
            double angle = Math.PI * 2 * (double)i / 48.0;
            double vecX = x + radius * Math.cos(angle);
            double vecZ = z + radius * Math.sin(angle);
            wr.pos(vecX, y, vecZ).color(r, g, b, lineAlpha).endVertex();
        }
        tessellator.draw();
        GL11.glShadeModel((int)7424);
        GlStateManager.depthMask((boolean)true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        GL11.glPopMatrix();
    }
}

