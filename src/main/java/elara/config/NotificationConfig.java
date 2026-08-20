package elara.config;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Slider;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;

public class NotificationConfig extends Config {

    public static NotificationConfig INSTANCE;

    // ===== 全局设置 =====
    @Switch(name = "Enable All Notifications", description = "Master switch for all notifications")
    public boolean enabled = true;

    @Slider(name = "Default Duration (ms)", min = 1000, max = 10000, step = 500)
    public int defaultDuration = 3500;

    @Slider(name = "Short Duration (ms)", min = 1000, max = 5000, step = 500)
    public int shortDuration = 2500;

    @Switch(name = "Show Icons", description = "Display icons in notifications")
    public boolean showIcons = true;

    // ===== HackerDetector 专用设置 =====
    @Switch(name = "HackerDetector Alerts", description = "Enable/disable hacker detection alerts")
    public boolean hackerDetectorEnabled = true;

    @Slider(name = "HackerDetector Cooldown (s)", min = 0, max = 120, step = 1)
    public int hackerDetectorCooldown = 20;

    @Switch(name = "HackerDetector Sound", description = "Play a 'pling' sound when a hacker is detected")
    public boolean hackerDetectorSound = true;

    // ----- 构造器（使用你提供的正确写法）-----
    public NotificationConfig(Mod modData, String configFile, boolean enabled, boolean canToggle) {
        super(modData, configFile, enabled, canToggle);
        initialize();
    }

    // ----- 初始化方法 -----
    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new NotificationConfig(
                    new Mod("Notifications", ModType.PVP),
                    "elara/notifications.json",
                    true,
                    true
            );
        }
    }
}