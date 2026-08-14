package elara.module.render;

import elara.Elara;
import elara.enums.ChatColors;
import elara.event.EventTarget;
import elara.event.types.Priority;
import elara.events.Render2DEvent;
import elara.events.Render3DEvent;
import elara.events.ResizeEvent;
import elara.mixin.IAccessorEntityRenderer;
import elara.mixin.IAccessorRenderManager;
import elara.module.Module;
import elara.util.ColorUtil;
import elara.util.RenderUtil;
import elara.util.TeamUtil;
import elara.util.shader.GlowShader;
import elara.util.shader.OutlineShader;
import elara.property.properties.BooleanProperty;
import elara.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class ESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final OutlineShader outlineRenderer = new OutlineShader();
    private final GlowShader glowShader = new GlowShader();
    private Framebuffer framebuffer = null;
    private boolean outline = true;
    private boolean glow = true;
    public final ModeProperty mode = new ModeProperty("mode", 2, new String[]{"NONE", "2D", "3D", "OUTLINE", "FAKECORNER", "FAKE2D"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "TEAMS", "HUD", "COLOR"});
    public final ModeProperty healthBar = new ModeProperty("health-bar", 0, new String[]{"NONE", "2D", "RAVEN"});
    public final ModeProperty healthSource = new ModeProperty("health-source", 0, new String[]{"DEFAULT", "TAB", "TAG"});
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true);
    public final BooleanProperty self = new BooleanProperty("self", false);
    public final BooleanProperty bots = new BooleanProperty("bots", false);
    
    // Performance optimization fields
    private final List<EntityPlayer> cachedPlayers = new java.util.ArrayList<>();
    private ScaledResolution cachedResolution = null;
    private long lastResolutionUpdate = 0L;

    private boolean shouldRenderPlayer(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
            return false;
        } else if (!entityPlayer.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entityPlayer.getEntityBoundingBox(), 0.1F)) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.isBot(entityPlayer)) {
                return this.bots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.friends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.enemies.getValue() : this.players.getValue();
            }
        } else {
            return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer) {
        if (TeamUtil.isFriend(entityPlayer)) {
            return Elara.friendManager.getColor();
        } else if (TeamUtil.isTarget(entityPlayer)) {
            return Elara.targetManager.getColor();
        } else {
            switch (this.color.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, 1.0F);
                case 1:
                    // Teams 模式：颜色跟随 Teams 模块选择的 mode（Mixed/Scoreboard/Armor/TagColor）
                    return new Color(elara.module.misc.Teams.getColorForESP(entityPlayer));
                case 2:
                    int hudColor = ((elara.module.render.HUD) Elara.moduleManager.modules.get(elara.module.render.HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                    return new Color(hudColor);
                case 3:
                    // Color 模式：所有玩家显示其 Teams 检测到的原始队伍颜色
                    return new Color(elara.module.misc.Teams.getDetectedColorForESP(entityPlayer));
                default:
                    return new Color(-1);
            }
        }
    }

    private float getDisplayHealth(EntityPlayer player) {
        int mode = this.healthSource.getValue();
        if (mode == 0) {
            return player.getHealth();
        }
        int slot = (mode == 1) ? 0 : 2; // TAB = PLAYER_LIST (0), TAG = BELOW_NAME (2)
        try {
            net.minecraft.scoreboard.Scoreboard sb = mc.theWorld.getScoreboard();
            if (sb == null) return player.getHealth();
            net.minecraft.scoreboard.ScoreObjective objective = sb.getObjectiveInDisplaySlot(slot);
            if (objective == null) return player.getHealth();
            net.minecraft.scoreboard.Score score = sb.getValueFromObjective(player.getName(), objective);
            if (score == null) return player.getHealth();
            int points = score.getScorePoints();
            if (points <= 0 && player.getHealth() > 0.0F) {
                return player.getHealth();
            }
            return (float) points;
        } catch (Throwable t) {
            return player.getHealth();
        }
    }

    public ESP() {
        super("ESP", false);
    }

    public boolean isOutlineEnabled() {
        return this.outline;
    }

    public boolean isGlowEnabled() {
        return this.glow;
    }

    @EventTarget
    public void onResize(ResizeEvent event) {
        if (this.framebuffer != null) {
            this.framebuffer.deleteFramebuffer();
        }
        this.framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
    }

    private List<EntityPlayer> getRenderedPlayers() {
        cachedPlayers.clear();
        for (Object entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (shouldRenderPlayer(player)) {
                    cachedPlayers.add(player);
                }
            }
        }
        return cachedPlayers;
    }
    
    @EventTarget(Priority.HIGH)
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && (this.mode.getValue() == 1 || this.mode.getValue() == 3 || this.healthBar.getValue() == 1)) {
            List<EntityPlayer> renderedEntities = getRenderedPlayers();
            if (!renderedEntities.isEmpty()) {
                if (this.mode.getValue() == 3) {
                    GlStateManager.pushMatrix();
                    GlStateManager.pushAttrib();
                    if (this.framebuffer == null) {
                        this.framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
                    }
                    this.framebuffer.bindFramebuffer(false);
                    ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
                    boolean shadow = mc.gameSettings.entityShadows;
                    mc.gameSettings.entityShadows = false;
                    this.outline = false;
                    this.glow = false;
                    this.glowShader.use();
                    for (EntityPlayer player : renderedEntities) {
                        Color entityColor = this.getEntityColor(player);
                        this.glowShader.W(entityColor);
                        boolean invisible = player.isInvisible();
                        player.setInvisible(false);
                        mc.getRenderManager().renderEntityStatic(player, event.getPartialTicks(), true);
                        player.setInvisible(invisible);
                    }
                    this.glowShader.stop();
                    this.glow = true;
                    this.outline = true;
                    mc.gameSettings.entityShadows = shadow;
                    mc.entityRenderer.disableLightmap();
                    mc.entityRenderer.setupOverlayRendering();
                    mc.getFramebuffer().bindFramebuffer(false);
                    this.outlineRenderer.use();
                    RenderUtil.drawFramebuffer(this.framebuffer);
                    this.outlineRenderer.stop();
                    this.framebuffer.framebufferClear();
                    mc.getFramebuffer().bindFramebuffer(false);
                    GlStateManager.popAttrib();
                    GlStateManager.popMatrix();
                }
                if (this.mode.getValue() == 1 || this.healthBar.getValue() == 1) {
                    RenderUtil.enableRenderState();
                    double scaleFactor = new ScaledResolution(mc).getScaleFactor();
                    double scale = scaleFactor / Math.pow(scaleFactor, 2.0);
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(scale, scale, scale);
                    for (EntityPlayer player : renderedEntities) {
                        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
                        Vector4d screenPosition = RenderUtil.projectToScreen(player, scaleFactor);
                        mc.entityRenderer.setupOverlayRendering();
                        if (screenPosition != null) {
                            float x = (float) screenPosition.x;
                            float y = (float) screenPosition.y;
                            float z = (float) screenPosition.z;
                            float w = (float) screenPosition.w;
                            if (this.mode.getValue() == 1) {
                                int color = this.getEntityColor(player).getRGB();
                                RenderUtil.drawOutlineRect(x, y, z, w, 3.0F, 0, (color & 16579836) >> 2 | color & 0xFF000000);
                                RenderUtil.drawOutlineRect(x, y, z, w, 1.5F, 0, color);
                            }
                            if (this.healthBar.getValue() == 1) {
                                float heal = this.getDisplayHealth(player) + player.getAbsorptionAmount();
                                float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                                float box = (z - x) * 0.08F;
                                Color healthColor = ColorUtil.getHealthBlend(percent);
                                RenderUtil.drawLine(x - box, y, x - box, w, 3.0F, ColorUtil.darker(healthColor, 0.2F).getRGB());
                                RenderUtil.drawLine(x - box, w, x - box, w + (y - w) * percent, 1.5F, healthColor.getRGB());
                            }
                        }
                    }
                    GlStateManager.popMatrix();
                    RenderUtil.disableRenderState();
                }
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && (this.mode.getValue() == 2 || this.mode.getValue() == 4 || this.mode.getValue() == 5 || this.healthBar.getValue() == 2)) {
            RenderUtil.enableRenderState();
            List<EntityPlayer> renderedEntities = getRenderedPlayers();
            for (EntityPlayer player : renderedEntities) {
                if (player.ignoreFrustumCheck || RenderUtil.isInViewFrustum(player.getEntityBoundingBox(), 0.1F)) {
                    if (this.mode.getValue() == 2) {
                        Color color = this.getEntityColor(player);
                        RenderUtil.drawEntityBoundingBox(player, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), 1.5F, 0.1F);
                        GlStateManager.resetColor();
                    }
                    if (this.mode.getValue() == 4) {
                        Color color = this.getEntityColor(player);
                        RenderUtil.drawCornerESP(player, color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
                    }
                    if (this.mode.getValue() == 5) {
                        Color color = this.getEntityColor(player);
                        RenderUtil.drawFake2DESP(player, color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
                    }
                    if (this.healthBar.getValue() == 2) {
                        double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
                        double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()
                                - 0.1F;
                        double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(x, y, z);
                        GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                        float heal = this.getDisplayHealth(player) + player.getAbsorptionAmount();
                        float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                        Color healthColor = ColorUtil.getHealthBlend(percent);
                        float height = player.height + 0.2F;
                        RenderUtil.drawRect3D(0.57250005F, -0.027500002F, 0.7275F, height + 0.027500002F, Color.black.getRGB());
                        RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height, Color.darkGray.getRGB());
                        RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height * percent, healthColor.getRGB());
                        GlStateManager.popMatrix();
                    }
                }
            }
            RenderUtil.disableRenderState();
        }
    }
}