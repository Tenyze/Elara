package elara.module.render;

import elara.Elara;
import elara.event.EventTarget;
import elara.events.Render2DEvent;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.property.properties.*;
import elara.util.shader.BlurUtils;
import elara.util.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

public class WaterMark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String LOGO_PATH = "assets/elara/logo.png";

    // 配置
    public final BooleanProperty showFPS = new BooleanProperty("show-fps", true);
    public final BooleanProperty showTime = new BooleanProperty("show-time", false);
    public final FloatProperty scale = new FloatProperty("scale", 1.0f, 0.5f, 2.0f);
    public final IntProperty offsetX = new IntProperty("offset-x", 4, 0, 200);
    public final IntProperty offsetY = new IntProperty("offset-y", 4, 0, 200);
    public final ModeProperty followHud = new ModeProperty("follow-hud", 0, new String[]{"Left-Top", "Right-Top", "Left-Bottom", "Right-Bottom"});
    public final ColorProperty accentColor = new ColorProperty("accent-color", new Color(170, 0, 255).getRGB());
    public final ColorProperty subColor = new ColorProperty("sub-color", new Color(187, 187, 187, 204).getRGB());
    public final ColorProperty badgeColor = new ColorProperty("badge-color", new Color(17, 17, 17, 176).getRGB());
    public final ModeProperty shadowStyle = new ModeProperty("shadow-style", 0, new String[]{"BLACK", "HUD-COLOR"});

    private static final int BLOOM_MASK_ALPHA = 210;

    // 图标
    private ResourceLocation logoTexture = null;
    private boolean logoLoaded = false;
    private int logoWidth = 64;
    private int logoHeight = 64;

    public WaterMark() {
        super("WaterMark", true, false, "", ModuleCategory.RENDER);
        this.showFPS.setCategory("Display");
        this.showTime.setCategory("Display");
        this.scale.setCategory("Appearance");
        this.offsetX.setCategory("Display");
        this.offsetY.setCategory("Display");
        this.followHud.setCategory("Display");
        this.accentColor.setCategory("Appearance");
        this.subColor.setCategory("Appearance");
        this.badgeColor.setCategory("Appearance");
        this.shadowStyle.setCategory("Appearance");
    }

    @Override
    public void onEnabled() {
        ensureLogo();
    }

    private void ensureLogo() {
        if (logoLoaded) return;

        try (InputStream in = WaterMark.class.getClassLoader().getResourceAsStream(LOGO_PATH)) {
            if (in != null) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    DynamicTexture tex = new DynamicTexture(img);
                    logoTexture = mc.getTextureManager().getDynamicTextureLocation("elara_logo", tex);
                    logoWidth = img.getWidth();
                    logoHeight = img.getHeight();
                    logoLoaded = true;
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 占位图标
        BufferedImage fallback = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = fallback.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(170, 0, 255));
        g.fillOval(2, 2, 60, 60);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("E", (64 - fm.stringWidth("E")) / 2, (64 - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();

        DynamicTexture tex = new DynamicTexture(fallback);
        logoTexture = mc.getTextureManager().getDynamicTextureLocation("elara_logo_fallback", tex);
        logoWidth = 64;
        logoHeight = 64;
        logoLoaded = true;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        ensureLogo();

        final float s = this.scale.getValue();
        final int accent = this.accentColor.getValue();
        final int sub = this.subColor.getValue();
        final int bg = this.badgeColor.getValue();

        // 文字
        final String brand = "ELARA";
        final String version = " Indev 6.x";
        final String fpsText = this.showFPS.getValue() ? "  |  " + mc.getDebugFPS() + "fps" : null;
        final String timeText = this.showTime.getValue() ? "  |  " + new SimpleDateFormat("HH:mm").format(new Date()) : null;

        final int labelW = mc.fontRendererObj.getStringWidth(brand);
        final int versionW = mc.fontRendererObj.getStringWidth(version);
        final int fpsW = fpsText != null ? mc.fontRendererObj.getStringWidth(fpsText) : 0;
        final int timeW = timeText != null ? mc.fontRendererObj.getStringWidth(timeText) : 0;
        final int fontH = mc.fontRendererObj.FONT_HEIGHT;

        // 几何
        final float pad = 5.0f;
        final float gap = 7.0f;
        final float badgeInnerW = (labelW + versionW + fpsW + timeW) + pad * 2.0f;
        final float badgeH = fontH + pad * 2.0f;
        final float logoBlockH = badgeH;
        final float logoBlockW = badgeH;
        final float totalW = logoBlockW + gap + badgeInnerW;
        final float totalH = badgeH;
        final float radius = 6.0f;
        final float imgPad = 1.5f;

        // 位置
        final ScaledResolution sr = new ScaledResolution(mc);
        final int screenW = sr.getScaledWidth();
        final int screenH = sr.getScaledHeight();

        final int mode = this.followHud.getValue();
        final int ox = this.offsetX.getValue();
        final int oy = this.offsetY.getValue();

        final float scaledTotalW = totalW * s;
        final float scaledTotalH = totalH * s;

        float startX, startY;
        switch (mode) {
            case 1: startX = screenW - scaledTotalW - ox; startY = oy; break;
            case 2: startX = ox; startY = screenH - scaledTotalH - oy; break;
            case 3: startX = screenW - scaledTotalW - ox; startY = screenH - scaledTotalH - oy; break;
            default: startX = ox; startY = oy; break;
        }

        // 两个块的本地坐标
        final float lvX1 = 0.0f;
        final float lvY1 = 5.0f;
        final float lvX2 = lvX1 + logoBlockW;
        final float lvY2 = lvY1 + logoBlockH;
        final float bgX1 = lvX2 + gap;
        final float bgY1 = lvY1;
        final float bgX2 = bgX1 + badgeInnerW;
        final float bgY2 = bgY1 + badgeH;

        // ========== 阴影：BlurUtils（与 HUD 完全一致） ==========
        int shadowColor;
        if (this.shadowStyle.getValue() == 0) {
            // 黑色模式：纯黑 + 210 透明度
            shadowColor = new Color(0, 0, 0, BLOOM_MASK_ALPHA).getRGB();
        } else {
            // HUD-COLOR 模式
            HUD hud = (HUD) Elara.moduleManager.modules.get(HUD.class);
            Color hudColor = (hud != null && hud.isEnabled()) ? hud.getColor(System.currentTimeMillis()) : new Color(this.accentColor.getValue());
            if (hudColor.getRed() == 255 && hudColor.getGreen() == 255 && hudColor.getBlue() == 255) {
                hudColor = new Color(this.accentColor.getValue());
            }
            shadowColor = new Color(hudColor.getRed(), hudColor.getGreen(), hudColor.getBlue(), BLOOM_MASK_ALPHA).getRGB();
        }

        float absX1 = startX + lvX1 * s;
        float absY1 = startY + lvY1 * s;
        float absX2 = startX + lvX2 * s;
        float absY2 = startY + lvY2 * s;
        float absBgX1 = startX + bgX1 * s;
        float absBgY1 = startY + bgY1 * s;
        float absBgX2 = startX + bgX2 * s;
        float absBgY2 = startY + bgY2 * s;

        BlurUtils.prepareBloom();

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        RoundedUtils.drawRound(absX1, absY1, absX2 - absX1, absY2 - absY1, radius * s, new Color(shadowColor, true));
        RoundedUtils.drawRound(absBgX1, absBgY1, absBgX2 - absBgX1, absBgY2 - absBgY1, radius * s, new Color(shadowColor, true));

        GlStateManager.enableTexture2D();

        // 跟 HUD 一样的参数
        BlurUtils.bloomEnd(3, 2.0f);

        // ========== 实际内容 ==========
        GlStateManager.pushMatrix();
        GlStateManager.translate(startX, startY, 0.0f);
        GlStateManager.scale(s, s, 1.0f);

        Color bgColor = new Color(bg, true);
        RoundedUtils.drawRound(lvX1, lvY1, lvX2 - lvX1, lvY2 - lvY1, radius, bgColor);

        if (logoTexture != null) {
            try {
                GlStateManager.enableTexture2D();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                mc.getTextureManager().bindTexture(logoTexture);
                float ix = lvX1 + imgPad;
                float iy = lvY1 + imgPad;
                float iw = logoBlockW - imgPad * 2.0f;
                float ih = logoBlockH - imgPad * 2.0f;
                Gui.drawScaledCustomSizeModalRect(
                        (int) Math.floor(ix), (int) Math.floor(iy),
                        0.0f, 0.0f,
                        logoWidth, logoHeight,
                        (int) Math.ceil(iw), (int) Math.ceil(ih),
                        logoWidth, logoHeight
                );
                GlStateManager.disableTexture2D();
            } catch (Throwable e) {
                drawFallbackLogo(lvX1, lvY1, lvX2, lvY2, radius, accent);
            }
        } else {
            drawFallbackLogo(lvX1, lvY1, lvX2, lvY2, radius, accent);
        }

        RoundedUtils.drawRound(bgX1, bgY1, bgX2 - bgX1, bgY2 - bgY1, radius, bgColor);

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        float tx = bgX1 + pad;
        final float ty = bgY1 + pad;
        mc.fontRendererObj.drawString(brand, tx, ty, accent, true);
        tx += labelW;
        mc.fontRendererObj.drawString(version, tx, ty, sub, true);
        tx += versionW;
        if (fpsText != null) {
            mc.fontRendererObj.drawString(fpsText, tx, ty, sub, true);
            tx += fpsW;
        }
        if (timeText != null) {
            mc.fontRendererObj.drawString(timeText, tx, ty, sub, true);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private void drawFallbackLogo(float x1, float y1, float x2, float y2, float radius, int accent) {
        try {
            Color accentColor = new Color(accent, true);
            float w = x2 - x1;
            float h = y2 - y1;
            float inset = 2.0f;
            RoundedUtils.drawRound(x1 + inset, y1 + inset, w - inset * 2, h - inset * 2, Math.max(0, radius - inset), accentColor);
            String ch = "E";
            int tw = mc.fontRendererObj.getStringWidth(ch);
            int th = mc.fontRendererObj.FONT_HEIGHT;
            float cx = (x1 + x2) / 2.0f - tw / 2.0f;
            float cy = (y1 + y2) / 2.0f - th / 2.0f;
            GlStateManager.enableTexture2D();
            mc.fontRendererObj.drawString(ch, cx, cy, 0xFFFFFFFF, false);
        } catch (Throwable ignored) {}
    }
}