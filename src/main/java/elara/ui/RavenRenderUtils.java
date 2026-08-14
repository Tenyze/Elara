package elara.ui;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;

/**
 * Raven-style rendering utilities ported from raven-bs ClickGUI.
 *
 * <p>Provides rounded rectangle, gradient outline, and color manipulation
 * helpers that mirror the visual style of the Raven client's ClickGUI.
 * All methods use raw GL11 calls with 0.5x scaling for crisp sub-pixel
 * rendering, identical to the original Raven implementation.</p>
 */
public final class RavenRenderUtils {

    private RavenRenderUtils() {
    }

    // ------------------------------------------------------------------
    // Color helpers
    // ------------------------------------------------------------------

    /**
     * Sets the current GL color from an ARGB integer.
     * Extracted from Raven's RenderUtils.glColor.
     */
    public static void glColor(int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }

    /**
     * Returns a new color with the specified alpha (0-1.0).
     */
    public static int setAlpha(int rgb, double alpha) {
        int a = (int) Math.max(0, Math.min(255, alpha * 255.0));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Merges the alpha channel of srcAlpha into baseRGB.
     */
    public static int mergeAlpha(int baseRGB, int alphaValue) {
        int a = (baseRGB >> 24 & 255) * alphaValue / 255;
        return (a << 24) | (baseRGB & 0x00FFFFFF);
    }

    /**
     * Linear interpolation between two colors.
     */
    public static int convertColor(int c1, int c2, double ratio) {
        double n2 = 1.0 - ratio;
        int r = (int) (((c1 >> 16 & 255)) * ratio + ((c2 >> 16 & 255)) * n2);
        int g = (int) (((c1 >> 8 & 255)) * ratio + ((c2 >> 8 & 255)) * n2);
        int b = (int) (((c1 & 255)) * ratio + ((c2 & 255)) * n2);
        int a = (int) (((c1 >> 24 & 255)) * ratio + ((c2 >> 24 & 255)) * n2);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------
    // Rounded rectangle drawing (ported from Raven RenderUtils)
    // ------------------------------------------------------------------

    /**
     * Draws a filled rounded rectangle.
     * Ported from Raven's drawRoundedRectangle.
     *
     * @param x      left edge
     * @param y      top edge
     * @param x2     right edge
     * @param y2     bottom edge
     * @param radius corner radius
     * @param color  ARGB fill color
     */
    public static void drawRoundedRectangle(float x, float y, float x2, float y2, float radius, int color) {
        if (x2 <= x) return;
        float width = x2 - x;
        if (width < 3) {
            radius = Math.min(radius, width / 2.0f);
        }

        x *= 2.0f;
        y *= 2.0f;
        x2 *= 2.0f;
        y2 *= 2.0f;
        radius *= 2.0f;

        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBegin(GL11.GL_POLYGON);
        glColor(color);
        for (int i = 0; i <= 90; i += 3) {
            double a = i * 0.017453292f;
            GL11.glVertex2d(x + radius + Math.sin(a) * radius * -1.0, y + radius + Math.cos(a) * radius * -1.0);
        }
        for (int j = 90; j <= 180; j += 3) {
            double a = j * 0.017453292f;
            GL11.glVertex2d(x + radius + Math.sin(a) * radius * -1.0, y2 - radius + Math.cos(a) * radius * -1.0);
        }
        if (x2 - x >= 9.0f) {
            for (int k = 0; k <= 90; k += 1) {
                double a = k * 0.017453292f;
                GL11.glVertex2d(x2 - radius + Math.sin(a) * radius, y2 - radius + Math.cos(a) * radius);
            }
            for (int l = 90; l <= 180; l += 1) {
                double a = l * 0.017453292f;
                GL11.glVertex2d(x2 - radius + Math.sin(a) * radius, y + radius + Math.cos(a) * radius);
            }
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Draws a rounded rectangle with a gradient outline (two-color border).
     * Ported from Raven's drawRoundedGradientOutlinedRectangle.
     *
     * @param x      left edge
     * @param y      top edge
     * @param x2     right edge
     * @param y2     bottom edge
     * @param radius corner radius
     * @param fill   ARGB fill color
     * @param outline1 first outline color (top-left)
     * @param outline2 second outline color (bottom-right)
     */
    public static void drawRoundedGradientOutlinedRectangle(float x, float y, float x2, float y2,
                                                             float radius, int fill, int outline1, int outline2) {
        x *= 2.0f;
        y *= 2.0f;
        x2 *= 2.0f;
        y2 *= 2.0f;
        radius *= 2.0f;

        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        // Fill
        GL11.glBegin(GL11.GL_POLYGON);
        glColor(fill);
        for (int i = 0; i <= 90; i += 3) {
            double a = i * 0.017453292f;
            GL11.glVertex2d(x + radius + Math.sin(a) * radius * -1.0, y + radius + Math.cos(a) * radius * -1.0);
        }
        for (int j = 90; j <= 180; j += 3) {
            double a = j * 0.017453292f;
            GL11.glVertex2d(x + radius + Math.sin(a) * radius * -1.0, y2 - radius + Math.cos(a) * radius * -1.0);
        }
        for (int k = 0; k <= 90; k += 3) {
            double a = k * 0.017453292f;
            GL11.glVertex2d(x2 - radius + Math.sin(a) * radius, y2 - radius + Math.cos(a) * radius);
        }
        for (int l = 90; l <= 180; l += 3) {
            double a = l * 0.017453292f;
            GL11.glVertex2d(x2 - radius + Math.sin(a) * radius, y + radius + Math.cos(a) * radius);
        }
        GL11.glEnd();

        // Gradient outline
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glLineWidth(2.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        if (outline1 != 0) glColor(outline1);
        for (int i = 0; i <= 90; i += 3) {
            double a = i * 0.017453292f;
            GL11.glVertex2d(x + radius + Math.sin(a) * radius * -1.0, y + radius + Math.cos(a) * radius * -1.0);
        }
        for (int j = 90; j <= 180; j += 3) {
            double a = j * 0.017453292f;
            GL11.glVertex2d(x + radius + Math.sin(a) * radius * -1.0, y2 - radius + Math.cos(a) * radius * -1.0);
        }
        if (outline2 != 0) glColor(outline2);
        for (int k = 0; k <= 90; k += 3) {
            double a = k * 0.017453292f;
            GL11.glVertex2d(x2 - radius + Math.sin(a) * radius, y2 - radius + Math.cos(a) * radius);
        }
        for (int l = 90; l <= 180; l += 3) {
            double a = l * 0.017453292f;
            GL11.glVertex2d(x2 - radius + Math.sin(a) * radius, y + radius + Math.cos(a) * radius);
        }
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Draws a simple outlined rounded rectangle with a single border color.
     */
    public static void drawRoundedOutlinedRectangle(float x, float y, float x2, float y2,
                                                     float radius, int fill, int outline) {
        drawRoundedGradientOutlinedRectangle(x, y, x2, y2, radius, fill, outline, outline);
    }

    /**
     * Draws a filled rounded rectangle with a horizontal gradient fill.
     * Ported from Raven's drawRoundedGradientRect — the four color args mirror
     * the original (topLeft, topRight, bottomLeft, bottomRight) so a horizontal
     * gradient is produced by passing (left, right, left, right).
     *
     * @param x          left edge
     * @param y          top edge
     * @param x2         right edge (drawn up to this width, like Raven)
     * @param y2         bottom edge
     * @param radius     corner radius
     * @param topLeft    fill color at the top-left
     * @param topRight   fill color at the top-right
     * @param bottomLeft fill color at the bottom-left
     * @param bottomRight fill color at the bottom-right
     */
    public static void drawRoundedGradientRect(float x, float y, float x2, float y2, float radius,
                                               int topLeft, int topRight, int bottomLeft, int bottomRight) {
        if (x2 <= x) return;
        float width = x2 - x;
        if (width < 3) {
            radius = Math.min(radius, width / 2.0f);
        }

        x *= 2.0f;
        y *= 2.0f;
        x2 *= 2.0f;
        y2 *= 2.0f;
        radius *= 2.0f;

        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        GL11.glBegin(GL11.GL_POLYGON);
        // Top-left corner — topLeft color
        glColor(topLeft);
        for (int i = 0; i <= 90; i += 3) {
            double a = i * 0.017453292f;
            GL11.glVertex2d(x + radius + Math.sin(a) * radius * -1.0, y + radius + Math.cos(a) * radius * -1.0);
        }
        // Top-right corner — topRight color
        glColor(topRight);
        for (int k = 0; k <= 90; k += 3) {
            double a = k * 0.017453292f;
            GL11.glVertex2d(x2 - radius + Math.sin(a) * radius, y + radius + Math.cos(a) * radius);
        }
        // Bottom-right corner — bottomRight color
        glColor(bottomRight);
        for (int k = 0; k <= 90; k += 3) {
            double a = k * 0.017453292f;
            GL11.glVertex2d(x2 - radius + Math.sin(a) * radius, y2 - radius + Math.cos(a) * radius);
        }
        // Bottom-left corner — bottomLeft color
        glColor(bottomLeft);
        for (int j = 90; j <= 180; j += 3) {
            double a = j * 0.017453292f;
            GL11.glVertex2d(x + radius + Math.sin(a) * radius * -1.0, y2 - radius + Math.cos(a) * radius * -1.0);
        }
        GL11.glEnd();

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Darkens a color by reducing each RGB channel by the given percentage.
     * Ported from Raven's Utils.darkenColor.
     */
    public static int darkenColor(int color, int percent) {
        int r = (color >> 16 & 255) * (100 - percent) / 100;
        int g = (color >> 8 & 255) * (100 - percent) / 100;
        int b = (color & 255) * (100 - percent) / 100;
        int a = (color >> 24 & 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
