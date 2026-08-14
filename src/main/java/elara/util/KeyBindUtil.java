package elara.util;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class KeyBindUtil {
    // Special key codes for scroll wheel binding (Raven convention)
    public static final int SCROLL_UP = 1069;
    public static final int SCROLL_DOWN = 1070;

    public static String getKeyName(int keyCode) {
        // Scroll wheel bindings
        if (keyCode == SCROLL_UP) return "MScrollUp";
        if (keyCode == SCROLL_DOWN) return "MScrollDown";
        if (keyCode < 0) {
            int mouseButton = keyCode + 100;
            switch (mouseButton) {
                case 0:
                    return "LMB";
                case 1:
                    return "RMB";
                case 2:
                    return "MMB";
                case 3:
                    return "MOUSE3";
                case 4:
                    return "MOUSE4";
                case 5:
                    return "MOUSE5";
                case 6:
                    return "MOUSE6";
                case 7:
                    return "MOUSE7";
                default:
                    String buttonName = Mouse.getButtonName(mouseButton);
                    return buttonName != null ? buttonName : "MOUSE" + mouseButton;
            }
        }
        return Keyboard.getKeyName(keyCode);
    }

    public static boolean isKeyDown(int keyCode) {
        // Scroll wheel is momentary, never "held down"
        if (keyCode == SCROLL_UP || keyCode == SCROLL_DOWN) return false;
        return keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
    }

    public static void updateKeyState(int keyCode) {
        KeyBindUtil.setKeyBindState(keyCode, keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode));
    }

    public static void setKeyBindState(int keyCode, boolean pressed) {
        KeyBinding.setKeyBindState(keyCode, pressed);
    }

    public static void pressKeyOnce(int keyCode) {
        KeyBinding.onTick(keyCode);
    }
}
