package elara.ui.components;

import elara.module.Module;
import elara.ui.AnimationTimer;
import elara.ui.Component;
import elara.ui.RavenRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Category panel for the Raven-style ClickGUI.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Rounded rectangle background with gradient outline</li>
 *   <li>Smooth open/close animation with height clamped to screen space</li>
 *   <li>Scissor-based content clipping for scroll</li>
 * </ul>
 */
public class CategoryComponent {
    // 12 visible modules = 192px tall, matching standard Raven ClickGUI density.
    // computeTargetHeight / update will additionally clamp
    // this value against available screen space so the panel never overflows.
    private static final int MAX_VISIBLE_MODULES = 12;
    private static final int MODULE_HEIGHT = 16;
    private static final int MAX_HEIGHT = MAX_VISIBLE_MODULES * MODULE_HEIGHT;
    private static final float OPEN_ANIMATION_DURATION = 200.0f;
    // Dynamic max height based on MAX_HEIGHT and available screen space.
    // Used both for the panel target height and the scroll cap calculation.
    private int dynamicMaxHeight = MAX_HEIGHT;

    // Performance optimization: cached ScaledResolution
    private ScaledResolution cachedResolution = null;
    private long lastResolutionUpdate = 0L;

    // Raven-style colors
    private static final int TRANSLUCENT_BACKGROUND = new Color(0, 0, 0, 110).getRGB();
    private static final int DARK_BACKGROUND = new Color(0, 0, 0, 200).getRGB();
    private static final int REGULAR_OUTLINE = new Color(81, 99, 149).getRGB();
    private static final int REGULAR_OUTLINE2 = new Color(97, 67, 133).getRGB();
    private static final int CATEGORY_NAME_COLOR = new Color(220, 220, 220).getRGB();

    public ArrayList<Component> modulesInCategory = new ArrayList<>();
    public String categoryName;
    private boolean categoryOpened;
    private int width;
    private int y;
    private int x;
    private final int bh;
    public boolean dragging;
    public int xx;
    public int yy;
    public boolean pin = false;
    private double marginY, marginX;
    private int scroll = 0;
    private double animScroll = 0;
    private int height = 0;
    private int displayHeight = 0;
    private float targetHeight = 0;
    private float animationStartHeight = 0;
    private AnimationTimer openTimer;

    public CategoryComponent(String category, List<Module> modules) {
        this.categoryName = category;
        this.width = 92;
        this.x = 5;
        this.y = 5;
        this.bh = 13;
        this.xx = 0;
        this.categoryOpened = false;
        this.dragging = false;
        int tY = this.bh + 3;
        this.marginX = 80;
        this.marginY = 4.5;
        for (Module mod : modules) {
            ModuleComponent b = new ModuleComponent(mod, this, tY);
            this.modulesInCategory.add(b);
            tY += 16;
        }
        this.displayHeight = this.bh + 4;
        this.targetHeight = this.bh + 4;
        this.animationStartHeight = this.bh + 4;
    }

    public ArrayList<Component> getModules() {
        return this.modulesInCategory;
    }

