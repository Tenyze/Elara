package elara.module.misc;

import elara.module.Module;
import elara.ui.ClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private ClickGui ravenGui;

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (ravenGui == null) {
            ravenGui = new ClickGui();
        }
        mc.displayGuiScreen(ravenGui);
    }
}
