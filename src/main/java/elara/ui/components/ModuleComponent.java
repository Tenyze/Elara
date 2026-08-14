package elara.ui.components;

import elara.Elara;
import elara.module.Module;
import elara.module.render.HUD;
import elara.property.Property;
import elara.property.properties.*;
import elara.ui.AnimationTimer;
import elara.ui.Component;
import elara.ui.RavenRenderUtils;
import elara.ui.dataset.impl.FloatSlider;
import elara.ui.dataset.impl.IntSlider;
import elara.ui.dataset.impl.PercentageSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Module entry component for the Raven-style ClickGUI.
 *
 * <p>Features ported from Raven's ModuleComponent:</p>
 * <ul>
 *   <li>Smooth hover effect with alpha transition (75ms)</li>
 *   <li>Smooth expand/collapse animation for settings (250ms)</li>
 *   <li>Enabled modules use theme/rainbow color, disabled use gray</li>
 *   <li>Scissor-based clipping during expand/collapse animation</li>
 * </ul>
 */
public class ModuleComponent implements Component {
    // Raven-style colors
    private static final int HOVER_ALPHA = 120;
    private static final int HOVER_COLOR = new Color(0, 0, 0, HOVER_ALPHA).getRGB();
    private static final int ENABLED_COLOR = new Color(24, 154, 255).getRGB();
    private static final int DISABLED_COLOR = new Color(192, 192, 192).getRGB();
    private static final float ANIMATION_DURATION = 250.0f;
    private static final float HOVER_DURATION = 75.0f;

    public Module mod;
    public CategoryComponent category;
    public int offsetY;
    private final ArrayList<Component> settings;
    public boolean panelExpand;

    // Animation state
    private boolean hovering = false;
    private AnimationTimer hoverTimer;
    private boolean hoverStarted = false;
    private AnimationTimer expandTimer;
    private float animationStartHeight = 16;
    private float animationTargetHeight = 16;
    private float smoothingHeight = 16;

    public ModuleComponent(Module mod, CategoryComponent category, int offsetY) {
        this.mod = mod;
        this.category = category;
        this.offsetY = offsetY;
        this.settings = new ArrayList<>();
        this.panelExpand = false;
        int y = offsetY + 12;
        if (Elara.propertyManager.properties.get(mod.getClass()) != null && !Elara.propertyManager.properties.get(mod.getClass()).isEmpty()) {
            for (Property<?> baseProperty : Elara.propertyManager.properties.get(mod.getClass())) {
                if (baseProperty instanceof BooleanProperty) {
                    BooleanProperty property = (BooleanProperty) baseProperty;
                    CheckBoxComponent c = new CheckBoxComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof FloatProperty) {
                    FloatProperty property = (FloatProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new FloatSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof IntProperty) {
                    IntProperty property = (IntProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new IntSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof PercentProperty) {
                    PercentProperty property = (PercentProperty) baseProperty;
                    SliderComponent c = new SliderComponent(new PercentageSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ModeProperty) {
                    ModeProperty property = (ModeProperty) baseProperty;
                    ModeComponent c = new ModeComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ColorProperty) {
                    ColorProperty property = (ColorProperty) baseProperty;
                    ColorSliderComponent c = new ColorSliderComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof TextProperty) {
                    TextProperty property = (TextProperty) baseProperty;
                    TextComponent c = new TextComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ButtonProperty) {
                    ButtonProperty property = (ButtonProperty) baseProperty;
                    ButtonComponent c = new ButtonComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                }
            }
        }

        this.settings.add(new BindComponent(this, y));
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
        int y = this.offsetY + 16;

        for (Component c : this.settings) {
            c.setComponentStartAt(y);
            if (c.isVisible()) {
                y += c.getHeight();
            }
        }
    }

    public void draw(AtomicInteger offset) {
        // Update expand/collapse animation
        if (expandTimer != null) {
            if (!expandTimer.isActive()) {
                this.smoothingHeight = this.animationTargetHeight;
                this.expandTimer = null;
            } else {
                this.smoothingHeight = expandTimer.getValueFloat(this.animationStartHeight, this.animationTargetHeight);
            }
        }

        // Current rendered height of this module entry.
        float currentHeight = expandTimer != null ? smoothingHeight : getHeight();

        // Compute scissor: intersection of this module's bounds with the category panel's
        // visible module area. This prevents any part of the module (name, hover, settings)
        // from rendering outside the panel, and also keeps CategoryComponent's scissor
        // state from being broken for subsequent modules.
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        double scale = sr.getScaleFactor();

        int moduleTop = category.getY() + offsetY;
        int moduleBottom = (int) (category.getY() + offsetY + currentHeight);
        int panelModuleTop = category.getY() + category.getHeaderHeight() + 3;
        int panelModuleBottom = category.getY() + category.getDisplayHeight() - 4;
        int screenBottom = sr.getScaledHeight() - 1;
        panelModuleBottom = Math.min(panelModuleBottom, screenBottom);

        int clipTop = Math.max(moduleTop, panelModuleTop);
        int clipBottom = Math.min(moduleBottom, panelModuleBottom);
        if (clipBottom <= clipTop) {
            // Module is completely outside the visible panel area; nothing to draw.
            return;
        }

        int scissorY = Math.max(0, (int) ((sr.getScaledHeight() - clipBottom) * scale));
        int scissorH = Math.max(0, (int) ((clipBottom - clipTop) * scale));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (category.getX() * scale), scissorY,
                (int) (category.getWidth() * scale), scissorH);

        // Draw hover effect (smooth alpha transition)
        if (hovering || hoverTimer != null) {
            double hoverAlpha;
            if (hovering && hoverTimer != null) {
                hoverAlpha = hoverTimer.getValueFloat(0, HOVER_ALPHA);
            } else if (hoverTimer != null && !hovering) {
                hoverAlpha = HOVER_ALPHA - hoverTimer.getValueFloat(0, HOVER_ALPHA);
            } else {
                hoverAlpha = HOVER_ALPHA;
            }
            if (hoverAlpha == 0) {
                hoverTimer = null;
            }
            int mergedColor = RavenRenderUtils.mergeAlpha(HOVER_COLOR, (int) hoverAlpha);
            RavenRenderUtils.drawRoundedRectangle(
                    category.getX(), category.getY() + offsetY,
                    category.getX() + category.getWidth(), category.getY() + 16 + offsetY,
                    8, mergedColor);
        }

        // Draw module name with appropriate color
        int textColor;
        if (this.mod.isEnabled()) {
            textColor = ((HUD) Elara.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis(), offset.get()).getRGB();
        } else {
            textColor = DISABLED_COLOR;
        }

        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
                this.mod.getName(),
                (float) (this.category.getX() + this.category.getWidth() / 2 - Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.mod.getName()) / 2),
                (float) (this.category.getY() + this.offsetY + 4),
                textColor);

        // Draw settings
        if ((this.panelExpand || expandTimer != null) && !this.settings.isEmpty()) {
            GL11.glPushMatrix();
            for (Component c : this.settings) {
                if (c.isVisible()) {
                    c.draw(offset);
                    offset.incrementAndGet();
                }
            }
            GL11.glPopMatrix();
        }
    }

