package elara.property.properties;

import com.google.gson.JsonObject;
import elara.property.Property;

import java.util.function.BooleanSupplier;

/**
 * ColorProperty — stores colors in full ARGB format (0xAARRGGBB).
 *
 * <p>Persists as 8-digit hex (#AARRGGBB) to preserve the alpha channel.
 * Backwards-compatible: when loading a legacy 6-digit RGB value, alpha defaults to 0xFF.
 */
public class ColorProperty extends Property<Integer> {
    public ColorProperty(String name, Integer color) {
        this(name, color, null);
    }

    public ColorProperty(String string, Integer color, BooleanSupplier check) {
        super(string, color, argb -> true, check);
    }

    @Override
    public String getValuePrompt() {
        return "ARGB";
    }

    @Override
    public String formatValue() {
        int value = this.getValue();
        String hex = String.format("%08X", value);
        String a = hex.substring(0, 2);
        String r = hex.substring(2, 4);
        String g = hex.substring(4, 6);
        String b = hex.substring(6, 8);
        return String.format("&8%s&c%s&a%s&9%s", a, r, g, b);
    }

    @Override
    public boolean parseString(String string) {
        String cleaned = string.replace("#", "");
        int len = cleaned.length();
        if (len == 8) {
            // Full ARGB hex
            return this.setValue((int) Long.parseLong(cleaned, 16));
        } else if (len == 6) {
            // Legacy RGB hex — default alpha to 0xFF
            return this.setValue((int) Long.parseLong(cleaned, 16) | 0xFF000000);
        }
        return false;
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        try {
            return this.parseString(jsonObject.get(this.getName()).getAsString());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), String.format("%08X", this.getValue()));
    }
}
