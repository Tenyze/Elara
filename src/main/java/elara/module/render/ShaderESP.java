/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.EntityLivingBase
 *  net.minecraft.entity.monster.EntityMob
 *  net.minecraft.entity.passive.EntityAnimal
 *  net.minecraft.entity.player.EntityPlayer
 */
package elara.module.render;

import elara.Elara;
import elara.event.EventTarget;
import elara.events.Render2DEvent;
import elara.module.Module;
import elara.module.render.HUD;
import elara.property.properties.BooleanProperty;
import elara.property.properties.FloatProperty;
import elara.property.properties.ModeProperty;
import elara.util.TeamUtil;
import elara.util.shader.GlowEffectShader;
import elara.util.shader.OutlineEffectShader;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;

public class ShaderESP
extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final GlowEffectShader glowShader = new GlowEffectShader();
    private final OutlineEffectShader outlineShader = new OutlineEffectShader();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"GLOW", "OUTLINE"});
    public final FloatProperty radius = new FloatProperty("radius", Float.valueOf(2.3f), Float.valueOf(1.0f), Float.valueOf(3.0f));
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "TEAMS", "HUD"});
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false);
    public final BooleanProperty animals = new BooleanProperty("animals", false);
    public final BooleanProperty self = new BooleanProperty("self", false);

    public ShaderESP() {
        super("ShaderESP", false);
    }

    private boolean shouldRender(Entity entity) {
        if (entity instanceof EntityLivingBase && ((EntityLivingBase)entity).deathTime > 0) {
            return false;
        }
        if (mc.getRenderViewEntity().getDistanceToEntity(entity) > 512.0f) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)entity;
            if (player == ShaderESP.mc.thePlayer) {
                return (Boolean)this.self.getValue();
            }
            if (player.isInvisible()) {
                return false;
            }
            boolean isFriend = TeamUtil.isFriend(player);
            boolean isEnemy = TeamUtil.isTarget(player);
            if (isFriend) {
                return (Boolean)this.friends.getValue();
            }
            if (isEnemy) {
                return (Boolean)this.enemies.getValue();
            }
            return (Boolean)this.players.getValue();
        }
        if (entity instanceof EntityMob && ((Boolean)this.mobs.getValue()).booleanValue()) {
            return true;
        }
        return entity instanceof EntityAnimal && (Boolean)this.animals.getValue() != false;
    }

    private Color getEntityColor(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)entity;
            if (TeamUtil.isFriend(player)) {
                return Elara.friendManager.getColor();
            }
            if (TeamUtil.isTarget(player)) {
                return Elara.targetManager.getColor();
            }
        }
        switch ((Integer)this.color.getValue()) {
            case 1: {
                return TeamUtil.getTeamColor((EntityPlayer)entity, 1.0f);
            }
            case 2: {
                HUD hud = (HUD)Elara.moduleManager.getModule(HUD.class);
                return hud.getColor(System.currentTimeMillis());
            }
        }
        return new Color(255, 255, 255);
    }

    @EventTarget(value=1)
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        @SuppressWarnings("unchecked") LinkedHashMap colorGroups = new LinkedHashMap();
        for (Entity entity : ShaderESP.mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity == ShaderESP.mc.thePlayer && !((Boolean)this.self.getValue()).booleanValue() || !this.shouldRender(entity)) continue;
            EntityLivingBase living = (EntityLivingBase)entity;
            Color entityColor = this.getEntityColor(living);
            if (!colorGroups.containsKey(entityColor)) {
                colorGroups.put(entityColor, new ArrayList());
            }
            ((List)colorGroups.get(entityColor)).add(living);
        }
        if (colorGroups.isEmpty()) {
            return;
        }
        float r = ((Float)this.radius.getValue()).floatValue();
        boolean isGlow = (Integer)this.mode.getValue() == 0;
        Set cgEntries = colorGroups.entrySet(); for (Object cgObj : cgEntries) { Map.Entry entry = (Map.Entry)cgObj;
            if (isGlow) {
                this.glowShader.startDraw(event.getPartialTicks());
            } else {
                this.outlineShader.startDraw(event.getPartialTicks());
            }
            List entityList = (List)entry.getValue(); for (Object entObj : entityList) { EntityLivingBase entity = (EntityLivingBase)entObj;
                boolean invisible = entity.isInvisible();
                entity.setInvisible(false);
                mc.getRenderManager().renderEntityStatic((Entity)entity, event.getPartialTicks(), true);
                entity.setInvisible(invisible);
            }
            Color drawColor = new Color(((Color)entry.getKey()).getRed(), ((Color)entry.getKey()).getGreen(), ((Color)entry.getKey()).getBlue(), 200);
            if (isGlow) {
                this.glowShader.stopDraw(drawColor, r, 1.0f);
                continue;
            }
            this.outlineShader.stopDraw(drawColor, r, 1.0f);
        }
    }
}


