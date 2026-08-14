package elara.module;

import elara.Elara;
import elara.config.NotificationHelper;
import elara.module.render.HUD;
import elara.property.properties.BooleanProperty;
import elara.util.KeyBindUtil;

import java.util.function.BooleanSupplier;

public abstract class Module {
    protected final String name;
    protected final String description;
    protected final boolean defaultEnabled;
    protected final int defaultKey;
    protected final boolean defaultHidden;
    protected final ModuleCategory category;
    protected boolean enabled;
    protected int key;
    protected boolean hidden;

    /** Hold 模式：按住绑定键启用，松开禁用（false=Toggle 切换模式）*/
    public final BooleanProperty holdMode = new BooleanProperty("Hold", false);
    /** Hidden 设置：在模块的设置面板里以普通 checkbox 形式切换隐藏状态。与 {@link #hidden} 字段双向同步。 */
    public final BooleanProperty hiddenProperty = new HiddenProperty(this);

    public Module(String name, boolean enabled) {
        this(name, enabled, false, "", ModuleCategory.MISC);
    }

    public Module(String name, boolean enabled, boolean hidden) {
        this(name, enabled, hidden, "", ModuleCategory.MISC);
    }

    public Module(String name, boolean enabled, boolean hidden, String description) {
        this(name, enabled, hidden, description, ModuleCategory.MISC);
    }

    public Module(String name, boolean enabled, boolean hidden, String description, ModuleCategory category) {
        this.name = name;
        this.description = description;
        this.enabled = this.defaultEnabled = enabled;
        this.key = this.defaultKey = 0;
        this.hidden = this.defaultHidden = hidden;
        this.category = category;
    }

    public ModuleCategory getCategory() {
        return this.category;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public String formatModule() {
        return String.format(
                "%s%s &r(%s&r)",
                this.key == 0 ? "" : String.format("&l[%s] &r", KeyBindUtil.getKeyName(this.key)),
                this.name,
                this.enabled ? "&a&lON" : "&c&lOFF"
        );
    }

    public String[] getSuffix() {
        return new String[0];
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                this.onEnabled();
            } else {
                this.onDisabled();
            }
            NotificationHelper.sendModuleToggle(this.name, enabled);
        }
    }

    /**
     * Hold 模式专用：按住/松开时切换状态，不触发通知和音效。
     */
    public void setHoldState(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                this.onEnabled();
            } else {
                this.onDisabled();
            }
        }
    }

    public boolean toggle() {
        boolean enabled = !this.enabled;
        this.setEnabled(enabled);
        if (this.enabled == enabled) {
            if (((elara.module.render.HUD) Elara.moduleManager.modules.get(elara.module.render.HUD.class)).toggleSound.getValue()) {
                Elara.moduleManager.playSound();
            }
            return true;
        } else {
            return false;
        }
    }

    public int getKey() {
        return this.key;
    }

    public void setKey(int integer) {
        this.key = integer;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean boolean1) {
        this.hidden = boolean1;
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }

    public void verifyValue(String string) {
    }

    /** 重置模块状态和所有属性为构造时的默认值 */
    public void resetDefaults() {
        if (this.key != this.defaultKey) {
            this.key = this.defaultKey;
        }
        if (this.hidden != this.defaultHidden) {
            this.hidden = this.defaultHidden;
        }
        // 先重置属性（避免notify），再处理开关
        try {
            if (Elara.propertyManager != null) {
                java.util.ArrayList<elara.property.Property<?>> props =
                        Elara.propertyManager.properties.get(this.getClass());
                if (props != null) {
                    for (elara.property.Property<?> p : props) {
                        if (p != null) p.resetToDefault();
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (this.enabled != this.defaultEnabled) {
            this.setEnabled(this.defaultEnabled);
        }
        // 同步 hiddenProperty 的默认值（避免 PropertyManager reset 时与 defaultHidden 不一致）
        try {
            this.hiddenProperty.setValue(this.defaultHidden);
        } catch (Throwable ignored) {}
    }

    /**
     * 一个特殊的 BooleanProperty：它的值并不存储在 Property.value 里，
     * 而是直接代理到宿主 Module 的 {@link Module#hidden hidden} 字段。
     * 这样 HideCommand、Config、WaterMark 等直接操作 hidden 字段的旧代码，
     * 与 ClickGui 中通过 Settings 里的 checkbox 设置值能保持完全同步。
     */
    public static final class HiddenProperty extends BooleanProperty {
        private final Module owner;

        public HiddenProperty(Module owner) {
            super("Hidden", owner.defaultHidden);
            this.owner = owner;
        }

        @Override
        public Boolean getValue() {
            return this.owner.hidden;
        }

        @Override
        public boolean setValue(Object object) {
            if (!(object instanceof Boolean)) return false;
            this.owner.hidden = (Boolean) object;
            if (this.owner != null) {
                this.owner.verifyValue(this.getName());
            }
            return true;
        }

        @Override
        public void resetToDefault() {
            this.setValue(this.owner.defaultHidden);
        }

        @Override
        public String formatValue() {
            return this.getValue() ? "&aON" : "&cOFF";
        }
    }
}
