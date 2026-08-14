package elara.util;

import elara.util.shader.BlurUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;

/**
 * Blur utility for OneConfig HUDs.
 *
 * 之前通过反射调用 OneConfigBlur API，但该类在 OneConfig jar 中根本不存在，
 * 导致 blur 从未执行，HUD 背景显示为纯黑/深色。
 *
 * 修复：直接使用项目自有的 KawaseBlur 着色器系统（和原客户端 HUD 相同的方案）。
 * 流程：
 *   1. BlurUtils.prepareBlur() — 绑定 stencil FBO
 *   2. RenderUtil.drawRect() — 在 stencil 上画矩形作为模糊遮罩
 *   3. BlurUtils.blurEnd() — 用 Kawase 着色器模糊主 framebuffer 内容并渲染回屏幕
 */
public class BlurUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * Draw a blurred background region using the project's KawaseBlur shader.
     *
     * @param x          screen X (same coordinate space as RenderUtil.drawRect)
     * @param y          screen Y
     * @param width      region width in screen pixels
     * @param height     region height in screen pixels
     * @param blurRadius blur radius (passed as Kawase offset)
     */
    public static void drawBlurredBackground(float x, float y, float width, float height, float blurRadius) {
        if (mc == null || mc.theWorld == null) return;
        if (width <= 0 || height <= 0) return;

        float offset = Math.max(2.0f, Math.min(20.0f, blurRadius));

        GlStateManager.pushAttrib();
        try {
            // 1. 准备 stencil framebuffer
            BlurUtils.prepareBlur();
            // 2. 在 stencil 上画矩形作为模糊区域遮罩（和原客户端 HUD 一样用 RenderUtil.drawRect）
            RenderUtil.drawRect(x, y, x + width, y + height, Color.BLACK.getRGB());
            // 3. 执行 Kawase 模糊并渲染回主 framebuffer
            BlurUtils.blurEnd(2, offset);
        } catch (Throwable t) {
            // 着色器不可用时回退到半透明背景
            drawFallbackBackground(x, y, width, height);
        } finally {
            GlStateManager.popAttrib();
        }
    }

    public static void drawBlurredBackground(float x, float y, float width, float height) {
        drawBlurredBackground(x, y, width, height, 8.0f);
    }

    public static boolean isAvailable() {
        return true;
    }

    /**
     * 回退方案：绘制半透明深色背景
     */
    private static void drawFallbackBackground(float x, float y, float width, float height) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(0.1f, 0.1f, 0.1f, 0.85f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + height);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
    }
}
