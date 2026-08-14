package elara.ui.components;

import elara.enums.ChatColors;
import elara.property.properties.ButtonProperty;
import elara.ui.Component;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Button component — renders a clickable action button.
 * On left-click, fires the property's action callback.
 */
public class ButtonComponent implements Component {
    private final ButtonProperty property;
    private final ModuleComponent module;
    private int offsetY;
    private int x;
    private int y;

    public ButtonComponent(ButtonProperty property, ModuleComponent parentModule, int offsetY) {
        this.property = property;
        this.module = parentModule;
        this.x = parentModule.category.getX() + parentModule.category.getWidth();
        this.y = parentModule.category.getY() + parentModule.offsetY;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String display = ChatColors.formatColor("&b» &f" + this.property.getName().replace("-", " ") + " &b«");
        Minecraft.getMinecraft().fontRendererObj.drawString(
            display,
            (float) ((this.module.category.getX() + 4) * 2),
            (float) ((this.module.category.getY() + this.offsetY + 5) * 2),
            -1, false);
        GL11.glPopMatrix();
    }

    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return 12;
    }

    public void update(int mousePosX, int mousePosY) {
        this.y = this.module.category.getY() + this.offsetY;
        this.x = this.module.category.getX();
    }

    public void mouseDown(int x, int y, int button) {
        if (!this.module.panelExpand) return;
        this.update(x, y);
        if (this.isHovered(x, y) && button == 0) {
            this.property.click();
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
    }

    public boolean isHovered(int x, int y) {
        return x > this.x && x < this.x + this.module.category.getWidth() && y > this.y && y < this.y + 11;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }
}
