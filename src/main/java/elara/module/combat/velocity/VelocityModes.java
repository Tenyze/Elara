package elara.module.combat.velocity;

import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.AttackEvent;
import elara.events.MoveInputEvent;
import elara.events.PacketEvent;
import elara.events.SlowDownEvent;
import elara.events.StrafeEvent;
import elara.events.TickEvent;
import elara.events.UpdateEvent;
import elara.module.combat.Velocity;

/**
 * Dispatches events from {@link Velocity} to the currently-selected submode.
 * <p>
 * Mode indices match {@link Velocity#MODE_LIST}:
 * 0=Standard, 1=BufferAbuse, 2=Delay, 3=Legit/JumpReset, 4=Polar (LegitVelocity dup),
 * 5=Ground, 6=Intave, 7=Matrix, 8=AAC, 9=Vulcan, 10=Tick, 11=Bounce, 12=Karhu,
 * 13=MMC, 14=Universocraft, 15=GrimReduce, 16=Grim, 17=Grim2, 18=Watchdog,
 * 19=WatchdogPrediction, 20=WatchdogReduce.
 * <p>
 * Removed from the original 22-entry list: GrimTest (was index 7) and
 * WatchdogDamageBoostFlyDisabler (was index 18).
 */
public class VelocityModes {
    private final Velocity parent;

    public final StandardVelocity standard;
    public final AACVelocity aac;
    public final BounceVelocity bounce;
    public final BufferAbuseVelocity bufferAbuse;
    public final DelayVelocity delay;
    public final Grim2Velocity grim2;
    public final GrimReduceVelocity grimReduce;
    public final GrimVelocity grim;
    public final GroundVelocity ground;
    public final IntaveVelocity intave;
    public final KarhuVelocity karhu;
    public final LegitVelocity legit;
    public final MMCVelocity mmc;
    public final MatrixVelocity matrix;
    public final TickVelocity tick;
    public final UniversocraftVelocity universocraft;
    public final VulcanVelocity vulcan;
    public final WatchdogPredictionVelocity watchdogPrediction;
    public final WatchdogReduceVelocity watchdogReduce;
    public final WatchdogVelocity watchdog;

    public VelocityModes(Velocity parent) {
        this.parent = parent;
        this.standard = new StandardVelocity(parent);
        this.aac = new AACVelocity(parent);
        this.bounce = new BounceVelocity(parent);
        this.bufferAbuse = new BufferAbuseVelocity(parent);
        this.delay = new DelayVelocity(parent);
        this.grim2 = new Grim2Velocity(parent);
        this.grimReduce = new GrimReduceVelocity(parent);
        this.grim = new GrimVelocity(parent);
        this.ground = new GroundVelocity(parent);
        this.intave = new IntaveVelocity(parent);
        this.karhu = new KarhuVelocity(parent);
        this.legit = new LegitVelocity(parent);
        this.mmc = new MMCVelocity(parent);
        this.matrix = new MatrixVelocity(parent);
        this.tick = new TickVelocity(parent);
        this.universocraft = new UniversocraftVelocity(parent);
        this.vulcan = new VulcanVelocity(parent);
        this.watchdogPrediction = new WatchdogPredictionVelocity(parent);
        this.watchdogReduce = new WatchdogReduceVelocity(parent);
        this.watchdog = new WatchdogVelocity(parent);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        switch (parent.mode.getValue()) {
            case 0: standard.onPacket(event); break;
            case 1: bufferAbuse.onPacket(event); break;
            case 2: delay.onPacket(event); break;
            case 3: legit.onPacket(event); break;
            case 4: legit.onPacket(event); break; // Polar reuses LegitVelocity
            case 5: ground.onPacket(event); break;
            // Intave (6) has no onPacket handler.
            case 7: matrix.onPacket(event); break;
            case 8: aac.onPacket(event); break;
            case 9: vulcan.onPacket(event); break;
            case 10: tick.onPacket(event); break;
            case 11: bounce.onPacket(event); break;
            case 12: karhu.onPacket(event); break;
            case 13: mmc.onPacket(event); break;
            case 14: universocraft.onPacket(event); break;
            case 15: grimReduce.onPacket(event); break;
            case 16: grim.onPacket(event); break;
            case 17: grim2.onPacket(event); break;
            case 18: watchdog.onPacket(event); break;
            case 19: watchdogPrediction.onPacket(event); break;
            case 20: watchdogReduce.onPacket(event); break;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        switch (parent.mode.getValue()) {
            case 0: standard.onUpdate(event); break;
            case 3: legit.onUpdate(event); break;
            case 4: legit.onUpdate(event); break; // Polar reuses LegitVelocity
            case 5: ground.onUpdate(event); break;
            case 6: intave.onUpdate(event); break;
            case 8: aac.onUpdate(event); break;
            case 10: tick.onUpdate(event); break;
            case 11: bounce.onUpdate(event); break;
            case 12: karhu.onUpdate(event); break;
            case 13: mmc.onUpdate(event); break;
            case 16: grim.onUpdate(event); break;
            case 18: watchdog.onUpdate(event); break;
            case 19: watchdogPrediction.onUpdate(event); break;
            // Universocraft (14), BufferAbuse (1), Delay (2), Matrix (7),
            // Vulcan (9), GrimReduce (15), Grim2 (17), WatchdogReduce (20)
            // have no onUpdate handler.
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        switch (parent.mode.getValue()) {
            case 2: delay.onTick(event); break;
            // BufferAbuse (1) and Tick (10) previously dispatched here but their
            // submode classes no longer declare onTick - they use onUpdate instead.
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        switch (parent.mode.getValue()) {
            case 3: legit.onMoveInput(event); break;
            case 4: legit.onMoveInput(event); break; // Polar reuses LegitVelocity
            case 5: ground.onMoveInput(event); break;
            case 8: aac.onMoveInput(event); break;
            case 13: mmc.onMoveInput(event); break;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        switch (parent.mode.getValue()) {
            case 7: matrix.onStrafe(event); break;
        }
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        switch (parent.mode.getValue()) {
            case 6: intave.onSlowDown(event); break;
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        switch (parent.mode.getValue()) {
            case 6: intave.onAttack(event); break;
        }
    }

    public void onEnable() {
        bufferAbuse.onEnable();
        grim.onEnable();
    }

    public void onDisable() {
        bufferAbuse.onDisable();
        delay.onDisable();
        grim.onDisable();
    }
}
