package elara.module.combat;

import elara.event.EventTarget;
import elara.events.HitSlowDownEvent;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.property.properties.BooleanProperty;
import elara.property.properties.FloatProperty;
import elara.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty slowDownVelocity = new FloatProperty("Hit Slow Down During Velocity", 0.6F, 0F, 1F);
    public final FloatProperty slowDownNormal = new FloatProperty("Hit Slow Down Normal", 0.6F, 0F, 1F);
    public final BooleanProperty bufferAbuse = new BooleanProperty("Buffer Abuse", false);
    public final FloatProperty bufferDecrease = new FloatProperty("Buffer Decrease", 1F, 0.1F, 10F, () -> !bufferAbuse.getValue());
    public final IntProperty maxBuffer = new IntProperty("Max Buffer", 5, 1, 10, () -> !bufferAbuse.getValue());
    public final BooleanProperty sprintSlowDownVelocity = new BooleanProperty("Velocity Hit Sprint", false);
    public final BooleanProperty sprintSlowDownNormal = new BooleanProperty("Normal Hit Sprint", false);
    public final BooleanProperty onlyInAir = new BooleanProperty("Only In Air", false);

    private boolean resetting;
    private double combo;

    public KeepSprint() {
        super("KeepSprint", false, false, "", ModuleCategory.COMBAT);
    }

    @EventTarget
    public void onHitSlowDown(HitSlowDownEvent event) {
        if (!this.isEnabled()) return;
        if (!mc.thePlayer.onGround || !this.onlyInAir.getValue()) {
            if (this.bufferAbuse.getValue()) {
                if (this.combo < this.maxBuffer.getValue() && !this.resetting) {
                    this.combo++;
                } else {
                    if (this.combo > 0.0) {
                        this.combo = Math.max(0.0, this.combo - this.bufferDecrease.getValue());
                        this.resetting = true;
                        return;
                    }
                    this.resetting = false;
                }
            } else {
                this.combo = 0.0;
            }
            if (mc.thePlayer.hurtTime > 0) {
                event.setSlowDown(this.slowDownVelocity.getValue());
                event.setSprint(this.sprintSlowDownVelocity.getValue());
            } else {
                event.setSlowDown(this.slowDownNormal.getValue());
                event.setSprint(this.sprintSlowDownNormal.getValue());
            }
        }
    }
}
