package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.AttackEvent;
import elara.events.SlowDownEvent;
import elara.events.UpdateEvent;
import net.minecraft.client.Minecraft;

public class IntaveVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    private boolean attacked = false;
    private boolean slowedDown = false;

    public IntaveVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        if (this.attacked && !this.slowedDown && mc.thePlayer.isSprinting()) {
            mc.thePlayer.motionX *= 0.6;
            mc.thePlayer.motionZ *= 0.6;
            mc.thePlayer.setSprinting(false);
        }

        this.attacked = false;
        this.slowedDown = false;
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        // Rise HitSlowDownEvent: this.slowedDown = true
        this.slowedDown = true;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        this.attacked = true;
    }
}
