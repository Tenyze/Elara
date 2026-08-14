package elara.module;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.KeyEvent;
import elara.events.TickEvent;
import elara.module.misc.GuiModule;
import elara.module.render.HUD;
import elara.util.ChatUtil;
import elara.util.KeyBindUtil;
import elara.util.SoundUtil;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private boolean sound = false;
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();

    public static void get(Class<HUD> hudClass) {
    }

    public Module getModule(String string) {
        return this.modules.values().stream().filter(mD -> mD.getName().equalsIgnoreCase(string)).findFirst().orElse(null);
    }

    public Module getModule(Class<?> clazz){
        return this.modules.get(clazz);
    }

    public List<Module> getModulesByCategory(ModuleCategory category) {
        return this.modules.values().stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }

    public List<Module> getEnabledModules() {
        return this.modules.values().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());
    }

    public List<Module> getEnabledModulesByCategory(ModuleCategory category) {
        return this.modules.values().stream()
                .filter(module -> module.isEnabled() && module.getCategory() == category)
                .collect(Collectors.toList());
    }

    public List<Module> getAllModules() {
        return new ArrayList<>(this.modules.values());
    }

    public void playSound() {
        this.sound = true;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        for (Module module : this.modules.values()) {
            if (module.getKey() != event.getKey()) {
                continue;
            }
            // Hold 模式不在按键事件里 toggle，由 onTick 轮询按键状态处理
            if (module.holdMode.getValue()) {
                continue;
            }
            boolean shouldNotify = module.toggle();
            HUD hud = (HUD) this.modules.get(HUD.class);
            if (hud != null && shouldNotify) {
                shouldNotify = hud.toggleAlerts.getValue();
            }
            if(module instanceof GuiModule){
                shouldNotify = false;
            }
            if (shouldNotify) {
                String status = module.isEnabled() ? "&a&lON" : "&c&lOFF";
                String message = String.format("%s%s: %s&r", Elara.clientName, module.getName(), status);
                ChatUtil.sendFormatted(message);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            // Hold 模式：按住绑定键启用，松开禁用
            Minecraft mc = Minecraft.getMinecraft();
            for (Module module : this.modules.values()) {
                if (!module.holdMode.getValue()) continue;
                int key = module.getKey();
                if (key == 0) continue;
                boolean keyDown = mc.currentScreen == null && KeyBindUtil.isKeyDown(key);
                if (keyDown && !module.isEnabled()) {
                    module.setHoldState(true);
                } else if (!keyDown && module.isEnabled()) {
                    module.setHoldState(false);
                }
            }
            if (this.sound) {
                this.sound = false;
                SoundUtil.playSound("random.click");
            }
        }
    }
}
