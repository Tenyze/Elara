package elara.module.movement;

import elara.event.EventTarget;
import elara.events.MotionEvent;
import elara.events.PacketEvent;
import elara.events.PlayerUpdateEvent;
import elara.events.RightClickMouseEvent;
import elara.events.SlowDownEvent;
import elara.events.StrafeEvent;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.module.movement.noslow.NoSlowModes;
import elara.property.properties.BooleanProperty;
import elara.property.properties.ModeProperty;

public class NoSlow extends Module {
    public static final String[] MODE_LIST = new String[]{
            "Vanilla", "NCP", "NewNCP", "Intave", "Legit",
            "WatchdogPrediction", "Variable", "Prediction",
            "Watchdog", "Grim19", "Grim", "Grim30", "Matrix"
    };

    public final ModeProperty mode = new ModeProperty("Mode", 0, MODE_LIST);

    public final BooleanProperty food = new BooleanProperty("Food", false);
    public final BooleanProperty potion = new BooleanProperty("Potion", false);
    public final BooleanProperty sword = new BooleanProperty("Sword", false);
    public final BooleanProperty bow = new BooleanProperty("Bow", false);

    private final NoSlowModes modes = new NoSlowModes(this);

    public NoSlow() {
        super("NoSlow", false, false, "", ModuleCategory.MOVEMENT);
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (!this.isEnabled()) return;
        modes.onSlowDown(event);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (!this.isEnabled()) return;
        modes.onMotion(event);
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled()) return;
        modes.onPlayerUpdate(event);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        modes.onPacket(event);
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (!this.isEnabled()) return;
        modes.onRightClick(event);
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled()) return;
        modes.onStrafe(event);
    }

    @Override
    public void onDisabled() {
        modes.onDisable();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }
}