    public int getHeight() {
        if (expandTimer != null) {
            return (int) smoothingHeight;
        }
        if (!this.panelExpand) {
            return 16;
        } else {
            int h = 16;
            for (Component c : this.settings) {
                if (c.isVisible()) {
                    h += c.getHeight();
                }
            }
            return h;
        }
    }

    public void update(int mousePosX, int mousePosY) {
        // Update hover state
        boolean nowHovering = isHovered(mousePosX, mousePosY);
        if (nowHovering && !hovering) {
            hoverTimer = new AnimationTimer(HOVER_DURATION);
            hoverTimer.start();
            hoverStarted = true;
        } else if (!nowHovering && hovering && hoverStarted) {
            hoverTimer = new AnimationTimer(HOVER_DURATION);
            hoverTimer.start();
        }
        hovering = nowHovering;
        if (!nowHovering) hoverStarted = false;

        if (!panelExpand && expandTimer == null) return;
        if (!this.settings.isEmpty()) {
            for (Component c : this.settings) {
                if (c.isVisible()) {
                    c.update(mousePosX, mousePosY);
                }
            }
        }
    }

    public void mouseDown(int x, int y, int button) {
        if (this.isHovered(x, y) && button == 0) {
            this.mod.toggle();
            return;
        }

        if (this.isHovered(x, y) && button == 1) {
            toggleExpand();
            return;
        }

        if (!panelExpand && expandTimer == null) return;

        float visibleHeight = expandTimer != null ? smoothingHeight : getHeight();
        float clickY = y - (category.getY() + offsetY);
        if (clickY < 0 || clickY > visibleHeight) return;

        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.mouseDown(x, y, button);
            }
        }
    }

    /**
     * Toggles the settings panel with smooth animation.
     */
    private void toggleExpand() {
        this.panelExpand = !this.panelExpand;
        this.animationStartHeight = expandTimer != null ? smoothingHeight : (panelExpand ? 16 : getHeight());
        this.animationTargetHeight = panelExpand ? computeExpandedHeight() : 16;
        this.expandTimer = new AnimationTimer(ANIMATION_DURATION);
        this.expandTimer.start();
    }

    /**
     * Computes the expanded height (header + max 5 visible settings).
     */
    private int computeExpandedHeight() {
        int h = 16;
        int count = 0;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                h += c.getHeight();
                count++;
                if (count >= 5) break; // 限制最多显示5个设置
            }
        }
        return h;
    }

    public void mouseReleased(int x, int y, int button) {
        if (!panelExpand && expandTimer == null) return;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.mouseReleased(x, y, button);
            }
        }
    }

    public void keyTyped(char chatTyped, int keyCode) {
        if (!panelExpand && expandTimer == null) return;
        for (Component c : this.settings) {
            if (c.isVisible()) {
                c.keyTyped(chatTyped, keyCode);
            }
        }
    }

    public boolean isHovered(int x, int y) {
        return x > this.category.getX() && x < this.category.getX() + this.category.getWidth()
                && y > this.category.getY() + this.offsetY
                && y < this.category.getY() + 16 + this.offsetY;
    }

    /**
     * Returns the list of setting components for this module.
     * Used by ClickGui to propagate scroll events to BindComponents.
     */
    public ArrayList<Component> getSettingsList() {
        return settings;
    }

    @Override
    public boolean isVisible() {
        return true;
    }
}
