package elara.module.render;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.Priority;
import elara.events.Render2DEvent;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.property.properties.BooleanProperty;
import elara.property.properties.ColorProperty;
import elara.property.properties.FloatProperty;
import elara.property.properties.ModeProperty;
import elara.property.properties.PercentProperty;
import elara.util.RenderUtil;
import elara.util.shader.GlowEffectShader;
import java.awt.Color;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.item.ItemStack;

/**
 * Handheld item edge glow halo.
 *
 * Minimal, side-effect-free implementation: no manual GL state poking, no
 * extra FBOs, no custom shaders. Everything goes through the project's
 * existing {@link GlowEffectShader} pipeline which already handles matrix
 * save/restore, FBO lifecycle, viewport reset and overlay projection.
 *
 * Only two optimisations are retained, both allocation-free and independent
 * of the render pipeline:
 *   - Reflection handle for EntityRenderer.renderHand resolved once at class
 *     init (never Method#getDeclaredMethods inside a frame).
 *   - ScaledResolution cached for the current display size (no new on every
 *     hotbar draw when size is unchanged).
 */
public class ItemGlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty(
            "color-mode", 0, new String[]{"HUD", "RAINBOW", "STATIC"}
    );
    public final ColorProperty staticColor = new ColorProperty(
            "static-color", new Color(90, 200, 255, 255).getRGB(),
            () -> mode.getValue() == 2
    );
    public final FloatProperty radius = new FloatProperty("radius", 4.0F, 1.0F, 10.0F);
    public final PercentProperty alpha = new PercentProperty("alpha", 255);
    public final FloatProperty intensity = new FloatProperty("intensity", 3.5F, 0.5F, 10.0F);
    public final BooleanProperty hotbar = new BooleanProperty("hotbar", true);

    /** Cached reflection handle for EntityRenderer.renderHand(float, int). */
    private static final Method RENDER_HAND_METHOD;
    static {
        Method m = null;
        try {
            for (Method method : EntityRenderer.class.getDeclaredMethods()) {
                Class<?>[] pts = method.getParameterTypes();
                if (pts.length == 2 && pts[0] == float.class && pts[1] == int.class) {
                    method.setAccessible(true);
                    m = method;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        RENDER_HAND_METHOD = m;
    }

    /**
     * Separate GlowEffectShader instance (never the GLOW_SHADER singleton).
     * FramebufferShader.setupFrameBuffer already size-matches, so this FBO
     * stays resident across frames; there is nothing to pre-cache or warm
     * beyond letting the first draw populate it.
     */
    private static final class HandGlowHolder {
        private static final GlowEffectShader HAND_GLOW = new GlowEffectShader();
    }

    private ScaledResolution cachedSr;
    private int lastSrW, lastSrH;

    public ItemGlow() {
        super("ItemGlow", false, true, "Handheld item edge glow halo", ModuleCategory.RENDER);
    }

    private Color hudColor(long offset) {
        HUD hud = (HUD) Elara.moduleManager.getModule(HUD.class);
        return hud == null ? Color.WHITE : hud.getColor(System.currentTimeMillis(), offset);
    }

    private Color rainbowColor(long offset) {
        float hue = (System.currentTimeMillis() + offset * 300L) % 3000L / 3000.0F;
        return Color.getHSBColor(hue, 1.0F, 1.0F);
    }

    private Color getColor(long offset) {
        switch (mode.getValue()) {
            case 0: return hudColor(offset);
            case 1: return rainbowColor(offset);
            default: return new Color(staticColor.getValue());
        }
    }

    private boolean shouldSkip() {
        if (mc == null) return true;
        if (mc.thePlayer == null) return true;
        if (mc.gameSettings == null) return true;
        if (mc.gameSettings.thirdPersonView != 0) return true;
        if (mc.entityRenderer == null) return true;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return true;
        if (held.getItem() == null) return true;
        return RENDER_HAND_METHOD == null;
    }

    private ScaledResolution getCachedResolution() {
        int dw = mc.displayWidth;
        int dh = mc.displayHeight;
        if (cachedSr == null || lastSrW != dw || lastSrH != dh) {
            cachedSr = new ScaledResolution(mc);
            lastSrW = dw;
            lastSrH = dh;
        }
        return cachedSr;
    }

    @EventTarget(Priority.HIGH)
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;
        if (shouldSkip()) return;

        float partialTicks = event.getPartialTicks();
        EntityRenderer er = mc.entityRenderer;

        Color base = getColor(0);
        int a = Math.max(0, Math.min(255, alpha.getValue()));
        Color drawColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
        float r = radius.getValue();
        float intensityVal = intensity.getValue();

        GlowEffectShader handGlow = HandGlowHolder.HAND_GLOW;

        // ---- Hand glow ----
        // FramebufferShader.startDraw does: pushMatrix, pushAttrib, FBO bind+clear,
        // viewport pin, camera transform setup. stopDraw undoes all of it and
        // composites back to the main framebuffer. No GL leakage expected.
        handGlow.startDraw(partialTicks);
        try {
            RENDER_HAND_METHOD.invoke(er, partialTicks, 1);
        } catch (Throwable ignored) {
            // Mask stays blank; stopDraw will still balance the shader+matrix state.
        }
        handGlow.stopDraw(drawColor, r, intensityVal);

        // ---- Optional hotbar glow ----
        if (!hotbar.getValue()) return;
        if (mc.ingameGUI == null) return;

        ScaledResolution sr = getCachedResolution();
        int scaledW = sr.getScaledWidth();
        int scaledH = sr.getScaledHeight();
        int cx = scaledW / 2;
        int cy = scaledH - 22;
        int pad = 6;

        Color bc = new Color(getColor(1).getRGB());
        int ba = Math.min(255, Math.max(90, (int) (a * 0.75f)));
        Color hotbarGlow = new Color(bc.getRed(), bc.getGreen(), bc.getBlue(), ba);
        float hbR = Math.max(2.0f, r * 0.8f);

        int left = cx - 91 - pad;
        int right = cx + 91 + pad;
        int top = cy - 22 - pad;
        int bot = cy + pad;

        handGlow.startDraw(partialTicks);
        RenderUtil.drawRect(left, top, right, bot, hotbarGlow.getRGB());
        handGlow.stopDraw(hotbarGlow, hbR, intensityVal);
    }
}