    public void setX(int n) {
        this.x = n;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void mousePressed(boolean d) {
        this.dragging = d;
    }

    public boolean isPin() {
        return this.pin;
    }

    public void setPin(boolean on) {
        this.pin = on;
    }

    public boolean isOpened() {
        return this.categoryOpened;
    }

    public void setOpened(boolean on) {
        this.categoryOpened = on;
        this.animationStartHeight = this.displayHeight;
        this.targetHeight = on ? computeTargetHeight() : (this.bh + 4);
        this.openTimer = new AnimationTimer(OPEN_ANIMATION_DURATION);
        this.openTimer.start();
    }

    /**
     * Returns how far the panel is opened, from 0.0 (fully closed) to 1.0 (fully open).
     */
    private float getOpenProgress() {
        float closedH = this.bh + 4;
        float openH = this.targetHeight;
        if (openH <= closedH) return this.categoryOpened ? 1.0f : 0.0f;
        return Math.max(0.0f, Math.min(1.0f, (this.displayHeight - closedH) / (openH - closedH)));
    }

    /**
     * Computes the target height when the category is opened (title + visible modules).
     * The panel is bounded by both MAX_HEIGHT and the available vertical space between
     * the panel's current y position and the bottom of the screen (with a 4px margin).
     * If the category has fewer modules than the maximum visible count, the panel shrinks
     * to fit the actual content. An empty category reserves exactly one module height.
     */
    private float computeTargetHeight() {
        int screenH = Minecraft.getMinecraft().displayHeight;
        try {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            screenH = sr.getScaledHeight();
        } catch (Exception ignored) {}
        int available = Math.max(screenH - this.y - 4, this.bh + 8);
        int maxVisibleModuleH = Math.min(MAX_HEIGHT, available - this.bh - 7);
        maxVisibleModuleH = Math.max(maxVisibleModuleH, 0);

        int actualContentH = 0;
        for (Component c : this.modulesInCategory) {
            actualContentH += c.getHeight();
        }
        if (this.modulesInCategory.isEmpty()) {
            actualContentH = MODULE_HEIGHT;
        }

        int visibleModuleH = Math.min(actualContentH, maxVisibleModuleH);
        visibleModuleH = Math.max(visibleModuleH, MODULE_HEIGHT);
        return this.bh + 3 + visibleModuleH + 4;
    }

    public void render(FontRenderer renderer) {
        this.width = 92;
        update();
        height = 0;
        for (Component moduleRenderManager : this.modulesInCategory) {
            height += moduleRenderManager.getHeight();
        }
        int maxScroll = Math.max(0, height - dynamicMaxHeight);
        if (scroll > maxScroll) scroll = maxScroll;
        if (animScroll > maxScroll) animScroll = maxScroll;
        animScroll += (scroll - animScroll) * 0.2;

        // Compute title text position (smoothly transitions between left-aligned
        // when closed and centered when opened).
        float closedNamePos = this.x + 2;
        float openedNamePos = this.x + this.width / 2.0f - renderer.getStringWidth(this.categoryName) / 2.0f;
        float progress = getOpenProgress();
        float namePos = closedNamePos + (openedNamePos - closedNamePos) * progress;

        // Cache ScaledResolution for performance
        long now = System.currentTimeMillis();
        if (cachedResolution == null || now - lastResolutionUpdate > 50L) {
            cachedResolution = new ScaledResolution(Minecraft.getMinecraft());
            lastResolutionUpdate = now;
        }

        // Draw the category panel with unified background color
        int panelBottom = this.y + this.displayHeight;
        // Clamp panel bottom to screen bounds to prevent overflow
        panelBottom = Math.min(panelBottom, cachedResolution.getScaledHeight() - 2);
        RavenRenderUtils.drawRoundedGradientOutlinedRectangle(
                this.x - 2, this.y,
                this.x + this.width + 2, panelBottom,
                6, TRANSLUCENT_BACKGROUND, REGULAR_OUTLINE, REGULAR_OUTLINE2);

        // Draw category name
        renderer.drawString(this.categoryName, namePos, this.y + 4, CATEGORY_NAME_COLOR, false);

        // Draw open/close indicator
        renderer.drawString(this.categoryOpened ? "-" : "+",
                (float) (this.x + marginX), (float) ((double) this.y + marginY),
                Color.white.getRGB(), false);

        // Draw modules with scissor clipping
        if (this.categoryOpened && !this.modulesInCategory.isEmpty()) {
            int moduleDisplayHeight = (int) Math.min(
                    Math.min(this.displayHeight - this.bh - 7, this.dynamicMaxHeight),
                    height);
            if (moduleDisplayHeight < 0) moduleDisplayHeight = 0;

            double scale = cachedResolution.getScaleFactor();
            int moduleAreaTop = this.y + this.bh + 3;
            // Clamp bottom to at most screenHeight - 1, so the scissor box never
            // inverts (negative height) or spills past the framebuffer edge.
            int maxBottom = cachedResolution.getScaledHeight() - 1;
            int bottom = Math.min(moduleAreaTop + moduleDisplayHeight, maxBottom);
            int clippedHeight = Math.max(bottom - moduleAreaTop, 0);

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            // Screen Y in GL is inverted (0 = bottom), so translate accordingly.
            int scissorY = Math.max(0, (int) ((cachedResolution.getScaledHeight() - bottom) * scale));
            int scissorH = Math.max(0, (int) (clippedHeight * scale));
            int scissorX = Math.max(0, (int) (this.x * scale));
            int scissorW = Math.max(0, (int) (this.width * scale));
            GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

            int renderHeight = 0;
            for (Component c2 : this.modulesInCategory) {
                int compHeight = c2.getHeight();
                if (renderHeight + compHeight > animScroll &&
                        renderHeight < animScroll + moduleDisplayHeight) {
                    int drawY = (int) (renderHeight - animScroll);
                    c2.setComponentStartAt(this.bh + 3 + drawY);
                    c2.draw(new AtomicInteger(0));
                }
                renderHeight += compHeight;
            }

            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            // Scroll bar indicator
            if (height > moduleDisplayHeight) {
                float scrollY = (float) this.y + this.bh + 3 + (float) (animScroll * moduleDisplayHeight / height);
                RavenRenderUtils.drawRoundedRectangle(
                        this.x + this.width - 2, (int) scrollY,
                        this.x + this.width, (int) (scrollY + ((float) moduleDisplayHeight * moduleDisplayHeight / height)),
                        1, new Color(255, 255, 255, 60).getRGB());
            }
        }
    }

    public void update() {
        int offset = this.bh + 3;
        for (Component component : this.modulesInCategory) {
            component.setComponentStartAt(offset);
            offset += component.getHeight();
        }
        // Panel height is limited to MIN(MAX_HEIGHT, available screen space, actual content).
        // This ensures panels near the bottom of the screen do not overflow, panels with
        // few modules do not leave empty space, and the scroll cap matches the visible area.
        int screenH = Minecraft.getMinecraft().displayHeight;
        try {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            screenH = sr.getScaledHeight();
        } catch (Exception ignored) {}
        int available = Math.max(screenH - this.y - 4, this.bh + 8);
        int maxVisibleModuleH = Math.min(MAX_HEIGHT, available - this.bh - 7);
        maxVisibleModuleH = Math.max(maxVisibleModuleH, 0);

        int actualContentH = 0;
        for (Component c : this.modulesInCategory) {
            actualContentH += c.getHeight();
        }
        if (this.modulesInCategory.isEmpty()) {
            actualContentH = MODULE_HEIGHT;
        }

        this.dynamicMaxHeight = Math.min(actualContentH, maxVisibleModuleH);
        this.dynamicMaxHeight = Math.max(this.dynamicMaxHeight, MODULE_HEIGHT);

        // Animate panel height toward the target height.
        if (this.openTimer != null) {
            if (!this.openTimer.isActive()) {
                this.displayHeight = (int) this.targetHeight;
                this.openTimer = null;
            } else {
                this.displayHeight = (int) this.openTimer.getValueFloat(this.animationStartHeight, this.targetHeight);
            }
        }
        // Keep target height in sync with content changes while opened
        if (this.categoryOpened && this.openTimer == null) {
            this.targetHeight = computeTargetHeight();
            this.displayHeight = (int) this.targetHeight;
        }
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getDisplayHeight() {
        return this.displayHeight;
    }

    public int getHeaderHeight() {
        return this.bh;
    }

    public void handleDrag(int x, int y) {
        if (this.dragging) {
            this.setX(x - this.xx);
            this.setY(y - this.yy);
            clampPositionToScreen();
        }
    }

    /**
     * Prevents panels from being dragged so close to the screen bottom that they
     * get visually squashed to less than one closed panel height. The minimum
     * visible height of any panel is always the closed title-bar height (bh + 4).
     */
    private void clampPositionToScreen() {
        int screenH = Minecraft.getMinecraft().displayHeight;
        try {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            screenH = sr.getScaledHeight();
        } catch (Exception ignored) {}
        int minVisibleHeight = this.bh + 4; // one closed panel length
        int maxY = screenH - minVisibleHeight - 2;
        if (this.y > maxY) this.y = maxY;
        if (this.y < 2) this.y = 2;
    }

    public boolean isHovered(int x, int y) {
        // Pin button: rightmost 10px of the title bar (was x+79..x+92, which overlapped the
        // open/close region below). Disjoint from mousePressed(x,y) now.
        return x >= this.x + 82 && x <= this.x + this.width && (float) y >= (float) this.y + 2.0F && y <= this.y + this.bh + 1;
    }

    public boolean mousePressed(int x, int y) {
        // Open/close button: middle region of the title bar, disjoint from the pin button
        // on the right (was x+77..x+86, overlapping isHovered on x+79..x+86).
        return x >= this.x + 72 && x <= this.x + 82 && (float) y >= (float) this.y + 2.0F && y <= this.y + this.bh + 1;
    }

    public boolean insideArea(int x, int y) {
        return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.bh;
    }

    /**
     * Returns true if (x, y) falls inside this category's visible module region (below the
     * title bar, within the current panel height). Used by ClickGui to dispatch
     * module clicks to exactly one category instead of every open category. Does not expose
     * the private bh/displayHeight fields.
     */
    public boolean isPointInModuleArea(int x, int y) {
        if (!this.categoryOpened) return false;
        int moduleDisplayHeight = (int) Math.min(
                Math.min(this.displayHeight - this.bh - 7, this.dynamicMaxHeight),
                height);
        if (moduleDisplayHeight < 0) moduleDisplayHeight = 0;
        int moduleAreaTop = this.y + this.bh + 3;
        int moduleAreaBottom = moduleAreaTop + moduleDisplayHeight;
        // Extra screen clamp so a panel pushed partially off the bottom of the
        // window does not accept clicks in the off-screen region.
        int screenBottom;
        try {
            screenBottom = new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight() - 1;
        } catch (Exception ignored) {
            screenBottom = Integer.MAX_VALUE;
        }
        moduleAreaBottom = Math.min(moduleAreaBottom, screenBottom);
        return x >= this.x && x <= this.x + this.width
                && y >= moduleAreaTop && y <= moduleAreaBottom;
    }

    public String getName() {
        return categoryName;
    }

    public void setLocation(int parseInt, int parseInt1) {
        this.x = parseInt;
        this.y = parseInt1;
        clampPositionToScreen();
    }

    public void onScroll(int mouseX, int mouseY, int scrollAmount) {
        if (!categoryOpened || height <= dynamicMaxHeight) return;

        int areaTop = this.y + this.bh;
        int areaBottom = this.y + this.bh + dynamicMaxHeight;

        if (mouseX >= this.x && mouseX <= this.x + width && mouseY >= areaTop && mouseY <= areaBottom) {
            scroll -= scrollAmount * 12;
            scroll = Math.max(0, Math.min(scroll, height - dynamicMaxHeight));
        }
    }

    public void mouseDown(int x, int y, int button) {
        if (!this.categoryOpened) return;

        int moduleDisplayHeight = (int) Math.min(
                Math.min(this.displayHeight - this.bh - 7, this.dynamicMaxHeight),
                height);
        if (moduleDisplayHeight < 0) moduleDisplayHeight = 0;

        int renderHeight = 0;
        for (Component c : this.modulesInCategory) {
            int compHeight = c.getHeight();
            if (renderHeight + compHeight > animScroll &&
                    renderHeight < animScroll + moduleDisplayHeight) {
                int drawY = (int) (renderHeight - animScroll);
                c.setComponentStartAt(this.bh + 3 + drawY);

                float panelTop = this.y + this.bh + 3 + drawY;
                float panelBottom = panelTop + compHeight;
                if (x >= this.x && x <= this.x + width && y >= panelTop && y < panelBottom) {
                    c.mouseDown(x, y, button);
                    return;
                }
            }
            renderHeight += compHeight;
        }
    }

    public void mouseReleased(int x, int y, int button) {
        if (!this.categoryOpened) return;

        int moduleDisplayHeight = (int) Math.min(
                Math.min(this.displayHeight - this.bh - 7, this.dynamicMaxHeight),
                height);
        if (moduleDisplayHeight < 0) moduleDisplayHeight = 0;

        int renderHeight = 0;
        for (Component c : this.modulesInCategory) {
            int compHeight = c.getHeight();
            if (renderHeight + compHeight > animScroll &&
                    renderHeight < animScroll + moduleDisplayHeight) {
                int drawY = (int) (renderHeight - animScroll);
                c.setComponentStartAt(this.bh + 3 + drawY);

                float panelTop = this.y + this.bh + 3 + drawY;
                float panelBottom = panelTop + compHeight;
                if (x >= this.x && x <= this.x + width && y >= panelTop && y < panelBottom) {
                    c.mouseReleased(x, y, button);
                    return;
                }
            }
            renderHeight += compHeight;
        }
    }

    public void keyTyped(char typedChar, int key) {
        if (!this.categoryOpened) return;

        int moduleDisplayHeight = (int) Math.min(
                Math.min(this.displayHeight - this.bh - 7, this.dynamicMaxHeight),
                height);
        if (moduleDisplayHeight < 0) moduleDisplayHeight = 0;

        int renderHeight = 0;
        for (Component c : this.modulesInCategory) {
            int compHeight = c.getHeight();
            if (renderHeight + compHeight > animScroll &&
                    renderHeight < animScroll + moduleDisplayHeight) {
                c.keyTyped(typedChar, key);
            }
            renderHeight += compHeight;
        }
    }
}
