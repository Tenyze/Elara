package elara.property.properties;

import com.google.gson.JsonObject;
import elara.property.Property;
import elara.util.ChatUtil;

/**
 * A button property that executes an action when clicked.
 * Not persisted to config (actions are not state).
 * The click() method is wrapped in try-catch so any exception
 * in the callback is reported via chat instead of crashing the game.
 */
public class ButtonProperty extends Property<Boolean> {
    private final Runnable action;

    public ButtonProperty(String name, Runnable action) {
        super(name, false, null);
        this.action = action;
    }

    public void click() {
        if (action == null) return;
        try {
            action.run();
        } catch (Throwable t) {
            ChatUtil.sendFormatted("&cButton '" + getName() + "' error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    @Override
    public String getValuePrompt() {
        return "button";
    }

    @Override
    public String formatValue() {
        return "&b[Click]";
    }

    @Override
    public boolean parseString(String string) {
        click();
        return true;
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        return false;
    }

    @Override
    public void write(JsonObject jsonObject) {
    }
}
