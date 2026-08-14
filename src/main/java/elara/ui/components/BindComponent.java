package elara.ui.components;

import elara.Elara;
import elara.module.misc.GuiModule;
import elara.module.render.HUD;
import elara.ui.Component;
import elara.ui.dataset.BindStage;
import elara.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Key binding component ported from Raven's BindComponent.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Left-click to enter binding mode ("Press a key...")</li>
 *   <li>Press any keyboard key to bind it</li>
 *   <li>Press ESCAPE or DELETE/BACKSPACE to unbind (set to 0)</li>
 *   <li>Click any mouse button (button &gt; 1) to bind it as mouse button</li>
 *   <li>Scroll up/down to bind as MouseScrollUp/MouseScrollDown</li>
 *   <li>GuiModule is always bound to key 54 (Raven behavior)</li>
 * </ul>
 */
public class BindComponent implements Component {
    private boolean isBinding;
    private final ModuleComponent parentModule;
    private int offsetY;
    private int x;
    private int y;

    public BindComponent(ModuleComponent b, int offsetY) {
        this.parentModule = b;
        this.x = b.category.getX() + b.category.getWidth();
        this.y = b.category.getY() + b.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String displayText = this.isBinding ? BindStage.binding : BindStage.bind + ": " + getKeyDisplayString();
        this.renderText(displayText, ((HUD) Elara.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis(), offset.get()).getRGB());
        GL11.glPopMatrix();
    }

    /**
     * Returns the display string for the current key bind.
     * Supports keyboard keys, mouse buttons, and scroll directions.
     */
    private String getKeyDisplayString() {
        int key = this.parentModule.mod.getKey();
        return KeyBindUtil.getKeyName(key);
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.category.getX();
    }

    public void mouseDown(int x, int y, int button) {
        if (this.isBinding) {
            // In binding mode: mouse buttons > 0 bind as mouse buttons (Elara convention: button - 100)
            if (button > 0) {
                this.parentModule.mod.setKey(button - 100);
                this.isBinding = false;
            }
            return;
        }

        if (!this.parentModule.panelExpand) return;
        this.update(x, y);
        if (this.isHovered(x, y) && button == 0) {
            this.isBinding = !this.isBinding;
        }
    }

    /**
     * Handles scroll wheel input for binding.
     * Scroll up = 1069, Scroll down = 1070.
     */
    public void onScroll(int scrollDirection) {
        if (!isBinding) return;
        this.parentModule.mod.setKey(scrollDirection > 0 ? 1069 : 1070);
        this.isBinding = false;
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
        if (!this.isBinding) return;

        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
            // Unbind: set to 0, except GuiModule which always binds to 54
            if (this.parentModule.mod instanceof GuiModule) {
                this.parentModule.mod.setKey(54);
            } else {
                this.parentModule.mod.setKey(0);
            }
        } else {
            this.parentModule.mod.setKey(keyCode);
        }

        this.isBinding = false;
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    public boolean isHovered(int x, int y) {
        return x > this.x && x < this.x + this.parentModule.category.getWidth()
                && y > this.y - 1 && y < this.y + 12;
    }

    public int getHeight() {
        return 12;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    public boolean isBinding() {
        return isBinding;
    }

    private void renderText(String s, int color) {
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(s,
                (float) ((this.parentModule.category.getX() + 4) * 2),
                (float) ((this.parentModule.category.getY() + this.offsetY + 3) * 2),
                color);
    }
}
