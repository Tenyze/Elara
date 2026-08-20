package elara.module.combat;

import elara.event.EventTarget;
import elara.events.AttackEvent;
import elara.events.MoveInputEvent;
import elara.events.PacketEvent;
import elara.events.SlowDownEvent;
import elara.events.StrafeEvent;
import elara.events.TickEvent;
import elara.events.UpdateEvent;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.module.combat.velocity.VelocityModes;
import elara.property.properties.BooleanProperty;
import elara.property.properties.ModeProperty;
import elara.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // 21 entries: Standard, BufferAbuse, Delay, Legit/JumpReset, Polar (LegitVelocity dup),
    // Ground, Intave, Matrix, AAC, Vulcan, Tick, Bounce, Karhu, MMC, Universocraft,
    // GrimReduce, Grim, Grim2, Watchdog, WatchdogPrediction, WatchdogReduce.
    // (Removed GrimTest and WatchdogDamageBoostFlyDisabler per port spec.)
    public static final String[] MODE_LIST = new String[]{
            "Standard", "BufferAbuse", "Delay", "Legit/JumpReset", "Polar",
            "Ground", "Intave", "Matrix", "AAC", "Vulcan",
            "Tick", "Bounce", "Karhu", "MMC", "Universocraft",
            "GrimReduce", "Grim", "Grim2", "Watchdog", "WatchdogPrediction", "WatchdogReduce"
    };

    public final ModeProperty mode = new ModeProperty("Mode", 0, MODE_LIST);
    public final BooleanProperty onSwing = new BooleanProperty("On Swing", false);

    private final VelocityModes modes = new VelocityModes(this);

    public final PercentProperty horizontal = new PercentProperty("Horizontal", 0, () -> mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("Vertical", 0, () -> mode.getValue() == 0);
    public final BooleanProperty explosionIgnore = new BooleanProperty("Explosion Ignore", false, () -> mode.getValue() == 0);

    public Velocity() {
        super("Velocity", false, false, "", ModuleCategory.COMBAT);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        modes.onPacket(event);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        modes.onUpdate(event);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) return;
        modes.onTick(event);
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled()) return;
        modes.onMoveInput(event);
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled()) return;
        modes.onStrafe(event);
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (!this.isEnabled()) return;
        modes.onSlowDown(event);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        modes.onAttack(event);
    }

    @Override
    public void onEnabled() {
        modes.onEnable();
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
